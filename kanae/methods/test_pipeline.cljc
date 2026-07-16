(ns kanae.methods.test-pipeline
  "test_pipeline.py — danjo→kanae→yoro fiscal pipeline + charter-gate tests.
  1:1 Clojure port of methods/test_pipeline.py (stdlib unittest → clojure.test).

  Run: bb test:kanae   (from 20-actors as the bb source root)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [danjo.methods.budget-ledger :as bl]
            [kanae.methods.assemble-flows :as af]
            [kanae.methods.project-yoro :as py]))

;; ROOT/20-actors via *file* (…/20-actors/kanae/methods/test_pipeline.cljc → up 3 = 20-actors)
(def ^:private actors-dir (-> *file* io/file .getParentFile .getParentFile .getParentFile))
(def ^:private seed (io/file actors-dir "danjo" "data" "gov-fiscal-seed.jp.json"))
(def ^:private mext "did:web:etzhayyim.com:actor:gov-jp-mext")

(defn- group-by-prefix
  "Mirror of Python `_group`: collect datoms whose attr starts with prefix into
  per-entity maps {attr-tail → json.loads(v_edn)}; return the values."
  [datoms prefix]
  (->> datoms
       (reduce (fn [acc d]
                 (if (str/starts-with? (get d "a") prefix)
                   (let [attr (second (str/split (get d "a") #"/" 2))]
                     (assoc-in acc [(get d "e") attr] (bl/parse-json (get d "v_edn"))))
                   acc))
               {})
       vals
       vec))

(defn- asof-ok [observed asof]
  (cond
    (not (and asof (not= "" asof)))         true
    (not (and observed (not= "" observed))) true
    :else (<= (compare (str observed) (str asof "T23:59:59.999Z")) 0)))

(defn- ledger [] (bl/build-ledger (bl/load-seed seed)))
(defn- edges  [] (af/assemble (ledger)))
(defn- datoms [] (py/project (edges)))

;; ── TestBudgetLedger ─────────────────────────────────────────────────────────
(deftest test-groups
  (let [l (ledger)]
    (is (= 5 (count (get l "lines"))))
    (let [g24 (get-in l ["groups" "JP-MEXT-EDUSCI|2024"])]
      (is (= 1 (count (get g24 "appropriations"))))
      (is (= 2 (count (get g24 "outlays")))))))

(deftest test-cid-deterministic
  (let [rec {"recordId" "x" "fiscalYear" 2024 "sourceSensor" "jp_yosan" "amountLocal" 1}]
    (is (= (bl/record-cid rec) (bl/record-cid (into {} rec))))))

;; ── TestAssembleCharterGates ─────────────────────────────────────────────────
(deftest test-edges-assembled
  (is (= 5 (count (edges)))))

(deftest test-every-edge-valid
  ;; G5 (≥2 CIDs), flowClass enum, required fields, G4 non-adjudicating — all enforced.
  (doseq [e (edges)]
    (af/validate-edge e)
    (is (>= (count (get e "sourceRecordCids")) 2))
    (is (contains? #{"appropriation" "outlay"} (get e "flowClass")))))

(deftest test-g10-aggregate-first-no-named-party
  ;; No endpoint asserts a named private party (no publiclyNamedBasis fabricated).
  (doseq [e (edges)]
    (is (not (contains? (get e "fromEndpoint") "publiclyNamedBasis")))
    (is (not (contains? (get e "toEndpoint") "publiclyNamedBasis")))))

(deftest test-g4-verdict-token-rejected
  (let [e0       (first (edges))
        poisoned (assoc e0 "fromEndpoint"
                        (assoc (get e0 "fromEndpoint") "label" "不正 appropriation"))]
    (is (thrown? #?(:clj Exception :cljs js/Error) (af/assert-non-adjudicating poisoned)))))

(deftest test-g5-single-cid-rejected
  (let [bad (assoc (first (edges)) "sourceRecordCids" ["only-one"])]
    (is (thrown? #?(:clj Exception :cljs js/Error) (af/validate-edge bad)))))

;; ── TestYoroProjection ───────────────────────────────────────────────────────
(deftest test-mext-incoming-outgoing
  (let [flows    (group-by-prefix (datoms) ":yoro.fiscal/")
        incoming (filter #(= (get % "to") mext) flows)
        outgoing (filter #(= (get % "from") mext) flows)]
    ;; 2 appropriations into MEXT (FY2023 + FY2024); 3 outlays out (1 FY2023 + 2 FY2024)
    (is (= 2 (count incoming)))
    (is (= 3 (count outgoing)))))

(deftest test-mext-has-profile
  (let [profiles (group-by-prefix (datoms) ":yoro.profile/")]
    (is (some #(= (get % "did") mext) profiles))))

(deftest test-asof-time-travel
  (let [flows  (group-by-prefix (datoms) ":yoro.fiscal/")
        cur-in (filter #(= (get % "to") mext) flows)]
    (is (= 2 (count cur-in)))
    ;; as-of 2023-12-31: only the FY2023 appropriation (observed 2023-03-28) is visible
    (let [past-in (filter #(and (= (get % "to") mext) (asof-ok (get % "observedAt") "2023-12-31")) flows)]
      (is (= 1 (count past-in)))
      (is (= 2023 (get (first past-in) "fiscalYear"))))
    ;; as-of 2022-01-01: nothing observed yet
    (let [none-in (filter #(and (= (get % "to") mext) (asof-ok (get % "observedAt") "2022-01-01")) flows)]
      (is (= 0 (count none-in))))))

#?(:clj (defn -main [& _] (run-tests 'kanae.methods.test-pipeline)))
