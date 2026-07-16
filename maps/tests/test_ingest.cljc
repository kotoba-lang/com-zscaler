(ns maps.tests.test-ingest
  "maps — ingest method tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of `methods/test_methods.py` (TestIngest)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [maps.methods.ingest :as ingest]))

(def sample-path
  (-> *file* io/file .getParentFile .getParentFile
      (io/file "data" "ingest" "sample-vertex-spatial.json") str))

(defn load-sample []
  ((requiring-resolve 'cheshire.core/parse-string) (slurp sample-path)))

(deftest test-label-map
  (is (= ":building"     (ingest/normalize-label "Building")))
  (is (= ":station"      (ingest/normalize-label "Station")))
  (is (= ":legal-entity" (ingest/normalize-label "LegalEntity")))
  ;; Unknown label degrades to a kebab keyword, never crashes
  (is (= ":weirdthing"   (ingest/normalize-label "WeirdThing"))))

(deftest test-normalize-row
  (let [export (load-sample)
        row    (first (get export "rows"))
        f      (ingest/normalize-row row)]
    (is (= ":building" (get f ":feature/label")))
    (is (= 179.0       (get f ":feature/height-m")))
    (is (= 37          (get f ":feature/levels")))
    (is (= ":representative" (get f ":feature/sourcing")))))  ; G3

(deftest test-normalize-row-no-id-returns-nil
  (is (nil? (ingest/normalize-row {})))
  (is (nil? (ingest/normalize-row {"label" "Building"}))))

(deftest test-normalize-full-export
  (let [export               (load-sample)
        [feats stamped _un]  (ingest/normalize export)]
    (is (pos? (count feats)) "expected features")
    ;; G3: every normalized feature carries :representative sourcing
    (doseq [[_ f] feats]
      (is (= ":representative" (get f ":feature/sourcing"))))))

(deftest test-to-kg-batch-shape
  (let [export         (load-sample)
        [feats _ _]    (ingest/normalize export)
        batch          (ingest/to-kg-batch feats)]
    (is (contains? batch "entities"))
    (is (pos? (count (get batch "entities"))))
    (doseq [e (get batch "entities")]
      (is (contains? e "id"))
      (is (contains? e "claims"))
      (is (string? (get e "id")))
      (is (vector? (get e "claims"))))))

(deftest test-to-kg-batch-claims-have-pred-value
  (let [export      (load-sample)
        [feats _ _] (ingest/normalize export)
        batch       (ingest/to-kg-batch feats)
        ent         (first (get batch "entities"))]
    (doseq [c (get ent "claims")]
      (is (contains? c "pred") "claim missing pred")
      (is (contains? c "value") "claim missing value")
      (is (string? (get c "pred")))
      (is (some? (get c "value"))))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-ingest))
     (run-tests)))
