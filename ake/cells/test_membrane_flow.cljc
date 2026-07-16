(ns ake.cells.test-membrane-flow
  "Cell-chain integration test for 朱 (ake). Clojure 1:1 port of cells/test_membrane_flow.py
  (ADR-2606052100). Threads one edit through all five CELLS in sequence —
  propose → edit_triage → review_vote → promote → revision_log — exactly as the runtime Pregel
  graph would, proving they compose. Hermetic; .solve() is never called (R0 scaffolds raise)."
  (:require [clojure.test :refer [deftest is]]
            [ake.cells.propose.state-machine :as propose]
            [ake.cells.edit-triage.state-machine :as edit-triage]
            [ake.cells.review-vote.state-machine :as review-vote]
            [ake.cells.promote.state-machine :as promote]
            [ake.cells.revision-log.state-machine :as revision-log]))

(def ROUTE->MECHANISM
  {"auto-accept" "optimistic"
   "vote" "sbt-vote"
   "council-lv7" "council-lv7"})

(defn- edit*
  [eid & {:keys [kind entity attr op value author provenance rationale sourcing]
          :or {kind ":kg-fact" entity "org.corp.x" attr ":status" op ":assert"
               value ":delisted" author "did:web:etzhayyim.com:member:a"
               provenance "https://primary.example.com/notice"
               rationale "a sourced correction with an adequate explanation"
               sourcing ":authoritative"}}]
  {":edit/id" eid ":edit/target-kind" kind ":edit/target-entity" entity
   ":edit/target-attr" attr ":edit/op" op ":edit/proposed-value" value
   ":edit/author" author ":edit/author-kind" ":member"
   ":edit/provenance" provenance ":edit/rationale" rationale ":edit/sourcing" sourcing})

(defn run-flow
  "Thread one :edit/* through all five cells. Returns a trace of where it ended up."
  [edit & {:keys [yes no signer history as-of]
           :or {yes 0 no 0 signer "did:web:etzhayyim.com:member:op" history nil as-of 500}}]
  (let [history (vec (or history []))
        trace {:recorded false :route nil :outcome nil
               :promoted false :appended false :stopped-at nil :history history}
        ;; 1) propose — screen + record (flat-keyed cell input)
        flat {"cell_state" {} "edit_id" (get edit ":edit/id")
              "target_kind" (get edit ":edit/target-kind") "target_entity" (get edit ":edit/target-entity")
              "target_attr" (get edit ":edit/target-attr") "op" (get edit ":edit/op")
              "author" (get edit ":edit/author") "author_kind" (get edit ":edit/author-kind")
              "provenance" (get edit ":edit/provenance") "server_held_key" false}
        s (propose/transition-to-screened flat)]
    (if (not= (get-in s ["cell_state" "phase"]) propose/phase-screened)
      (assoc trace :stopped-at "propose")
      (let [s (propose/transition-to-recorded {"cell_state" (get s "cell_state")})
            trace (assoc trace :recorded (= (get-in s ["cell_state" "phase"]) propose/phase-recorded))
            ;; 2) edit_triage — score + route (never decides)
            t (edit-triage/triage {"cell_state" {} "edit" edit})]
        (if (not= (get-in t ["cell_state" "phase"]) edit-triage/phase-triaged)
          (assoc trace :stopped-at "triage")
          (let [route (get-in t ["cell_state" "payload" "route"])
                trace (assoc trace :route route)]
            (if (= route "refused")
              (assoc trace :stopped-at "triage:refused")
              ;; 3) review_vote — optimistic / sbt-vote / council-lv7
              (let [r (review-vote/tally {"cell_state" {} "edit_id" (get edit ":edit/id")
                                          "mechanism" (get ROUTE->MECHANISM route) "yes" yes "no" no
                                          "signed_by" signer})]
                (if (not= (get-in r ["cell_state" "phase"]) review-vote/phase-tallied)
                  (assoc trace :stopped-at "review")
                  (let [outcome (get-in r ["cell_state" "payload" "outcome"])
                        trace (assoc trace :outcome outcome)]
                    (if (not= outcome "accepted")
                      (assoc trace :stopped-at (str "review:" outcome))
                      ;; 4) promote — no-server-key membrane
                      (let [p (promote/review-promotion {"cell_state" {} "edit_id" (get edit ":edit/id")
                                                         "entity" (get edit ":edit/target-entity")
                                                         "attr" (get edit ":edit/target-attr")
                                                         "value" (get edit ":edit/proposed-value")
                                                         "outcome" outcome
                                                         "to_sourcing" (get edit ":edit/sourcing")
                                                         "provenance" (get edit ":edit/provenance")
                                                         "signed_by" signer "as_of" as-of})]
                        (if (not= (get-in p ["cell_state" "phase"]) promote/phase-cleared)
                          (assoc trace :stopped-at "promote")
                          (let [trace (assoc trace :promoted true)
                                ;; 5) revision_log — append-only
                                a (revision-log/append {"cell_state" {} "history" history "edit" edit "as_of" as-of})]
                            (if (not= (get-in a ["cell_state" "phase"]) revision-log/phase-appended)
                              (assoc trace :stopped-at "revision_log")
                              (assoc trace
                                     :appended true
                                     :history (get-in a ["cell_state" "history"])))))))))))))))))

