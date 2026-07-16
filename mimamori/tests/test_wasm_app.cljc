(ns mimamori.tests.test-wasm-app
  "mimamori wasm export-body tests -- dev-mode verification of the exact component surface.
  1:1 Clojure port of tests/test_wasm_app.py (stdlib asserts -> clojure.test).

  The Python test imports the four `etzhayyim:mimamori/mimamori-actor` world
  exports from `wasm/app.py` (heartbeat / coverage / bonds-of / vow). wasm/app.py
  has no .cljc sibling, so the four export BODIES are reconstructed here verbatim
  from the same actor siblings app.py composes (bond / coverage-report / kotoba /
  match / shakai). They stay STATELESS: heartbeat returns the content-addressed
  transaction for the host to append; the component never holds the log (G5 in the
  architecture). Run: bb test:mimamori.

  (No __main__ CLI dispatch: the Python `t(...)`/argv harness is replaced by deftest.)"
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :as json]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])
            [mimamori.methods.-edn :as edn]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.coverage-report :as cov]
            [mimamori.methods.kotoba :as kotoba]
            [mimamori.methods.match :as match]
            [mimamori.methods.shakai :as shakai]))

(def ^:private a "did:web:etzhayyim.com:member:fictional:aleph")
(def ^:private b "did:web:etzhayyim.com:member:fictional:bet")

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))

(def ^:private vow-text
  (str "問: 私は弟の保持者でしょうか (創4:9)。\n"
       "誓: 然り。私はあなたの保持者です。\n"
       "見張るのではなく、見守ります。裁くのではなく、傍にいます。\n"
       "私はまどろみ、眠る者です — 私が眠るとき、継ぐ者が見守ります (詩121:4)。\n"
       "あなたはいつでも、この縁を解くことができます。\n"
       "私が誰を見守っているか、あなたには常に見えています。\n"))

(defn- seed []
  (bond/load-seed-file (io/file actor-dir "data" "seed-mimamori-bonds.json")))

;; ── world export bodies (wasm/app.py shapes) ──────────────────────────────────
(defn- app-heartbeat
  ([cycle prev]
   (let [s (seed)
         engine (bond/replay s)
         [engine summary] (match/match-cycle engine (:roster s))
         c (cov/coverage-of-engine engine (:roster s))
         [ledger shakai-summary] (shakai/mint-from-keeping engine [] (int cycle))
         datoms (into (into (kotoba/bond-datoms engine)
                            (kotoba/coverage-datoms c (int cycle)))
                      (shakai/social-capital-datoms ledger (int cycle)))
         tx (kotoba/make-tx datoms {:tx-id (int cycle) :as-of (int cycle) :prev-cid prev})]
     (json/generate-string
      {"cid" (:tx/cid tx)
       "txEdn" (kotoba/tx->edn tx)
       "summary" (merge summary {"shakai" shakai-summary "coverage" c})}))))

(defn- app-coverage []
  (let [s (seed)]
    (cov/render (cov/coverage-of-engine (bond/replay s) (:roster s)))))

(defn- app-bonds-of [did]
  (json/generate-string (bond/bonds-of (bond/replay (seed)) did)))

(defn- app-vow [] vow-text)

;; ── tests ─────────────────────────────────────────────────────────────────────
(deftest test-heartbeat-stateless-deterministic
  (let [r1 (json/parse-string (app-heartbeat 1 ""))
        r2 (json/parse-string (app-heartbeat 1 ""))]
    (is (= (get r1 "cid") (get r2 "cid")))               ;; same (cycle, prev) -> same CID
    (let [r3 (json/parse-string (app-heartbeat 2 (get r1 "cid")))]
      (is (not= (get r3 "cid") (get r1 "cid"))))          ;; prev-linked chain
    (is (str/starts-with? (get r1 "txEdn") "{:tx/id 1 "))
    (is (not (str/includes? (json/generate-string (get r1 "summary")) "did:")))))  ;; G5

(deftest test-host-side-chain-verifies
  (let [r1 (json/parse-string (app-heartbeat 1 ""))
        r2 (json/parse-string (app-heartbeat 2 (get r1 "cid")))
        ;; the host can recompute both CIDs from the returned datoms via _edn parity:
        tx1 (edn/parse (edn/tokens (get r1 "txEdn")))
        tx2 (edn/parse (edn/tokens (get r2 "txEdn")))]
    (is (= (kotoba/tx-cid (get tx1 ":tx/datoms") "") (get r1 "cid")))
    (is (= (kotoba/tx-cid (get tx2 ":tx/datoms") (get r1 "cid")) (get r2 "cid")))))

(deftest test-bonds-of-own-did-only
  (let [mine (json/parse-string (app-bonds-of b))]
    (is (and (seq mine)
             (every? #(or (= b (get % "keeper")) (= b (get % "kept"))) mine)))  ;; G4
    (is (= [] (json/parse-string
               (app-bonds-of "did:web:etzhayyim.com:member:fictional:nobody"))))))  ;; G5

(deftest test-coverage-and-vow
  (let [c (app-coverage)]
    (is (and (not (str/includes? c "did:")) (str/includes? c "unkept")))  ;; aggregate-only
    (let [v (app-vow)]
      (is (str/includes? v "私は弟の保持者でしょうか"))
      (is (str/includes? v "継ぐ者が見守ります")))))
