(ns ake.methods.test-revision
  "test_revision.cljc — 朱 (ake) append-only history, time-travel reads, non-destructive
  promotion (G5). 1:1 Clojure port of `methods/test_revision.py` (clojure.test). Every
  Python assertion ported. The seed is loaded via `ake.methods._edn` (the #?(:clj) file
  edge); `_e` mirrors the Python helper that indexes the seed batch by `:edit/id`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [ake.methods.revision :as rev]
            #?(:clj [ake.methods._edn :as edn])))

#?(:clj
   (def ^:private seed-path
     "20-actors/ake/data/seed-edit-graph.kotoba.edn"))

;; _e(eid) = {e[":edit/id"]: e for e in load_edn(_SEED)[":edit/batch"]}[eid]
#?(:clj
   (defn- e [eid]
     (get (into {} (map (fn [ed] [(get ed ":edit/id") ed])
                        (get (edn/reconstitute (edn/load-edn seed-path) "data.seed-edit-graph") ":edit/batch")))
          eid)))

(deftest test-append-returns-new-list-and-never-shrinks
  (let [h0 []
        h1 (rev/append-revision h0 (e "e1") 100)]
    (is (and (= 0 (count h0)) (= 1 (count h1))))   ;; input untouched
    (let [h2 (rev/append-revision h1 (e "e3") 110)]
      (is (and (= 2 (count h2)) (= 1 (count h1)))))))

(deftest test-current-is-latest-as-of
  (let [h (-> []
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "old") 100)
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "new") 200))
        cur (rev/current h "org.corp.tsmc" "hq-address")]
    (is (= "new" (get cur ":revision/value")))))

(deftest test-as-of-time-travel
  (let [h (-> []
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "old") 100)
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "new") 200))]
    (is (= "old" (get (rev/as-of h "org.corp.tsmc" "hq-address" 150) ":revision/value")))
    (is (= "new" (get (rev/as-of h "org.corp.tsmc" "hq-address" 250) ":revision/value")))
    (is (nil? (rev/as-of h "org.corp.tsmc" "hq-address" 50)))))

(deftest test-history-of-is-ordered-and-full
  (let [h (-> []
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "v1") 300)
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "v2") 100)
              (rev/append-revision (assoc (e "e1") ":edit/proposed-value" "v3") 200))
        hist (rev/history-of h "org.corp.tsmc" "hq-address")]
    (is (= [100 200 300] (mapv #(get % ":revision/as-of") hist)))))  ;; sorted, nothing dropped

(deftest test-promote-sourcing-is-non-destructive
  (let [h (-> []
              (rev/append-revision (assoc (e "e1")
                                          ":edit/sourcing" ":representative"
                                          ":edit/proposed-value" "addr") 100))
        before (count h)
        h2 (rev/promote-sourcing h "org.corp.tsmc" "hq-address"
                                 "https://tsmc.com/profile" 200 "did:member:x" "ePromote")]
    (is (= (+ before 1) (count h2)))                ;; appended, not replaced
    (is (= ":authoritative"
           (get (rev/current h2 "org.corp.tsmc" "hq-address") ":revision/sourcing")))
    ;; the representative revision still exists at its own as-of
    (is (= ":representative"
           (get (rev/as-of h2 "org.corp.tsmc" "hq-address" 150) ":revision/sourcing")))))

(deftest test-promote-requires-verifiable-provenance
  (let [h (rev/append-revision [] (e "e1") 100)
        ex (try
             (rev/promote-sourcing h "org.corp.tsmc" "hq-address" "trust me"
                                   200 "did:member:x" "eP")
             nil
             (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) ex ex))]
    (is (some? ex) "expected an exception")
    (is (clojure.string/includes? (str (#?(:clj .getMessage :cljs ex-message) ex)) "G4"))))

(deftest test-promote-with-nothing-to-promote-raises
  (let [ex (try
             (rev/promote-sourcing [] "org.corp.none" "x" "https://e.com"
                                   1 "did:member:x" "eP")
             nil
             (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) ex ex))]
    (is (some? ex) "expected an exception")
    (is (clojure.string/includes? (str (#?(:clj .getMessage :cljs ex-message) ex))
                                  "nothing to promote"))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-revision)))
