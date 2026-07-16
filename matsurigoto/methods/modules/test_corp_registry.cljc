(ns matsurigoto.methods.modules.test-corp-registry
  "test_corp_registry.py — conformance tests for the corp-registry module.
  1:1 Clojure port (stdlib unittest-style → clojure.test).

  The LEI tests exercise the real ISO 7064 MOD 97-10 checksum. The __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [matsurigoto.methods.modules.corp-registry :as R]))

(deftest test-no-server-authority-certificate-unsigned
  (is (= R/SERVER-HELD-AUTHORITY false))
  (let [r (R/register-incorporation "Co" ["o"] 0 "art" "addr" "JPN" 1)]
    (is (nil? (get-in r ["certificate" "proof"])))
    (is (= (get-in r ["certificate" "server_held_authority"]) false))))

(deftest test-to-digits-iso7064-mapping
  (is (= (#'R/to-digits "0A9Z") (str "0" "10" "9" "35"))))

(deftest test-lei-roundtrip-validates
  (let [lei (R/assign-lei "EZHY" "000000000001")]
    (is (= (count lei) 20))
    (is (= (R/validate-lei lei) true))))

(deftest test-lei-check-digits-make-mod97-one-for-many-entities
  (doseq [n (range 1 50)]
    (let [lei (R/assign-lei "EZHY" (format "%012d" n))]
      (is (R/validate-lei lei) lei))))

(deftest test-lei-corruption-detected
  (let [lei (R/assign-lei "EZHY" "000000000042")]
    (is (R/validate-lei lei))
    (let [bad (vec lei)
          bad (assoc bad 8 (if (not= (nth bad 8) \Z) \Z \Y))]
      (is (= (R/validate-lei (apply str bad)) false)))))

(deftest test-lei-rejects-bad-length-and-chars
  (is (= (R/validate-lei "TOOSHORT") false))
  (is (= (R/validate-lei "EZHY00000000000001*9") false))  ; '*' illegal
  (is (= (R/validate-lei 12345) false)))

(deftest test-check-digits-two-chars-zero-padded
  (let [cd (R/compute-lei-check-digits (str "EZHY00" "000000000007"))]
    (is (and (= (count cd) 2) (re-matches #"\d+" cd)))))

(deftest test-incorporation-assigns-registry-number-and-lei
  (let [r (R/register-incorporation "Tree of Life K.K." ["officer:rin"] 10000000
                                    "articles" "東京都" "JPN" 7)]
    (is (= (get r "registry_number") "JPN-00000007"))
    (is (R/validate-lei (get r "lei")))
    (is (= (get-in r ["record" "immutable"]) true))))

(deftest test-incorporation-validation-rules
  (let [bad-args [["" ["o"] 0 "a" "ad"]       ; no name
                  ["Co" [] 0 "a" "ad"]        ; no officer
                  ["Co" ["o"] -1 "a" "ad"]    ; negative capital
                  ["Co" ["o"] 0 "" "ad"]      ; no articles
                  ["Co" ["o"] 0 "a" ""]]]     ; no address
    (doseq [[name officers capital articles address] bad-args]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (R/register-incorporation name officers capital articles address "JPN" 1))))))

(deftest test-change-is-append-only-g5
  (let [inc (R/register-incorporation "Co" ["o"] 0 "a" "ad" "JPN" 1)
        hist (R/append [] inc)
        chg (R/register-change (get inc "registry_number") {"address" "new"} "2026-06-05")
        hist2 (R/append hist chg)]
    (is (and (= (count hist) 1) (= (count hist2) 2)))     ; original untouched, new list
    (is (= (get-in hist2 [0 "kind"]) "incorporation"))    ; incorporation record preserved
    (is (= (get-in hist2 [1 "kind"]) "change"))))         ; amendment appended

(deftest test-solve-is-gated-at-r0
  (is (thrown? #?(:clj Exception :cljs js/Error) (R/solve))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.modules.test-corp-registry)))
