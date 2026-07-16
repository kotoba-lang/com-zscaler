(ns keizu.methods.test-autorun
  "test_autorun.py — keizu autonomous power-relations heartbeat + kotoba Datom-log invariants.
  ADR-2606066000. 1:1 Clojure port (stdlib harness → clojure.test).

  Guards the autonomy + persistence + accountability-not-target-list contract:
   - one content-addressed tx per heartbeat to an append-only log;
   - the log is a verifiable commit-DAG (every CID recomputes; tamper is detected);
   - determinism / resume-safe (canonically ordered datoms → reproducible CID);
   - append-only; derived :keizu.conc/* signals flagged;
   - G4 edge-primary / non-adjudicating (revolving-door + award-and-fund carry
     :keizu.conc/non-adjudicating true);
   - G1 no-doxxing (NO PII node attr in the log);
   - NO external I/O.

  Temp logs + file I/O live behind #?(:clj …); SEED/LOG defaults supplied by autorun."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [keizu.methods.autorun :as autorun]
            [keizu.methods.kotoba :as kotoba]
            [keizu.methods.weave :as w]))

#?(:clj
   (defn- tmp-log
     "A non-existent temp path ending .datoms.kotoba.edn (mkstemp + unlink in Python)."
     []
     (let [f (java.io.File/createTempFile "keizu-autorun" ".datoms.kotoba.edn")]
       (.delete f)
       f)))

#?(:clj
   (defn- canon-json
     "json.dumps(d, ensure_ascii=False, sort_keys=True) of one datom — the canonical key."
     [d]
     (#'kotoba/canon d)))

(deftest test-heartbeat-persists
  #?(:clj
     (let [log (tmp-log)]
       (try
         (let [res (autorun/run-autonomous 3 autorun/SEED log)]
           (is (= 3 (get res "log_length")) "one tx per heartbeat")
           (is (every? #(> (get % "datoms") 0) (get res "beats")) "every heartbeat persisted datoms")
           (is (get-in res ["chain" "ok"]) "commit-DAG verifies (chain OK)")
           (is (str/starts-with? (get res "head_cid") "b") "head CID is content-addressed"))
         (finally (.delete log))))
     :cljs (is true)))

(deftest test-canonical-order-deterministic
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 autorun/SEED log)
         (let [datoms (get (first (kotoba/read-log log)) ":tx/datoms")
               keyed (mapv canon-json datoms)]
           (is (= keyed (vec (sort keyed)))
               "persisted datoms are in canonical sorted order (cross-process deterministic)"))
         (finally (.delete log))))
     :cljs (is true)))

(deftest test-deterministic-resume-safe
  #?(:clj
     (let [a (tmp-log) b (tmp-log)]
       (try
         (let [ra (autorun/run-autonomous 3 autorun/SEED a)
               rb (autorun/run-autonomous 3 autorun/SEED b)]
           (is (= (mapv #(get % "cid") (get ra "beats"))
                  (mapv #(get % "cid") (get rb "beats")))
               "same cycles → same CIDs (deterministic / resume-safe)"))
         (finally (.delete a) (.delete b))))
     :cljs (is true)))

(deftest test-append-only-and-tamper
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 autorun/SEED log)
         (let [first-log (kotoba/read-log log)]
           (autorun/run-cycle 2 autorun/SEED log)
           (let [second-log (kotoba/read-log log)]
             (is (= (count second-log) (inc (count first-log)))
                 "second heartbeat appends, does not rewrite")
             (is (= (get (nth second-log 1) ":tx/prev") (get (nth first-log 0) ":tx/cid"))
                 "tx 2 links tx 1's CID (commit-DAG)"))
           ;; tamper an earlier tx's :keizu.conc/derived true → false
           (let [lines (vec (str/split-lines (slurp log)))
                 idx (first (keep-indexed (fn [i ln] (when (str/includes? ln ":tx/id 1 ") i)) lines))
                 lines' (assoc lines idx
                               (str/replace-first (nth lines idx)
                                                  ":keizu.conc/derived true"
                                                  ":keizu.conc/derived false"))]
             (spit log (str (str/join "\n" lines') "\n"))
             (let [v (kotoba/verify-chain log)]
               (is (and (not (get v "ok")) (= 0 (get v "broken_at")))
                   "tampering an earlier tx breaks the chain"))))
         (finally (.delete log))))
     :cljs (is true)))

(deftest test-g4-non-adjudicating-co-occurrence
  ;; revolving-door + award-and-fund are co-occurrences of disclosed flows, NEVER allegations.
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 autorun/SEED log)
         (let [datoms (get (first (kotoba/read-log log)) ":tx/datoms")
               flagged-ents (set (keep (fn [d] (when (and (= (nth d 2) ":keizu.conc/non-adjudicating")
                                                          (true? (nth d 3)))
                                                 (nth d 1)))
                                       datoms))
               award-ents (set (keep (fn [d] (when (= (nth d 2) ":keizu.conc/award-and-fund-node") (nth d 1))) datoms))
               revolving-ents (set (keep (fn [d] (when (= (nth d 2) ":keizu.conc/revolving-from") (nth d 1))) datoms))]
           (doseq [e (into award-ents revolving-ents)]
             (is (contains? flagged-ents e)
                 (str e " carries :keizu.conc/non-adjudicating true (G4)")))
           ;; no verdict/allegation attr anywhere
           (let [attrs (set (map (fn [d] (str/lower-case (str (nth d 2)))) datoms))]
             (doseq [tok ["verdict" "guilt" "corrupt" "bribe" "illegal" "wrongdoing" "allegation"]]
               (is (not (some #(str/includes? % tok) attrs))
                   (str "no verdict token `" tok "` in any attr (G4)")))))
         (finally (.delete log))))
     :cljs (is true)))

(deftest test-g1-no-doxxing
  ;; G1: NO PII node attr may reach the log — keizu maps power entities, never private persons.
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 autorun/SEED log)
         (let [datoms (get (first (kotoba/read-log log)) ":tx/datoms")
               attrs (set (map (fn [d] (str/lower-case (str (nth d 2)))) datoms))]
           (doseq [pii w/PII-FORBIDDEN-NODE-ATTRS]
             (is (not (some #(str/includes? (last (str/split % #"/")) pii) attrs))
                 (str "no PII attr containing `" pii "` in the log (G1 no-doxxing)")))
           (let [ops (set (map first datoms))]
             (is (= #{":db/add"} ops) "every datom is append-only :db/add (no :db/retract)")))
         (finally (.delete log))))
     :cljs (is true)))

(deftest test-no-external-io
  #?(:clj
     (let [;; *file* may be a bare basename under the bb classpath; resolve the methods dir from
           ;; the running impl namespace's source instead (robust regardless of cwd / classpath root).
           impl-file (java.io.File. ^String (:file (meta #'autorun/run-cycle)))
           here (.getParentFile (.getAbsoluteFile impl-file))
           src (str (slurp (java.io.File. here "autorun.cljc"))
                    (slurp (java.io.File. here "kotoba.cljc")))]
       (doseq [banned ["urllib" "http.client" "socket" "requests" "subprocess"
                       "java.net.URL" "clojure.java.shell" "ProcessBuilder"]]
         (is (not (str/includes? src banned))
             (str "autorun/kotoba does no external I/O (no `" banned "`)"))))
     :cljs (is true)))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-autorun)))
