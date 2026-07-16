(ns noroshi.methods.test-fibre-loop
  "Tests for the noroshi fibre-optic loop core — lay → align → splice (ADR-2606051600).
  1:1 Clojure port of methods/test_fibre_loop.py. Every Python assertion is ported.

  Reuses the EXISTING noroshi aligner (active-alignment/align) and the shared infra-robotics
  substrate (substrate ≅ _substrate.py); nothing here re-implements Hooke-Jeeves or the
  laser-safety gate. The constitutional gates are made explicit + test-enforced:

    N1/G3 civilian-use   — a forbidden / non-allowlisted use RAISES SafetyError before any model.
    G5    laser-safety   — a weapon use / hazardous class w/o interlock RAISES LaserSafetyError.
    G7    no-server-key  — a server signature, or a missing member signature, RAISES SafetyError.
    G8    witness quorum — quorum < 2 ⇒ witness-ok false ⇒ overall-ok false.
    G7/G8/G10            — server-held-key=false, dry-run=true, representative=true on the result."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [noroshi.methods.fibre-loop :as fl]
            [noroshi.methods.active-alignment :as aa]
            [noroshi.methods.substrate :as sub]))

(defn approx?
  "pytest.approx-equivalent (relative tolerance, mirrors pytest.approx rel=1e-6)."
  [a b rel]
  (<= (Math/abs (- (double a) (double b))) (* rel (Math/abs (double b)))))

(defn safety-error?
  "Matches Python `pytest.raises(SafetyError)` — the substrate gate exception."
  [thunk]
  (try (thunk) false
       (catch clojure.lang.ExceptionInfo e (sub/safety-error? e))))

