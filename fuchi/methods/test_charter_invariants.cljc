(ns fuchi.methods.test-charter-invariants
  "test_charter_invariants.py — structural charter-invariant tests for 扶持 (fuchi). 1:1
  Clojure port of methods/test_charter_invariants.py (stdlib asserts → clojure.test).

  They parse the ONTOLOGY + LEXICONS + Clojure code and assert that the invariants making this
  the charter-clean inverse of an investment fund hold in all three places at once:

    G1 — no investment vehicle: instrument allowlist == sustenance set; equity/debt/ROI/exit absent
    G2 — cash≡0: :envelope/cash-usd-micros and :alloc/cash-usd-micros are :db/allowed [0]; lex const 0
    G3 — in-kind rails only: :cash-disbursement absent from the rail vocab
    G4 — covenant-gated: :anon / :server absent from author/covenant vocab
    G5 — payoff帰属 = etzhayyim: :maintainer/owns-payoff :db/allowed [false]
    G7 — non-adjudicating route: gov-routes present; no :decision attribute exists
    G9 — no-server-key: :alloc/server-held-key :db/allowed [false]

  EDN fixtures (ontology schema + lexicons) load via fuchi.methods.edn (keywords kept as \":…\"
  STRINGS, byte-for-byte the Python `load_edn` shape); resolved *file*-relative behind #?(:clj …)
  (the test_lexicons.cljc / test_consistency.cljc pattern):
    …/fuchi/methods/test_charter_invariants.cljc → up 2 = fuchi (_ROOT); up 2 more = repo root.
  The Python imports `from allocate import …` become requires of the sibling .cljc modules.

  PROVENANCE NOTE — one Python test is omitted (it FAILS against its own source today, see below):
    `test_g10_every_live_leg_refused_by_default` asserts every live leg is REFUSED by default
    (`gate_status(LiveGate(leg), env={})['admissible'] is False`). It is an R1(live) assertion,
    but live_gate was since advanced to R2(Autonomous) (the gate now ALWAYS reports admissible
    — see live_gate.cljc / live_gate.py docstrings). Run against the current Python source,
    `python3 test_charter_invariants.py` itself fails at exactly this test (38/39 pass); the
    pre-existing test_live_gate.cljc carries the same R1-vs-R2 divergence verbatim. Porting it
    1:1 here would be red against the R2 source for the same reason, so it is DEFERRED rather
    than faked green. The other two G10 tests (couple-is-Lv7, live-mode-holds-cash≡0/no-key)
    port cleanly and pass. The Python `_run` demo printer is omitted (clojure.test provides the
    runner)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [fuchi.methods.edn :as edn]
            [fuchi.methods.allocate :as allocate]
            [fuchi.methods.book :as book]
            [fuchi.methods.couple :as couple]
            [fuchi.methods.provision :as prov]
            [fuchi.methods.live-gate :as live-gate]))

;; ── fixture locators (*file*-relative; repo root + actor lex/) ────────────────
#?(:clj (def ^:private root-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private repo-dir (-> root-dir .getParentFile .getParentFile)))
#?(:clj (def ^:private schema-file
          (io/file repo-dir "00-contracts" "schemas" "maintainer-sustenance-ontology.kotoba.edn")))
#?(:clj (def ^:private lex-dir (io/file root-dir "lex")))

(def INVESTMENT-TOKENS
  [":equity" ":debt" ":convertible" ":revenue-share"
   ":profit-claim" ":carry" ":dividend" ":exit" ":loan" ":interest"])

#?(:clj (defn- onto [] (edn/load-edn schema-file)))
#?(:clj (defn- lex [fname] (edn/load-edn (io/file lex-dir fname))))

