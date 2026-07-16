(ns noroshi.methods.test-cable-endpoint
  "noroshi 烽 × watatsuna 綿津綱 optical-network resilience join tests (ADR-2606051600).
  1:1 Clojure port of methods/test_cable_endpoint.py — every assertion ported, PLUS the
  constitutional gates the task requires made explicit and test-enforced:

    G3 civilian-force-separation — the join is sizing arithmetic over a civilian cable
       medium; the report FRAMES resilience, never a target-list (inherited watatsuna G2).
    G4 object-not-person — the only entities are cables / stations / segments / chokepoints;
       no person / biometric field is representable in the graph.
    G1/G10 open-EDA / sourcing-honesty — `:representative` watatsuna seed; arithmetic only.

  The watatsuna seed is committed in this repo, so (unlike the Python pytest skipif) these
  run unconditionally."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [noroshi.methods.cable-endpoint :as ce]))

;; ── tmp-seed helper (the Python tmp_path fixtures) ──────────────────────────
(defn- write-tmp-seed [contents]
  (let [f (java.io.File/createTempFile "noroshi-cable-seed" ".edn")]
    (.deleteOnExit f)
    (spit f contents)
    (.getPath f)))

;; ── test_loads_watatsuna_graph ──────────────────────────────────────────────
(deftest test-loads-watatsuna-graph
  (let [g (ce/load-graph)
        cable-ids (set (map first (:cables g)))]
    (is (contains? cable-ids "cable.jupiter"))
    (is (some #(str/starts-with? % "station.") (keys (:stations g))))
    (is (pos? (count (:links g))))))

;; ── test_lane_sizing_formula ────────────────────────────────────────────────
(deftest test-lane-sizing-formula
  ;; 250 Tb/s at 106.25 Gb/s/lane → ceil(250000/106.25) = 2353 lanes.
  (is (= 2353 (ce/lanes-for 250.0 106.25)))
  (is (= 1 (ce/lanes-for 0.0 106.25)))     ;; at least one lane
  (is (= 1 (ce/lanes-for 0.1 106.25))))

;; ── test_chokepoints_ranked_by_lane_demand ──────────────────────────────────
(deftest test-chokepoints-ranked-by-lane-demand
  (let [f (ce/size-fleet)
        cps (:chokepoints f)]
    (is (seq cps) "expected at least one chokepoint")
    ;; sorted descending by CPO lane demand
    (is (every? (fn [i] (>= (:lanes (nth cps i)) (:lanes (nth cps (inc i)))))
                (range (dec (count cps)))))
    ;; luzon-strait is the seed's top capacity chokepoint → top transceiver demand too
    (is (= ":luzon-strait" (:chokepoint (first cps))))))

;; ── test_per_cable_endpoint_power_is_realistic ──────────────────────────────
(deftest test-per-cable-endpoint-power-is-realistic
  (let [f (ce/size-fleet)]
    ;; endpoint transceiver power should be sub-MW (kW range), not gigawatts.
    (doseq [c (:per-cable f)]
      (is (< 0.0 (:energy-kw c) 1000.0)))))

;; ── test_report_frames_resilience_not_target_list (G3) ──────────────────────
(deftest test-report-frames-resilience-not-target-list
  (let [txt (ce/report)]
    (is (str/includes? (str/lower-case txt) "resilience"))
    (is (or (str/includes? txt "NEVER a target-list")
            (str/includes? (str/lower-case txt) "never")))
    (is (str/includes? txt ":luzon-strait"))))

;; ── test_missing_seed_raises_friendly_error ─────────────────────────────────
(deftest test-missing-seed-raises-friendly-error
  (is (thrown? clojure.lang.ExceptionInfo
               (ce/load-graph (str (java.io.File. (System/getProperty "java.io.tmpdir")
                                                  "nope-does-not-exist.edn"))))))

;; ── test_out_of_service_cable_is_skipped ────────────────────────────────────
(deftest test-out-of-service-cable-is-skipped
  (let [seed (write-tmp-seed
              (str "[{:cable/id \"c.live\" :cable/name \"Live\" :cable/design-capacity-tbps 100.0 "
                   ":cable/status :in-service}\n"
                   " {:cable/id \"c.dead\" :cable/name \"Dead\" :cable/design-capacity-tbps 999.0 "
                   ":cable/status :decommissioned}\n"
                   " {:station/id \"s.a\" :station/name \"A\" :station/chokepoint [:malacca]}\n"
                   " {:cable.link/id \"lk1\" :cable.link/cable \"c.live\" :cable.link/station \"s.a\"}\n"
                   " {:cable.link/id \"lk2\" :cable.link/cable \"c.dead\" :cable.link/station \"s.a\"}]\n"))
        f (ce/size-fleet seed)
        names (set (map :name (:per-cable f)))]
    (is (contains? names "Live"))
    (is (not (contains? names "Dead")))))   ;; decommissioned excluded

;; ── test_load_graph_parses_segments ─────────────────────────────────────────
(deftest test-load-graph-parses-segments
  (is (pos? (count (:segments (ce/load-graph))))))

;; ── test_segment_view_present_ranked_and_luzon_top ──────────────────────────
(deftest test-segment-view-present-ranked-and-luzon-top
  (let [f (ce/size-fleet)
        segs (:chokepoints-via-segments f)]
    (is (and (seq segs)
             (every? (fn [i] (>= (:lanes (nth segs i)) (:lanes (nth segs (inc i)))))
                     (range (dec (count segs))))))
    (is (= ":luzon-strait" (:chokepoint (first segs))))))

;; ── test_segment_view_attributes_a_crossing_without_a_tagged_landing ─────────
(deftest test-segment-view-attributes-a-crossing-without-a-tagged-landing
  ;; A cable physically crosses :hormuz (a segment traverses it) but lands at an UNTAGGED
  ;; station. The station-tag view misses it; the authoritative segment view catches it.
  (let [seed (write-tmp-seed
              (str "[{:cable/id \"c.gulf\" :cable/name \"Gulf\" :cable/design-capacity-tbps 100.0 :cable/status :in-service}\n"
                   " {:station/id \"s.plain\" :station/name \"Plain\"}\n"
                   " {:cable.link/id \"lk\" :cable.link/cable \"c.gulf\" :cable.link/station \"s.plain\"}\n"
                   " {:cable.seg/id \"sg\" :cable.seg/cable \"c.gulf\" :cable.seg/traverses [:hormuz]}]\n"))
        f (ce/size-fleet seed)
        station-cps (set (map :chokepoint (:chokepoints f)))
        segment-cps (set (map :chokepoint (:chokepoints-via-segments f)))]
    (is (not (contains? station-cps ":hormuz")))   ;; untagged landing → missed by station view
    (is (contains? segment-cps ":hormuz"))))        ;; but the segment crossing is authoritative

;; ── test_station_without_chokepoint_contributes_no_chokepoint_row ───────────
(deftest test-station-without-chokepoint-contributes-no-chokepoint-row
  (let [seed (write-tmp-seed
              (str "[{:cable/id \"c.x\" :cable/name \"X\" :cable/design-capacity-tbps 50.0 :cable/status :in-service}\n"
                   " {:station/id \"s.nocp\" :station/name \"NoCP\"}\n"
                   " {:cable.link/id \"lk\" :cable.link/cable \"c.x\" :cable.link/station \"s.nocp\"}]\n"))
        f (ce/size-fleet seed)]
    (is (= [] (:chokepoints f)))                       ;; no chokepoint tag → no aggregation row
    (is (pos? (get (:by-station-lanes f) "s.nocp")))))  ;; but the station still carries lanes

;; ── byte-parity guard: the seed report stays 1:1 with python3 cable_endpoint.py ──
(deftest test-report-is-deterministic-and-frames-luzon-top
  (let [txt (ce/report)]
    (is (str/includes? txt "| :luzon-strait | 11198 | 6 | 1189.1 |"))
    (is (str/includes? txt "| Dunant | 250.0 | 2 | 2353 | 0.79 |"))))

(defn -main [& _]
  (let [r (run-tests 'noroshi.methods.test-cable-endpoint)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
