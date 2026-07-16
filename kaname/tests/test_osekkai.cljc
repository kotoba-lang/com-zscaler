(ns kaname.tests.test-osekkai
  "kaname 要 — おせっかい handoff tests (ADR-2606172100; G1/G3). The proposal is advisory/unsent,
  carried by ossekai, structural-first; it can never target a natural person."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.osekkai :as osekkai]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

#?(:clj
   (defn- res- []
     (let [{:keys [nodes edges]} (sos/load-file* seed)]
       [nodes (sos/leverage nodes edges)])))

(deftest test-point-proposal-is-advisory-unsent
  (testing "G3 — the 要 proposal is advisory, unsent, ossekai-carried, structural-first, no-server-key"
    (let [[nodes res] (res-)
          p (osekkai/point-proposal nodes res)]
      (is (= "kaname.if.accred" (:target p)))
      (is (true? (:advisory p)))
      (is (false? (:sent p)))
      (is (false? (:server-key p)))
      (is (true? (:structural-first p)))
      (is (= ":ossekai" (:carrier p))))))

(deftest test-proposals-exclude-insufficient
  (testing "proposals never include an insufficient-evidence (nothing-to-dissolve) target"
    (let [[nodes res] (res-)
          ps (osekkai/proposals nodes res 12)]
      (is (seq ps))
      (is (not-any? #(= ":insufficient-evidence" (:route %)) ps))
      (is (every? #(false? (:sent %)) ps)))))

(deftest test-g1-refuses-named-person
  (testing "G1 — a proposal targeting a natural person is unrepresentable (raises)"
    (let [nodes {"p.jane" {":organism/id" "p.jane" ":organism/label" "Jane" ":person/private" true}}
          rec {:id "p.jane" :label "Jane" :route ":open" :leverage 9.0}]
      (is (thrown? clojure.lang.ExceptionInfo (osekkai/proposal rec nodes))))))

(deftest test-report-framing
  (testing "the handoff report names ossekai as carrier and asserts no-person / advisory"
    (let [[nodes res] (res-)
          md (osekkai/report-md nodes res)]
      (is (clojure.string/includes? md "ossekai"))
      (is (clojure.string/includes? md "advisory"))
      (is (clojure.string/includes? md "no natural person")))))
