(ns ake.cells.promote.state-machine
  "Phase state machine for the 朱 (ake) promote cell — the G8/G9 no-server-key membrane.
  Clojure 1:1 port of cells/promote/state_machine.py (ADR-2606052100).

  An accepted edit is CLEARED for promotion (append the revision; optionally
  :representative→:authoritative) ONLY if:
    the review outcome was 'accepted' (not pending/rejected);
    G9 — the promotion is member/operator-signed (a server signature is REFUSED, no-server-key);
    G4 — a sourcing promotion to :authoritative carries verifiable provenance;
    G8 — published stays false (live publish into the served registry is Council Lv6+ + operator).
  REFUSAL gate, not an auto-promoter."
  (:require [clojure.string :as str]
            [ake.methods.triage :as triage]))

(def phase-init "init")
(def phase-cleared "cleared")
(def phase-refused "refused")

(defn default-state []
  {"phase" phase-init
   "edit_id" ""
   "entity" ""
   "attr" ""
   "value" ""
   "outcome" "pending"
   "to_sourcing" "representative"
   "provenance" ""
   "signed_by" ""
   "as_of" 0
   "refusal" ""
   "payload" {}})

(defn- state-of [d]
  (merge (default-state) (get d "cell_state" {})))

(defn- strip-colon [v]
  (str/replace (str v) #"^:+" ""))

(defn review-promotion [state]
  (let [s0 (state-of state)
        cs (-> s0
               (assoc "edit_id" (get state "edit_id" (get s0 "edit_id")))
               (assoc "entity" (get state "entity" (get s0 "entity")))
               (assoc "attr" (strip-colon (get state "attr" (get s0 "attr"))))
               (assoc "value" (get state "value" (get s0 "value")))
               (assoc "outcome" (strip-colon (get state "outcome" (get s0 "outcome"))))
               (assoc "to_sourcing" (strip-colon (get state "to_sourcing" (get s0 "to_sourcing"))))
               (assoc "provenance" (or (get state "provenance" (get s0 "provenance")) ""))
               (assoc "signed_by" (or (get state "signed_by" (get s0 "signed_by")) ""))
               (assoc "as_of" (long (get state "as_of" (get s0 "as_of")))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not= (get cs "outcome") "accepted")
      (refuse (str "outcome '" (get cs "outcome") "' is not 'accepted'; nothing to promote"))

      (or (str/blank? (get cs "signed_by"))
          (str/starts-with? (str/lower-case (get cs "signed_by")) "server"))
      (refuse "G9: promotion needs a member/operator signature; server signature refused")

      (and (= (get cs "to_sourcing") "authoritative")
           (not (triage/verifiable-provenance? (get cs "provenance"))))
      (refuse "G4: promotion to :authoritative requires verifiable provenance (URL/CID)")

      :else
      {"cell_state" (assoc cs
                           "payload" {"editId" (get cs "edit_id")
                                      "entity" (get cs "entity")
                                      "attr" (get cs "attr")
                                      "value" (get cs "value")
                                      "sourcing" (get cs "to_sourcing")
                                      "asOf" (get cs "as_of")
                                      "signedBy" (get cs "signed_by")
                                      "serverHeldKey" false
                                      "promoted" true
                                      "published" false}   ;; G8 — live publish is Council Lv6+ + operator
                           "refusal" ""
                           "phase" phase-cleared)})))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (G8)."
  [_input-state]
  (throw (ex-info "ake R0 scaffold: promote clears offline; live promotion + publish into the served registry is Council Lv6+ + operator gated (G8)."
                  {:scaffold true})))
