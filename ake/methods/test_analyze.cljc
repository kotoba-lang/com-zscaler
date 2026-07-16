(ns ake.methods.test-analyze
  "test_analyze.cljc — 朱 (ake) end-to-end membrane (propose → triage → route → revision).
  1:1 Clojure port of `methods/test_analyze.py` (clojure.test). Every Python assertion ported.
  `run` is pure over a parsed seed; the file edge (`load-edn`) is #?(:clj). The report is
  exercised via `report` (byte-identical to analyze.py's _report)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [ake.methods.analyze :as a]
            [ake.methods.contributor :as contrib]
            [ake.methods.revision :as rev]
            #?(:clj [ake.methods._edn :as edn])))

#?(:clj
   (def ^:private seed-path
     "20-actors/ake/data/seed-edit-graph.kotoba.edn"))

#?(:clj
   (defn- run* [] (a/run (edn/reconstitute (edn/load-edn seed-path) "data.seed-edit-graph"))))

#?(:clj
   (defn- by-id [res]
     (into {} (map (fn [r] [(get r "edit") r]) (get res "rows")))))

#?(:clj
   (deftest test-run-routes-every-seed-edit
     (let [m (by-id (run*))]
       (is (= #{"e1" "e2" "e3" "e4" "e5"} (set (keys m)))))))

#?(:clj
   (deftest test-optimistic-and-voted-edits-are-accepted
     (let [m (by-id (run*))]
       (is (and (= ":auto-accept" (get-in m ["e1" "route"])) (get-in m ["e1" "accepted"])))
       (is (and (= ":vote" (get-in m ["e2" "route"])) (get-in m ["e2" "accepted"])))   ;; 8-1
       (is (and (= ":vote" (get-in m ["e3" "route"])) (get-in m ["e3" "accepted"]))))))  ;; 5-0

#?(:clj
   (deftest test-invariant-and-rider-edits-are-not-accepted
     (let [m (by-id (run*))]
       (is (and (= ":council-lv7" (get-in m ["e4" "route"])) (not (get-in m ["e4" "accepted"]))))
       (is (and (= ":refused" (get-in m ["e5" "route"])) (not (get-in m ["e5" "accepted"])))))))

#?(:clj
   (deftest test-accepted-edits-landed-in-revision-history
     (let [res (run*)
           h (get res "history")]
       ;; e1 (tsmc hq-address) and e2 (example-listed status) accepted → present as current
       (is (some? (rev/current h "org.corp.tsmc" "hq-address")))
       (is (some? (rev/current h "org.corp.example-listed" "status")))
       ;; e4 (license, council-pending) and e5 (refused) did NOT land
       (is (nil? (rev/current h "org.corp.example-listed" "license"))))))

#?(:clj
   (deftest test-contributor-trajectory-recorded
     (let [res (run*)
           traj (get res "trajectory")]
       ;; the rider-violating author (esau) is recorded as refused, not accepted
       (let [esau "did:web:etzhayyim.com:member:esau"
             c (contrib/counts traj esau)]
         (is (and (= 0 (get c "accepted")) (>= (get c "refused") 1))))
       ;; the council-pending author (dan) has no decided event yet (pending ≠ refused)
       (let [dan "did:web:etzhayyim.com:member:dan"]
         (is (= {"accepted" 0 "refused" 0} (contrib/counts traj dan)))))))

#?(:clj
   (deftest test-report-renders
     (let [md (a/report (run*))]
       (is (str/includes? md "community-edit membrane dry-run"))
       (is (str/includes? md "Revision history")))))

;; ── edge cases the :representative seed can't express (mirror of test_analyze.py) ──
;; `run` is pure over PARSED data, so the synthetic batch is built inline (no file edge) and
;; these run on every platform: a hard-gate refusal at intake (unsourced → G4) plus two
;; accepted edits on the SAME (entity, attr).
(def ^:private edge-seed
  {":edit/batch"
   [{":edit/id" "g1" ":edit/target-kind" ":kg-fact" ":edit/target-entity" "org.corp.x"
     ":edit/target-attr" ":corp/hq-address" ":edit/op" ":assert" ":edit/proposed-value" "addr v1"
     ":edit/author" "did:web:etzhayyim.com:member:abel" ":edit/author-kind" ":member"
     ":edit/provenance" "https://example.com/x1" ":edit/rationale" "a clear sourced reason"
     ":edit/server-held-key" false ":edit/info-as-of" 1000 ":edit/sourcing" ":authoritative"}
    {":edit/id" "g2" ":edit/target-kind" ":kg-fact" ":edit/target-entity" "org.corp.x"
     ":edit/target-attr" ":corp/hq-address" ":edit/op" ":assert" ":edit/proposed-value" "addr v2"
     ":edit/author" "did:web:etzhayyim.com:member:abel" ":edit/author-kind" ":member"
     ":edit/provenance" "https://example.com/x2" ":edit/rationale" "a later sourced correction"
     ":edit/server-held-key" false ":edit/info-as-of" 1020 ":edit/sourcing" ":authoritative"}
    {":edit/id" "bad" ":edit/target-kind" ":kg-fact" ":edit/target-entity" "org.corp.x"
     ":edit/target-attr" ":corp/note" ":edit/op" ":assert" ":edit/proposed-value" "unsourced claim"
     ":edit/author" "did:web:etzhayyim.com:member:korah" ":edit/author-kind" ":member"
     ":edit/provenance" "" ":edit/rationale" "no source provided"
     ":edit/server-held-key" false ":edit/info-as-of" 1010 ":edit/sourcing" ":representative"}]})

(defn- edge-by-id [res]
  (into {} (map (fn [r] [(get r "edit") r]) (get res "rows"))))

(deftest test-intake-refused-edit-is-recorded-not-accepted-and-does-not-crash-the-run
  (let [m (edge-by-id (a/run edge-seed))]
    (is (= ":refused-at-intake" (get-in m ["bad" "route"])))
    (is (false? (get-in m ["bad" "accepted"])))
    (is (seq (get-in m ["bad" "note"])))
    (is (and (get-in m ["g1" "accepted"]) (get-in m ["g2" "accepted"])))))

(deftest test-intake-refused-author-trajectory
  (let [traj (get (a/run edge-seed) "trajectory")]
    (is (= {"accepted" 0 "refused" 1}
           (contrib/counts traj "did:web:etzhayyim.com:member:korah")))))

(deftest test-report-dedups-repeated-entity-attr-key
  (let [res (a/run edge-seed)
        h (get res "history")]
    (is (= 2 (count (rev/history-of h "org.corp.x" "hq-address"))))
    (is (= "addr v2" (get (rev/current h "org.corp.x" "hq-address") ":revision/value")))
    (let [md (a/report res)]
      (is (= 1 (count (re-seq #"`org.corp.x` `hq-address`" md))))
      (is (str/includes? md "2 revision(s)")))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-analyze)))
