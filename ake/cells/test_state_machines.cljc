(ns ake.cells.test-state-machines
  "State-machine tests for 朱 (ake) cells (R0). Clojure 1:1 port of cells/test_state_machines.py
  (ADR-2606052100). .solve() is NOT called for behaviour; each cell's solve raises at R0.
  The cell.py-importing variant (test_all_cells_solve_raise via *.cell) is dropped (no cell.py);
  the equivalent solve-raises assertion is exercised per state-machine."
  (:require [clojure.test :refer [deftest is]]
            [ake.cells.propose.state-machine :as propose]
            [ake.cells.edit-triage.state-machine :as edit-triage]
            [ake.cells.review-vote.state-machine :as review-vote]
            [ake.cells.promote.state-machine :as promote]
            [ake.cells.revision-log.state-machine :as revision-log]))

(defn- good-edit []
  {":edit/id" "e1" ":edit/target-kind" ":kg-fact" ":edit/target-entity" "org.corp.tsmc"
   ":edit/target-attr" ":corp/hq-address" ":edit/op" ":assert"
   ":edit/proposed-value" "Hsinchu 300-096, Taiwan" ":edit/author" "did:web:etzhayyim.com:member:abel"
   ":edit/author-kind" ":member" ":edit/provenance" "https://tsmc.com/profile"
   ":edit/rationale" "postal code update" ":edit/server-held-key" false ":edit/sourcing" ":authoritative"})

;; ───────────────────────────── propose (G1/G3/G4) ───────────────────────────
(defn- screen [over]
  (let [base {"cell_state" {} "edit_id" "e1" "target_kind" "kg-fact"
              "target_entity" "org.corp.tsmc" "target_attr" ":corp/hq-address" "op" "assert"
              "author" "did:web:etzhayyim.com:member:abel" "author_kind" "member"
              "provenance" "https://tsmc.com/profile" "server_held_key" false}]
    (propose/transition-to-screened (merge base over))))

(deftest test-propose-screens-and-records-a-good-edit
  (let [cs (get (screen {}) "cell_state")]
    (is (= propose/phase-screened (get cs "phase")))
    (let [cs2 (get (propose/transition-to-recorded {"cell_state" cs}) "cell_state")]
      (is (= propose/phase-recorded (get cs2 "phase")))
      (is (= "member" (get-in cs2 ["payload" "authorKind"])))
      (is (= false (get-in cs2 ["payload" "serverHeldKey"]))))))

