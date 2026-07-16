(ns jinushi.methods.test-confidence
  "jinushi 地主 — source reliability + conflict-resolution tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [jinushi.methods.confidence :as c]))

(deftest test-trust-ordering
  ;; authoritative gov/registry > curated crowd (Wikidata) > open crowd (OSM) > web > unknown
  (is (> (c/trust-score :nyc-pluto) (c/trust-score :wikidata)))
  (is (> (c/trust-score :gleif) (c/trust-score :wikidata)))
  (is (> (c/trust-score :wikidata) (c/trust-score :osm)))
  (is (> (c/trust-score :osm) (c/trust-score :commoncrawl)))
  (is (> (c/trust-score :commoncrawl) (c/trust-score :unrecorded-xyz)) "unknown source → low default"))

(deftest test-record-confidence
  (let [hi (c/record-confidence {:source :nyc-pluto :fields-present 2 :fields-expected 2})
        lo (c/record-confidence {:source :commoncrawl :fields-present 1 :fields-expected 4})]
    (is (> (:confidence hi) (:confidence lo)) "trusted+complete > untrusted+sparse")
    (is (<= 0.0 (:confidence hi) 1.0))
    (is (= 1.0 (:completeness hi)))))

(deftest test-resolve-conflict
  ;; gov cadastre wins over Wikidata when they disagree
  (let [r (c/resolve-conflict [{:source :wikidata :value "ACME LLC"}
                               {:source :nyc-pluto :value "ACME HOLDINGS LLC"}])]
    (is (= :nyc-pluto (:source r)) "highest-trust source wins")
    (is (= "ACME HOLDINGS LLC" (:value r)))
    (is (true? (:disagreed? r)) "disagreement recorded, not hidden"))
  (let [agree (c/resolve-conflict [{:source :wikidata :value "X"} {:source :osm :value "X"}])]
    (is (false? (:disagreed? agree)))
    (is (= 1.0 (:agreement agree)))))

(deftest test-reliability-tier
  (is (= :high (c/reliability-tier 0.9)))
  (is (= :medium (c/reliability-tier 0.65)))
  (is (= :very-low (c/reliability-tier 0.2))))

(deftest test-report
  (let [recs [{:source :nyc-pluto} {:source :nyc-pluto} {:source :wikidata} {:source :osm}]
        rep (c/report recs)]
    (is (= 4 (:total rep)))
    (is (= 2 (get-in rep [:by-source :nyc-pluto])))
    (is (contains? (:by-source-trust rep) :wikidata))
    (is (< 0.0 (:mean-trust rep) 1.0))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-confidence)]
    (System/exit (+ (or fail 0) (or error 0)))))
