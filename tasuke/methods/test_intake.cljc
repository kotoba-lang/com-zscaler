(ns tasuke.methods.test-intake
  "Tests for 助 (tasuke) plain-language intake — 誰でも使える, invariants baked in.
  1:1 port of `methods/test_intake.py` (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [tasuke.methods.intake :as intake]
            [tasuke.methods.packet :as packet]))

;; ── loss parser handles human input ──────────────────────────────────────────
(deftest test-parse-yen-forms
  (is (= 480000 (intake/parse-yen "480000")))
  (is (= 480000 (intake/parse-yen "48万")))
  (is (= 480000 (intake/parse-yen "48万円")))
  (is (= 480000 (intake/parse-yen "480,000円")))
  (is (= 15000 (intake/parse-yen "1万5000")))
  (is (= 0 (intake/parse-yen "なし")))
  (is (= 0 (intake/parse-yen ""))))

(deftest test-parse-yesno
  (is (= true (intake/parse-yesno "はい")))
  (is (= true (intake/parse-yesno "yes")))
  (is (= false (intake/parse-yesno "いいえ")))
  (is (= false (intake/parse-yesno ""))))

;; ── build_case bakes in G1/G7 ────────────────────────────────────────────────
(defn- answers [& {:as over}]
  (merge {"consent" "はい" "narrative" "口座から不正送金された" "occurred" "2026-06-03"
          "loss" "48万" "service" "○○銀行" "account_id" "普通1234567"}
         over))

(deftest test-build-case-sets-invariants
  (let [c (intake/build-case-from-answers (answers))]
    (is (= true (get c ":case/consent")))
    (is (= 0 (get c ":case/support-cost-jpy")))
    (is (= false (get c ":case/server-held-key")))
    (is (= 480000 (get c ":case/loss-jpy")))
    (is (= 480000 (get (first (get c ":case/loss-breakdown")) ":jpy")))))

(deftest test-build-case-refuses-without-consent
  (doseq [bad ["いいえ" "" "no"]]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G7"
          (intake/build-case-from-answers (answers "consent" bad))))))

(deftest test-case-id-is-deterministic
  (is (= (get (intake/build-case-from-answers (answers)) ":case/id")
         (get (intake/build-case-from-answers (answers)) ":case/id"))))

;; ── end-to-end: answers → packet, free, member-authored ──────────────────────
(deftest test-answers-to-packet-is-free-and-complete
  (let [case (intake/build-case-from-answers (answers))
        p (packet/build-packet case)
        kinds (map #(clojure.string/replace (get % ":doc/kind") #"^:+" "") (get p "documents"))]
    (is (= 0 (get p "cost")))
    (is (and (some #{"damage-report"} kinds) (some #{"bank-freeze-request"} kinds)))  ;; money moved
    (doseq [d (get p "documents")]
      (is (and (= ":member" (get d ":doc/authored-by"))
               (= false (get d ":doc/published")))))))

;; ── interactive shell honors the injected asker + consent abort ──────────────
(deftest test-interactive-collects-with-injected-asker
  (let [a (atom ["はい" "乗っ取り被害" "2026-06-02" "なし" "LINE" "@me"])
        ask (fn [_prompt] (let [x (first @a)] (swap! a rest) x))
        case (intake/interactive ask)]
    (is (and (= true (get case ":case/consent")) (= 0 (get case ":case/loss-jpy"))))))

(deftest test-interactive-aborts-on-no-consent
  (is (thrown? #?(:clj Exception :cljs js/Error)
        (intake/interactive (fn [_prompt] "いいえ")))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-intake)))
