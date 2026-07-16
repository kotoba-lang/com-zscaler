(ns aburi.app
  "cljc-native WASM entry for the aburi 炙り actor (ADR-2606261200). Mirrors the
  componentize-py wasm/app.py: binds the WIT world exports (analyze/datoms/coverage)
  to the shared methods/*.cljc over the embedded representative seed. Sandbox has no
  FS/network (G7/no-server-key); the host supplies the member's own graph at R1."
  (:require [aburi.methods.analyze :as az]
            [aburi.methods.datom-emit :as de]
            [aburi.methods.coverage-report :as cov]
            [aburi.seed :as seed]))

(defn- graph [] (az/load-graph (az/read-edn seed/edn)))

(defn- round4 [v] (/ (js/Math.round (* (js/Number v) 10000)) 10000))

(defn- rows [d nodes limit]
  ;; cherry cljs data is not native JS — materialize a JS array of JS objects so the
  ;; WIT string boundary (JSON.stringify) serializes correctly (ADR-2606261200).
  (into-array
   (map (fn [[nid label v]] #js {:id nid :label label :value (round4 v)})
        (az/rank d nodes limit))))

(defn- result []
  (let [{:keys [nodes edges]} (graph)
        res (az/analyze nodes edges)]
    #js {:actor "aburi" :own_data true :reciprocity_restoring true :non_adjudicating true
         :who_tracks_you (rows (get res "net_exposure") nodes 14)
         :surface_leak  (rows (get res "surface_leak") nodes 10)
         :data_spread   (rows (get res "spread") nodes 10)
         :reciprocity_gap (into-array (get res "unrouted_permissions"))}))

(defn ^:export analyze [] (js/JSON.stringify (result)))
(defn ^:export datoms [tx]
  (let [{:keys [nodes edges]} (graph)] (de/emit nodes edges (az/analyze nodes edges) tx)))
(defn ^:export coverage []
  (let [{:keys [nodes edges]} (graph)] (cov/report nodes edges)))