(defn laser-safety-error?
  "Matches Python `pytest.raises(LaserSafetyError)` — the aligner laser gate exception
  (active-alignment throws an ex-info tagged :error \"LaserSafetyError\")."
  [thunk]
  (try (thunk) false
       (catch clojure.lang.ExceptionInfo e (= "LaserSafetyError" (:error (ex-data e))))))

;; ── LAY: cross-track tracking converges ──────────────────────────────────────
(deftest test-lay-converges-to-route
  (let [res (fl/lay-segment 2.0)]
    (is (:track-converged res))
    (is (< (Math/abs (:final-xte-m res)) 1e-2))          ; driven onto the planned route
    (is (> (:settling-seconds res) 0))))

(deftest test-lay-rejects-drift-to-zero-steady-state
  ;; A non-zero constant drift must be integrated out by the PI term (no offset).
  (let [res (fl/lay-segment -3.0 {:drift 0.2})]
    (is (:track-converged res))
    (is (< (Math/abs (:final-xte-m res)) 1e-2))))

(deftest test-lay-plant-is-a-plant
  (let [p (fl/->cable-lay-plant {:e 1.0})]
    (is (= 1.0 (fl/cable-lay-measure p)))
    (fl/cable-lay-step! p -1.0 0.1)                       ; negative steering reduces +e
    (is (< (fl/cable-lay-measure p) 1.0))))

(deftest test-lay-non-civilian-use-raises
  (is (safety-error? #(fl/lay-segment 1.0 {:use "weapon"}))))

;; ── SPLICE: loss model + acceptance ──────────────────────────────────────────
(deftest test-splice-loss-grows-with-offset
  (is (< (fl/splice-loss-db 0.0 0.0)
         (fl/splice-loss-db 2.0 0.0)
         (fl/splice-loss-db 5.0 0.0))))

(deftest test-splice-loss-grows-with-cleave-angle
  (is (< (fl/splice-loss-db 0.0 0.0)
         (fl/splice-loss-db 0.0 1.0)
         (fl/splice-loss-db 0.0 3.0))))

(deftest test-splice-loss-is-quadratic-in-offset
  ;; Doubling the lateral offset quadruples the offset-loss contribution.
  (let [l1 (fl/splice-loss-db 1.0 0.0)
        l2 (fl/splice-loss-db 2.0 0.0)]
    (is (approx? l2 (* 4.0 l1) 1e-6))))

(deftest test-splice-loss-uses-magnitude
  (is (= (fl/splice-loss-db -2.0 -1.0)
         (fl/splice-loss-db 2.0 1.0))))

(deftest test-splice-passes-when-well-aligned
  (let [res (fl/splice 0.4 0.3)]
    (is (map? res))
    (is (<= (:loss-db res) fl/SPLICE-LOSS-MAX-DB))
    (is (:passed res))))

(deftest test-splice-fails-when-offset-large
  (let [res (fl/splice 12.0 0.0)]
    (is (> (:loss-db res) fl/SPLICE-LOSS-MAX-DB))
    (is (not (:passed res)))))

;; ── laser-safety inherited from the REUSED aligner (G5 / N1) ─────────────────
(deftest test-weapon-laser-use-cannot-be-energised-in-the-loop
  ;; The loop calls the existing align()/enable-laser() gate; a weapon use raises before probe.
  (is (laser-safety-error?
       #(fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"]
                             :laser (aa/laser-spec :use "weapon")))))

(deftest test-hazardous-laser-without-interlock-refused-in-the-loop
  (is (laser-safety-error?
       #(fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"]
                             :laser (aa/laser-spec :laser-class "4" :use "alignment"
                                                   :enclosure-interlock false)))))

;; ── G7 no-server-key gate ────────────────────────────────────────────────────
(deftest test-server-signature-refused
  (is (safety-error?
       #(fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"]
                             :server-sig "s:platform:sig"))))

(deftest test-missing-member-signature-refused
  (is (safety-error?
       #(fl/lay-align-splice 2.0 "" ["did:web:robot-a" "did:web:robot-b"]))))

;; ── N1 civilian-use gate on the composed loop ────────────────────────────────
(deftest test-non-civilian-use-raises-on-full-loop
  (is (safety-error?
       #(fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"]
                             :use "fire-control"))))

;; ── full happy path ──────────────────────────────────────────────────────────
(deftest test-full-lay-align-splice-happy-path
  (let [seg (fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"])]
    (is (map? seg))
    (is (:track-converged seg))
    (is (:align-converged seg))
    (is (:splice-passed seg))
    (is (:witness-ok seg))
    (is (= true (:overall-ok seg)))
    (is (= false (:server-held-key seg)))    ; G7
    (is (= true (:dry-run seg)))             ; G8
    (is (= true (:representative seg)))       ; G10
    (is (> (:coupling-loss-db seg) 0.0))))

(deftest test-overall-not-ok-when-witness-quorum-fails
  (let [seg (fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a"])]   ; quorum < 2 (G8)
    (is (= false (:witness-ok seg)))
    (is (= false (:overall-ok seg)))))

(deftest test-overall-not-ok-when-splice-fails
  (let [seg (fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"]
                                 :splice-offset-um 15.0)]   ; forces splice loss over threshold
    (is (= false (:splice-passed seg)))
    (is (= false (:overall-ok seg)))))

;; ── datom projection ─────────────────────────────────────────────────────────
(deftest test-to-datoms-carries-charter-invariants
  (let [seg (fl/lay-align-splice 2.0 "m:ed25519:demo" ["did:web:robot-a" "did:web:robot-b"])
        d (fl/to-datoms seg "fibre-seg-001")]
    (is (= "fibre-seg-001" (d ":fibre.segment/id")))
    (is (= false (d ":fibre.segment/server-held-key")))   ; G7
    (is (= true (d ":fibre.segment/dry-run")))            ; G8
    (is (= true (d ":fibre.segment/representative")))      ; G10
    (is (= true (d ":fibre.segment/overall-ok")))))

;; ── byte-parity: the report matches python3 fibre_loop.py exactly ────────────
(deftest test-report-byte-parity-headline
  (let [r (fl/report)]
    (is (str/includes? r "final cross-track error   : -0.0 m  (converged in 10.12s)"))
    (is (str/includes? r "coupling insertion loss   : 0.9692 dB  (converged)"))
    (is (str/includes? r "splice loss               : 0.001336 dB  (threshold 0.1 dB → PASS)"))
    (is (str/includes? r "segment overall : OK  (serverHeldKey=False, dryRun=True)"))))
