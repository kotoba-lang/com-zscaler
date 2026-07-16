(ns mizuho.cells.water-supply.state-machine
  "water_supply state machine — supply commissioning + dosing + dispatch (gated).
  1:1 port of cells/water_supply/state_machine.py.

  Pure, deterministic transitions enforcing mizuho gates. The runnable control loops
  live in ../../methods/water-supply.cljc (level/pressure) and ../../methods/chlorination.cljc
  (residual dosing); this wires them into a phase machine that ends at a member-signed,
  dry-run supply record (G6/G10/G12). cell.py .solve() stays Council-gated — these
  transitions are exercised by tests, not live actuation.

  Conventions: dataclass SupplyState → a plain map with the SAME string field keys the
  Python `cs.__dict__` round-trips; phase enum value identities stay strings; ValueError →
  ex-info; SafetyError refusals come from the substrate."
  (:require [mizuho.methods.water-supply :as ws]
            [mizuho.methods.chlorination :as chl]
            [mizuho.methods.-substrate :as sub]))

;; ── SupplyPhase (enum — Python value identities preserved) ──
(def phase-init "init")
(def phase-commissioned "commissioned")
(def phase-supply-committed "supply_committed")

;; ── SupplyState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"             phase-init
   "source_id"         "spring-01"
   "use"               "supply"
   "demand_step_lps"   20.0
   "service_population" 200
   "dosing_agent"      "disinfect"
   "per_member_consent" false
   "level_restored"    false
   "residual_held"     false
   "ceiling_respected" false
   "member_sig"        ""
   "server_sig"        ""
   "witness_sigs"      []
   "payload"           {}})

(defn- cell-state [state]
  (let [cs (get state "cell_state")]
    (if (map? cs)
      (merge state-defaults cs)
      state-defaults)))

(defn transition-commission
  "Run the supply + dosing acceptance tests (raises on non-civilian use / G3 /
  G6 fluoride-without-consent — all before any actuation modelling)."
  [state]
  (let [cs (cell-state state)
        use (get state "use" (get cs "use"))
        demand-step-lps (double (get state "demand_step_lps" (get cs "demand_step_lps")))
        service-population (long (get state "service_population" (get cs "service_population")))
        dosing-agent (get state "dosing_agent" (get cs "dosing_agent"))
        per-member-consent (boolean (get state "per_member_consent" (get cs "per_member_consent")))
        cs (assoc cs
                  "use" use
                  "demand_step_lps" demand-step-lps
                  "service_population" service-population
                  "dosing_agent" dosing-agent
                  "per_member_consent" per-member-consent)
        supply (ws/commission-water-supply
                :demand-step-lps demand-step-lps
                :use use
                :service-population service-population)
        dosing (chl/commission-dosing
                :agent dosing-agent
                :per-member-consent per-member-consent)
        cs (assoc cs
                  "level_restored" (:level-restored supply)
                  "residual_held" (:residual-held dosing)
                  "ceiling_respected" (:ceiling-respected dosing)
                  "payload" (assoc (get cs "payload")
                                   "supply" (ws/to-datoms supply (get cs "source_id"))
                                   "dosing" (chl/to-datoms dosing (get cs "source_id")))
                  "phase" phase-commissioned)]
    {"cell_state" cs "next_node" "commit_supply"}))

(defn transition-commit-supply
  "G7/G12 member-signed supply record + G8 witness quorum; always dry-run at R0."
  [state]
  (let [cs (cell-state state)
        member-sig (get state "member_sig" (get cs "member_sig"))
        server-sig (get state "server_sig" (get cs "server_sig"))
        witness-sigs (get state "witness_sigs" (get cs "witness_sigs"))
        cs (assoc cs
                  "member_sig" member-sig
                  "server_sig" server-sig
                  "witness_sigs" witness-sigs)]
    (sub/require-member-signature member-sig server-sig) ; raises on violation
    (let [quorum (sub/witness-quorum-ok witness-sigs)]
      (when-not (get cs "level_restored")
        (throw (ex-info "acceptance test failed: service level not restored; cannot commission" {})))
      (when-not (get cs "ceiling_respected")
        (throw (ex-info "acceptance test failed: residual ceiling not respected; cannot commission" {})))
      (when-not (get quorum "ok")
        (throw (ex-info (str "witness quorum < 2 (G8): cannot commit supply record (" (get quorum "reason") ")") {})))
      (let [cs (assoc cs
                      "payload" (assoc (get cs "payload")
                                       "supply_record"
                                       {"sourceId" (get cs "source_id")
                                        "use" (get cs "use")
                                        "servicePopulation" (get cs "service_population")
                                        "levelRestored" (get cs "level_restored")
                                        "residualHeld" (get cs "residual_held")
                                        "ceilingRespected" (get cs "ceiling_respected")
                                        "dosingAgent" (get cs "dosing_agent")
                                        "memberSig" (get cs "member_sig")
                                        "witnessOk" (get quorum "ok")
                                        "escalateCouncilLv6" (get quorum "escalate_council_lv6" false)
                                        "serverHeldKey" false  ; no-server-key structural invariant
                                        "dryRun" true})        ; G10: R0 offline only
                      "phase" phase-supply-committed)]
        {"cell_state" cs "next_node" "end"}))))