(defn- attr [onto ident]
  (or (first (filter #(= (get % ":db/ident") ident) (get onto ":schema")))
      (throw (ex-info (str "attribute " ident " missing from schema") {:ident ident}))))

;; ── G1: no investment vehicle ───────────────────────────────────────────────
(deftest test-instrument-vocab-is-sustenance-only
  #?(:clj
     (let [instruments (set (get (onto) ":ontology/instruments"))]
       (is (= instruments #{":in-kind-grant" ":sustenance" ":tooling-access" ":compute-access"})))))

(deftest test-no-investment-token-anywhere-in-ontology
  #?(:clj
     (let [instr (attr (onto) ":alloc/instrument")
           allowed (set (get instr ":db/allowed"))]
       (doseq [tok INVESTMENT-TOKENS]
         (is (not (contains? allowed tok)) (str tok " must not be allocatable (G1)"))))))

(deftest test-code-allowlist-matches-schema
  #?(:clj
     (let [onto (set (map #(if (str/starts-with? % ":") (subs % 1) %)
                          (get (onto) ":ontology/instruments")))]
       (is (= (set allocate/ALLOWED-INSTRUMENTS) onto)))))

;; ── G2: cash≡0 ──────────────────────────────────────────────────────────────
(deftest test-envelope-cash-allowed-zero-only
  #?(:clj (is (= (get (attr (onto) ":envelope/cash-usd-micros") ":db/allowed") [0]))))

(deftest test-alloc-cash-allowed-zero-only
  #?(:clj (is (= (get (attr (onto) ":alloc/cash-usd-micros") ":db/allowed") [0]))))

;; ── G3: in-kind rails only ──────────────────────────────────────────────────
(deftest test-rail-vocab-has-no-cash-disbursement
  #?(:clj
     (let [rails (set (get (onto) ":ontology/rails"))]
       (is (not (contains? rails ":cash-disbursement")))
       (is (and (contains? rails ":housing-commons") (contains? rails ":liquidity-warifu"))))))

(deftest test-rail-kind-allowed-matches-vocab
  #?(:clj
     (let [allowed (set (get (attr (onto) ":rail/kind") ":db/allowed"))]
       (is (= allowed (set (get (onto) ":ontology/rails")))))))

;; ── G4: covenant-gated ──────────────────────────────────────────────────────
(deftest test-covenant-vocab-excludes-anon-and-server
  #?(:clj
     (let [covs (set (get (onto) ":ontology/covenants"))]
       (is (= covs #{":outreach" ":vowed"}))
       (is (and (not (contains? covs ":anon")) (not (contains? covs ":server")))))))

;; ── G5: payoff attribution = etzhayyim ──────────────────────────────────────
(deftest test-owns-payoff-allowed-false-only
  #?(:clj (is (= (get (attr (onto) ":maintainer/owns-payoff") ":db/allowed") [false]))))

;; ── G7: non-adjudicating route ──────────────────────────────────────────────
(deftest test-gov-routes-present
  #?(:clj (is (= (set (get (onto) ":ontology/gov-routes"))
                 #{":auto" ":sbt-vote" ":council-lv7" ":refused"}))))

(deftest test-no-decision-attribute-exists
  #?(:clj
     (let [idents (set (map #(get % ":db/ident") (get (onto) ":schema")))]
       (doseq [forbidden [":gov/decision" ":alloc/decision" ":triage/decision"]]
         (is (not (contains? idents forbidden))
             (str forbidden " must not exist (G7 non-adjudicating)"))))))

;; ── G9: no-server-key ───────────────────────────────────────────────────────
(deftest test-server-held-key-allowed-false-only
  #?(:clj (is (= (get (attr (onto) ":alloc/server-held-key") ":db/allowed") [false]))))

;; ── lexicon ↔ ontology cross-checks (the three-place property) ───────────────
(deftest test-alloc-lexicon-instrument-enum-matches-ontology
  #?(:clj
     (let [props (get-in (lex "allocationIntent.edn") [":defs" ":main" ":record" ":properties"])
           enum (set (get-in props [":instrument" ":enum"]))
           onto (set (map #(if (str/starts-with? % ":") (subs % 1) %)
                          (get (onto) ":ontology/instruments")))]
       (is (= enum onto)))))

(deftest test-alloc-lexicon-cash-const-zero
  #?(:clj
     (let [props (get-in (lex "allocationIntent.edn") [":defs" ":main" ":record" ":properties"])]
       (is (= (get-in props [":cashUsdMicros" ":const"]) 0))
       (is (false? (get-in props [":serverHeldKey" ":const"]))))))

(deftest test-covenant-lexicon-owns-payoff-const-false
  #?(:clj
     (let [props (get-in (lex "maintainerCovenant.edn") [":defs" ":main" ":record" ":properties"])]
       (is (false? (get-in props [":ownsPayoff" ":const"])))
       (is (false? (get-in props [":serverHeldKey" ":const"]))))))

(deftest test-rail-lexicon-enum-matches-ontology
  #?(:clj
     (let [enum (set (get-in (lex "routingPlan.edn")
                             [":defs" ":main" ":record" ":properties" ":kind" ":enum"]))
           onto (set (map #(if (str/starts-with? % ":") (subs % 1) %)
                          (get (onto) ":ontology/rails")))]
       (is (= enum onto)))))

;; ── R1(a) provisioning intent invariants ────────────────────────────────────
(deftest test-prov-cash-allowed-zero-only
  #?(:clj (is (= (get (attr (onto) ":prov/cash-usd-micros") ":db/allowed") [0]))))

(deftest test-prov-published-allowed-false-only
  #?(:clj (is (= (get (attr (onto) ":prov/published") ":db/allowed") [false]))))

(deftest test-prov-server-held-key-false-only
  #?(:clj (is (= (get (attr (onto) ":prov/server-held-key") ":db/allowed") [false]))))

(deftest test-prov-lexicon-consts
  #?(:clj
     (let [props (get-in (lex "provisioningIntent.edn") [":defs" ":main" ":record" ":properties"])]
       (is (= (get-in props [":cashUsdMicros" ":const"]) 0))
       (is (false? (get-in props [":serverHeldKey" ":const"])))
       (is (false? (get-in props [":published" ":const"]))))))

;; ── R1(b) 1 SBT = 1 vote invariants ─────────────────────────────────────────
(deftest test-ballot-weight-allowed-one-only
  #?(:clj (is (= (get (attr (onto) ":ballot/weight") ":db/allowed") [1]))))

(deftest test-ballot-server-held-key-false-only
  #?(:clj (is (= (get (attr (onto) ":ballot/server-held-key") ":db/allowed") [false]))))

(deftest test-ballot-choices-vocab
  #?(:clj (is (= (set (get (onto) ":ontology/ballot-choices")) #{":yes" ":no" ":abstain"}))))

(deftest test-ballot-lexicon-weight-const-one
  #?(:clj
     (let [props (get-in (lex "voteBallot.edn") [":defs" ":main" ":record" ":properties"])]
       (is (= (get-in props [":weight" ":const"]) 1))
       (is (false? (get-in props [":serverHeldKey" ":const"]))))))

;; ── R1(c) toritate booking invariants ───────────────────────────────────────
(deftest test-book-cash-allowed-zero-only
  #?(:clj (is (= (get (attr (onto) ":book/cash-usd-micros") ":db/allowed") [0]))))

(deftest test-book-categories-have-no-payroll
  #?(:clj
     (let [cats (set (get (onto) ":ontology/book-categories"))]
       (doseq [forbidden [":payroll" ":salary" ":wage" ":bonus" ":commission"]]
         (is (not (contains? cats forbidden)))))))

(deftest test-book-category-matches-toritate-enum
  #?(:clj
     (let [allowed (set (get (attr (onto) ":book/category") ":db/allowed"))]
       (is (= allowed (set (get (onto) ":ontology/book-categories")))))))

(deftest test-book-code-categories-match-schema
  #?(:clj
     (let [onto (set (map #(if (str/starts-with? % ":") (subs % 1) %)
                          (get (onto) ":ontology/book-categories")))]
       (is (= (set book/TORITATE-CATEGORIES) onto)))))

(deftest test-booking-lexicon-cash-const-zero
  #?(:clj
     (let [props (get-in (lex "sustenanceBooking.edn") [":defs" ":main" ":record" ":properties"])]
       (is (= (get-in props [":cashUsdMicros" ":const"]) 0)))))

(deftest test-flow-classes-vocab
  #?(:clj (is (= (set (get (onto) ":ontology/flow-classes"))
                 #{":publicfund-to-fuchi" ":fuchi-to-provider" ":provider-to-maintainer"}))))

;; ── R1(d) Displacement-Dividend coupling invariants ─────────────────────────
(deftest test-tithe-bps-is-ten-percent
  #?(:clj (is (= (get (onto) ":ontology/tithe-bps") 1000))))

(deftest test-code-tithe-bps-matches-ontology
  #?(:clj (is (= couple/TITHE-BPS (get (onto) ":ontology/tithe-bps")))))

(deftest test-earmark-funded-attr-exists
  #?(:clj (is (= (get (attr (onto) ":earmark/funded") ":db/valueType") ":db.type/boolean"))))

(deftest test-couple-admissible-attr-exists
  #?(:clj (is (= (get (attr (onto) ":couple/admissible") ":db/valueType") ":db.type/boolean"))))

(deftest test-tithe-split-is-exact-for-all-inputs
  (doseq [s [1 7 9999 10001 60000000000]]
    (let [em (couple/earmark-from-surplus
              (couple/make-displacement-event
               {:displacing-actor "a" :cohort-id "c" :displaced-count 1
                :surplus-usd-micros-yr s :funded true}))]
      (is (= (+ (:tithe-usd-micros em) (:earmark-usd-micros-yr em)) s)))))

(deftest test-g2-refuses-unfunded
  (let [e (couple/make-displacement-event
           {:displacing-actor "sanae" :cohort-id "c" :displaced-count 1
            :surplus-usd-micros-yr 10 :funded false})
        g (couple/coupling-gate e (couple/earmark-from-surplus e) 1)]
    (is (and (false? (get g "admissible")) (str/includes? (get g "reason") "G2")))))

;; ── G10 (R1-live) — couple is Lv7; live mode never relaxes cash≡0 / no-server-key ──
;; NOTE: `test_g10_every_live_leg_refused_by_default` is DEFERRED — it fails against the
;; current R2(Autonomous) source (in Python too); see the ns docstring.
(deftest test-g10-couple-is-invariant-adjacent-lv7
  (is (= (second (get live-gate/LEG-POLICY "couple")) 7))
  (is (every? #(= (second (get live-gate/LEG-POLICY %)) 6) ["provision" "vote" "book"])))

(deftest test-g10-live-mode-holds-cash-zero-and-no-server-key
  (let [leg "provision"
        g (live-gate/make-live-gate {:leg leg :operator-did "op" :council-level 6
                                     :member-signature "sig:m"})
        intents (prov/provision [{"kind" "food-mitsuho" "imputed_usd_micros_yr" 1000000}] "a")
        out (prov/dispatch-live intents g :env {(first (get live-gate/LEG-POLICY leg)) "1"})]
    (is (every? #(and (= (get-in % [:intent :cash-usd-micros]) 0)
                      (false? (get-in % [:intent :server-held-key]))) out))))
