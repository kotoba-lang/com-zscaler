(ns tsumugi.methods.test-ingest
  "Cross-language oracle tests for tsumugi.methods.ingest — the Clojure port of
  methods/ingest.py (the §D7.1 weave merger).

  No test_ingest.py existed, so the expected values were produced by running the REAL
  Python weave() over the committed seed-power-graph + data/ingest/*.edn and embedded
  verbatim (merged 100 = orgs 39 + edges 61, 12 latent; per-source counts seed 73 /
  atproto 18 / latent-evidence 9) — a genuine cross-language oracle. Pins: dedup
  first-wins, the G1/G5 latent + :representative defaults on non-seed orgs."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.ingest :as ing]))

(defn- woven [] (ing/weave))

(deftest weave-merges-seed-and-ingest
  (let [{:keys [merged counts]} (woven)
        orgs (filter #(contains? % ":organism/id") merged)
        edges (filter #(contains? % ":en/id") merged)
        latent (count (filter #(= false (get % ":organism/claimed?")) orgs))]
    (is (= 100 (count merged)))
    (is (= 39 (count orgs)))
    (is (= 61 (count edges)))
    (is (= 12 latent))
    (is (= 73 (get counts "seed-power-graph.kotoba.edn")))
    (is (= 18 (get counts "atproto-institutional.kotoba.edn")))
    (is (= 9 (get counts "latent-evidence-example.kotoba.edn")))))

(deftest non-seed-orgs-get-latent-and-sourcing-defaults
  (let [{:keys [merged]} (woven)
        orgs (filter #(contains? % ":organism/id") merged)]
    ;; every org carries a sourcing flag (G5); every latent org is :latent standing (G1)
    (is (every? #(contains? % ":organism/sourcing") orgs))
    (is (every? #(= ":latent" (get % ":organism/standing"))
                (filter #(= false (get % ":organism/claimed?")) orgs)))))

(deftest weave-sources-dedup-first-wins
  (let [sources [{:name "seed" :seed? true
                  :records [{":organism/id" ":org.a" ":organism/label" "FromSeed"}
                            {":en/id" ":e1" ":en/from" ":org.a" ":en/to" ":org.b"}]}
                 {:name "ingest" :seed? false
                  :records [{":organism/id" ":org.a" ":organism/label" "DuplicateIgnored"}
                            {":organism/id" ":org.b" ":organism/label" "NewLatent"}
                            {":no-key" true}]}]   ; record without org/en id → skipped
        {:keys [merged counts]} (ing/weave-sources sources)
        by-id (into {} (map (fn [r] [(get r ":organism/id") r])
                            (filter #(contains? % ":organism/id") merged)))]
    (is (= 3 (count merged)))                                   ; org.a, e1, org.b (dup + no-key dropped)
    (is (= "FromSeed" (get-in by-id [":org.a" ":organism/label"])))   ; seed wins
    (is (= 2 (get counts "seed")))
    (is (= 1 (get counts "ingest")))
    ;; org.b is a non-seed org → gets latent + :representative defaults
    (is (= false (get-in by-id [":org.b" ":organism/claimed?"])))
    (is (= ":latent" (get-in by-id [":org.b" ":organism/standing"])))
    (is (= ":representative" (get-in by-id [":org.b" ":organism/sourcing"])))
    ;; seed org.a is NOT forced latent (seed records keep their own flags)
    (is (nil? (get-in by-id [":org.a" ":organism/standing"])))))

(deftest to-edn-shape
  (let [edn (ing/to-edn [{":organism/id" ":org.a" ":organism/label" "Alpha" ":organism/claimed?" false}])]
    (is (clojure.string/includes? edn "GENERATED woven graph"))
    (is (clojure.string/includes? edn ":organism/id :org.a"))
    (is (clojure.string/includes? edn ":organism/label \"Alpha\""))
    (is (clojure.string/includes? edn ":organism/claimed? false"))))