(deftest test-propose-refuses-non-member
  (let [cs (get (screen {"author_kind" "server"}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G1"))))

(deftest test-propose-refuses-server-held-key
  (let [cs (get (screen {"server_held_key" true}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "no-server-key"))))

(deftest test-propose-refuses-unsourced
  (let [cs (get (screen {"provenance" ""}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G4"))))

(deftest test-propose-refuses-impersonation-target
  (let [cs (get (screen {"target_kind" "entity-speech"}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G3"))))

(deftest test-propose-refuses-throttled-author
  ;; documented in CLAUDE.md ("G9 anti-vandalism / contributor-trajectory... Repeated
  ;; charter-violating proposals throttle") but previously never wired into propose — a
  ;; throttled DID could keep proposing unimpeded. An unbroken run of 5 recent refusals
  ;; (methods/contributor.cljc's default THROTTLE-RECENT/THROTTLE-REFUSED-RUN) throttles.
  (let [author "did:web:etzhayyim.com:member:abel"
        refused-run (mapv (fn [i] {"outcome" "refused" "as_of" i}) (range 1 6))
        traj {author refused-run}
        cs (get (screen {"contributor_trajectory" traj}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G9"))))

(deftest test-propose-allows-author-recovered-by-one-accepted-edit
  ;; throttling is recoverable by construction — a single accepted edit anywhere in the
  ;; recent window breaks the unbroken-refusal run and un-throttles the author.
  (let [author "did:web:etzhayyim.com:member:abel"
        mixed-run (conj (mapv (fn [i] {"outcome" "refused" "as_of" i}) (range 1 5))
                        {"outcome" "accepted" "as_of" 5})
        traj {author mixed-run}
        cs (get (screen {"contributor_trajectory" traj}) "cell_state")]
    (is (= propose/phase-screened (get cs "phase")))))

(deftest test-propose-cannot-record-without-screening
  (let [cs (get (propose/transition-to-recorded {"cell_state" {}}) "cell_state")]
    (is (= propose/phase-refused (get cs "phase")))))

;; ───────────────────────────── triage (G2/G6) ───────────────────────────────
(deftest test-triage-scores-and-routes
  (let [cs (get (edit-triage/triage {"cell_state" {} "edit" (good-edit)}) "cell_state")]
    (is (= edit-triage/phase-triaged (get cs "phase")))
    (is (= "auto-accept" (get-in cs ["payload" "route"])))
    (is (= "low" (get-in cs ["payload" "risk"])))))

(deftest test-triage-refuses-a-hard-gate-violation
  (let [bad (assoc (good-edit) ":edit/provenance" "")
        cs (get (edit-triage/triage {"cell_state" {} "edit" bad}) "cell_state")]
    (is (= edit-triage/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G4"))))

(deftest test-triage-routes-invariant-to-council
  (let [e (assoc (good-edit) ":edit/target-attr" ":license")
        cs (get (edit-triage/triage {"cell_state" {} "edit" e}) "cell_state")]
    (is (= "council-lv7" (get-in cs ["payload" "route"])))))

;; ───────────────────────────── review_vote ──────────────────────────────────
(deftest test-vote-accepts-majority-yes
  (let [cs (get (review-vote/tally {"cell_state" {} "edit_id" "e2" "mechanism" "sbt-vote" "yes" 8 "no" 1
                                    "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= review-vote/phase-tallied (get cs "phase")))
    (is (= "accepted" (get-in cs ["payload" "outcome"])))))

(deftest test-vote-rejects-majority-no
  (let [cs (get (review-vote/tally {"cell_state" {} "edit_id" "e2" "mechanism" "sbt-vote" "yes" 1 "no" 8
                                    "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= "rejected" (get-in cs ["payload" "outcome"])))))

(deftest test-vote-optimistic-accepts
  (let [cs (get (review-vote/tally {"cell_state" {} "edit_id" "e1" "mechanism" "optimistic"
                                    "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= "accepted" (get-in cs ["payload" "outcome"])))))

(deftest test-vote-council-is-pending
  (let [cs (get (review-vote/tally {"cell_state" {} "edit_id" "e4" "mechanism" "council-lv7"
                                    "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= "pending" (get-in cs ["payload" "outcome"])))))

(deftest test-vote-refuses-server-signature
  (let [cs (get (review-vote/tally {"cell_state" {} "edit_id" "e2" "mechanism" "sbt-vote" "yes" 9 "no" 0
                                    "signed_by" "server-bot"}) "cell_state")]
    (is (= review-vote/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "no-server-key"))))

;; ───────────────────────────── promote (G4/G8/G9) ───────────────────────────
(deftest test-promote-clears-accepted-member-signed
  (let [cs (get (promote/review-promotion {"cell_state" {} "edit_id" "e1" "entity" "org.corp.tsmc"
                                           "attr" ":corp/hq-address" "value" "Hsinchu 300-096" "outcome" "accepted"
                                           "to_sourcing" "authoritative" "provenance" "https://tsmc.com/profile"
                                           "signed_by" "did:web:etzhayyim.com:member:abel" "as_of" 2010}) "cell_state")]
    (is (= promote/phase-cleared (get cs "phase")))
    (is (= true (get-in cs ["payload" "promoted"])))
    (is (= false (get-in cs ["payload" "published"])))
    (is (= false (get-in cs ["payload" "serverHeldKey"])))))

(deftest test-promote-refuses-server-signature
  (let [cs (get (promote/review-promotion {"cell_state" {} "edit_id" "e1" "outcome" "accepted"
                                           "signed_by" "server" "to_sourcing" "representative"}) "cell_state")]
    (is (= promote/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G9"))))

(deftest test-promote-refuses-unaccepted
  (let [cs (get (promote/review-promotion {"cell_state" {} "edit_id" "e4" "outcome" "pending"
                                           "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= promote/phase-refused (get cs "phase")))))

(deftest test-promote-authoritative-needs-verifiable-provenance
  (let [cs (get (promote/review-promotion {"cell_state" {} "edit_id" "e1" "outcome" "accepted"
                                           "to_sourcing" "authoritative" "provenance" "trust me"
                                           "signed_by" "did:web:etzhayyim.com:member:op"}) "cell_state")]
    (is (= promote/phase-refused (get cs "phase")))
    (is (clojure.string/includes? (get cs "refusal") "G4"))))

;; ───────────────────────────── revision_log (G5) ────────────────────────────
(deftest test-revision-log-appends-and-grows
  (let [s1 (get (revision-log/append {"cell_state" {} "history" [] "edit" (good-edit) "as_of" 100}) "cell_state")]
    (is (= revision-log/phase-appended (get s1 "phase")))
    (is (= 1 (get-in s1 ["payload" "revisions"])))
    (let [s2 (get (revision-log/append {"cell_state" {} "history" (get s1 "history") "edit" (good-edit) "as_of" 200}) "cell_state")]
      (is (= 2 (get-in s2 ["payload" "revisions"]))))))   ;; append-only, never overwrites

;; ───────────────────────────── .solve() raises at R0 ────────────────────────
(deftest test-all-cells-solve-raise
  (doseq [f [propose/solve edit-triage/solve review-vote/solve promote/solve revision-log/solve]]
    (is (thrown? clojure.lang.ExceptionInfo (f {})))))
