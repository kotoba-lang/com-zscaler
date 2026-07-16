(ns meyasu.viz.build-viz-data
  "meyasu 目安 — unified arbitrage-intel dashboard payload + viewer. 1:1 port of the PURE functions
  of viz/build_viz_data.py: build-payload (seed items → dashboard payload via agent/handle-fuse,
  the single source of truth — the viz re-implements no fusion) and render-html (inline the payload
  JSON into the self-contained template). The __main__ file-reading/writing CLI is the omitted I/O
  leg. A BUYER-transparency + supply-resilience surface, never a trading board (meyasu G1)."
  (:require [clojure.string :as str]
            [meyasu.methods.agent :as agent]
            #?(:clj [cheshire.core :as json])))

(defn build-payload [seed-items]
  (let [fused (agent/handle-fuse {"items" seed-items})]
    {"generator" "meyasu/viz/build_viz_data.py"
     "intent" "buyer-transparency+supply-resilience"   ; G1
     "cards" (get fused "cards")
     "refused" (get fused "refused")}))

(defn render-html
  "Inline the payload JSON into the self-contained template (mirror of render_html)."
  [payload template]
  (str/replace (slurp (str template)) "/*__PAYLOAD__*/null"
               #?(:clj (json/generate-string payload) :cljs (str payload))))
