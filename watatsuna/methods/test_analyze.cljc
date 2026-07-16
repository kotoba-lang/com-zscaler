(ns watatsuna.methods.test-analyze
  "Tests for the watatsuna 綿津綱 submarine-cable resilience analyzer (methods/analyze.cljc).
  1:1 port of `methods/test_analyze.py`.

      bb --classpath 20-actors -e \"(require 'watatsuna.methods.test-analyze 'clojure.test) \\
        (clojure.test/run-tests 'watatsuna.methods.test-analyze)\"

  Covers the chokepoint/station/diversity roll-ups, the render-datoms bridge-input shape
  (the :resilience/chokepoint-load records mitooshi's bridge consumes), AND the load-bearing
  charter invariant: watatsuna is a RESILIENCE map, NEVER a target-list — it ranks fragility
  to ADD redundancy, never to identify where to cut, and never asserts sabotage intent (G2)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [watatsuna.methods.analyze :as a]
            [watatsuna.methods._edn :as edn]
            [clojure.java.io :as io]))

;; _SEED = pathlib.Path(__file__).resolve().parent.parent / "data" / "seed-cable-graph.kotoba.edn"
;; Resolve relative to *file* so the suite runs from any cwd.
(def seed
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-cable-graph.kotoba.edn")))

(defn- load*
  "Mirror test_analyze._load(): rows → classify → analyze. Returns
   {:cables :stations :links :segs :faults :a}."
  []
  (let [rows (edn/load-edn seed)
        {:keys [cables stations links segs faults]} (#'a/attach-orders (a/classify rows))
        an (a/analyze cables stations links segs faults)]
    {:cables cables :stations stations :links links :segs segs :faults faults :a an}))

(defn- approx= [x y eps] (< (Math/abs (- (double x) (double y))) eps))

;; ── test_classify_buckets_the_seed ──────────────────────────────────────────
(deftest classify-buckets-the-seed
  (let [{:keys [cables stations links segs faults]} (load*)]
    (is (and (= 14 (count cables)) (= 22 (count stations))))
    (is (and (= 43 (count links)) (= 11 (count segs)) (= 2 (count faults))))))

;; ── test_chokepoint_load_ranking ────────────────────────────────────────────
(deftest chokepoint-load-ranking
  (let [{:keys [a]} (load*)
        load (get a "choke_load")
        top (take 3 (#'a/sorted-choke a))]
    (is (= ":malacca" (nth (vec top) 0)))
    (is (approx= (get load ":malacca") 490.16 1e-6))
    (is (approx= (get load ":luzon-strait") 454.56 1e-6))
    (is (approx= (get load ":gibraltar") 324.0 1e-6))))

;; ── test_chokepoint_count_matches_load_keys ─────────────────────────────────
(deftest chokepoint-count-matches-load-keys
  (let [{:keys [a]} (load*)
        cc (get a "choke_count")
        cl (get a "choke_load")]
    (is (= (set (keys cc)) (set (keys cl))))
    (is (every? (fn [cp] (>= (get cc cp) 1)) (keys cc)))))

;; ── test_redundancy_gap_is_single_cable_stations ────────────────────────────
(deftest redundancy-gap-is-single-cable-stations
  (let [{:keys [a]} (load*)
        sd (get a "station_degree")]
    (doseq [s (get a "redundancy_gap")]
      (is (<= (get sd s) 1)))))

;; ── test_render_datoms_emit_bridge_input_shape ──────────────────────────────
(deftest render-datoms-emit-bridge-input-shape
  (let [{:keys [cables stations a]} (load*)
        edn-text (a/render-datoms cables stations a)]
    ;; the exact records mitooshi's bridge reads: :resilience/chokepoint + chokepoint-load
    (is (and (str/includes? edn-text ":resilience/chokepoint ")
             (str/includes? edn-text ":resilience/chokepoint-load ")))
    (is (str/includes? edn-text ":resilience/derived true"))))   ;; never re-ingested as authoritative

;; ── test_chokepoint_keys_are_mitooshi_bridge_compatible ─────────────────────
(deftest chokepoint-keys-are-mitooshi-bridge-compatible
  (let [{:keys [a]} (load*)
        known #{":malacca" ":luzon-strait" ":suez-red-sea" ":hormuz" ":gibraltar"
                ":south-china-sea" ":bab-el-mandeb"}]
    ;; the chokepoints watatsuna emits must be joinable in mitooshi's keyword space
    (is (clojure.set/subset? (set (keys (get a "choke_load"))) known))))

;; ── test_g2_resilience_not_targeting_invariant ──────────────────────────────
(deftest g2-resilience-not-targeting-invariant
  (let [{:keys [cables stations links segs faults a]} (load*)
        md (a/render-report cables stations links segs faults a)]
    (is (and (str/includes? md "RESILIENCE") (str/includes? md "target-list")))   ;; framing stated
    (is (str/includes? md "NOT a target-list"))))

;; ── test_g4_faults_do_not_adjudicate_intent ─────────────────────────────────
(deftest g4-faults-do-not-adjudicate-intent
  ;; fault records mirror public bulletins; the analyzer must not assert sabotage intent.
  (let [rows (edn/load-edn seed)]
    (doseq [r rows]
      (when (and (map? r) (contains? r ":cable.fault/id"))
        ;; no field claims who-did-it / intent; only public-bulletin kind/location
        (is (not (some (fn [k] (or (str/includes? k "culprit")
                                   (str/includes? k "attribution")
                                   (str/includes? k "intent")))
                       (keys r))))))))
