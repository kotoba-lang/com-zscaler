(ns ake.cells.review-vote.state-machine
  "Phase state machine for the 朱 (ake) review_vote cell — the community-consensus membrane.
  Clojure 1:1 port of cells/review_vote/state_machine.py (ADR-2606052100).

  An escalated proposal is decided by the mechanism its triage route selected:
    optimistic  — low-risk well-sourced edit; accepted on the fast path (no vote needed).
    sbt-vote    — 1 SBT = 1 vote with a timelock; accepted iff yes > no after the window.
    council-lv7 — invariant-adjacent; this cell returns :pending (Council attests separately, G7).
  A server-signed tally is REFUSED (no-server-key)."
  (:require [clojure.string :as str]))

(def DEFAULT-TIMELOCK-H 48)
(def MECHANISMS #{"optimistic" "sbt-vote" "council-lv7"})

(def phase-init "init")
(def phase-tallied "tallied")
(def phase-refused "refused")

(defn default-state []
  {"phase" phase-init
   "edit_id" ""
   "mechanism" "sbt-vote"
   "yes" 0
   "no" 0
   "timelock_h" DEFAULT-TIMELOCK-H
   "signed_by" ""
   "outcome" "pending"
   "refusal" ""
   "payload" {}})

(defn- state-of [d]
  (merge (default-state) (get d "cell_state" {})))

(defn tally [state]
  (let [s0 (state-of state)
        cs (-> s0
               (assoc "edit_id" (get state "edit_id" (get s0 "edit_id")))
               (assoc "mechanism" (str/replace (str (get state "mechanism" (get s0 "mechanism"))) #"^:+" ""))
               (assoc "yes" (long (get state "yes" (get s0 "yes"))))
               (assoc "no" (long (get state "no" (get s0 "no"))))
               (assoc "timelock_h" (long (get state "timelock_h" (get s0 "timelock_h"))))
               (assoc "signed_by" (or (get state "signed_by" (get s0 "signed_by")) "")))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (not (contains? MECHANISMS (get cs "mechanism")))
      (refuse (str "unknown review mechanism '" (get cs "mechanism") "'"))

      (str/starts-with? (str/lower-case (get cs "signed_by")) "server")
      (refuse "no-server-key: a tally cannot be server-signed (ADR-2605231525)")

      :else
      (let [outcome (case (get cs "mechanism")
                      "optimistic" "accepted"
                      "sbt-vote" (if (> (get cs "yes") (get cs "no")) "accepted" "rejected")
                      "pending")    ;; council-lv7 → Council attests via councilEditReview (G7)
            cs (assoc cs
                      "outcome" outcome
                      "payload" {"editId" (get cs "edit_id")
                                 "mechanism" (get cs "mechanism")
                                 "yes" (get cs "yes")
                                 "no" (get cs "no")
                                 "timelockH" (get cs "timelock_h")
                                 "outcome" outcome
                                 "signedBy" (get cs "signed_by")}
                      "refusal" ""
                      "phase" phase-tallied)]
        {"cell_state" cs}))))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (G8)."
  [_input-state]
  (throw (ex-info "ake R0 scaffold: review_vote tallies offline; live binding votes are Council Lv6+ + operator gated (G8)."
                  {:scaffold true})))
