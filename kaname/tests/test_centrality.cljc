(ns kaname.tests.test-centrality
  "kaname 要 — R1 centrality tests (ADR-2606172100 R1). Exact Brandes betweenness, eigenvector,
  ΔΦ percolation sensitivity all converge on the cross-domain Accreditation Interface as the 要."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.centrality :as c]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

#?(:clj
   (defn- load- []
     (let [{:keys [nodes edges]} (sos/load-file* seed)
           res (sos/leverage nodes edges)]
       [nodes edges res (c/leverage-r1 nodes edges res)])))

(deftest test-adjacency-symmetric
  (let [[nodes edges _ _] (load-)
        {:keys [adj]} (c/adjacency nodes edges)]
    (testing "the projection is symmetric (undirected)"
      (doseq [[u nbrs] adj, v nbrs]
        (is (contains? (get adj v) u))))))

(deftest test-betweenness-hub-is-the-interface
  (testing "Brandes betweenness peaks at the cross-domain Accreditation Interface (the hub)"
    (let [[nodes _ _ res1] (load-)
          bw (:betweenness res1)
          top (first (sos/rank bw nodes 1))]
      (is (= "kaname.if.accred" (first top)))
      (is (every? #(>= (val %) 0.0) bw)))))

(deftest test-delta-phi-peaks-at-the-point
  (testing "ΔΦ (fragmentation sensitivity) is maximal at the 要 — opening it dissolves the most"
    (let [[nodes _ _ res1] (load-)
          top (first (sos/rank (:delta-phi res1) nodes 1))]
      (is (= "kaname.if.accred" (first top))))))

(deftest test-eigenvector-normalized
  (testing "eigenvector centrality is L2-normalized and led by the interface"
    (let [[nodes _ _ res1] (load-)
          ev (:eigenvector res1)
          ss (reduce + 0.0 (map #(* % %) (vals ev)))]
      (is (< (Math/abs (- ss 1.0)) 1e-6))
      (is (= "kaname.if.accred" (first (first (sos/rank ev nodes 1))))))))

(deftest test-r1-leverage-point
  (testing "the R1 要 (real betweenness inside L1) is still the Accreditation Interface"
    (let [[nodes _ _ res1] (load-)
          kp (c/kaname-point-r1 res1 nodes)]
      (is (= "kaname.if.accred" (first kp)))))
  (testing "an already-open position still scores R1 leverage 0"
    (let [[nodes _ _ res1] (load-)]
      (is (< (Math/abs (double (get-in res1 [:leverage-r1 "kaname.if.open-commons"] 0.0))) 1e-9)))))

(deftest test-report-r1
  (testing "the R1 report names the real measures"
    (let [[nodes edges _ res1] (load-)
          md (c/report-md nodes edges res1)]
      (is (clojure.string/includes? md "betweenness"))
      (is (clojure.string/includes? md "ΔΦ"))
      (is (clojure.string/includes? md "要")))))
