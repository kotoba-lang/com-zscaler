;; test_budget_ledger.clj — budget-ledger ingest + byte-identical CID parity with budget_ledger.py.
;; Run: bb test_budget_ledger.clj   (or: clojure -M test_budget_ledger.clj)   from methods/.
(ns root.danjo.methods.test-budget-ledger
  (:require [clojure.string :as str]))

(load-file "budget_ledger.cljc")
(alias 'bl 'danjo.methods.budget-ledger)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))

;; ── byte-identical CID parity (golden values computed from budget_ledger.py) ──
;; Synthetic record (ASCII + Japanese + bool) — proves canonical-json-utf8 mirrors Python exactly.
(let [rec {"recordId" "r1" "sourceSensor" "mof" "fiscalYear" "2024"
           "recordKind" "appropriation" "programCode" "JP-MEXT-EDUSCI"
           "programName" "教育・科学技術" "amountLocal" 5000000000
           "jurisdiction" "jpn" "currencyIso4217" "JPY" "stateAlignedFlag" true}]
  (check "synthetic record CID == Python golden (canonical-json parity)"
         (= "gov.dataset.budgetRecord:mof:2024:r1#fefe2d55af03145ffcfd1f69"
            (bl/record-cid rec))))
;; NOTE: canonical-json-utf8 itself is `defn-` (private) in budget_ledger.cljc — its correctness
;; is exercised indirectly via the byte-identical CID goldens above/below (record-cid hashes its
;; output), not tested directly here.

;; ── normalize-record discipline ──
(check "normalize rejects unknown recordKind"
       (try (bl/normalize-record {"recordKind" "bribe" "amountLocal" 1}) false
            (catch Exception _ true)))
(check "normalize rejects negative amountLocal"
       (try (bl/normalize-record {"recordKind" "outlay" "amountLocal" -1}) false
            (catch Exception _ true)))
(check "normalize rejects non-integer amountLocal"
       (try (bl/normalize-record {"recordKind" "outlay" "amountLocal" "lots"}) false
            (catch Exception _ true)))
(let [ln (bl/normalize-record {"recordKind" "outlay" "amountLocal" 42 "fiscalYear" "2024"
                               "programCode" "P" "stateAlignedFlag" true})]
  (check "normalize line carries its own CID (G5)" (str/starts-with? (get ln "cid") "gov.dataset.budgetRecord:"))
  (check "normalize coerces fiscalYear to int" (= 2024 (get ln "fiscalYear")))
  (check "normalize passes stateAlignedFlag through (G13)" (true? (get ln "stateAlignedFlag"))))

;; ── G4: no adjudication field is computed (factual ledger structure only) ──
(let [ln (bl/normalize-record {"recordKind" "appropriation" "amountLocal" 1 "fiscalYear" 2024 "programCode" "P"})
      ks (set (map name (keys ln)))]
  (check "G4 — no verdict/crime/guilt key on a ledger line"
         (empty? (filter #(some (fn [t] (str/includes? (str/lower-case %) t))
                                ["verdict" "guilt" "crime" "violation" "illegal" "fraud"])
                         ks))))

;; ── end-to-end on the representative JP seed (parity with Python build_ledger) ──
;; NOTE: build-ledger/normalize-record return STRING-keyed maps (mirroring the JSON lexicon
;; field names 1:1), not keyword-keyed — access below uses (get ... "field"), not (:field ...).
(let [led (bl/build-ledger (bl/load-seed "../data/gov-fiscal-seed.jp.json"))
      g24 (get-in led ["groups" "JP-MEXT-EDUSCI|2024"])
      g23 (get-in led ["groups" "JP-MEXT-EDUSCI|2023"])]
  (check "seed → 5 ledger lines" (= 5 (count (get led "lines"))))
  (check "seed → 2 program-year groups" (= 2 (count (get led "groups"))))
  (check "2024 group: 1 appropriation / 2 outlay" (and (= 1 (count (get g24 "appropriations"))) (= 2 (count (get g24 "outlays")))))
  (check "2023 group: 1 appropriation / 1 outlay" (and (= 1 (count (get g23 "appropriations"))) (= 1 (count (get g23 "outlays")))))
  (check "first seed line CID == Python golden (real-data byte parity)"
         (= "gov.dataset.budgetRecord:jp_yosan:2024:br-jp-2024-mext-appropriation#53caa39f889350226d1eb77f"
            (get (first (get led "lines")) "cid")))
  (check "every line carries a source-record CID (G5)" (every? #(str/starts-with? (get % "cid") "gov.dataset.budgetRecord:") (get led "lines"))))

(println (format "── test_budget_ledger: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