;; ── the four routes, threaded through the real cells ────────────────────────────
(deftest test-auto-accept-flows-to-appended
  (let [e (edit* "f1" :kind ":kg-fact" :attr ":hq-address" :value "Hsinchu 300-096")
        tr (run-flow e)]
    (is (= "auto-accept" (:route tr)))
    (is (= "accepted" (:outcome tr)))
    (is (:promoted tr))
    (is (:appended tr))
    (is (= 1 (count (:history tr))))))

(deftest test-high-risk-vote-accepted-flows-to-appended
  (let [e (edit* "f2" :attr ":status" :value ":delisted")    ;; :status → high → vote
        tr (run-flow e :yes 8 :no 1)]
    (is (= "vote" (:route tr)))
    (is (= "accepted" (:outcome tr)))
    (is (:promoted tr))
    (is (:appended tr))))

(deftest test-high-risk-vote-rejected-stops-before-promote
  (let [e (edit* "f3" :attr ":status" :value ":delisted")
        tr (run-flow e :yes 1 :no 8)]
    (is (= "vote" (:route tr)))
    (is (= "rejected" (:outcome tr)))
    (is (not (:promoted tr)))
    (is (not (:appended tr)))
    (is (= "review:rejected" (:stopped-at tr)))))

(deftest test-invariant-edit-reaches-council-pending-not-promoted
  (let [e (edit* "f4" :attr ":license" :value "Apache-2.0")   ;; invariant-adjacent
        tr (run-flow e :yes 9 :no 0)]
    (is (= "council-lv7" (:route tr)))
    (is (= "pending" (:outcome tr)))
    (is (not (:promoted tr)))
    (is (= "review:pending" (:stopped-at tr)))))

(deftest test-rider-edit-refused-at-triage-never-reaches-review
  (let [e (edit* "f5" :kind ":actor-profile" :entity "kataribe" :attr ":actor/description"
                 :value "now also runs third-party advertising" :sourcing ":representative")
        tr (run-flow e)]
    (is (= "refused" (:route tr)))
    (is (= "triage:refused" (:stopped-at tr)))
    (is (nil? (:outcome tr)))
    (is (not (:promoted tr)))))

(deftest test-server-signed-promotion-is-refused-in-the-chain
  (let [e (edit* "f6" :attr ":hq-address" :value "X" :kind ":kg-fact")
        tr (run-flow e :signer "server-bot")]
    ;; review tally refuses a server signature first (no-server-key), so it never promotes
    (is (not (:promoted tr)))
    (is (contains? #{"review" "promote"} (:stopped-at tr)))))

(deftest test-chain-history-grows-across-two-accepted-edits
  (let [h (:history (run-flow (edit* "g1" :attr ":hq-address" :value "v1" :kind ":kg-fact") :history [] :as-of 10))
        h (:history (run-flow (edit* "g2" :attr ":hq-address" :value "v2" :kind ":kg-fact") :history h :as-of 20))]
    (is (= 2 (count h)))))   ;; append-only across independent membrane runs
