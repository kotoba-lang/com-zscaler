(ns tanemaki.tests.test-propose
  "tanemaki 種蒔き — scorecard + advisory-proposal tests (ADR-2606122001).
  1:1 Clojure port of tests/test_propose.py.

  The action-layer expression of the gates:
    - G1: build-proposal REFUSES (throws) an :excluded or :insufficient-evidence org; the record
      it does build is structurally advisory (advisory=true, bindsFund=false, decidedBy=vote,
      drafted-unsent)
    - G2: investment instruments throw (equity/debt/convertible/…); investment-return language
      in a justification throws
    - G4: the scorecard is content-addressed (CIDv1+SHA-256) and the proposal carries the CID
    - milestone-escrow requires :watched-by milestones

  pytest try/except AssertionError + raise SystemExit on the no-raise path → (is (thrown? …)).
  The __main__ runner is intentionally omitted."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.propose :as propose]
            [tanemaki.methods.cid :as cid]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-stewardship-graph.kotoba.edn"))

(defn graph [] (analyze/load-file* seed))

(deftest test-proposal-is-structurally-advisory
  (let [{:keys [nodes edges]} (graph)
        p (propose/build-proposal "org.foodbank" nodes edges
                                  {:amount-usdc-micros 5000000000
                                   :instrument ":grant"
                                   :justification "地域の食料再分配 commons の運営継続"})]
    (is (= "com.etzhayyim.tanemaki.grantProposal" (get p "$type")))
    (is (and (= true (get p "advisory")) (= false (get p "bindsFund"))))
    (is (and (= "1-sbt-1-vote" (get p "decidedBy")) (= "drafted-unsent" (get p "status"))))
    (is (= true (get p "orgSynthetic"))))) ;; G6 — the seed is fictional

(deftest test-g1-refuses-excluded-org
  (let [{:keys [nodes edges]} (graph)]
    (doseq [org ["org.adfunded-media" "org.surveil-vendor" "org.equity-seeker"]]
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"REFUSAL"
           (propose/build-proposal org nodes edges))
          (str "FAIL: proposal built for excluded org " org)))))

(deftest test-g1-refuses-under-evidenced-org
  (let [{:keys [nodes edges]} (graph)]
    (doseq [org ["org.newgroup" "org.opaque-finance"]]
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"REFUSAL"
           (propose/build-proposal org nodes edges))
          (str "FAIL: proposal built for under-evidenced org " org)))))

(deftest test-g2-investment-instruments-unrepresentable
  (doseq [inst propose/FORBIDDEN-INSTRUMENTS]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (propose/assert-instrument inst))
        (str "FAIL: " inst " accepted")))
  (let [{:keys [nodes edges]} (graph)]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (propose/build-proposal "org.foodbank" nodes edges {:instrument ":equity"})))))

(deftest test-g2-investment-language-rejected
  (doseq [bad ["運転資金と引き換えに持分10%を取得" "expected ROI of 3x" "revenue share 5%"
               "配当を見込む" "exit 時のキャピタルゲイン"]]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (propose/assert-no-investment-language bad))
        (str "FAIL: accepted " (pr-str bad))))
  (is (some? (propose/assert-no-investment-language "OSS maintainer の持続のための交付"))))

(deftest test-g4-scorecard-content-addressed
  (let [{:keys [nodes edges]} (graph)
        card (propose/render-scorecard "org.osslib" nodes edges)]
    ;; advisory + G6 disclosed on the card
    (is (and (str/includes? card "参考意見") (str/includes? card "FICTIONAL")))
    (let [p (propose/build-proposal "org.osslib" nodes edges {:instrument ":milestone-escrow"})]
      (is (= (get p "scorecardCid") (cid/cidv1-raw (cid/utf8-bytes card))))
      (is (and (str/starts-with? (get p "scorecardSha256") "0x")
               (= 66 (count (get p "scorecardSha256"))))))))

(deftest test-milestone-escrow-requires-milestones
  (let [{:keys [nodes edges]} (graph)
        p (propose/build-proposal "org.osslib" nodes edges {:instrument ":milestone-escrow"})]
    (is (= ["ms.osslib-1" "ms.osslib-2"] (get p "milestones")))
    ;; an eligible org WITHOUT milestones cannot take the escrow rail
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"milestone"
         (propose/build-proposal "org.foodbank" nodes edges {:instrument ":milestone-escrow"})))))

(deftest test-proposal-deterministic
  (let [{:keys [nodes edges]} (graph)
        a (propose/build-proposal "org.foodbank" nodes edges)
        b (propose/build-proposal "org.foodbank" nodes edges)]
    (is (= a b))))
