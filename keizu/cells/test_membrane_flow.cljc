(ns keizu.cells.test-membrane-flow
  "Cell-chain integration for the 系図 (keizu) cells. ADR-2606066000.
  1:1 port of cells/test_membrane_flow.py.

  Unit tests prove each cell in isolation (test-state-machines); THIS proves they COMPOSE into
  the documented pipeline:

      ingest ─▶ committee_graph ─▶ relation_weave ─▶ social_post (dry-run)
               money_graph ──────┘

  One public-source batch is threaded through all five cell state machines in sequence; the finding
  that falls out of relation_weave becomes social_post's subject. .solve() is never called (R0)."
  (:require [clojure.test :refer [deftest is]]
            [keizu.cells.ingest.state-machine :as ingest]
            [keizu.cells.committee-graph.state-machine :as committee]
            [keizu.cells.money-graph.state-machine :as money]
            [keizu.cells.relation-weave.state-machine :as weave]
            [keizu.cells.social-post.state-machine :as social]
            [clojure.string :as str]))

;; one self-contained batch (public seats/organs, >=2-sourced factual ties + flows)
(def nodes [{"id" "s1" "scope" ":public-role" "organ" "MOF"}
            {"id" "s2" "scope" ":public-role" "organ" "Cabinet"}])
(def committees [{"id" "c1" "organ" "MOF" "members" ["s1" "s2"]}
                 {"id" "c2" "organ" "Cabinet" "members" ["s2"]}])
(def rels [{"kind" ":committee-membership" "sources" ["a" "b"]}])
(def moneys [{"payee" "vendor" "amount" 90.0 "kind" ":procurement-award" "sources" ["a" "b"]}
             {"payee" "other" "amount" 10.0 "kind" ":subsidy" "sources" ["a" "b"]}])

(deftest test-full-membrane-chain-reaches-dry-run-post
  ;; 1) ingest — screen + record the batch
  (let [st (ingest/transition-to-screened {"cell_state" {} "nodes" nodes "rels" rels "money" moneys})]
    (is (= "screened" (get-in st ["cell_state" "phase"])))
    (let [st (ingest/transition-to-recorded st)]
      (is (= "recorded" (get-in st ["cell_state" "phase"])))
      (is (> (get-in st ["cell_state" "recorded"]) 0))))

  ;; 2) committee_graph — compose composition + co-membership
  (let [cs (committee/transition-to-composed {"cell_state" {} "committees" committees})]
    (is (= "composed" (get-in cs ["cell_state" "phase"])))
    (is (some #(= "s2" (get % "seat")) (get-in cs ["cell_state" "co_membership"]))))

  ;; 3) money_graph — aggregate per-payee HHI
  (let [ms (money/transition-to-aggregated {"cell_state" {} "money" moneys})]
    (is (= "aggregated" (get-in ms ["cell_state" "phase"])))
    (is (= "vendor" (get-in ms ["cell_state" "shares" 0 0]))))

  ;; 4) relation_weave — derive a cross-organ finding from the composition
  (let [ws (weave/transition-to-woven
            {"cell_state" {}
             "nodes" (into {} (map (fn [n] [(get n "id") n]) nodes))
             "committees" committees})]
    (is (= "woven" (get-in ws ["cell_state" "phase"])))
    (let [finding (get-in ws ["cell_state" "findings" 0])]
      (is (>= (get finding "distinct_organs") 1))

      ;; 5) social_post — the finding becomes a DRY-RUN post subject (the wire holds)
      (let [ps (social/transition-to-drafted
                {"cell_state" {}
                 "subject" (str "committee " (get finding "committee") " cross-organ")
                 "sources" ["a" "b"]})]
        (is (= "drafted" (get-in ps ["cell_state" "phase"])))
        (let [payload (get-in ps ["cell_state" "payload"])]
          (is (= ":dry-run" (get payload ":post/status")))
          (is (= false (get payload ":post/server-held-key")))
          (is (str/includes? (get payload ":post/subject") (get finding "committee"))))))))

(deftest test-chain-aborts-when-ingest-refuses
  ;; a private-person node at the head refuses; the chain must not proceed to a post
  (let [st (ingest/transition-to-screened {"cell_state" {} "nodes" [{"scope" ":private-person"}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (let [st2 (ingest/transition-to-recorded st)]   ; cannot record an unscreened batch
      (is (= "refused" (get-in st2 ["cell_state" "phase"]))))))

(deftest test-chain-refuses-published-at-tail
  ;; even with a clean head, a 'published' request at the tail is refused (G8)
  (let [ps (social/transition-to-drafted
            {"cell_state" {} "subject" "x" "sources" ["a" "b"] "requested_status" "published"})]
    (is (= "refused" (get-in ps ["cell_state" "phase"])))
    (is (str/includes? (get-in ps ["cell_state" "refusal"]) "G8"))))
