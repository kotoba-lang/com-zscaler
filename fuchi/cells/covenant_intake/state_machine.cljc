(ns fuchi.cells.covenant-intake.state-machine
  "Phase state machine for 扶持 (fuchi) covenant_intake — the G4/G5/G9 eligibility membrane.
  1:1 port of cells/covenant_intake/state_machine.py (ADR-2606052300). A 信者 maintainer's covenant
  is SCREENED (refused) unless G4 covenant ∈ {outreach,vowed}, G5 owns-payoff false, G9 server-held-key
  false; a SCREENED covenant is RECORDED. REFUSAL gate, not a clamp."
  (:require [clojure.string :as str]))

(def covenants #{"outreach" "vowed"})
(def state-defaults
  {"phase" "init" "did" "" "covenant" "" "tenure_months" 0 "hazard_permille" 1000
   "owns_payoff" false "server_held_key" false "refusal" "" "payload" {}})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- to-int [v] (long (or v 0)))

(defn transition-to-screened [state]
  (let [cs (cell-state state)
        cs (assoc cs "did" (get state "did" (get cs "did"))
                  "covenant" (kw (get state "covenant" (get cs "covenant")))
                  "tenure_months" (to-int (get state "tenure_months" (get cs "tenure_months")))
                  "hazard_permille" (to-int (get state "hazard_permille" (get cs "hazard_permille")))
                  "owns_payoff" (boolean (get state "owns_payoff" (get cs "owns_payoff")))
                  "server_held_key" (boolean (get state "server_held_key" (get cs "server_held_key"))))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})]
    (cond
      (not (contains? covenants (get cs "covenant")))
      (refuse (str "G4: covenant " (pr-str (get cs "covenant")) " unrepresentable (conversion-gated §1.16)"))
      (get cs "owns_payoff")
      (refuse "G5: owns-payoff must be false (work product is commons; payoff帰属=etzhayyim)")
      (get cs "server_held_key")
      (refuse "G9/no-server-key: server-held-key must be false (ADR-2605231525)")
      (not (<= 1000 (get cs "hazard_permille") 2000))
      (refuse "hazard-permille out of [1000,2000]")
      :else {"cell_state" (assoc cs "refusal" "" "phase" "screened")})))

(defn transition-to-recorded [state]
  (let [cs (cell-state state)]
    (if (not= (get cs "phase") "screened")
      {"cell_state" (assoc cs "refusal" "cannot record a covenant that was not screened clean" "phase" "refused")}
      {"cell_state" (assoc cs "phase" "recorded"
                           "payload" {"maintainerDid" (get cs "did") "covenant" (get cs "covenant")
                                      "tenureMonths" (get cs "tenure_months") "hazardPermille" (get cs "hazard_permille")
                                      "ownsPayoff" false "serverHeldKey" false})})))

(defn solve [_input-state]
  (throw (ex-info "fuchi R0 scaffold: activate covenant_intake via Council ADR (post-2606052300 ratification)" {:scaffold true})))
