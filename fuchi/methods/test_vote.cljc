(ns fuchi.methods.test-vote
  "Tests for 扶持 (fuchi) vote.cljc — 1:1 port of methods/test_vote.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.vote :as v]))

(defn- B [did choice cast-at] (v/make-ballot {:voter-did did :choice choice :cast-at cast-at}))

(defn- ballots
  ([spec] (ballots spec 1000))
  ([spec opened]
   (loop [out [] t (inc opened) s spec]
     (if (empty? s) out
         (let [[did choice] (first s)]
           (recur (v/cast out (B did choice t)) (+ t 2) (rest s)))))))

(deftest test-accepts-after-timelock-with-quorum
  (let [b (ballots [["did:m:a" "yes"] ["did:m:b" "yes"] ["did:m:c" "yes"] ["did:m:d" "no"]])
        r (v/tally b 1000 1060 48)]
    (is (and (get r "finalizable") (= (get r "outcome") "accepted")
             (= (get r "yes") 3) (= (get r "no") 1)))))

(deftest test-pending-before-timelock
  (let [b (ballots [["did:m:a" "yes"] ["did:m:b" "yes"] ["did:m:c" "yes"]])
        r (v/tally b 1000 1010 48)]
    (is (and (false? (get r "finalizable")) (= (get r "outcome") "pending")))))

(deftest test-finalize-raises-before-timelock
  (let [b (ballots [["did:m:a" "yes"] ["did:m:b" "yes"]])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"timelock" (v/finalize b 1000 1010 48)))))

(deftest test-rejected-without-quorum
  (let [b (ballots [["did:m:a" "yes"]])
        r (v/tally b 1000 1060 48 3)]
    (is (and (false? (get r "quorum_met")) (= (get r "outcome") "rejected")))))

(deftest test-rejected-when-no-beats-yes
  (let [b (ballots [["did:m:a" "yes"] ["did:m:b" "no"] ["did:m:c" "no"]])
        r (v/tally b 1000 1060 48)]
    (is (= (get r "outcome") "rejected"))))

(deftest test-one-sbt-one-vote-rejects-duplicate
  (let [b (v/cast [] (B "did:m:a" "yes" 1001))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"1 SBT = 1 vote" (v/cast b (B "did:m:a" "no" 1002))))))

(deftest test-ballot-weight-must-be-one
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"1 SBT = 1 vote"
                        (v/make-ballot {:voter-did "did:m:a" :choice "yes" :cast-at 1001 :weight 5}))))

(deftest test-server-voter-unrepresentable
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"(G9|G4)" (v/make-ballot {:voter-did "server" :choice "yes" :cast-at 1001}))))

(deftest test-ballot-server-key-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-server-key"
                        (v/make-ballot {:voter-did "did:m:a" :choice "yes" :cast-at 1001 :server-held-key true}))))

(deftest test-out-of-window-ballot-not-counted
  (let [b [(B "did:m:a" "yes" 1004) (B "did:m:b" "yes" 1004)
           (B "did:m:c" "yes" 1004) (B "did:m:d" "yes" 9999)]
        r (v/tally b 1000 1060 48)]
    (is (= (get r "yes") 3))))

(deftest test-ballots-from-seed-dedupes
  (let [recs [{":ballot/voter" "did:m:a" ":ballot/choice" ":yes" ":ballot/cast-at" 1004}
              {":ballot/voter" "did:m:b" ":ballot/choice" ":no" ":ballot/cast-at" 1006}]
        b (v/ballots-from-seed recs)]
    (is (and (= (count b) 2) (= (:choice (first b)) "yes")))))
