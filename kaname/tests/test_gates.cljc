(ns kaname.tests.test-gates
  "kaname 要 — constitutional gate tests (ADR-2606172100). Each guard MUST throw.
    G2 — route is opening-only; capture/seize/control refused.
    G1 — natural person + coordinate refused (public ROLE allowed).
    G5 — belief-content score refused; :influences must carry an on-the-record :en/basis."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [kaname.methods.gates :as gates]))

(deftest test-g2-route-opening-only
  (testing "G2 — every admissible route dissolves concentration"
    (doseq [r [":open" ":route-around" ":add-redundancy" ":decentralize" ":insufficient-evidence"]]
      (is (= r (gates/assert-route! r)))))
  (testing "G2 — capture / seize / control / monopolize are unrepresentable"
    (doseq [r [":capture" ":seize" ":control" ":exploit" ":corner" ":monopolize"]]
      (is (thrown? clojure.lang.ExceptionInfo (gates/assert-route! r)))))
  (testing "G2 — an unknown route is refused (not silently allowed)"
    (is (thrown? clojure.lang.ExceptionInfo (gates/assert-route! ":nationalize")))))

(deftest test-g1-person-excluded
  (testing "G1 — a public ROLE / structural node passes"
    (is (map? (gates/assert-not-person! {":organism/id" "kaname.role.accreditor"
                                         ":organism/kind" ":sos/role"}))))
  (testing "G1 — a private natural person is refused"
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-not-person! {":organism/id" "p.jane" ":person/private" true}))))
  (testing "G1 — a coordinate is refused (no place to target)"
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-not-person! {":organism/id" "x" ":coord/lat" 35.0 ":coord/lon" 139.0})))))

(deftest test-g5-no-thought-policing
  (testing "G5 — a belief-content score is unrepresentable"
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-no-belief-score! {":organism/id" "f" ":belief/wrongness" 0.9})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-no-belief-score! {":organism/id" "f" ":faith/rank" 3}))))
  (testing "G5 — a structural node with no belief score passes"
    (is (map? (gates/assert-no-belief-score! {":organism/id" "kaname.inst.doctrine"})))))

(deftest test-g5-influence-needs-basis
  (testing "G5 — an :influences 縁 without an on-the-record basis is refused"
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-influence-basis! {":en/from" "a" ":en/to" "b" ":en/kind" ":influences"})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (gates/assert-influence-basis! {":en/from" "a" ":en/to" "b" ":en/kind" ":influences" ":en/basis" "  "}))))
  (testing "G5 — an :influences 縁 WITH a basis passes; a non-influences edge is unaffected"
    (is (map? (gates/assert-influence-basis! {":en/from" "a" ":en/to" "b" ":en/kind" ":influences" ":en/basis" "on-record charter §3"})))
    (is (map? (gates/assert-influence-basis! {":en/from" "a" ":en/to" "b" ":en/kind" ":gates"})))))
