(ns hibiki.methods.test-present-plan
  "Tests for hibiki 響 present-plan — the 説得力 knife-edge is structurally pinned.
  Run: bb -cp 20-actors -m hibiki.methods.test-present-plan"
  (:require [clojure.test :refer [deftest is run-tests]]
            [hibiki.methods.present-plan :as p]))

(def base
  {"proposalId" "ossekai-R14-2606"
   "finding"    "aggregate-share 41% < 50% floor"
   "why"        "targeted dispatch is drifting above the aggregate-first floor"
   "action"     "cap mention_dispatcher × 0.5 next quarter until recovery"
   "severity"   "structural"
   "linkUrl"    "at://did:web:ossekai.etzhayyim.com/app.bsky.feed.post/abc"})

(defn- refuses? [f]
  (try (f) false (catch Exception e (boolean (:charter-refusal (ex-data e))))))

(deftest builds-a-bounded-plan
  (let [plan (p/build-plan base)]
    (is (= "hibiki-ossekai-R14-2606" (get plan "presentationId")))
    (is (= "aggregate" (get plan "audience")) "H7 — aggregate is the default audience")
    (is (<= (count (get plan "narrationScript")) p/EXCERPT-MAX) "H2 — narration is bounded")
    (is (= "synthetic-neutral" (get plan "narrator")) "H3 — neutral synthetic voice only")
    (is (= "consent" (get (last (get plan "storyboard")) "scene"))
        "御節介 knife-edge — the last scene is ALWAYS the opt-out/consent card")
    (is (every? false? (vals (get plan "gates"))) "all manipulation gates const-false")))

(deftest aggregate-first-default-targeted-is-opt-in
  (is (= "aggregate" (get (p/build-plan base) "audience")))
  (is (= "targeted"  (get (p/build-plan (assoc base "aggregate" false)) "audience"))))

(deftest severity-tints-mood-never-unlocks-fear
  (is (= "hopeful-sober" (get (p/build-plan base) "musicBed")))
  (is (= "calm" (get (p/build-plan (assoc base "severity" "warn")) "musicBed")))
  (is (contains? p/ALLOWED-MUSIC (get (p/build-plan base) "musicBed"))))

(deftest refuses-the-manipulation-axis            ; the 説得力 knife-edge
  (is (refuses? #(p/build-plan (assoc base "engagementOptimized" true))) "H4 anti-engagement")
  (is (refuses? #(p/build-plan (assoc base "fakeUrgency" true)))         "H4/H8 anti-urgency")
  (is (refuses? #(p/build-plan (assoc base "manipulativeAudio" true)))  "H4/H8 anti-weaponized-sound"))

(deftest refuses-deepfake-and-external-render
  (is (refuses? #(p/build-plan (assoc base "depictsPerson" true))) "H3 anti-deepfake likeness")
  (is (refuses? #(p/build-plan (assoc base "voiceClone" true)))    "H3 anti-deepfake voice")
  (is (refuses? #(p/build-plan (assoc base "externalGpuRender" true))) "H5 Murakumo-only")
  (is (refuses? #(p/build-plan (assoc base "verdict" true)))       "H1 no truth-verdict"))

(deftest refuses-empty-proposal
  (is (refuses? #(p/build-plan (dissoc base "finding"))) "nothing to present")
  (is (refuses? #(p/build-plan (dissoc base "action")))  "a proposal must carry an action"))

(deftest render-is-r0-gated
  (is (thrown? Exception (p/render (p/build-plan base))) "H5/G8 — live render raises at R0"))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'hibiki.methods.test-present-plan)]
    (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1))))
