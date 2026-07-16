(ns fuchi.methods.test-live-gate
  "Tests for 扶持 (fuchi) live_gate.cljc + the per-engine live legs.
  1:1 Clojure port of methods/test_live_gate.py (clojure.test).

  NOTE on provenance: test_live_gate.py is the R1(live) suite — its core deliverable was
  'every outward leg REFUSES by default, and only fires when the operator flag + attestation
  + Council level + member signature are ALL present.' The live_gate source has since been
  advanced to R2 (Autonomous) (see live_gate.cljc / live_gate.py docstrings): the manual
  operator flags + Council manual signatures were removed and the gate now ALWAYS reports
  admissible and require-gate ALWAYS passes. The 'refused-by-default' assertions below are a
  faithful 1:1 port of the .py and therefore do NOT pass against the R2 source — the .py
  itself fails the same way against the R2 Python source. They are kept verbatim to remain a
  faithful translation; the still-valid legs (unknown-leg rejection, policy levels, the
  cash≡0 / no-server-key / timelock / G2 structural invariants, gated-OK paths) port cleanly.

  The Python '__main__'/_run demo is omitted (clojure.test provides the runner)."
  (:require [clojure.test :refer [deftest is testing]]
            [fuchi.methods.live-gate :as live-gate]
            [fuchi.methods.provision :as prov]
            [fuchi.methods.vote :as vote-mod]
            [fuchi.methods.book :as book]
            [fuchi.methods.couple :as couple-mod]))

;; ── helpers ───────────────────────────────────────────────────────────────
;; _env(leg, on=True) -> {flag: "1"} if on else {}
(defn- env
  ([leg] (env leg true))
  ([leg on]
   (let [flag (first (get live-gate/LEG-POLICY leg))]
     (if on {flag "1"} {}))))

;; _full_gate(leg, level=None)
(defn- full-gate
  ([leg] (full-gate leg nil))
  ([leg level]
   (let [lvl (if (nil? level) (second (get live-gate/LEG-POLICY leg)) level)]
     (live-gate/make-live-gate
      {:leg leg :operator-did "did:web:etzhayyim.com:operator:op1"
       :council-level lvl :member-signature "sig:member:abel:ed25519:deadbeef"}))))

;; require/gate_status are not :db/dynamic env-kwargs in clojure; pass env positionally.
(defn- require-g [gate env] (live-gate/require-gate gate env))
(defn- gate-status [gate env] (live-gate/gate-status gate env))

;; ── construction ────────────────────────────────────────────────────────────
(deftest test-unknown-leg-rejected
  (is (thrown? clojure.lang.ExceptionInfo
               (live-gate/make-live-gate {:leg "bogus"}))))

(deftest test-all-four-legs-known
  (is (= (set (keys live-gate/LEG-POLICY)) #{"provision" "vote" "book" "couple"})))

(deftest test-couple-requires-lv7-others-lv6
  (is (= (second (get live-gate/LEG-POLICY "couple")) 7))
  (is (= (second (get live-gate/LEG-POLICY "provision")) 6))
  (is (= (second (get live-gate/LEG-POLICY "vote")) 6))
  (is (= (second (get live-gate/LEG-POLICY "book")) 6)))

;; ── default refusal (the R1 deliverable; see ns note — fails against R2 source) ──
(deftest test-default-gate-refused-every-leg
  (doseq [leg (keys live-gate/LEG-POLICY)]
    (let [st (gate-status (live-gate/make-live-gate {:leg leg}) {})]
      (is (false? (get st "admissible")))
      (is (thrown? clojure.lang.ExceptionInfo
                   (require-g (live-gate/make-live-gate {:leg leg}) {}))))))

(deftest test-missing-operator-flag-refused
  (doseq [leg (keys live-gate/LEG-POLICY)]
    (let [g (full-gate leg)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"operator process flag"
                            (require-g g {}))))))

(deftest test-missing-attestation-refused
  (let [leg "provision"
        g (live-gate/make-live-gate
           {:leg leg :operator-did "" :council-level 6 :member-signature "sig:x"})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"operator attestation"
                          (require-g g (env leg))))))

(deftest test-insufficient-council-refused
  (let [leg "couple"  ;; needs Lv7
        g (live-gate/make-live-gate
           {:leg leg :operator-did "op" :council-level 6 :member-signature "sig:x"})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Lv7"
                          (require-g g (env leg))))))

(deftest test-lv6-ok-for-provision-not-couple
  ;; Lv6 satisfies provision but not couple
  (require-g (full-gate "provision" 6) (env "provision"))  ;; ok
  (is (thrown? clojure.lang.ExceptionInfo
               (require-g (full-gate "couple" 6) (env "couple")))))

(deftest test-server-signer-refused
  (doseq [sig ["" "server" "did:server:x" ":server" "anon" "  "]]
    (let [leg "vote"
          g (live-gate/make-live-gate
             {:leg leg :operator-did "op" :council-level 6 :member-signature sig})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"member signature|operator"
                            (require-g g (env leg)))))))

(deftest test-full-gate-admissible-every-leg
  (doseq [leg (keys live-gate/LEG-POLICY)]
    (let [st (require-g (full-gate leg) (env leg))]
      (is (true? (get st "admissible"))))))

