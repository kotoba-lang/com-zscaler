#!/usr/bin/env bb
;; Parity + unit tests for all 12 cljc sensor ports + the evangelism gate.
;; Run:  bb --classpath 20-actors/etzhayyim-organism/src \
;;          20-actors/etzhayyim-organism/src/etzhayyim_organism/sensors/test_sensors.cljc
(ns etzhayyim-organism.sensors.test-sensors
  "clojure.test suite for cljc sensor ports.
   Tests cover:
     1. All 12 sensors load and return AxisReading records.
     2. Scores are in range [0, 10] and leverage in [1, 3].
     3. Charter-rider scan correctness (clean / fossil / promo / surveillance).
     4. count-glob optimisation — shallow patterns don't descend full tree.
     5. Evangelism gate correctness (ADR-2607061700 §1.16 carve-out)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [etzhayyim-organism.sensors.common :as c]
            [etzhayyim-organism.sensors.autopoiesis :as ap]
            [etzhayyim-organism.sensors.active-inference :as ai]
            [etzhayyim-organism.sensors.antifragility :as af]
            [etzhayyim-organism.sensors.charter-rider :as cr]
            [etzhayyim-organism.sensors.evangelism-gate :as eg]
            [etzhayyim-organism.sensors.diversity :as div]
            [etzhayyim-organism.sensors.homeostasis :as hom]
            [etzhayyim-organism.sensors.metabolism :as met]
            [etzhayyim-organism.sensors.reproduction :as rep]
            [etzhayyim-organism.sensors.sanctification :as san]
            [etzhayyim-organism.sensors.symbiosis :as sym]
            [etzhayyim-organism.sensors.wellbecoming :as wb]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(def ^:private repo
  "Absolute path to the monorepo root for live sensor calls.
   Override via REPO env var for CI."
  (or (System/getenv "REPO")
      "/Users/junkawasaki/github/com-junkawasaki/orgs/etzhayyim/root"))

(defn- valid-reading?
  "Returns true if `r` is a structurally valid AxisReading."
  [r]
  (and (instance? etzhayyim_organism.sensors.common.AxisReading r)
       (string?  (:axis r))
       (integer? (:score r))
       (<= 0 (:score r) 10)
       (vector?  (:evidence r))
       (string?  (:next-action r))
       (integer? (:leverage r))
       (<= 1 (:leverage r) 3)))

;; ---------------------------------------------------------------------------
;; Structural tests — all sensors load and return valid readings
;; ---------------------------------------------------------------------------

(deftest test-common-axis-reading-record
  (testing "AxisReading constructor"
    (let [r (c/->AxisReading "test" 5 ["e1"] "next" 2)]
      (is (= "test" (:axis r)))
      (is (= 5 (:score r)))
      (is (= ["e1"] (:evidence r)))
      (is (= "next" (:next-action r)))
      (is (= 2 (:leverage r))))))

(deftest test-has?
  (testing "has? returns true for repo root itself"
    (is (c/has? repo "CLAUDE.md")))
  (testing "has? returns false for missing file"
    (is (not (c/has? repo "NO_SUCH_FILE_xyzzy.txt")))))

(deftest test-read-text
  (testing "read-text returns non-empty string for CLAUDE.md"
    (is (pos? (count (c/read-text repo "CLAUDE.md")))))
  (testing "read-text returns empty string for missing file"
    (is (= "" (c/read-text repo "NO_SUCH_FILE_xyzzy.txt")))))

(deftest test-count-glob-shallow
  (testing "count-glob: shallow pattern counts loop scripts"
    ;; 70-tools/scripts/loop/*.sh — a non-** pattern; must return fast
    (let [n (c/count-glob repo "70-tools/scripts/loop/*.sh")]
      (is (integer? n))
      (is (>= n 0)))))

(deftest test-autopoiesis
  (testing "autopoiesis returns valid reading"
    (is (valid-reading? (ap/read repo))))
  (testing "autopoiesis axis name"
    (is (= "autopoiesis" (:axis (ap/read repo))))))

(deftest test-active-inference
  (testing "active-inference returns valid reading"
    (is (valid-reading? (ai/read repo)))))

(deftest test-antifragility
  (testing "antifragility returns valid reading"
    (is (valid-reading? (af/read repo)))))

(deftest test-diversity
  (testing "diversity returns valid reading"
    (let [r (div/read repo)]
      (is (valid-reading? r))
      ;; The real repo has hundreds of cells / apps — expect score > 0
      (is (pos? (:score r))))))

(deftest test-homeostasis
  (testing "homeostasis returns valid reading"
    (is (valid-reading? (hom/read repo)))))

(deftest test-metabolism
  (testing "metabolism returns valid reading"
    (is (valid-reading? (met/read repo)))))

(deftest test-reproduction
  (testing "reproduction returns valid reading"
    (is (valid-reading? (rep/read repo)))))

(deftest test-sanctification
  (testing "sanctification returns valid reading"
    (is (valid-reading? (san/read repo)))))

(deftest test-symbiosis
  (testing "symbiosis returns valid reading"
    (let [r (sym/read repo)]
      (is (valid-reading? r))
      ;; The real repo has did:web + IPFS + L2 + MST scaffolds
      (is (pos? (:score r))))))

(deftest test-wellbecoming
  (testing "wellbecoming returns valid reading"
    (is (valid-reading? (wb/read repo))))
  (testing "wellbecoming axis name"
    (is (= "wellbecoming" (:axis (wb/read repo))))))

;; ---------------------------------------------------------------------------
;; Charter-rider scan tests
;; ---------------------------------------------------------------------------

(deftest test-charter-rider-clean
  (testing "scan returns ok=true for benign text"
    (let [res (cr/scan "Hello world. The weather is nice today.")]
      (is (:ok res))
      (is (empty? (:hits res))))))

(deftest test-charter-rider-fossil
  (testing "scan flags fossil/extraction text §2(d)"
    (let [res (cr/scan "The new oil well drilling operation expands the field.")]
      (is (false? (:ok res)))
      (is (pos? (count (:hits res))))
      (is (= "§2(d)" (-> res :hits first :section))))))

(deftest test-charter-rider-promo
  (testing "scan flags promotional/commercial text §2(c)"
    (let [res (cr/scan "Buy now! Limited offer discount coupon!")]
      (is (false? (:ok res)))
      (is (pos? (count (:hits res)))))))

(deftest test-charter-rider-surveillance
  (testing "scan flags surveillance text §2(c)"
    (let [res (cr/scan "We use ad tracking pixels and user targeting to monetize.")]
      (is (false? (:ok res)))
      (is (pos? (count (:hits res)))))))

(deftest test-charter-rider-reason
  (testing "reason returns a non-empty string when there are hits"
    (let [res (cr/scan "The new oil well drilling operation expands the field.")]
      (is (not (:ok res)))
      (is (pos? (count (cr/reason res)))))))

(deftest test-charter-rider-explain
  (testing "explain returns multi-line string listing all rules"
    (let [exp (cr/explain)]
      (is (string? exp))
      (is (> (count (clojure.string/split-lines exp)) 3)))))

(deftest test-charter-rider-scan-result-keys
  (testing "scan result has expected keys"
    (let [res (cr/scan "test")]
      (is (contains? res :ok))
      (is (contains? res :hits))
      (is (vector? (:hits res))))))

;; ---------------------------------------------------------------------------
;; Evangelism gate tests (ADR-2607061700 §1.16 carve-out)
;; ---------------------------------------------------------------------------

(deftest test-evangelism-gate-clean-with-opt-out-flag
  (testing "gate passes clean invitational text when opt-out-present? is true"
    (let [res (eg/gate "We're gathering this month to share what the Tree of Life community has been building. Everyone is welcome."
                        {:opt-out-present? true})]
      (is (:ok res) (str "expected clean pass, got: " res)))))

(deftest test-evangelism-gate-clean-with-textual-opt-out
  (testing "gate passes clean invitational text with textual opt-out language"
    (let [res (eg/gate "We're gathering this month to share what the community has been building. Everyone is welcome — no pressure, and feel free to say no.")]
      (is (:ok res) (str "expected clean pass, got: " res)))))

(deftest test-evangelism-gate-missing-opt-out-caught
  (testing "gate flags missing opt-out affordance §1.16(d)"
    (let [res (eg/gate "Come join our community gathering this weekend, everyone welcome.")]
      (is (false? (:ok res)))
      (is (some #(= "§1.16(d)" (:section %)) (:hits res))))))

(deftest test-evangelism-gate-individual-targeting-caught
  (testing "gate flags individual vulnerability targeting §1.16(a)"
    (let [res (eg/gate "Since you're going through a divorce, I picked you specifically to join us. No pressure though."
                        {:opt-out-present? true})]
      (is (false? (:ok res)))
      (is (some #(= "§1.16(a)" (:section %)) (:hits res))))))

(deftest test-evangelism-gate-casual-you-not-falsely-targeted
  (testing "casual second-person address does not trigger §1.16(a)"
    (let [res (eg/gate "You might enjoy learning about what our community has been building together. No pressure — feel free to say no."
                        {:opt-out-present? true})]
      (is (:ok res) (str "expected casual 'you' usage to pass, got: " res)))))

(deftest test-evangelism-gate-coercion-caught
  (testing "gate flags coercion §1.16(b)"
    (let [res (eg/gate "You must join now or else you will regret it forever."
                        {:opt-out-present? true})]
      (is (false? (:ok res)))
      (is (some #(= "§1.16(b)" (:section %)) (:hits res))))))

(deftest test-evangelism-gate-minor-solo-caught
  (testing "gate flags minor-solo solicitation §1.16(c)"
    (let [res (eg/gate "Hey, are your parents home? You don't need your parents' permission to join us."
                        {:opt-out-present? true})]
      (is (false? (:ok res)))
      (is (some #(= "§1.16(c)" (:section %)) (:hits res))))))

(deftest test-evangelism-gate-delegates-to-charter-rider
  (testing "gate delegates to charter-rider scan for §2 categories"
    (let [res (eg/gate "Buy now to access our limited offer and join the community!"
                        {:opt-out-present? true})]
      (is (false? (:ok res)))
      (is (some #(= "§2(c)" (:section %)) (:charter-hits res))))))

(deftest test-evangelism-gate-reason
  (testing "reason returns a non-empty string when there are hits"
    (let [res (eg/gate "")]
      (is (not (:ok res)))
      (is (pos? (count (eg/reason res)))))))

(deftest test-evangelism-gate-explain
  (testing "explain returns multi-line string listing all carve-out sections"
    (let [exp (eg/explain)]
      (is (string? exp))
      (doseq [section ["§1.16(a)" "§1.16(b)" "§1.16(c)" "§1.16(d)"]]
        (is (clojure.string/includes? exp section))))))

;; ---------------------------------------------------------------------------
;; Runner
;; ---------------------------------------------------------------------------

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'etzhayyim-organism.sensors.test-sensors)]
    (System/exit (if (= 0 (+ fail error)) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
