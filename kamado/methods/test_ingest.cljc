(ns kamado.methods.test-ingest
  "Cross-language oracle tests for kamado.methods.ingest — the Clojure port of
  methods/ingest.py (the pure legacy→kotoba-EAVT migration core).

  Ported from the REAL Python test_ingest.py: the migration-logic assertions run over the
  committed legacy-oil-refining-export.sample.json (5 refineries / 5 units / 2 outages → 12
  kg entities), pinning the G4 person/non-org-operator refusals, the :observed-fossil /
  :representative discipline, and the kg.ingest contract shape. The Python test's push/--live
  cases drive push_batch + main's urllib/operator gate (G8) — that network leg is not part of
  this port, so those cases are out of scope here."
  (:require [clojure.test :refer [deftest is testing]]
            [kamado.methods.ingest :as ing]
            [cheshire.core :as json]))

(def sample-path "20-actors/kamado/data/ingest/legacy-oil-refining-export.sample.json")
(defn- export [] (json/parse-string (slurp sample-path)))

(deftest sample-export-migrates-to-kotoba-eavt
  (let [{:keys [refineries units outages]} (ing/migrate (export))]
    (is (= 5 (count refineries)))
    (is (= 5 (count units)))
    (is (= 2 (count outages)))
    (is (= "org.corp.eneos" (get-in refineries ["rf.jp.negishi" ":refinery/operator"])))
    (is (= ":active" (get-in refineries ["rf.jp.negishi" ":refinery/status"])))))

(deftest g4-observed-assets-are-never-operated
  (let [refineries (:refineries (ing/migrate (export)))]
    (doseq [[_ r] refineries]
      (is (= ":observed-fossil" (get r ":refinery/feedstock-class")))   ; G1/G4 — observation
      (is (= ":representative" (get r ":refinery/sourcing"))))))        ; G7 migrated, not authoritative

(deftest g4-refuses-a-person-field
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4 violation"
                        (ing/migrate {"Refinery" [{"refinery_code" "X-1" "country_code" "JP"
                                                   "owner_person" "山田太郎"}]}))))

(deftest g4-refuses-non-org-operator
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4 violation"
                        (ing/migrate {"Refinery" [{"refinery_code" "X-1" "country_code" "JP"
                                                   "operator_org" "alice"}]}))))

(deftest kg-batch-shape-matches-live-kotobase-contract
  (let [batch (ing/to-kg-batch (ing/migrate (export)))
        entities (get batch "entities")
        e0 (first entities)
        units (filter #(= "refinery-unit" (get % "type")) entities)]
    (is (= 12 (count entities)))                                        ; 5 + 5 + 2
    (is (= #{"id" "type" "label_en" "claims" "relations"} (set (keys e0))))
    (is (every? (fn [c] (and (contains? c "pred") (contains? c "value"))) (get e0 "claims")))
    (is (= "unit/refinery" (get-in (first units) ["relations" 0 "pred"])))))