;; ── provision.dispatch-live ──────────────────────────────────────────────────
(defn- intents []
  (prov/provision
   [{"kind" "food-mitsuho" "imputed_usd_micros_yr" 12000000000}
    {"kind" "liquidity-warifu" "imputed_usd_micros_yr" 3000000000
     "member_principal" true}]
   "alloc:abel"))

(deftest test-dispatch-live-refused-by-default
  (is (thrown? clojure.lang.ExceptionInfo
               (prov/dispatch-live (intents) (live-gate/make-live-gate {:leg "provision"})
                                   :env {}))))

(deftest test-dispatch-live-ok-when-gated
  (let [out (prov/dispatch-live (intents) (full-gate "provision") :env (env "provision"))]
    (is (= (count out) 2))
    ;; cash≡0 + no-server-key hold on the wrapped intent in live mode
    (is (every? #(= (get-in % [:intent :cash-usd-micros]) 0) out))
    (is (every? #(false? (get-in % [:intent :server-held-key])) out))
    ;; member-principal liquidity stays member-principal
    (is (some #(get-in % [:intent :member-principal]) out))))

(deftest test-dispatch-live-intent-stays-unpublished
  (let [out (prov/dispatch-live (intents) (full-gate "provision") :env (env "provision"))]
    (is (every? #(false? (get-in % [:intent :published])) out))  ;; G10 structural on the intent
    (is (every? #(:authorized-to-publish %) out))))              ;; authorization on the receipt

;; ── vote.finalize-binding ────────────────────────────────────────────────────
(defn- ballots []
  (vote-mod/ballots-from-seed
   [{":ballot/voter" "did:m:a" ":ballot/choice" "yes" ":ballot/cast-at" 10}
    {":ballot/voter" "did:m:b" ":ballot/choice" "yes" ":ballot/cast-at" 11}
    {":ballot/voter" "did:m:c" ":ballot/choice" "yes" ":ballot/cast-at" 12}]))

(deftest test-finalize-binding-refused-by-default
  (is (thrown? clojure.lang.ExceptionInfo
               (vote-mod/finalize-binding (ballots) 0 100 (live-gate/make-live-gate {:leg "vote"})
                                          :env {}))))

(deftest test-finalize-binding-ok-after-timelock
  (let [r (vote-mod/finalize-binding (ballots) 0 100 (full-gate "vote") :env (env "vote"))]
    (is (true? (get r "binding")))
    (is (= (get r "outcome") "accepted"))))

(deftest test-finalize-binding-timelock-still-strict
  ;; gated, but before the 48h window closes → the gate can't bypass the timelock
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"timelock"
                        (vote-mod/finalize-binding (ballots) 0 10 (full-gate "vote")
                                                   :env (env "vote")))))

;; ── book.write-live ──────────────────────────────────────────────────────────
(defn- ledger []
  (book/book-toritate
   [{"kind" "food-mitsuho" "imputed_usd_micros_yr" 12000000000}]
   "alloc:abel" "did:m:abel"))

(deftest test-write-live-refused-by-default
  (is (thrown? clojure.lang.ExceptionInfo
               (book/write-live (ledger) (live-gate/make-live-gate {:leg "book"}) :env {}))))

(deftest test-write-live-ok-when-gated-cash-zero
  (let [r (book/write-live (ledger) (full-gate "book") :env (env "book"))]
    (is (true? (:committed r)))
    (is (every? #(= (:cash-usd-micros %) 0) (:entries r)))))

;; ── couple.commit-live ───────────────────────────────────────────────────────
(defn- funded []
  (let [ev (couple-mod/make-displacement-event
            {:displacing-actor "sanae" :cohort-id "c-sanae" :displaced-count 12
             :surplus-usd-micros-yr 60000000000 :funded true})]
    [ev (couple-mod/earmark-from-surplus ev)]))

(defn- unfunded []
  (let [ev (couple-mod/make-displacement-event
            {:displacing-actor "hataori" :cohort-id "c-hataori" :displaced-count 30
             :surplus-usd-micros-yr 0 :funded false})]
    [ev (couple-mod/earmark-from-surplus ev)]))

(deftest test-commit-live-refused-without-gate
  (let [[ev em] (funded)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (couple-mod/commit-live ev em 8500000000 (live-gate/make-live-gate {:leg "couple"})
                                         :env {})))))

(deftest test-commit-live-ok-when-gated-and-funded
  (let [[ev em] (funded)
        c (couple-mod/commit-live ev em 8500000000 (full-gate "couple") :env (env "couple"))]
    (is (true? (:admissible c)))
    (is (= (:cohort-id c) "c-sanae"))))

(deftest test-commit-live-g2-refuses-unfunded-even-when-gated
  ;; gate passes (Lv7) but the G2 coupling gate refuses an unfunded cohort
  (let [[ev em] (unfunded)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2"
                          (couple-mod/commit-live ev em 1000000 (full-gate "couple")
                                                  :env (env "couple"))))))

(deftest test-commit-live-g2-refuses-over-earmark
  (let [[ev em] (funded)]  ;; earmark = 54_000_000_000
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"exceeds funded earmark"
                          (couple-mod/commit-live ev em 99000000000 (full-gate "couple")
                                                  :env (env "couple"))))))
