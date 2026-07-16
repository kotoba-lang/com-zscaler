(ns kiyome.cells.surface-cleaning.state-machine
  "1:1 port of cells/surface_cleaning/state_machine.py (ADR-2606032100) — the constitutional heart of
  kiyome 箒 (domestic/janitorial cleaning robotics). A cleaning pass into a private space can only be
  attested if privacy-by-construction holds as HARD invariants: on-device-only (no sensor feed left
  the robot), no occupant imagery retained (G9), no biometric/facial capture (N5). G3 requires a
  witness quorum (≥2 robot sigs + ≥1 human attestation) to finalize a pass. Pure phase-progression
  init → traversed → cleaned → pass_logged. CleaningState dataclass → string-keyed map under
  \"cell_state\"; ValueError → (throw (ex-info ...)).")

(def allowed-methods #{"sweep" "vacuum" "mop" "wipe"})

(def ^:private defaults
  {"phase" "init" "site_id" "did:web:kiyome.etzhayyim.com/site/demo-0001" "area_m2" 0 "method" "vacuum"
   "on_device_only" true "imagery_retained" false "biometric_capture" false
   "robot_sigs" [] "human_attestation" "" "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-to-traversed [state]
  {"cell_state" (assoc (state* state) "phase" "traversed" "area_m2" (int (get state "area_m2" 0)))
   "next_node" "cleaned"})

(defn transition-to-cleaned [state]
  (let [cs (state* state)
        method (get state "method" (get cs "method"))]
    (when-not (contains? allowed-methods method)
      (throw (ex-info (str "unknown cleaning method '" method "' not in " allowed-methods)
                      {:kiyome/method method})))
    {"cell_state" (assoc cs "phase" "cleaned" "method" method) "next_node" "pass_logged"}))

(defn transition-to-pass-logged [state]
  (let [cs (assoc (state* state)
                  "on_device_only" (boolean (get state "on_device_only" true))
                  "imagery_retained" (boolean (get state "imagery_retained" false))
                  "biometric_capture" (boolean (get state "biometric_capture" false))
                  "robot_sigs" (vec (get state "robot_sigs" []))
                  "human_attestation" (get state "human_attestation" ""))]
    ;; G9 privacy-by-construction — hard invariants (the cleaner robot is the opposite of a spy)
    (when-not (get cs "on_device_only")
      (throw (ex-info "G9 violation: imagery/sensor feed left the robot (on_device_only must be True)"
                      {:kiyome/violation :g9})))
    (when (get cs "imagery_retained")
      (throw (ex-info "G9 violation: occupant imagery retained (imagery_retained must be False)"
                      {:kiyome/violation :g9})))
    (when (get cs "biometric_capture")
      (throw (ex-info "N5 violation: biometric/facial recognition of occupants (biometric_capture must be False)"
                      {:kiyome/violation :n5})))
    (let [quorum-ok (and (>= (count (get cs "robot_sigs")) 2) (boolean (seq (get cs "human_attestation"))))]
      {"cell_state" (assoc cs "phase" "pass_logged"
                           "payload" {"cleaning_pass" {"siteId" (get cs "site_id")
                                                       "areaM2" (get cs "area_m2")
                                                       "method" (get cs "method")
                                                       "onDeviceOnly" true
                                                       "imageryRetained" false
                                                       "witnessQuorumMet" quorum-ok}})
       "next_node" "end"})))
