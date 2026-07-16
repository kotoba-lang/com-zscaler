(ns matsurigoto.methods.modules.test-civil-registry
  "test_civil_registry.py — conformance tests for the civil-registry module.
  1:1 Clojure port (stdlib unittest-style → clojure.test). The __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [matsurigoto.methods.modules.civil-registry :as C]))

(def NOW "2026-06-05T00:00:00Z")

(deftest test-no-server-authority-certificates-unsigned
  (is (= C/SERVER-HELD-AUTHORITY false))
  (let [b (C/register-birth "b1" "child:a" ["parent:p"] "place" "2026-01-01T00:00:00Z" NOW)]
    (is (nil? (get-in b ["certificate" "proof"])))
    (is (= (get-in b ["certificate" "server_held_authority"]) false))
    (is (= (get-in b ["certificate" "status"]) "issued-unsigned"))))

(deftest test-birth-requires-child-and-parent
  (doseq [[child parents] [["" ["p"]] ["c" []]]]
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (C/register-birth "b" child parents "place" "2026-01-01T00:00:00Z" NOW)))))

(deftest test-birth-rejects-future-occurrence
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (C/register-birth "b" "c" ["p"] "place" "2027-01-01T00:00:00Z" NOW))))

(deftest test-birth-record-is-immutable-and-minimized
  (let [b (C/register-birth "b1" "child:aoi" ["parent:rin"] "tokyo" "2026-06-01T00:00:00Z" NOW)
        rec (get b "record")]
    (is (= (get rec "immutable") true))
    (is (= (get rec "vital_kind") "birth"))
    (is (= (set (keys (get rec "fields"))) #{"child" "parents" "place"}))))  ; G6 minimization

(deftest test-death-registration
  (let [d (C/register-death "d1" "person:x" "osaka" "2026-05-01T00:00:00Z" NOW "ICD-11:XX")]
    (is (= (get-in d ["record" "fields" "cause"]) "ICD-11:XX"))
    (is (= (nth (get-in d ["certificate" "type"]) 1) "DeathCertificate"))))

(deftest test-marriage-requires-distinct-partners
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (C/register-marriage "m" "a" "a" "place" "2026-01-01T00:00:00Z" NOW))))

(deftest test-marriage-rejects-bigamy
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (C/register-marriage "m" "a" "b" "place" "2026-01-01T00:00:00Z" NOW [["a" "z"]]))))

(deftest test-marriage-partners-sorted-deterministic
  (let [m1 (C/register-marriage "m1" "rin" "aoi" "place" "2026-01-01T00:00:00Z" NOW)
        m2 (C/register-marriage "m2" "aoi" "rin" "place" "2026-01-01T00:00:00Z" NOW)]
    (is (= (get-in m1 ["record" "fields" "partners"]) (get-in m2 ["record" "fields" "partners"])))))

(deftest test-append-is-non-destructive-g5
  (let [hist []
        b (C/register-birth "b1" "c" ["p"] "place" "2026-01-01T00:00:00Z" NOW)
        hist2 (C/append hist b)]
    (is (= hist []))           ; original untouched
    (is (= (count hist2) 1)))) ; new list returned

(deftest test-residency-latest-is-current-address
  (let [hist (-> []
                 (C/append (C/register-residency "r1" "person:x" "addr-A" "2026-01-01T00:00:00Z" NOW))
                 (C/append (C/register-residency "r2" "person:x" "addr-B" "2026-03-01T00:00:00Z" NOW)))]
    (is (= (count hist) 2))
    (is (= (C/current-address hist "person:x") "addr-B"))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (C/solve))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.modules.test-civil-registry)))
