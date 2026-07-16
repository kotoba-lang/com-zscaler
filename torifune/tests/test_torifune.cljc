(ns torifune.tests.test-torifune
  "torifune 鳥船 — sim + carbon + disposal + datom-emit tests (ADR-2606162355).
  1:1 Clojure port of tests/test_torifune.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.set]
            [clojure.string]
            [torifune.methods.ascent-sim :as core]
            [torifune.methods.carbon-balance :as carbon]
            [torifune.methods.disposal-plan :as disposal]
            [torifune.methods.datom-emit :as datom]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-ama-vehicle.kotoba.edn"))
(defn load-seed [] (core/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 18) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 12) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset?
           #{":vehicle" ":stage" ":engine" ":propellant" ":mission"
             ":payload" ":trajectory" ":disposal-plan"} kinds)
          (str "missing kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-no-strike-profile
  (let [{:keys [nodes]} (load-seed)]
    (is (true? (core/check-g1 nodes)))
    (doseq [n (vals nodes)]
      (doseq [b core/banned-attrs] (is (not (contains? n b)) (str "G1 violation: weapon attr " b)))
      (when (= ":trajectory" (get n ":organism/kind"))
        (is (contains? core/civilian-traj (get n ":traj/class"))))
      (when (= ":payload" (get n ":organism/kind"))
        (is (contains? core/civilian-payload (get n ":payload/class")))))
    ;; a strike trajectory must be REFUSED
    (let [bad (assoc nodes "lv.traj.strike"
                     {":organism/id" "lv.traj.strike" ":organism/kind" ":trajectory"
                      ":traj/class" ":depressed-strike"})]
      (is (thrown? clojure.lang.ExceptionInfo (core/check-g1 bad))))
    ;; a munition payload must be REFUSED
    (let [bad (assoc nodes "lv.payload.muni"
                     {":organism/id" "lv.payload.muni" ":organism/kind" ":payload"
                      ":payload/class" ":munition"})]
      (is (thrown? clojure.lang.ExceptionInfo (core/check-g1 bad))))))

(deftest test-ascent-reaches-orbit
  (let [{:keys [nodes edges]} (load-seed)
        res (core/simulate nodes edges)]
    (is (= ":leo-low" (:target_regime res)))
    (is (> (:dv_margin_ms res) 0) (str "insufficient Δv: margin " (:dv_margin_ms res)))
    (is (< (Math/abs (- (:total_dv_ms res) (reduce + 0.0 (map :dv_ms (:per_stage res))))) 1e-6))
    (is (= (:required_dv_ms res) (get core/regime-dv ":leo-low")))))

(deftest test-carbon-g2-zero-net
  (let [{:keys [nodes edges]} (load-seed)
        res (carbon/balance nodes edges)]
    (is (:g2_pass res) (str "G2 fail: net " (:net_kgco2e res)))
    (is (<= (:net_kgco2e res) 0.0))
    (is (empty? (:used_disfavored res)))))

(deftest test-disposal-g5-required
  (let [{:keys [nodes edges]} (load-seed)
        res (disposal/plan nodes edges)]
    (is (seq (:missions res)))
    (doseq [m (:missions res)] (is (seq (:plans m)) (str "mission " (:mission m) " no disposal")))
    (let [edges-no-disp (remove #(= ":disposes" (get % ":en/kind")) edges)]
      (is (thrown? clojure.lang.ExceptionInfo (disposal/plan nodes edges-no-disp))))))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        out (datom/emit nodes edges 7)]
    (is (clojure.string/includes? out ":add]"))
    (is (clojure.string/includes? out ":vehicle/class"))
    (is (clojure.string/includes? out ":en/kind"))
    (is (clojure.string/includes? out ":bond/is-transient true"))
    (is (clojure.string/includes? out ":bond/dv-margin-ms"))
    (doseq [bad [":traj/impact-point" ":payload/warhead" ":depressed-strike" ":munition"]]
      (is (not (clojure.string/includes? out bad)) (str "G1 violation in datom log: " bad)))
    (doseq [line (clojure.string/split-lines out)]
      (when (and (clojure.string/starts-with? line "[") (clojure.string/includes? line ":bond/"))
        (is (clojure.string/includes? line ":derived]") (str "derived not transient: " line))))
    (is (clojure.string/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [{n1 :nodes e1 :edges} (load-seed)
        {n2 :nodes e2 :edges} (load-seed)]
    (is (= (datom/emit n1 e1 1) (datom/emit n2 e2 1)) "Datom emit is not deterministic")))

#?(:clj (defn -main [& _] (run-tests 'torifune.tests.test-torifune)))
