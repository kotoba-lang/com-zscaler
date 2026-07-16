(ns itonami.tests.test-analyze
  "itonami 営み — operations-KPI analyzer tests (ADR-2606082300).
  1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional + numeric invariants empirically:
    - the seed loads (8-cell line + 24 scan-cycle ticks), no dangling tick station
    - OEE and each factor is a well-formed fraction in [0,1] and OEE == A×P×Q
    - frame-weld (the DOWN interval) is the OEE bottleneck (G1 routed finding)
    - paint draws the most kWh/good AND the most idle-energy (the FOX energy-cut lever)
    - cab-weld is the quality target → routed to vision inspection, never the worker (G2)
    - idle_kwh aggregates ONLY non-:run ticks (the cut lever)
    - line OEE is gated by the weakest station; line energy/good is total kWh / total good
    - G2: no :worker/* or :person/* dimension appears in stations or ticks

  NOTE on scope (mirrors the inochi/rasen precedent): the Python test_analyze additionally
  exercises the `datom_emit` sibling (test_no_worker_dimension_anywhere's datoms blob,
  test_datom_emit_ground_and_transient, test_determinism). Those depend on the unported
  `datom_emit` module — the datom_emit port is a separate unit — so they are deferred here.
  The G2 no-worker assertion is still ported over the stations + ticks (the parts that do not
  need datom_emit). All PURE analyze assertions are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [itonami.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(defn- non-underscore [res]
  (filter #(not (str/starts-with? % "_")) (keys res)))

(deftest test-load-nontrivial
  (let [{:keys [stations ticks]} (load-seed)]
    (is (= 8 (count stations)) (str "expected 8-cell line, got " (count stations)))
    (is (= 24 (count ticks)) (str "expected 8×3 scan-cycle ticks, got " (count ticks)))
    (doseq [tk ticks]
      (is (contains? stations (get tk ":tick/station"))
          (str "dangling tick station " (get tk ":tick/station")))
      (is (contains? #{":run" ":idle" ":down"} (get tk ":tick/state"))))))

(deftest test-oee-in-unit-interval
  (testing "OEE and each factor must be a well-formed fraction in [0,1]."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)]
      (doseq [sid (non-underscore res)]
        (let [r (get res sid)]
          (doseq [k ["availability" "performance" "quality" "oee"]]
            (is (and (<= 0.0 (get r k)) (<= (get r k) (+ 1.0 1e-9)))
                (str sid "." k " = " (get r k) " outside [0,1]")))
          ;; OEE is the product of its three factors
          (let [prod (* (get r "availability") (get r "performance") (get r "quality"))]
            (is (< (Math/abs (- (get r "oee") prod)) 1e-9) (str sid " OEE != A×P×Q"))))))))

(deftest test-availability-bottleneck-is-frame-weld
  (testing "frame-weld has a DOWN interval → it must be the OEE bottleneck (G1 routed finding)."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)
          rec (get res "_recommend")]
      (is (= ":st.frame-weld" (get-in rec ["bottleneck" "station"]))
          (str "bottleneck mis-identified: " (get rec "bottleneck")))
      ;; availability MUST equal run_s / planned_s, computed independently
      (let [fw (get res ":st.frame-weld")]
        (is (< (Math/abs (- (get fw "availability") (/ (* 2 3600) (* 3 3600.0)))) 1e-9))))))

(deftest test-energy-per-good-target-is-paint
  (testing "paint draws the most kWh per good unit → the FOX energy-cut lever points there."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)
          rec (get res "_recommend")]
      (is (= ":st.paint" (get-in rec ["energy_target" "station"]))
          (str "energy target mis-identified: " (get rec "energy_target")))
      ;; paint also carries the largest idle-energy burn (powered while not producing)
      (is (= ":st.paint" (get-in rec ["idle_energy_target" "station"])))
      (is (= 45.0 (get-in res [":st.paint" "idle_kwh"]))))))

(deftest test-quality-target-is-cab-weld-and-routes-to-vision
  (testing "cab-weld has the highest scrap-rate → routed to vision inspection, never the worker."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)
          rec (get res "_recommend")]
      (is (= ":st.cab-weld" (get-in rec ["quality_target" "station"])))
      (let [cw (get res ":st.cab-weld")]
        ;; 2 scrap / 9 cycles
        (is (< (Math/abs (- (get cw "scrap_rate") (/ 2.0 9.0))) 1e-9))
        (is (< (Math/abs (- (get cw "quality") (/ 7.0 9.0))) 1e-9))))))

(deftest test-no-worker-dimension-anywhere
  (testing "G2: per-worker monitoring is structurally unrepresentable. No :worker/* may appear."
    ;; datoms blob deferred (needs datom_emit); stations + ticks checked here.
    (let [{:keys [stations ticks]} (load-seed)]
      (doseq [[blob name] [[(pr-str stations) "stations"] [(pr-str ticks) "ticks"]]]
        (is (and (not (str/includes? blob ":worker")) (not (str/includes? blob ":person")))
            (str name " leaked a person/worker dimension (G2 violation)"))))))

(deftest test-line-rollup-gated-by-weakest-station
  (testing "A serial line's OEE is its bottleneck — line OEE == min station OEE."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)
          sids (non-underscore res)]
      (is (< (Math/abs (- (get-in res ["_line" "oee"])
                          (reduce min (map #(get-in res [% "oee"]) sids)))) 1e-9))
      ;; line energy/good is total kWh over total good
      (let [tot-kwh (reduce + 0.0 (map #(get-in res [% "kwh"]) sids))
            tot-good (reduce + 0.0 (map #(get-in res [% "good"]) sids))]
        (is (< (Math/abs (- (get-in res ["_line" "energy_per_good"]) (/ tot-kwh tot-good))) 1e-9))))))

(deftest test-idle-energy-only-counts-non-producing-states
  (testing "idle_kwh must aggregate ONLY ticks whose state is not :run (the cut lever)."
    (let [{:keys [stations ticks]} (load-seed)
          res (analyze/analyze stations ticks)]
      (doseq [sid (non-underscore res)]
        (let [expect (reduce + 0.0
                             (map #(double (get % ":tick/kwh"))
                                  (filter #(and (= (get % ":tick/station") sid)
                                                (not= (get % ":tick/state") analyze/RUN))
                                          ticks)))]
          (is (< (Math/abs (- (get-in res [sid "idle_kwh"]) expect)) 1e-9)
              (str sid " idle_kwh mismatch")))))))
