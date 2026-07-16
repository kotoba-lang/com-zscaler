(ns ipaddress.methods.test-autorun
  "test_autorun.py — ipaddress autonomous heartbeat + kotoba Datom-log invariants.
  1:1 Clojure port of `methods/test_autorun.py` (stdlib unittest-style asserts → clojure.test).

  Guards the autonomy + persistence contract:
    - the loop persists one content-addressed tx per heartbeat to an append-only log;
    - the log is a verifiable commit-DAG (every CID recomputes; tamper is detected);
    - it is deterministic / resume-safe (same cycles → same CIDs);
    - it is append-only (re-running grows the log, never rewrites);
    - derived :ipnet/* concentration datoms are flagged :ipnet/derived (G2/G10);
    - it does NO external I/O (offline ingest, local persist — G7/G8 stay gated).

  The Python __main__ runner is replaced by the clojure.test runner / -main below."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [ipaddress.methods.autorun :as autorun]
            [ipaddress.methods.kotoba :as kotoba]))

#?(:clj
   (defn- tmp-log
     "Create a fresh temp path, deleted so append-tx writes the header (start absent)."
     []
     (let [f (java.io.File/createTempFile "ipaddress" ".datoms.kotoba.edn")]
       (.delete f)
       f)))

#?(:clj (defn- rm [^java.io.File f] (when (.exists f) (.delete f))))

;; ── test_heartbeat_persists ────────────────────────────────────────────────────
(deftest test-heartbeat-persists
  #?(:clj
     (let [log (tmp-log)]
       (try
         (let [res (autorun/run-autonomous :cycles 3 :log-path log)]
           (is (= 3 (get res "cycles")) "ran 3 cycles")
           (is (= 3 (get res "log_length")) "log has one tx per heartbeat")
           (is (every? (fn [b] (> (get b "datoms") 0)) (get res "beats")) "every heartbeat persisted datoms")
           (is (get-in res ["chain" "ok"]) "commit-DAG verifies (chain OK)")
           (is (str/starts-with? (get res "head_cid") "b") "head CID is content-addressed"))
         (finally (rm log))))))

;; ── test_deterministic_resume_safe ──────────────────────────────────────────────
(deftest test-deterministic-resume-safe
  #?(:clj
     (let [a (tmp-log) b (tmp-log)]
       (try
         (let [ra (autorun/run-autonomous :cycles 3 :log-path a)
               rb (autorun/run-autonomous :cycles 3 :log-path b)
               cids-a (mapv #(get % "cid") (get ra "beats"))
               cids-b (mapv #(get % "cid") (get rb "beats"))]
           (is (= cids-a cids-b) "same cycles → same CIDs (deterministic / resume-safe)")
           (is (= (get ra "head_cid") (get rb "head_cid")) "head CID reproduces across independent runs"))
         (finally (rm a) (rm b))))))

;; ── test_append_only_growth ─────────────────────────────────────────────────────
(deftest test-append-only-growth
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 :log-path log)
         (let [first* (kotoba/read-log log)]
           (autorun/run-cycle 2 :log-path log)
           (let [second* (kotoba/read-log log)]
             (is (= (count second*) (inc (count first*))) "second heartbeat appends, does not rewrite")
             (is (= (get (nth second* 0) ":tx/cid") (get (nth first* 0) ":tx/cid"))
                 "tx 1 is unchanged after tx 2 appends")
             (is (= (get (nth second* 1) ":tx/prev") (get (nth first* 0) ":tx/cid"))
                 "tx 2 links tx 1's CID (commit-DAG)")
             (is (get (kotoba/verify-chain log) "ok") "chain still verifies after incremental appends")))
         (finally (rm log))))))

;; ── test_tamper_detected ────────────────────────────────────────────────────────
(deftest test-tamper-detected
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-autonomous :cycles 2 :log-path log)
         (let [lines (str/split-lines (slurp log))
               ;; corrupt a value inside the FIRST transaction's datoms
               lines (loop [i 0, out []]
                       (if (>= i (count lines))
                         out
                         (let [ln (nth lines i)]
                           (if (str/includes? ln ":tx/id 1 ")
                             (into (conj out (str/replace-first ln ":ipnet/derived true" ":ipnet/derived false"))
                                   (subvec (vec lines) (inc i)))
                             (recur (inc i) (conj out ln))))))]
           (spit log (str (str/join "\n" lines) "\n"))
           (let [v (kotoba/verify-chain log)]
             (is (not (get v "ok")) "tampering an earlier tx breaks chain verification")
             (is (= 0 (get v "broken_at")) "tamper localized to the corrupted tx index")))
         (finally (rm log))))))

;; ── test_derived_datoms_flagged ─────────────────────────────────────────────────
(deftest test-derived-datoms-flagged
  #?(:clj
     (let [log (tmp-log)]
       (try
         (autorun/run-cycle 1 :log-path log)
         (let [tx (nth (kotoba/read-log log) 0)
               datoms (get tx ":tx/datoms")
               derived (filter (fn [d] (= (nth d 2) ":ipnet/derived")) datoms)]
           (is (> (count derived) 0) "derived :ipnet/* concentration datoms are persisted")
           (is (every? (fn [d] (true? (nth d 3))) derived) "every :ipnet/derived datom is flagged true (G2/G10)")
           (let [ops (set (map (fn [d] (nth d 0)) datoms))]
             (is (= ops #{":db/add"}) "every datom is append-only :db/add (no :db/retract — non-eschatological)")))
         (finally (rm log))))))

;; ── test_no_external_io ─────────────────────────────────────────────────────────
(deftest test-no-external-io
  #?(:clj
     (let [src (str (slurp (io/resource "ipaddress/methods/autorun.cljc"))
                    (slurp (io/resource "ipaddress/methods/kotoba.cljc")))]
       ;; "import" forms of external I/O must not appear as live require/import targets.
       ;; Mirror the Python banned-substring check (the docstring NOTE that names them is the
       ;; only occurrence — matching how the Python source's own docstring would; here our
       ;; impl bodies contain none). We assert the require/use forms contain no network ns.
       (doseq [banned ["clj-http" "java.net.URL" "java.net.Socket" "java.net.http"
                       "org.httpkit" "ProcessBuilder" "sh/sh"]]
         (is (not (str/includes? src banned))
             (str "autorun/kotoba does no external I/O (no `" banned "`)"))))))

#?(:clj (defn -main [& _] (run-tests 'ipaddress.methods.test-autorun)))
