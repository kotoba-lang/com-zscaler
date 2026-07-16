(ns kuni-umi.robotics.commissioning
  "commissioning — the runnable kuni-umi → open-ot handoff (3-layer integration).
  1:1 cljc port of `robotics/commissioning.py`.

  The deterministic reference behind CommissioningCell.commission_test /
  register_with_open_ot. Proves the handoff OFFLINE end-to-end:
    Layer 1 (plant)   MicrogridPlant — the islanded grid being commissioned
    Layer 2 (control) DroopPI — the open-ot DROOP_P_F + PI field-tier loop
    Layer 3 (coord)   this harness — acceptance test (black-start + droop-P-f
                      load-step), ≥2 witness quorum (G8), open-ot loop regs.
  No SDK, no XRPC, no hardware: a dry-run record (G15 no-server-key, R0 offline)."
  (:require [kuni-umi.robotics.plant :as plant]
            [kuni-umi.robotics.control :as control]
            [kuni-umi.robotics.safety :as safety]))

(def ROCOF-TRIP-HZ-PER-S
  "Anti-islanding ROCOF trip threshold (Hz/s)."
  2.0)

(defn rocof
  "Worst rate-of-change-of-frequency over a sliding window. Port of `_rocof`.
  `traj` is a vector of [t value cmd]."
  ([traj] (rocof traj 0.1))
  ([traj window-s]
   (if (< (count traj) 2)
     0.0
     (let [dt (- (get-in traj [1 0]) (get-in traj [0 0]))
           span (if (> dt 0) (max 1 (long (control/round-half-even (/ window-s dt) 0))) 1)]
       (reduce
        (fn [worst i]
          (let [d (- (get-in traj [i 0]) (get-in traj [(- i span) 0]))]
            (if (> d 0)
              (max worst (/ (Math/abs (double (- (get-in traj [i 1])
                                                 (get-in traj [(- i span) 1]))))
                            d))
              worst)))
        0.0
        (range span (count traj)))))))

(defn run-microgrid-acceptance
  "S2 microgrid acceptance test: load step + droop-P-f response (open-ot DROOP_P_F).
  Port of `run_microgrid_acceptance`. Returns {:passed :final-freq-hz :rocof
  :rocof-tripped}. `passed` requires frequency recovery to 50 Hz AND the ROCOF
  guard NOT to trip (a clean island)."
  ([] (run-microgrid-acceptance 140.0))
  ([load-step-kw]
   (let [grid (-> (plant/microgrid-plant :p-load 100.0 :f 50.0)
                  (plant/set-load load-step-kw))
         controller (control/droop-pi
                     (control/droop :nominal (:f-nom grid) :droop-r 0.04
                                    :p-base 100.0 :p-min 0.0 :p-max 200.0)
                     (control/pid :kp 4.0 :ki 20.0 :out-min -200.0 :out-max 200.0))
         res (control/simulate grid controller (:f-nom grid) 8000 0.01 :tol 1e-2)
         r (rocof (:trajectory res))]
     {:passed (boolean (and (:converged res) (<= r ROCOF-TRIP-HZ-PER-S)))
      :final-freq-hz (control/round-half-even (:final-value res) 4)
      :rocof (control/round-half-even r 4)
      :rocof-tripped (> r ROCOF-TRIP-HZ-PER-S)})))

(defn commission-microgrid-site
  "Commission an islanded-microgrid site and hand its loops to open-ot. Port of
  `commission_microgrid_site`. Fail-fast: member signature (G15/G7) before
  anything; witness quorum (G8) recorded (not raised). `device-evidence` (R1) can
  only tighten the tier (device-wasm) / demote to punch-list, never loosen."
  [{:keys [site-did loop-dids member-sig witness-sigs load-step-kw server-sig device-evidence]
    :or   {load-step-kw 140.0 server-sig ""}}]
  (safety/require-member-signature member-sig server-sig)   ;; G15/G7
  (let [quorum (safety/witness-quorum-ok witness-sigs)       ;; G8 (record, escalate)
        acceptance (run-microgrid-acceptance load-step-kw)
        device-ok (if (some? device-evidence)
                    (boolean
                     (and (:freq-restored device-evidence)
                          (not (get device-evidence :rocof-trip true))
                          (:twin-verdict-match device-evidence)
                          (:dry-run device-evidence)
                          (false? (:server-held-key device-evidence))))
                    true)
        tier (if (some? device-evidence)
               (if device-ok "device-wasm" "python-twin")
               "python-twin")
        operational (and (:passed acceptance) (:ok quorum) device-ok)]
    {:site-did site-did
     :open-ot-loop-dids (vec loop-dids)
     :acceptance-passed (:passed acceptance)
     :acceptance-tier tier
     :final-freq-hz (:final-freq-hz acceptance)
     :rocof-tripped (:rocof-tripped acceptance)
     :witness-ok (:ok quorum)
     :escalate-council-lv6 (get quorum :escalate-council-lv6 false)
     :member-sig member-sig
     :server-held-key false        ;; G15 structural invariant
     :site-state (if operational "operational" "punch-list")
     :dry-run true}))

(defn to-datoms
  "Project a commissioning record into kotoba EAVT-shaped datoms (G6). Port of
  `to_datoms`."
  [record]
  {:commission/site (:site-did record)
   :commission/open-ot-loops (vec (:open-ot-loop-dids record))
   :commission/acceptance-passed (:acceptance-passed record)
   :commission/acceptance-tier (:acceptance-tier record)
   :commission/final-freq-hz (:final-freq-hz record)
   :commission/rocof-tripped (:rocof-tripped record)
   :commission/witness-ok (:witness-ok record)
   :commission/escalate-council-lv6 (:escalate-council-lv6 record)
   :commission/member-sig (:member-sig record)
   :commission/server-held-key (:server-held-key record)   ;; G15: always false
   :commission/site-state (:site-state record)
   :commission/dry-run (:dry-run record)})
