(ns tate.tests.test-coverage
  "tate 盾 — jurisdiction-coverage honesty tests (G10, ADR-2606112400). 1:1 port of
  test_coverage.py. clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [tate.methods.terms-scan :as terms]
            [tate.methods.respond-plan :as respond]
            [tate.methods.coverage-report :as cov-ns]
            [tate.methods.edn :as edn]))

(def core #{":jp" ":us" ":eu" ":uk" ":de"})

(defn- to-double [v] (if (number? v) (double v) (Double/parseDouble (str v))))

(deftest test-jurisdiction-registry-complete
  (let [juris (respond/load-jurisdictions)]
    (is (clojure.set/subset? core (set (keys juris))))
    (doseq [j (vals juris)]
      (is (get j ":juris/upl-anchor") (get j ":juris/id"))
      (is (and (get j ":juris/fake-help") (get j ":juris/referrals")) (get j ":juris/id"))
      (is (some #(str/includes? % "tasuke") (get j ":juris/fake-help")) (get j ":juris/id"))
      (is (get j ":juris/service-note") (get j ":juris/id"))
      (is (= true (get j ":juris/verify-current-law")))
      (is (= ":representative" (get j ":juris/sourcing")))
      (is (> (to-double (get j ":juris/refer-over-amount")) 0)))))

(deftest test-no-hollow-jurisdiction
  (let [cov (cov-ns/coverage)]
    (doseq [j (get cov "jurisdictions")]
      (is (>= (get (get cov "patterns_by_jurisdiction") j 0) 1) (str j " has no clause patterns"))
      (is (>= (get (get cov "procedures_by_jurisdiction") j 0) 1) (str j " has no procedures")))))

(deftest test-coverage-ratio-honest
  (let [cov (cov-ns/coverage)]
    (is (= (get cov "covered_count") (count (respond/load-jurisdictions))))
    (is (= (get cov "un_member_states") 193))
    (is (< (get cov "coverage_ratio") 0.25))
    (is (>= (count (get cov "named_gaps")) 4))))

(deftest test-gap-list-never-stale
  (let [cov (cov-ns/coverage)
        covered (set (get cov "jurisdictions"))
        gap-text (str/join " " (get cov "named_gaps"))]
    (doseq [j (get cov "worklist_remaining")]
      (is (not (contains? covered j)) (str j " is covered but still on the worklist")))
    (doseq [j covered]
      (is (not (str/includes? gap-text (str j " — 未収載"))) (str j " is covered but named as a gap")))))

(deftest test-us-states-registry
  (let [states (respond/load-us-states)
        cov (cov-ns/coverage)]
    (is (= (count states) 50))
    (doseq [s (vals states)]
      (is (and (get s ":state/label") (get s ":state/answer-rule")) (get s ":state/id"))
      (is (get s ":state/answer-anchor") (get s ":state/id"))
      (is (> (to-double (get s ":state/small-claims-usd")) 0) (get s ":state/id"))
      (is (= true (get s ":state/verify-current-law")))
      (is (= ":representative" (get s ":state/sourcing"))))
    (is (= (get cov "us_states_covered") (count states)))
    (is (= (get cov "us_states_total") 50))
    (is (some #(str/includes? % "全50州収載") (get cov "named_gaps")))))

(deftest test-manifest-jurisdictions-in-sync
  (let [manifest (edn/load-edn (clojure.java.io/file (terms/here) "manifest.edn"))
        declared (set (get manifest ":actor/jurisdictions"))
        actual (set (keys (respond/load-jurisdictions)))]
    (is (= declared actual) [(sort (clojure.set/difference declared actual))
                             (sort (clojure.set/difference actual declared))])))

(deftest test-civil-only-jurisdictions-named
  (let [cov (cov-ns/coverage)]
    (is (= (get cov "civil_only_jurisdictions") []))
    (is (some #(str/includes? % "全管轄に専門トラックあり") (get cov "named_gaps")))))

(deftest test-critical-deadline-census
  (let [cov (cov-ns/coverage)
        cds (get cov "critical_deadlines")
        ids (set (map #(get % "proc") cds))]
    (is (>= (count cds) 8))
    (is (clojure.set/subset?
         #{"proc:de-kuendigung" "proc:ch-zahlungsbefehl" "proc:au-unfair-dismissal"
           "proc:it-licenziamento" "proc:es-despido"} ids))
    (is (str/includes? (cov-ns/report cov) "Critical deadlines"))))

(deftest test-protective-census
  (let [cov (cov-ns/coverage)
        n (count (for [p (get cov "_procs") o (get p ":proc/options")
                       :when (= (get o ":opt/protective") true)] o))]
    (is (>= n 60))
    (is (str/includes? (cov-ns/report cov) "protective options"))))

(deftest test-report-names-the-gap
  (let [text (cov-ns/report (cov-ns/coverage))]
    (is (or (str/includes? (str/lower-case text) "named gaps") (str/includes? text "Named gaps")))
    (is (str/includes? text ":unknown-jurisdiction"))
    (is (str/includes? text "193"))))

(deftest test-every-clause-pattern-exercised
  (let [[docs _] (terms/load-docs)
        patterns (terms/load-patterns)
        hit (set (map #(get % "clause") (get (terms/scan docs patterns) "flags")))
        missing (sort (for [p patterns :when (not (contains? hit (get p ":clause/id")))]
                        (get p ":clause/id")))]
    (is (empty? missing) (str "patterns with no exercising seed doc: " missing))))

(deftest test-every-procedure-exercised
  (let [[_ notices] (terms/load-docs)
        procs (respond/load-procs)
        exercised (set (for [p (respond/plans notices procs) :when (= (get p "status") ":genuine")]
                         (get p "proc")))
        missing (sort (for [p procs :when (not (contains? exercised (get p ":proc/id")))]
                        (get p ":proc/id")))]
    (is (empty? missing) (str "procedures with no genuine seed notice: " missing))))

(deftest test-registry-lint
  (let [patterns (terms/load-patterns)
        procs (respond/load-procs)
        pids (map #(get % ":clause/id") patterns)
        qids (map #(get % ":proc/id") procs)]
    (is (= (count pids) (count (set pids))) "duplicate clause ids")
    (is (= (count qids) (count (set qids))) "duplicate proc ids")
    (doseq [p patterns]
      (is (and (get p ":clause/keywords") (get p ":clause/anchor")) (get p ":clause/id")))
    (doseq [p procs]
      (is (get p ":proc/options") (get p ":proc/id"))
      (is (get p ":proc/deadline-rules") (get p ":proc/id"))
      (doseq [dl (get p ":proc/deadline-rules")]
        (is (get dl ":dl/anchor") [(get p ":proc/id") dl])
        (is (= true (get dl ":dl/verify-service-date")) [(get p ":proc/id") (get dl ":dl/label")]))
      (is (get p ":proc/genuine-channels") (get p ":proc/id"))
      (is (get p ":proc/refer-when") (get p ":proc/id"))
      (is (contains? #{":civil" ":labor" ":housing" ":enforcement" ":insolvency" ":family"}
                     (get p ":proc/track" ":civil")) (get p ":proc/id")))))

(deftest test-specialty-track-counted
  (let [cov (cov-ns/coverage)
        t (get cov "procedure_tracks")]
    (is (>= (get t ":labor" 0) 3))
    (is (>= (get t ":housing" 0) 4))
    (is (>= (get t ":enforcement" 0) 3))
    (is (>= (get t ":insolvency" 0) 3))
    (is (>= (get t ":family" 0) 3))
    (is (>= (get t ":civil" 0) 20))
    (is (some #(and (str/includes? % "専門トラック") (str/includes? % "管轄横展開"))
              (get cov "named_gaps")))))

(deftest test-track-matrix
  (let [cov (cov-ns/coverage)
        matrix (get cov "track_matrix")]
    (doseq [[track total] (get cov "procedure_tracks")]
      (is (= (reduce + (map #(get % track 0) (vals matrix))) total) track))
    (is (>= (count (filter #(pos? (get % ":labor" 0)) (vals matrix))) 6))
    (is (>= (count (filter #(pos? (get % ":housing" 0)) (vals matrix))) 6))
    (is (>= (count (filter #(pos? (get % ":enforcement" 0)) (vals matrix))) 5))
    (is (>= (count (filter #(pos? (get % ":insolvency" 0)) (vals matrix))) 5))
    (is (>= (count (filter #(pos? (get % ":family" 0)) (vals matrix))) 4))
    (is (str/includes? (cov-ns/report cov) "Track × jurisdiction matrix"))))

(defn -main [& _] (run-tests 'tate.tests.test-coverage))
