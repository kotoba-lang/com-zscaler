(ns nusa.cells.cultivation-license-plan.state-machine
  "Phase state machine for the nusa 幣 cultivation_license_plan cell.
  1:1 port of cells/cultivation_license_plan/state_machine.py (ADR-2606039800).

  Turns a low-THC fibre cultivar into an *unsigned, member-principal* 栽培者免許 design, then
  authorizes it with a member signature only (the yadori reservation pattern).
  Invariants: G1 fibre/low-THC re-screen · G4 member-principal/no-fiat-inflow · G5 no-server-key ·
  G8 outward-gated. Conventions: dataclass → plain map with Python string field keys; ValueError → ex-info."
  (:require [clojure.string :as str]))

(def allowed-thc-classes #{"fiber" "low-thc"})
(def allowed-funding #{"member-okaimono"})
(def prohibited-funding #{"org-treasury" "org-fiat" "stripe" "paypal" "card-on-file"})
(def allowed-purpose #{"ritual-fiber" "industrial-fiber"})

(def license-phases {:init "init" :screened "screened" :plan-built "plan_built" :authorized "authorized"})
(def phase-init       (:init license-phases))
(def phase-screened   (:screened license-phases))
(def phase-plan-built (:plan-built license-phases))
(def phase-authorized (:authorized license-phases))

(def state-defaults
  {"phase"              phase-init
   "license_id"         ""
   "cultivar"           ""
   "thc_class"          "fiber"
   "purpose"            "ritual-fiber"
   "licensee_principal" "member"          ; G4: always the member
   "funding_source"     "member-okaimono"
   "server_held_key"    false             ; G5: always false
   "outward_gated"      true              ; G8: always true
   "legal_basis"        "大麻草の栽培の規制に関する法律 (栽培者免許; 要 primary-source 引用)"
   "member_sig"         ""
   "server_sig"         ""                ; G5: must remain empty
   "payload"            {}})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- norm [v] (str/replace (str (or v "")) #"^:+" ""))

(defn transition-to-screened
  "G1: re-screen the cultivar's THC class (defence in depth)."
  [state]
  (let [cs (cell-state state)
        cs (assoc cs "cultivar" (get state "cultivar" (get cs "cultivar")))
        cls (norm (get state "thc_class" (get cs "thc_class")))]
    (when-not (contains? allowed-thc-classes cls)
      (throw (ex-info (str "G1 violation: cultivar " (pr-str (get cs "cultivar")) " thc-class " (pr-str cls)
                           " not in " (pr-str (vec (sort allowed-thc-classes)))
                           "; no licence design for non-fibre/low-THC cultivars.") {:gate "G1"})))
    (let [purpose (norm (get state "purpose" (get cs "purpose")))]
      (when-not (contains? allowed-purpose purpose)
        (throw (ex-info (str "purpose " (pr-str purpose) " not in " (pr-str (vec (sort allowed-purpose)))) {:gate "purpose"})))
      {"cell_state" (assoc cs "thc_class" cls "purpose" purpose "phase" phase-screened)})))

(defn transition-to-plan-built
  "G4/G5/G8: build the unsigned member-principal licence design."
  [state]
  (let [cs (cell-state state)
        funding (norm (get state "funding_source" (get cs "funding_source")))]
    (when (or (contains? prohibited-funding funding) (not (contains? allowed-funding funding)))
      (throw (ex-info (str "G4 violation: funding_source " (pr-str funding) " must be member-okaimono; "
                           "licence fees never from org treasury / fiat processor; nusa is never the funder.") {:gate "G4"})))
    (let [lid (let [v (get state "license_id" (get cs "license_id"))]
                (if (seq v) v (str "license." (get cs "cultivar"))))
          cs (assoc cs
                    "funding_source" funding
                    "licensee_principal" "member"   ; G4
                    "server_held_key" false         ; G5
                    "outward_gated" true            ; G8
                    "license_id" lid
                    "phase" phase-plan-built)]
      {"cell_state" cs})))

(defn transition-to-authorized
  "G5: authorize with a MEMBER signature only; refuse any server signature."
  [state]
  (let [cs (cell-state state)
        server-sig (get state "server_sig" (get cs "server_sig"))]
    (when (seq server-sig)
      (throw (ex-info "G5 violation: server signature refused; the member signs the licence plan" {:gate "G5"})))
    (let [member-sig (get state "member_sig" (get cs "member_sig"))]
      (when-not (seq member-sig)
        (throw (ex-info "authorization requires a member signature (G5)" {:gate "G5"})))
      (let [cs (assoc cs
                      "member_sig" member-sig
                      "phase" phase-authorized
                      "payload" {"licenseId" (get cs "license_id")
                                 "cultivar" (get cs "cultivar")
                                 "purpose" (get cs "purpose")
                                 "licenseePrincipal" "member"
                                 "fundingSource" (get cs "funding_source")
                                 "serverHeldKey" false
                                 "outwardGated" true
                                 "legalBasis" (get cs "legal_basis")
                                 "signed" true
                                 "signedBy" "member"})]
        {"cell_state" cs}))))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (ADR-2606039800 §Decision)."
  [_input-state]
  (throw (ex-info "nusa R0 scaffold: activate cultivation_license_plan via Council ADR (post-2606039800 ratification)"
                  {:scaffold true})))
