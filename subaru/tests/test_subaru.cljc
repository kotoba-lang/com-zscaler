(ns subaru.tests.test-subaru
  "subaru 昴 — link-budget + coverage + stewardship + datom-emit tests (ADR-2606162355).
  1:1 Clojure port of tests/test_subaru.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string]
            [clojure.set]
            [subaru.methods.link-budget :as core]
            [subaru.methods.coverage :as coverage]
            [subaru.methods.stewardship :as stewardship]
            [subaru.methods.datom-emit :as datom]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-constellation.kotoba.edn"))
(defn load-seed [] (core/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 14) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 12) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset?
           #{":constellation" ":bus" ":shell" ":link" ":service-area"
             ":entitlement" ":disposal-plan"} kinds) (str "missing kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-no-surveillance-relay
  (let [{:keys [nodes]} (load-seed)]
    (is (true? (core/check-g1 nodes)))
    (doseq [n (vals nodes)]
      (doseq [b core/banned-attrs] (is (not (contains? n b)) (str "G1 violation: " b)))
      (when (= ":service-area" (get n ":organism/kind"))
        (is (get n ":area/region") "service-area must be an aggregate region (G1)")))
    (let [bad (assoc nodes "con.link.tap"
                     {":organism/id" "con.link.tap" ":organism/kind" ":link" ":link/dpi" true})]
      (is (thrown? clojure.lang.ExceptionInfo (core/check-g1 bad))))
    (let [bad (assoc nodes "con.u.1"
                     {":organism/id" "con.u.1" ":organism/kind" ":service-area"
                      ":area/region" "x" ":user/location" "geo"})]
      (is (thrown? clojure.lang.ExceptionInfo (core/check-g1 bad))))))

(deftest test-g3-commons-entitlement-only
  (let [{:keys [nodes]} (load-seed)]
    (doseq [n (vals nodes)]
      (when (= ":entitlement" (get n ":organism/kind"))
        (is (contains? core/commons-entitlement (get n ":entitlement/kind")))))
    (let [bad (assoc nodes "con.ent.sub"
                     {":organism/id" "con.ent.sub" ":organism/kind" ":entitlement"
                      ":entitlement/kind" ":subscription"})]
      (is (thrown? clojure.lang.ExceptionInfo (core/check-g1 bad))))))

(deftest test-link-budgets-close
  (let [{:keys [nodes edges]} (load-seed)
        res (core/link-budget nodes edges)]
    (is (seq (:links res)))
    (is (:all_closed res) (str "a link does not close: min margin " (:min_margin_db res)))))

(deftest test-coverage-ss-reach
  (let [{:keys [nodes edges]} (load-seed)
        res (coverage/coverage nodes edges)]
    (is (>= (:n_priority res) 3) "expected several §1.16-priority service areas")
    (let [baseline (first (filter #(= "con.area.urban-baseline" (:area %)) (:areas res)))]
      (is (false? (:ss_priority baseline))))
    (let [expect (reduce + 0.0 (map :coverage_pct (filter :ss_priority (:areas res))))]
      (is (< (Math/abs (- (:ss_reach res) expect)) 1e-9)))))

(deftest test-stewardship-g5
  (let [{:keys [nodes edges]} (load-seed)
        res (stewardship/stewardship nodes edges)]
    (is (:g5_pass res) (str "G5 fail: " res))
    (is (:darksat_all res) "darksat must be applied to all buses (G5)")
    (let [edges-no-disp (remove #(= ":disposes" (get % ":en/kind")) edges)]
      (is (thrown? clojure.lang.ExceptionInfo (stewardship/stewardship nodes edges-no-disp))))))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        out (datom/emit nodes edges 7)]
    (is (clojure.string/includes? out ":add]"))
    (is (clojure.string/includes? out ":entitlement/kind"))
    (is (clojure.string/includes? out ":en/kind"))
    (is (clojure.string/includes? out ":bond/is-transient true"))
    (is (clojure.string/includes? out ":bond/ss-reach"))
    (doseq [bad [":link/dpi" ":user/location" ":relay/targeting" ":subscription"]]
      (is (not (clojure.string/includes? out bad)) (str "G1/G3 violation in datom log: " bad)))
    (doseq [line (clojure.string/split-lines out)]
      (when (and (clojure.string/starts-with? line "[") (clojure.string/includes? line ":bond/"))
        (is (clojure.string/includes? line ":derived]") (str "derived not transient: " line))))
    (is (clojure.string/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [{n1 :nodes e1 :edges} (load-seed)
        {n2 :nodes e2 :edges} (load-seed)]
    (is (= (datom/emit n1 e1 1) (datom/emit n2 e2 1)) "Datom emit is not deterministic")))

#?(:clj (defn -main [& _] (run-tests 'subaru.tests.test-subaru)))
