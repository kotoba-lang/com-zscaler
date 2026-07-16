(ns maps.methods.test-maps3d-persist
  "Tests for the maps3d → kotoba Datom-log persistence builders + gated push
  (Clojure port of the Python test_maps3d_persist.py). clojure.test; pure
  builders run everywhere, the gate-refusal test is #?(:clj ...) (binds
  ingest/*getenv* so the gate is exercised without a live kotoba node)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [maps.methods.ingest :as ingest]
            [maps.methods.maps3d-persist :as p]))

;; ── stable-rkey ──────────────────────────────────────────────────────────────

(deftest test-stable-rkey-deterministic-and-field-sensitive
  (is (= (p/stable-rkey "tileX" "Starbucks" "img1")
         (p/stable-rkey "tileX" "Starbucks" "img1")))
  (is (not= (p/stable-rkey "tileX" "Starbucks" "img1")
            (p/stable-rkey "tileX" "Starbucks" "img2")))
  (is (= 16 (count (p/stable-rkey "a" "b" "c")))))

;; ── vision-result-entity / vision-results->batch ─────────────────────────────

(deftest test-vision-entity-shape
  (let [e (p/vision-result-entity
           "tile-h3-abc"
           {"label" "Starbucks" "confidence" 0.9 "category" "business" "imageRef" "img1"})]
    (is (= "maps3d-vision-result" (get e "type")))
    (is (= "Starbucks" (get e "label_en")))
    (is (str/starts-with? (get e "id") "at://did:web:maps.etzhayyim.com/"))
    (is (some #(= % {"pred" "maps3d.vision/tile-h3" "value" "tile-h3-abc"}) (get e "claims")))
    (is (some #(= % {"pred" "maps3d.vision/image-ref" "value" "img1"}) (get e "claims")))))

(deftest test-vision-entity-blank-label-dropped
  (is (nil? (p/vision-result-entity "t" {"label" "  "})))
  (is (nil? (p/vision-result-entity "t" {"label" ""}))))

(deftest test-vision-entity-omits-empty-image-ref-claim
  (let [e (p/vision-result-entity "t" {"label" "Cafe" "confidence" 0.7})]
    (is (not-any? #(= "maps3d.vision/image-ref" (get % "pred")) (get e "claims")))))

(deftest test-vision-batch-drops-blanks-and-keeps-rest
  (let [b (p/vision-results->batch
           "t" [{"label" "A" "confidence" 0.8 "imageRef" "i1"}
                {"label" "  "}                           ; dropped
                {"label" "B" "confidence" 0.6 "imageRef" "i2"}])]
    (is (= 2 (count (get b "entities"))))))

(deftest test-vision-entity-idempotent-id
  (let [d {"label" "Cafe" "confidence" 0.7 "imageRef" "imgA"}
        a (p/vision-result-entity "t1" d)
        b (p/vision-result-entity "t1" d)]
    (is (= (get a "id") (get b "id")))))    ; re-run upserts, no dup id

(deftest test-vision-entity-accepts-keyword-keys
  ;; the pipeline may hand maps with keyword keys; builder must read both.
  (let [e (p/vision-result-entity "t" {:label "Tower" :confidence 0.85 :category "landmark"})]
    (is (= "Tower" (get e "label_en")))))

;; ── actor-link-entity / actor-links->batch ───────────────────────────────────

(deftest test-actor-link-entity-shape
  (let [e (p/actor-link-entity
           "tile-9"
           {"label" "Starbucks"
            "actorDid" "did:web:maps.etzhayyim.com:registry:wikidata:Q38076"
            "confidence" 0.95 "source" "wikidata"})]
    (is (= "maps3d-actor-link" (get e "type")))
    (is (str/ends-with? (->> (get e "claims")
                             (some #(when (= "maps3d.link/actor-did" (get % "pred")) (get % "value"))))
                        "Q38076"))
    (is (= [{"pred" "maps3d/links-to"
             "target" "did:web:maps.etzhayyim.com:registry:wikidata:Q38076"}]
           (get e "relations")))))

(deftest test-actor-link-without-did-dropped
  (is (nil? (p/actor-link-entity "t" {"label" "NoDid" "actorDid" "" "confidence" 0.99})))
  (let [b (p/actor-links->batch
           "t" [{"label" "X" "actorDid" "did:web:y" "confidence" 0.9}
                {"label" "NoDid" "actorDid" ""}])]
    (is (= 1 (count (get b "entities"))))))

;; ── gated push (host-only) ───────────────────────────────────────────────────

#?(:clj
   (deftest test-persist-vision-refuses-without-operator-gate
     (binding [ingest/*getenv* (fn [_] nil)]   ; MAPS_OPERATOR_GATE unset
       (is (thrown-with-msg?
            clojure.lang.ExceptionInfo #"G7"
            (p/persist-vision! "t" [{"label" "A" "confidence" 0.9}]))))))

#?(:clj
   (deftest test-persist-vision-refuses-without-credentials
     (binding [ingest/*getenv* (fn [k] (when (= k "MAPS_OPERATOR_GATE") "1"))]  ; gate on, no auth/endpoint
       (is (thrown-with-msg?
            clojure.lang.ExceptionInfo #"KOTOBA_AUTH"
            (p/persist-vision! "t" [{"label" "A" "confidence" 0.9}]))))))

#?(:clj
   (deftest test-persist-empty-batch-skips-push-no-gate-needed
     ;; nothing to write → no network, no gate refusal even with env unset.
     (binding [ingest/*getenv* (fn [_] nil)]
       (is (= {:skipped :empty} (p/persist-vision! "t" [])))
       (is (= {:skipped :empty} (p/persist-links! "t" [{"actorDid" ""}]))))))
