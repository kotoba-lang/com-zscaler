(ns itonami.tests.test-digest
  "itonami 営み — R4 daily digest + Murakumo narration tests (ADR-2606082300).
  1:1 Clojure port of tests/test_digest.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [itonami.methods.analyze :as analyze]
            [itonami.methods.inspect :as vis]
            [itonami.methods.trend :as trend]
            [itonami.methods.digest :as digest]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private ops (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))
(def ^:private det (io/file actor-dir "data" "seed-vision-detections.kotoba.edn"))

(defn- build []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)
        detections (vis/load-detections (slurp det))]
    (digest/build-digest stations ticks detections)))

(defn- build-with-history []
  (let [{:keys [stations ticks]} (analyze/load-file* ops)
        detections (vis/load-detections (slurp det))
        history (trend/load-history (slurp (io/file actor-dir "data" "seed-ops-history.kotoba.edn")))]
    (digest/build-digest stations ticks detections history)))

(deftest test-digest-fuses-all-four-cells
  (let [d (build)]
    (is (and (contains? d "line") (contains? (get d "line") "oee")))
    (is (and (contains? d "energy") (contains? (get d "energy") "energy_reduction_frac")))
    (is (and (contains? d "bottleneck") (= ":st.frame-weld" (get-in d ["bottleneck" "bottleneck"]))))
    (is (= ":st.cab-weld" (get-in d ["quality" "station"])))
    (is (= ":weld-porosity" (get-in d ["quality" "top_defect"])))))

(deftest test-fallback-narration-is-deterministic-and-factual
  (let [d (build)
        a (digest/fallback-narration d)
        b (digest/fallback-narration d)]
    (is (= a b))
    (is (and (str/includes? a "OEE") (str/includes? a "%")))
    (is (str/includes? a "Frame Weld Cell"))
    (is (str/includes? a "vision inspection"))))

(deftest test-narration-carries-no-worker-dimension
  (let [d (build)]
    (doseq [text [(digest/narration-prompt d) (digest/fallback-narration d)]]
      (let [low (str/lower-case text)]
        (doseq [forbidden ["worker" "operator" "person" "employee" "staff"]]
          (is (not (str/includes? low forbidden)) (str "narration leaked " forbidden)))))))

(deftest test-narration-is-murakumo-only
  (let [d (build)]
    (is (= "murakumo" digest/NARRATION-BACKEND))
    (is (str/includes? digest/MURAKUMO-GATEWAY "127.0.0.1"))
    (let [blob (str/lower-case (str (digest/narration-prompt d) digest/MURAKUMO-GATEWAY))]
      (doseq [forbidden ["openai" "anthropic" "vertex" "runpod" "bedrock" "api.openai"]]
        (is (not (str/includes? blob forbidden)))))))

(deftest test-narrate-falls-back-when-murakumo-unreachable
  (let [d (build)
        boom (fn [_] (throw (ex-info "murakumo down" {})))
        out (digest/narrate d boom)]
    (is (= "fallback-deterministic" (get out "backend")))
    (is (= (get out "text") (digest/fallback-narration d)))))

(deftest test-narrate-uses-murakumo-when-available
  (let [d (build)
        seen (atom {})
        fake (fn [prompt] (swap! seen assoc "prompt" prompt) "narrated by murakumo")
        out (digest/narrate d fake)]
    (is (= "murakumo" (get out "backend")))
    (is (= "narrated by murakumo" (get out "text")))
    (is (str/includes? (get @seen "prompt") "line OEE"))))

(deftest test-digest-includes-throughput-two-lens
  (let [d (build)]
    (is (= ":st.paint" (get-in d ["throughput" "bottleneck"])))
    (is (= ":st.frame-weld" (get-in d ["bottleneck" "bottleneck"])))
    (is (> (get-in d ["throughput" "units_per_day_good"]) 0))
    (is (true? (get (digest/facts d) "two_lens")))))

(deftest test-narration-surfaces-both-bottlenecks
  (let [d (build)
        text (digest/fallback-narration d)
        p (digest/narration-prompt d)]
    (is (and (str/includes? text "OEE bottleneck") (str/includes? text "throughput bottleneck")
             (str/includes? text "units/day")))
    (is (and (str/includes? p "OEE bottleneck") (str/includes? p "throughput bottleneck")))))

(deftest test-emit-includes-throughput-datoms
  (let [d (build)
        out (digest/emit d (digest/narrate d) 2)]
    (is (str/includes? out ":ops/digest-throughput-bottleneck :st.paint"))
    (is (str/includes? out ":ops/digest-units-per-day"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":ops/digest"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))))

(deftest test-digest-without-history-has-no-drift
  (let [d (build)
        f (digest/facts d)]
    (is (nil? (get d "drift")))
    (is (and (= 0 (get f "drift_n")) (nil? (get f "drift_top"))))
    (is (not (str/includes? (str/lower-case (digest/fallback-narration d)) "drift")))))

(deftest test-digest-with-history-surfaces-drift
  (let [d (build-with-history)]
    (is (and (some? (get d "drift")) (>= (get-in d ["drift" "n"]) 1)))
    (is (= ":st.cab-weld" (get-in d ["drift" "top" "scope"])))
    (is (str/includes? (str/lower-case (digest/fallback-narration d)) "drift"))
    (is (str/includes? (digest/emit d (digest/narrate d)) ":ops/digest-drift-count"))))

(deftest test-emit-transient-only
  (let [d (build)
        out (digest/emit d (digest/narrate d) 9)]
    (is (str/includes? out ":ops/digest-line-oee"))
    (is (str/includes? out ":ops/digest-narration-backend"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":ops/digest"))
        (is (and (str/includes? line ":derived]") (str/includes? line ":bond/is-transient true")) line)))
    (is (not (str/includes? out ":add]")))))

(deftest test-determinism
  (let [d (build)
        a (digest/emit d (digest/narrate d) 1)
        d2 (build)
        b (digest/emit d2 (digest/narrate d2) 1)]
    (is (= a b))))
