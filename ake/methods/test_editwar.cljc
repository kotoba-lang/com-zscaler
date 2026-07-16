(ns ake.methods.test-editwar
  "test_editwar.cljc — 朱 (ake) edit-war / challenge→revert integration. 1:1 Clojure port
  of `methods/test_editwar.py` (clojure.test). Every Python assertion ported.

  Wires triage (the challenge routes high-risk → vote) to revision/revert (the append-only
  rollback). Proves the Wikipedia revert/rollback semantics on the immutable log: a bad edit
  is UNDONE by restoring its predecessor, yet every revision (incl. the bad one and the
  revert) stays in the history — the war is fully auditable (danjo-observable), nothing is
  deleted (G5).

  Pure over in-memory history vectors (no file edge). assertRaises(ValueError) →
  (thrown? …). The __main__ standalone runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [ake.methods.revision :as rev]
            [ake.methods.triage :as triage]))

(def ^:private ENT "org.corp.example-listed")
(def ^:private ATTR "status")

(defn- edit*
  ([eid value author] (edit* eid value author ":assert"))
  ([eid value author op]
   {":edit/id" eid ":edit/target-kind" ":kg-fact" ":edit/target-entity" ENT
    ":edit/target-attr" (str ":" ATTR) ":edit/op" op ":edit/proposed-value" value
    ":edit/author" author ":edit/author-kind" ":member"
    ":edit/provenance" "https://primary.example.com/notice"
    ":edit/rationale" "sourced correction with adequate explanation"
    ":edit/sourcing" ":authoritative"}))

(defn- build-war
  "v1 good (accepted) → v2 bad (accepted) → challenge upheld → revert to v1."
  []
  (let [h0 []
        h1 (rev/append-revision h0 (edit* "v1" ":active" "did:m:a") 100)            ;; good
        h2 (rev/append-revision h1 (edit* "v2" ":delisted-WRONG" "did:m:b") 200)    ;; bad, now current
        ;; member C challenges the current (bad) value
        challenge (edit* "c1" "" "did:m:c" ":challenge")
        tri (triage/score-edit challenge)
        ;; challenge upheld by vote (simulated) → revert
        h3 (rev/revert h2 ENT ATTR "did:m:c" "c1" 300)]
    [h3 tri]))

(deftest test-challenge-routes-high-to-vote
  (let [[_ tri] (build-war)]
    (is (= ":high" (get tri ":triage/risk")))    ;; op==challenge is high-risk
    (is (= ":vote" (get tri ":triage/route")))))  ;; edit-wars settled by a vote, never auto

(deftest test-revert-restores-predecessor-value
  (let [[h _] (build-war)
        cur (rev/current h ENT ATTR)]
    (is (= ":active" (get cur ":revision/value")))   ;; reverted to v1, NOT the bad v2
    (is (= ":retract" (get cur ":revision/op")))))    ;; the revert is itself an append (no delete)

(deftest test-war-history-is-fully-preserved
  (let [[h _] (build-war)
        hist (rev/history-of h ENT ATTR)]
    (is (= 3 (count hist)))                          ;; v1 + v2(bad) + revert — nothing deleted
    (is (= [100 200 300] (mapv #(get % ":revision/as-of") hist)))))

(deftest test-bad-value-remains-auditable-in-time-travel
  (let [[h _] (build-war)]
    ;; the bad edit is undone for the CURRENT reader, but the record that it once stood is intact
    (is (= ":delisted-WRONG" (get (rev/as-of h ENT ATTR 250) ":revision/value")))  ;; auditable (danjo)
    (is (= ":active" (get (rev/as-of h ENT ATTR 150) ":revision/value")))          ;; before the bad edit
    (is (= ":active" (get (rev/as-of h ENT ATTR 350) ":revision/value")))))        ;; after the revert

(deftest test-revert-of-sole-revision-restores-empty
  (let [h0 (rev/append-revision [] (edit* "only" ":x" "did:m:a") 10)
        h (rev/revert h0 ENT ATTR "did:m:c" "c" 20)]
    (is (= "" (get (rev/current h ENT ATTR) ":revision/value")))))   ;; undone to pre-existence

(deftest test-revert-with-no-history-raises
  (let [ex (try
             (rev/revert [] ENT ATTR "did:m:c" "c" 1)
             nil
             (catch #?(:clj Exception :cljs js/Error) e e))]
    (is (some? ex) "expected ValueError")
    (is (str/includes? (str (#?(:clj .getMessage :cljs ex-message) ex)) "nothing to revert"))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-editwar)))
