(ns kotoba-actors.inochi-test
  "RED spec for the inochi 命 biosphere refactor.

  All counts/invariants below are PINNED from the real seed (computed, not
  guessed). Until kimi implements `kotoba-actors.inochi`, every assertion that
  calls a stub fn fails with a `todo:` ex-info — that is the expected RED.

  The `q-join-sanity` test does NOT touch any stub: it exercises the shared
  `kotoba-actors.datomic` engine directly with a 2-clause join over the real
  seed, so it should PASS even while the actor is unimplemented (it pins that the
  engine + seed wiring is correct)."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba-actors.datomic :as d]
            [kotoba-actors.inochi :as inochi]))

(deftest node-counts
  (let [db (inochi/db)]
    (testing "one count per node type (real, pinned)"
      (is (= 15 (inochi/species-count db)))
      (is (= 9 (inochi/ecosystem-count db)))
      (is (= 1 (inochi/biome-count db)))
      (is (= 5 (inochi/pressure-count db))))))

(deftest edge-counts
  (let [db (inochi/db)]
    (testing "total 縁 edges (real, pinned)"
      (is (= 43 (inochi/edge-count db))))))

(deftest graph-closure
  (let [db (inochi/db)]
    (testing "every edge endpoint resolves to a known node (closed graph)"
      (is (= #{} (inochi/dangling-edges db))))))

(deftest q-join-sanity
  (testing "direct engine 2-clause join over the real seed: CR + animalia species"
    (let [db (d/db-from-seed
              "/Users/junkawasaki/github/com-junkawasaki/orgs/etzhayyim/root/20-actors/inochi/data/seed-biosphere-graph.kotoba.edn")
          ;; join: same ?e must be both kingdom=animalia AND iucn=CR, project label
          res (d/q '{:find  [?label]
                     :where [[?e :taxon/kingdom :animalia]
                             [?e :taxon/iucn :CR]
                             [?e :organism/label ?label]]}
                   db)]
      (is (= 7 (count res)))
      (is (contains? res ["Vaquita"]))
      (is (contains? res ["Sumatran orangutan"])))))
