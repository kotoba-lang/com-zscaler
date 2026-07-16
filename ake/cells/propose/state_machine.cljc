(ns ake.cells.propose.state-machine
  "Phase state machine for the 朱 (ake) propose cell — the G1/G3/G4/G9 intake membrane.
  Clojure 1:1 port of cells/propose/state_machine.py (ADR-2606052100).

  A member-signed correction enters here. It is SCREENED (rejected outright) unless:
    G1 — author-kind is 'member' and server-held-key is false (no-server-key + 信者-gated);
    G3 — target-kind ∈ {kg-fact, actor-profile} (impersonation unrepresentable);
    G4 — provenance (URL/CID) is present (an unsourced proposal never enters the log);
    G9 — the author is not currently throttled (an unbroken recent run of refused proposals,
         methods/contributor.cljc). Caller-supplied via the OPTIONAL \"contributor_trajectory\"
         input key ({did -> [event, ...]}); absent/empty means no history for this author, which
         throttled? treats as not-throttled by construction — so existing callers that don't yet
         have a trajectory store keep their prior behaviour unchanged. Recoverable, not punitive
         (a single accepted edit un-throttles) — never a permanent ban or a stored reputation score.
  A SCREENED proposal is then RECORDED as an append-only :edit/* datom. REFUSAL gate, not a clamp.

  State dicts use STRING keys (\"cell_state\", \"edit_id\", …) mirroring the Python dict threading."
  (:require [clojure.string :as str]
            [ake.methods.contributor :as contributor]))

(def TARGET-KINDS #{"kg-fact" "actor-profile"})

(def phase-init "init")
(def phase-screened "screened")
(def phase-recorded "recorded")
(def phase-refused "refused")

(defn default-state
  "ProposeState dataclass defaults as a map."
  []
  {"phase" phase-init
   "edit_id" ""
   "target_kind" ""
   "target_entity" ""
   "target_attr" ""
   "op" "assert"
   "author" ""
   "author_kind" ""
   "provenance" ""
   "server_held_key" false
   "refusal" ""
   "payload" {}})

(defn- state-of [d]
  (merge (default-state) (get d "cell_state" {})))

(defn- kw* [v]
  (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))

(defn transition-to-screened [state]
  (let [s0 (state-of state)
        cs (-> s0
               (assoc "edit_id" (get state "edit_id" (get s0 "edit_id")))
               (assoc "target_kind" (kw* (get state "target_kind" (get s0 "target_kind"))))
               (assoc "target_entity" (get state "target_entity" (get s0 "target_entity")))
               (assoc "target_attr" (kw* (get state "target_attr" (get s0 "target_attr"))))
               (assoc "op" (kw* (get state "op" (get s0 "op"))))
               (assoc "author" (get state "author" (get s0 "author")))
               (assoc "author_kind" (kw* (get state "author_kind" (get s0 "author_kind"))))
               (assoc "provenance" (or (get state "provenance" (get s0 "provenance")) ""))
               (assoc "server_held_key" (boolean (get state "server_held_key" (get s0 "server_held_key")))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not (contains? TARGET-KINDS (get cs "target_kind")))
      (refuse (str "G3: target-kind '" (get cs "target_kind") "' unrepresentable (no impersonation)"))

      (not= (get cs "author_kind") "member")
      (refuse "G1: author-kind must be 'member' (no-server-key + 信者-gated)")

      (get cs "server_held_key")
      (refuse "G1/no-server-key: server-held-key must be false (ADR-2605231525)")

      (contributor/throttled? (get state "contributor_trajectory" {}) (get cs "author"))
      (refuse (str "G9: author is currently throttled (an unbroken recent run of refused "
                   "proposals) — a single accepted edit un-throttles (recoverable, not punitive)"))

      (str/blank? (str/trim (str (get cs "provenance"))))
      (refuse "G4: an unsourced proposal is refused — provenance is mandatory")

      :else
      {"cell_state" (assoc cs "refusal" "" "phase" phase-screened)})))

(defn transition-to-recorded [state]
  (let [cs (state-of state)]
    (if (not= (get cs "phase") phase-screened)
      {"cell_state" (assoc cs
                           "refusal" "cannot record a proposal that was not screened clean"
                           "phase" phase-refused)}
      {"cell_state" (assoc cs
                           "payload" {"editId" (get cs "edit_id")
                                      "targetKind" (get cs "target_kind")
                                      "targetEntity" (get cs "target_entity")
                                      "targetAttr" (get cs "target_attr")
                                      "op" (get cs "op")
                                      "author" (get cs "author")
                                      "authorKind" "member"
                                      "serverHeldKey" false
                                      "provenance" (get cs "provenance")}
                           "phase" phase-recorded)})))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (G8)."
  [_input-state]
  (throw (ex-info (str "ake R0 scaffold: propose screens + records offline; live recording into the "
                       "canonical Datom log is Council Lv6+ + operator gated (G8).")
                  {:scaffold true})))
