(ns keizu.cells.test-state-machines
  "State-machine tests for the 系図 (keizu) cells (R0). ADR-2606066000.
  1:1 port of cells/test_state_machines.py. The .solve()-raises test imported cell.py and is
  dropped (cell.py pruned in the py->clj wave); each cell's .solve() raise stays guarded by the
  RuntimeError contract in the Python ADR record."
  (:require [clojure.test :refer [deftest is]]
            [keizu.cells.ingest.state-machine :as ingest]
            [keizu.cells.committee-graph.state-machine :as committee]
            [keizu.cells.money-graph.state-machine :as money]
            [keizu.cells.relation-weave.state-machine :as weave]
            [keizu.cells.social-post.state-machine :as social]
            [clojure.string :as str]))

;; ── ingest ───────────────────────────────────────────────────────────────────────
(deftest test-ingest-clean-batch-records
  (let [st (ingest/transition-to-screened
            {"cell_state" {}
             "nodes" [{"scope" ":public-role"}]
             "rels"  [{"kind" ":funding-tie" "sources" ["a" "b"]}]
             "money" [{"kind" ":subsidy" "sources" ["a" "b"]}]})]
    (is (= "screened" (get-in st ["cell_state" "phase"])))
    (let [st2 (ingest/transition-to-recorded st)]
      (is (= "recorded" (get-in st2 ["cell_state" "phase"])))
      (is (= 3 (get-in st2 ["cell_state" "recorded"]))))))

(deftest test-ingest-refuses-private-node
  (let [st (ingest/transition-to-screened
            {"cell_state" {} "nodes" [{"scope" ":private-person"}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G1"))))

(deftest test-ingest-refuses-verdict-rel
  (let [st (ingest/transition-to-screened
            {"cell_state" {} "rels" [{"kind" ":bribe" "sources" ["a" "b"]}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G2"))))

(deftest test-ingest-refuses-under-sourced
  (let [st (ingest/transition-to-screened
            {"cell_state" {} "rels" [{"kind" ":funding-tie" "sources" ["a"]}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G3"))))

;; ── committee_graph ────────────────────────────────────────────────────────────────
(deftest test-committee-co-membership
  (let [st (committee/transition-to-composed
            {"cell_state" {}
             "committees" [{"id" "c1" "members" ["s1" "s2"]}
                           {"id" "c2" "members" ["s2" "s3"]}]})]
    (is (= "composed" (get-in st ["cell_state" "phase"])))
    (is (= #{"s2"} (set (map #(get % "seat") (get-in st ["cell_state" "co_membership"])))))))

;; ── money_graph ────────────────────────────────────────────────────────────────────
(deftest test-money-aggregates-hhi
  (let [st (money/transition-to-aggregated
            {"cell_state" {}
             "money" [{"payee" "x" "amount" 75} {"payee" "y" "amount" 25}]})]
    (is (= "aggregated" (get-in st ["cell_state" "phase"])))
    (is (< (Math/abs (- (get-in st ["cell_state" "hhi"]) (+ (* 0.75 0.75) (* 0.25 0.25)))) 1e-6))
    (is (= "x" (get-in st ["cell_state" "shares" 0 0])))))

;; ── relation_weave ─────────────────────────────────────────────────────────────────
(deftest test-weave-cross-organ
  (let [st (weave/transition-to-woven
            {"cell_state" {}
             "nodes" {"s1" {"organ" "A"} "s2" {"organ" "B"}}
             "committees" [{"id" "c1" "members" ["s1" "s2"]}]})]
    (is (= "woven" (get-in st ["cell_state" "phase"])))
    (is (= 2 (get-in st ["cell_state" "findings" 0 "distinct_organs"])))))

;; ── social_post ────────────────────────────────────────────────────────────────────
(deftest test-social-drafts-dry-run
  (let [st (social/transition-to-drafted
            {"cell_state" {} "subject" "demo委員会" "sources" ["a" "b"]})]
    (is (= "drafted" (get-in st ["cell_state" "phase"])))
    (let [p (get-in st ["cell_state" "payload"])]
      (is (= ":dry-run" (get p ":post/status")))
      (is (= false (get p ":post/server-held-key"))))))

(deftest test-social-refuses-published
  (let [st (social/transition-to-drafted
            {"cell_state" {} "subject" "x" "sources" ["a" "b"] "requested_status" "published"})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G8"))))

(deftest test-social-refuses-server-key
  (let [st (social/transition-to-drafted
            {"cell_state" {} "subject" "x" "sources" ["a" "b"] "server_held_key" true})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "no-server-key"))))

(deftest test-social-refuses-under-sourced
  (let [st (social/transition-to-drafted
            {"cell_state" {} "subject" "x" "sources" ["a"]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G3"))))
