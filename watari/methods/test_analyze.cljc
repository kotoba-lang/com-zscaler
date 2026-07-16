(ns watari.methods.test-analyze
  "watari 渡り — analyzer tests (ADR-2606041827). 1:1 Clojure port of methods/test_analyze.py.

  Covers the analyzer's aggregate roll-ups (chokepoint transit, lane load, freshness tail)
  AND the load-bearing charter invariant: watari is situational-awareness, NEVER
  person-surveillance — a craft is a craft, not a person (G4). The seed carries no person
  identity and the analyzer surfaces none.

  NOTE on scope: the Python autorun/ingest tests (test_autorun.py, test_ingest.py) depend
  on the unported autorun/ingest/kotoba modules; they are intentionally out of scope here
  (mirroring the inochi/rasen precedent). All 8 PURE analyze assertions are ported 1:1."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [watari.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-craft-graph.kotoba.edn"))

(defn load*
  "Mirror of test_analyze._load → [craft fixes legs lanes a]."
  []
  (let [rows (analyze/load-edn seed)
        [craft fixes legs lanes] (analyze/classify rows)]
    [craft fixes legs lanes (analyze/analyze craft fixes legs lanes)]))

(deftest test-classify-buckets-the-seed
  (let [[craft fixes _ lanes _] (load*)]
    (is (= 13 (count craft)))            ;; 8 vessels + 5 aircraft
    (is (= 26 (count fixes)))
    (is (= 9 (count lanes)))))

(deftest test-kind-split-vessels-and-aircraft
  (let [[_ _ _ _ a] (load*)]
    (is (= 8 (get-in a ["kind_count" ":vessel"])))
    (is (= 5 (get-in a ["kind_count" ":aircraft"])))))

(deftest test-latest-fix-is-max-observed-at-per-craft
  (let [[_ fixes _ _ a] (load*)
        by-craft (reduce (fn [m fx]
                           (update m (get fx ":craft.fix/craft") (fnil conj [])
                                   (get fx ":craft.fix/observed-at" "")))
                         {} fixes)]
    ;; the latest fix per craft must be the lexicographically-max ISO-8601 ts among its fixes
    (doseq [[c fx] (get a "latest")]
      (is (= (reduce (fn [acc t] (if (> (compare t acc) 0) t acc)) (get by-craft c))
             (get fx ":craft.fix/observed-at" ""))))))

(deftest test-chokepoint-transit-rollup
  (let [[_ _ _ _ a] (load*)
        ct (get a "choke_transit")]
    ;; matches the analyzer's own headline: malacca 3, suez-red-sea 1, hormuz 1
    (is (= 3 (get ct ":malacca")))
    (is (= 1 (get ct ":suez-red-sea")))
    (is (= 1 (get ct ":hormuz")))))

(deftest test-freshness-tail-flags-stale-craft
  (let [[_ _ _ _ a] (load*)
        stale (get a "stale")
        latest (get a "latest")
        dl (get a "dataset_latest")]
    ;; craft whose latest fix predates the dataset latest = the honest stale tail
    (is (= 2 (count stale)))
    (is (every? (fn [c] (< (compare (get-in latest [c ":craft.fix/observed-at"] "") dl) 0))
                stale))))

(deftest test-chokepoint-output-is-bridge-compatible-with-mitooshi
  ;; watari's chokepoint keys live in the SAME space mitooshi's bridge joins on
  (let [[_ _ _ _ a] (load*)
        known #{":malacca" ":luzon-strait" ":suez-red-sea" ":hormuz" ":gibraltar"
                ":south-china-sea" ":bab-el-mandeb"}]
    (is (clojure.set/subset? (set (keys (get a "choke_transit"))) known))))

(deftest test-g4-no-person-tracking-invariant
  ;; STRUCTURAL: a craft is a craft, not a person. The seed must carry NO person identity,
  ;; and the analyzer must surface none. Person-level surveillance is unrepresentable.
  (let [rows (analyze/load-edn seed)]
    (doseq [r rows :when (map? r)]
      (doseq [k (keys r)]
        (is (not (str/includes? k ":person")) (str "person-level key " k " present (violates G4)"))
        (is (not (str/includes? k "operator-name")))))))  ;; operator is an org id, never a named human

(deftest test-report-is-aggregate-first-and-non-targeting
  (let [[craft fixes legs lanes a] (load*)
        md (analyze/render-report craft fixes legs lanes a)]
    (is (and (str/includes? md "person-surveillance")
             (str/includes? (str/lower-case md) "never")))
    (is (str/includes? md "target-list"))))   ;; the framing invariant is stated in the report
