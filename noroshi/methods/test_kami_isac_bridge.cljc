(ns noroshi.methods.test-kami-isac-bridge
  "noroshi 烽 × kami-autodrive ISAC sensor bridge tests (ADR-2606051600). 1:1 Clojure port of
  methods/test_kami_isac_bridge.py, PLUS the constitutional gates the task requires made explicit:

    G3 civilian-force-separation / N1 — the bridge senses object range/velocity only; no
       fire-control / targeting field is representable.
    G4 sensing-not-surveillance / N2 — a ScenarioObject is a civilian object (range0/velocity),
       NEVER a person; a TrackPoint carries no biometric / pattern-of-life field.
    G10 sourcing-honesty — the track report is byte-identical to python3 + states the HONEST
       integration state (kami-engine submodule unpopulated)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [noroshi.methods.isac-sim :as isac]
            [noroshi.methods.kami-isac-bridge :as k]))

(def WF (isac/waveform))

(defn approx?
  ([a b] (approx? a b 1e-6))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) tol)))

(defn rel-approx? [a b tol]
  (<= (Math/abs (- (double a) (double b))) (* tol (Math/abs (double b)))))

(defn bins-set [ests] (set (map (fn [e] [(:range-bin e) (:doppler-bin e)]) ests)))

;; ── ported assertions ────────────────────────────────────────────────────────
(deftest test-track-follows-closing-range
  (let [obj (k/scenario-object "o1" (* 30 (isac/range-resolution-m WF))
                               (* 2 (isac/velocity-resolution-mps WF)))
        track (k/track-object WF obj :frames 6 :frame-dt-s 0.002)
        ranges (mapv :range-m track)]
    (is (= (count track) 6))
    (is (every? (fn [i] (>= (nth ranges i) (nth ranges (inc i)))) (range (dec (count ranges)))))))

(deftest test-velocity-recovered-each-frame
  (let [v (* 3 (isac/velocity-resolution-mps WF))
        obj (k/scenario-object "o1" (* 30 (isac/range-resolution-m WF)) v)
        track (k/track-object WF obj :frames 4)]
    (doseq [p track]
      (is (rel-approx? (:velocity-mps p) v 1e-6)))))

(deftest test-track-stops-when-object-passes-ego
  (let [obj (k/scenario-object "fast" (* 2 (isac/range-resolution-m WF))
                               (* 50 (isac/velocity-resolution-mps WF)))
        track (k/track-object WF obj :frames 20 :frame-dt-s 0.05)]
    (is (< (count track) 20))
    (is (every? (fn [p] (> (:range-m p) 0)) track))))

(deftest test-run-scenario-returns-track-per-object
  (let [objs [(k/scenario-object "a" (* 15 (isac/range-resolution-m WF)) (isac/velocity-resolution-mps WF))
              (k/scenario-object "b" (* 18 (isac/range-resolution-m WF)) (* 2 (isac/velocity-resolution-mps WF)))]
        tracks (k/run-scenario objs :wf WF :frames 3)]
    (is (= (set (keys tracks)) #{"a" "b"}))
    (is (every? (fn [t] (= (count t) 3)) (vals tracks)))))

(deftest test-object-starting-at-or-behind-ego-yields-empty-track
  (let [obj (k/scenario-object "at-ego" 0.0 (isac/velocity-resolution-mps WF))]
    (is (= (k/track-object WF obj :frames 5) []))))

(deftest test-zero-velocity-object-keeps-constant-range
  (let [obj (k/scenario-object "static" (* 12 (isac/range-resolution-m WF)) 0.0)
        track (k/track-object WF obj :frames 4)
        ranges (set (map (fn [p] (isac/py-round (:range-m p) 6)) track))]
    (is (= (count ranges) 1))
    (is (every? (fn [p] (= (:doppler-bin p) 0)) track))))

(deftest test-sense-frame-detects-all-objects-in-one-shot
  (let [objs [(k/scenario-object "a" (* 4 (isac/range-resolution-m WF)) (* 2 (isac/velocity-resolution-mps WF)))
              (k/scenario-object "b" (* 14 (isac/range-resolution-m WF)) (* 5 (isac/velocity-resolution-mps WF)))]
        dets (k/sense-frame objs :wf WF)]
    (is (= (bins-set dets) #{[4 2] [14 5]}))))

(deftest test-sense-frame-drops-objects-at-or-behind-ego
  (let [objs [(k/scenario-object "ahead" (* 6 (isac/range-resolution-m WF)) (isac/velocity-resolution-mps WF))
              (k/scenario-object "at-ego" 0.0 (isac/velocity-resolution-mps WF))]]
    (is (= (count (k/sense-frame objs :wf WF)) 1))))

(deftest test-report-renders-and-is-civilian
  (let [txt (k/report)]
    (is (str/includes? txt "ISAC sensor"))
    (is (or (str/includes? txt "Civilian") (str/includes? txt "civilian")))))

;; ── extra gate-enforcement tests (gates test-enforced 1:1) ────────────────────
(deftest test-gate-scenario-object-is-civilian-object-not-person
  ;; G4/N2: a ScenarioObject is range0/velocity only — never a person/biometric field.
  (let [o (k/scenario-object "obj" 100.0 10.0)]
    (is (= (set (keys o)) #{:object-id :range0-m :velocity-mps}))
    (is (not (contains? o :person)))
    (is (not (contains? o :biometric)))))

(deftest test-gate-track-point-has-no-targeting-field
  ;; G3/N1: a TrackPoint is kinematic (range/velocity/bins) — no fire-control / weaponizable field.
  (let [obj (k/scenario-object "o1" (* 30 (isac/range-resolution-m WF))
                               (* 2 (isac/velocity-resolution-mps WF)))
        p (first (k/track-object WF obj :frames 2))]
    (is (= (set (keys p)) #{:frame :time-s :range-m :velocity-mps :range-bin :doppler-bin}))
    (is (not (contains? p :weaponizable)))
    (is (not (contains? p :fire-control)))))

(deftest test-gate-report-states-honest-integration
  (let [txt (k/report)]
    (is (str/includes? txt "HONEST"))
    (is (str/includes? txt "kami-engine submodule is unpopulated"))))
