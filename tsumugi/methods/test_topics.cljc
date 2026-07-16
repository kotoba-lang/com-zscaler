(ns tsumugi.methods.test-topics
  "Cross-language oracle tests for tsumugi.methods.topics — the Clojure port of
  methods/topics.py (the pure topic-derivation core).

  No test_topics.py existed, so the expected values were produced by running the REAL
  Python derive_topics / humanize_viewpoint / to_edn on a synthetic evidence-edge set
  and embedded verbatim — a genuine cross-language oracle. Pins: only :evidence edges
  count, coherence = distinct-entities-with-viewpoint / entities-with-any-evidence,
  binding-confidence = clamped Σ evidence-weight, deterministic sort."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.topics :as tp]))

(def edges
  [{":en/kind" ":evidence" ":en/evidence-kind" ":labor" ":en/to" ":ent.a" ":en/evidence-weight" 0.6}
   {":en/kind" ":evidence" ":en/evidence-kind" ":labor" ":en/to" ":ent.b" ":en/evidence-weight" 0.5}
   {":en/kind" ":evidence" ":en/evidence-kind" ":ecology" ":en/to" ":ent.a" ":en/evidence-weight" 0.9}
   {":en/kind" ":evidence" ":en/evidence-kind" ":labor" ":en/to" ":ent.a" ":en/evidence-weight" 0.7}
   {":en/kind" ":custodies" ":en/evidence-kind" ":labor" ":en/to" ":ent.z" ":en/evidence-weight" 1.0}])

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-9))

(deftest humanize-viewpoint-strips-colon
  (is (= "labor-viewpoint topic" (tp/humanize-viewpoint ":labor")))
  (is (= "x-viewpoint topic" (tp/humanize-viewpoint "x"))))

(deftest derives-two-topics-sorted-by-viewpoint
  (let [[topics _] (tp/derive-topics edges)]
    (is (= 2 (count topics)))
    (is (= ["topic.ecology" "topic.labor"] (mapv #(get % ":topic/id") topics)))   ; sorted
    (is (= "ecology-viewpoint topic" (get (first topics) ":topic/label")))))

(deftest topic-coherence-is-entity-share
  (let [[topics _] (tp/derive-topics edges)
        by-id (into {} (map (fn [t] [(get t ":topic/id") t]) topics))]
    ;; labor: both ent.a + ent.b → 2/2 entities = 1.0; ecology: ent.a only → 1/2 = 0.5
    (is (close? 1.0 (get-in by-id ["topic.labor" ":topic/coherence"])))
    (is (close? 0.5 (get-in by-id ["topic.ecology" ":topic/coherence"])))))

(deftest binding-confidence-is-clamped-sum
  (let [[_ bindings] (tp/derive-topics edges)
        by-id (into {} (map (fn [b] [(get b ":en/id") b]) bindings))]
    (is (= 3 (count bindings)))
    ;; labor.ent.a: 0.6 + 0.7 = 1.3 → clamped to 1.0
    (is (close? 1.0 (get-in by-id ["topicbind.topic.labor.:ent.a" ":en/binding-confidence"])))
    ;; labor.ent.b: 0.5; ecology.ent.a: 0.9
    (is (close? 0.5 (get-in by-id ["topicbind.topic.labor.:ent.b" ":en/binding-confidence"])))
    (is (close? 0.9 (get-in by-id ["topicbind.topic.ecology.:ent.a" ":en/binding-confidence"])))
    (is (every? #(= ":topic-binding" (get % ":en/kind")) bindings))
    (is (every? #(= ":derived" (get % ":en/source")) bindings))))

(deftest bindings-sorted-by-en-id
  (let [[_ bindings] (tp/derive-topics edges)
        ids (mapv #(get % ":en/id") bindings)]
    (is (= ids (vec (sort ids))))))

(deftest to-edn-shape
  (let [[topics bindings] (tp/derive-topics edges)
        edn (tp/to-edn topics bindings)]
    (is (clojure.string/includes? edn "GENERATED topic graph"))
    (is (clojure.string/includes? edn ":topic/viewpoint :labor"))
    (is (clojure.string/includes? edn ":en/from \"topic.labor\""))   ; plain string quoted
    (is (clojure.string/includes? edn ":en/to :ent.a"))              ; keyword kept bare
    (is (clojure.string/includes? edn ":en/stability 1.0"))))

(deftest empty-edges-yield-nothing
  (let [[topics bindings] (tp/derive-topics [])]
    (is (= [] topics))
    (is (= [] bindings))))
