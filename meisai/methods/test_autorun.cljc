(ns meisai.methods.test-autorun
  "test_autorun.cljc — meisai autonomous intake heartbeat + kotoba Datom-log invariants
  (clojure.test). 1:1 Clojure port of methods/test_autorun.py + the G2 guard assertions from
  test_ingest.py (ADR-2606122400).

  Guards the autonomy + persistence + G2 contract for the Clojure port:
    - one content-addressed tx per NEW intake, appended to a verifiable commit-DAG;
    - dedup by intake content CID (a second cycle appends NOTHING, resume-safe); tamper detected;
    - G2: a credential-shaped key or PAN-shaped value RAISES (unrepresentable);
    - byte-parity: the 2-intake head CID equals the Python kotoba.py value."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [meisai.methods.autorun :as autorun]
            [meisai.methods.ingest :as ingest]
            [meisai.methods.kotoba :as k]))

(def edn-a
  (str "{:source :sumitclub :statement/month \"2026-05\" :statement/total-jpy 46540\n"
       " :statement/rows [{:date \"2026-05-02\" :merchant \"AMAZON.CO.JP\" :amount_jpy 3980}\n"
       "                  {:date \"2026-05-15\" :merchant \"JR東日本\" :amount_jpy 42560}]}\n"))
(def edn-b
  (str "{:source :sumitclub :statement/month \"2026-04\" :statement/total-jpy 1200\n"
       " :statement/rows [{:date \"2026-04-03\" :merchant \"SUICA\" :amount_jpy 1200}]}\n"))

(defn- tmpdir []
  (let [d (java.io.File/createTempFile "meisai-test" "")]
    (.delete d) (.mkdirs d) d))

(deftest heartbeat-dedup-and-tamper
  (let [td (tmpdir)
        intake (doto (io/file td "intake") .mkdirs)
        log (str (io/file td "meisai.datoms.kotoba.edn"))]
    (spit (io/file intake "2026-05.edn") edn-a)
    (spit (io/file intake "2026-04.edn") edn-b)
    (let [r1 (autorun/run-cycle 1 (str intake) log)]
      (is (= 2 (count (:appended r1))) "first cycle ingests both intakes")
      (is (:ok (k/verify-chain log)) "chain verifies")
      (is (= (get (second (k/read-log log)) ":tx/prev")
             (get (first (k/read-log log)) ":tx/cid")) "txs link (commit-DAG)"))
    (let [r2 (autorun/run-cycle 2 (str intake) log)]
      (is (and (empty? (:appended r2)) (= 2 (:skipped r2))) "second cycle appends nothing (dedup)")
      (is (= 2 (count (k/read-log log))) "log length still 2"))
    ;; new intake → exactly one new tx
    (spit (io/file intake "2026-06.edn") (clojure.string/replace edn-a "2026-05" "2026-06"))
    (is (= 1 (count (:appended (autorun/run-cycle 3 (str intake) log)))) "new intake → one new tx")
    (let [head-before (k/head-cid log)]
      (autorun/run-cycle 4 (str intake) log)
      (is (= head-before (k/head-cid log)) "resume-safe: idle cycle leaves head unchanged"))
    ;; tamper-detect
    (spit log (clojure.string/replace (slurp log) "42560" "1"))
    (is (false? (:ok (k/verify-chain log))) "tamper is detected")))

(deftest g2-credential-and-pan-unrepresentable
  (is (thrown? clojure.lang.ExceptionInfo
               (ingest/statement-datoms
                (k/parse-edn "{:source :x :password \"hunter2\" :statement/month \"m\" :statement/rows []}") "bcid"))
      "credential-shaped key RAISES (G2)")
  (is (thrown? clojure.lang.ExceptionInfo
               (ingest/statement-datoms
                (k/parse-edn (str "{:source :x :statement/month \"m\" :statement/rows "
                                  "[{:date \"d\" :merchant \"4111 1111 1111 1111\" :amount_jpy 1}]}")) "bcid"))
      "PAN-shaped value RAISES (G2)")
  (is (= 9 (count (ingest/statement-datoms
                   (k/parse-edn "{:source :x :statement/month \"m\" :statement/rows [{:date \"d\" :merchant \"SHOP\" :amount_jpy 1}]}")
                   "bcid")))
      "a clean intake yields datoms"))

(deftest cid-byte-parity-with-python
  ;; Two-intake head CID must equal the Python kotoba.py value (byte-identical commit-DAG).
  (let [td (tmpdir)
        intake (doto (io/file td "intake") .mkdirs)
        log (str (io/file td "meisai.datoms.kotoba.edn"))]
    (spit (io/file intake "2026-05.edn") edn-a)
    (spit (io/file intake "2026-04.edn") edn-b)
    (autorun/run-cycle 1 (str intake) log)
    (autorun/run-cycle 2 (str intake) log)
    (is (= "b0f03ac8fd4ddac1f0715278c13d847498f80e09b6102d4164f7a3a834251b62c"
           (k/head-cid log))
        "head CID is byte-identical to the Python meisai heartbeat")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [r (run-tests 'meisai.methods.test-autorun)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
