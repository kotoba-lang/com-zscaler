#!/usr/bin/env bb
;; kamado 竈 — validation of the seed-identity-wins ingest dedup.
;; Run:  bb --classpath 20-actors 20-actors/kamado/methods/test_dedup.cljc
(ns kamado.methods.test-dedup
  "Validation of dedup-vs-seed — the legacy-ingest merge step that folds migrated rows into the seed
  under the 'seed identity wins' convention (the watari / KG-mirror rule): keep every seed row, add
  only genuinely new migrated ids, and report how many were added. It was ISOLATED. A regression
  that let a migrated row OVERWRITE a seed row (losing curated seed data) or mis-counted the
  additions would silently corrupt the merged graph or its provenance count."
  (:require [kamado.methods.ingest :as i]
            [clojure.test :refer [deftest is run-tests]]))

(deftest seed-identity-wins-over-a-colliding-migrated-row
  ;; a migrated id already present in the seed is NOT overwritten and is NOT counted as added
  (is (= [{"a" 99 "b" 2} 1] (i/dedup-vs-seed [["a" 1] ["b" 2]] {"a" 99}))
      "seed's a=99 is kept; only the new b is added (added=1)"))

(deftest all-new-rows-are-added-and-counted
  (is (= [{"x" 1 "y" 2} 2] (i/dedup-vs-seed [["x" 1] ["y" 2]] {}))))

(deftest all-colliding-rows-add-nothing
  (is (= [{"a" 9 "b" 8} 0] (i/dedup-vs-seed [["a" 1] ["b" 2]] {"a" 9 "b" 8}))
      "every migrated id already in the seed → seed unchanged, added=0"))

(deftest empty-migrated-leaves-the-seed-untouched
  (is (= [{"a" 1} 0] (i/dedup-vs-seed [] {"a" 1}))))

(deftest a-duplicate-within-migrated-keeps-the-first
  ;; the running merge is its own dedup source: the second occurrence of a key is already present
  (is (= [{"a" 1} 1] (i/dedup-vs-seed [["a" 1] ["a" 2]] {}))
      "first migrated a=1 wins; the second a=2 is a no-op, added=1"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kamado.methods.test-dedup)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
