(ns danjo.methods.test-analyze
  "danjo 弾正 — analyzer tests (ADR-2605301600). 1:1 Clojure port of methods/test_analyze.py.

  Covers the single-bidder-streak detector AND the load-bearing charter invariants: every
  observation is NON-adjudicating (G4 — no verdict field representable), cites ≥2 source
  records (G5), references its open method (G6), and carries the method's knownFalsePositive
  modes (G4 honesty). danjo is the censor's EYE, never the SWORD.

  NOTE on scope: test_autorun.py is autorun-dependent (imports the unported `autorun` +
  `kotoba` modules) and is deferred — its deps are not satisfied by this analyzer port."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [danjo.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def corpus-path (io/file actor-dir "data" "corpus.seed.json"))
(def methods-path (io/file actor-dir "methods" "v1-jp-seed.json"))

(defn- setup [] [(analyze/load-json corpus-path) (analyze/load-json methods-path)])

(defn- streak-method [methods]
  (first (filter #(= (get % "methodId") "single-bidder-streak") (get methods "methods"))))

(deftest test-detector-fires-on-the-streak-only
  (let [[corpus methods] (setup)
        params (analyze/parse-json (get (streak-method methods) "thresholdParams"))
        hits (analyze/detect-single-bidder-streak (get corpus "procurementRecords") params)]
    ;; ACME has 6 consecutive single-bid (≥5) → 1 hit; BETA (2) and GAMMA (multi-bid) do not
    (is (= 1 (count hits)))
    (is (= "lei:5493ACME000000000001" (get (first hits) "awardee")))
    (is (= 6 (get (first hits) "count")))))

(deftest test-every-observation-is-non-adjudicating
  (let [[corpus methods] (setup)
        obs (analyze/run-all corpus methods)]
    (is (seq obs))
    (is (every? #(= true (get % "nonAdjudicatingNotice")) obs))))

(deftest test-every-observation-cites-two-or-more-sources
  (let [[corpus methods] (setup)]
    (doseq [o (analyze/run-all corpus methods)]
      (is (>= (count (get o "sourceRecordCids")) 2)))))     ;; G5

(deftest test-every-observation-references-its-open-method
  (let [[corpus methods] (setup)]
    (doseq [o (analyze/run-all corpus methods)]
      (is (and (get o "methodNoteCid")
               (str/starts-with? (get o "methodNoteCid") "method:"))))))  ;; G6

(deftest test-known-false-positive-modes-carried
  (let [[corpus methods] (setup)]
    (doseq [o (analyze/run-all corpus methods)]
      (is (seq (get o "knownFalsePositiveModes"))))))       ;; G4 honesty — why a hit ≠ a crime

(deftest test-no-verdict-field-representable
  (let [[corpus methods] (setup)]
    (doseq [o (analyze/run-all corpus methods)]
      (doseq [k (keys o)]
        (is (not (some #(str/includes? (str/lower-case k) %)
                       analyze/forbidden-verdict-fields)))))))

(deftest test-build-observation-refuses-single-source
  (let [[_ methods] (setup)
        m (streak-method methods)
        raised (try
                 (analyze/build-observation
                  {"authority" "a" "awardee" "b" "cids" ["only-one"] "count" 1} m)
                 false
                 (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                   (str/includes? #?(:clj (.getMessage e) :cljs (.-message e)) "G5")))]
    (is raised "an observation with <2 sources must be refused (G5)")))

(deftest test-method-cid-is-deterministic
  (let [[_ methods] (setup)
        m (streak-method methods)]
    (is (= (analyze/method-cid m) (analyze/method-cid m)))))

(deftest test-render-edn-marks-invariants
  (let [[corpus methods] (setup)
        edn (analyze/render-edn (analyze/run-all corpus methods))]
    (is (str/includes? edn ":danjo.obs/non-adjudicating true"))
    (is (and (str/includes? edn "censor's EYE") (str/includes? edn "gated")))))

;; ── parity assertions (not in the Python suite; pin byte-/hash-identity) ──────

(deftest test-method-cid-matches-python
  (let [[_ methods] (setup)]
    ;; the exact CID emitted by python3 analyze.py on the committed seed
    (is (= "method:single-bidder-streak:955ade7944f2"
           (analyze/method-cid (streak-method methods))))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'danjo.methods.test-analyze))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
