(ns utsushie.methods.test-render-plan
  "Cross-language oracle tests for utsushie.methods.render-plan — the Clojure port of
  methods/render_plan.py.

  Ported 1:1 from the REAL Python test_render_plan.py (9 tests): the U1–U6 charter
  gates enforced in code (mirroring lex/video.edn) — ok plan shape, narration bounded
  to the excerpt (U2/G4), all gate witnesses false, refusals for :original kind (G11),
  oversized excerpt (U2/G4), person depiction + voice clone (U3 anti-deepfake), a mirror
  without a link-out (G4), and the R0 render guard (G8 + Murakumo)."
  (:require [clojure.test :refer [deftest is testing]]
            [utsushie.methods.render-plan :as rp]))

(def ART {"articleId" "a1" "kind" "mirror" "section" "国際"
          "headline" "見出し" "excerpt" "短い要約。"
          "url" "https://example.org/a" "outlet" "outlet.x"})

(deftest build-plan-ok
  (let [p (rp/build-plan ART :langs ["ja" "en"])]
    (is (= "a1" (get p "sourceArticleId")))
    (is (= "mirror" (get p "kind")))
    (is (= ["ja" "en"] (get p "langs")))
    (is (= "synthetic-neutral" (get p "narrator")))
    (is (= "video/mp4" (get p "blobMime")))))

(deftest narration-bounded-to-excerpt
  (is (<= (count (get (rp/build-plan ART) "narrationScript")) rp/EXCERPT-MAX)))  ; U2 / G4

(deftest all-gate-witnesses-false
  (is (= {"verdict" false "fullTextNarration" false "depictsPerson" false
          "voiceClone" false "engagementOptimized" false
          "externalGpuRender" false "serverHeldKey" false}
         (get (rp/build-plan ART) "gates"))))

(deftest g11-refuses-original-kind
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G11" (rp/build-plan (assoc ART "kind" "original")))))

(deftest g4-refuses-oversized-excerpt
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"U2|G4"
                        (rp/build-plan (assoc ART "excerpt" (apply str (repeat (inc rp/EXCERPT-MAX) "x")))))))

(deftest u3-refuses-person-depiction
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"anti-deepfake" (rp/build-plan (assoc ART "depictsPerson" true)))))

(deftest u3-refuses-voice-clone
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"anti-deepfake" (rp/build-plan (assoc ART "voiceClone" true)))))

(deftest mirror-requires-url
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4" (rp/build-plan (assoc ART "url" "")))))

(deftest render-is-r0-gated
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G8" (rp/render {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Murakumo" (rp/render {}))))
