(ns ake.methods.test-triage
  "test_triage.cljc — 朱 (ake) risk/quality scoring + pure-function routing (G2).
  1:1 Clojure port of `methods/test_triage.py` (clojure.test). Every Python assertion ported.
  The seed is loaded via `ake.methods._edn` (the #?(:clj) file edge); the core route_for /
  assess sweeps are pure and run on every platform."
  (:require [clojure.test :refer [deftest is run-tests]]
            [ake.methods.triage :as triage]
            #?(:clj [ake.methods._edn :as edn])))

#?(:clj
   (def ^:private seed-path
     "20-actors/ake/data/seed-edit-graph.kotoba.edn"))

#?(:clj
   (defn- seed []
     (into {} (map (fn [e] [(get e ":edit/id") e])
                   (get (edn/reconstitute (edn/load-edn seed-path) "data.seed-edit-graph") ":edit/batch")))))

;; ── route-for is a PURE FUNCTION of (risk, quality, rider) — G2 ─────────────────
(deftest test-route-invariant-goes-to-council
  (is (= "council-lv7" (triage/route-for "invariant" 1.0 ""))))

(deftest test-route-low-high-quality-auto-accepts
  (is (= "auto-accept" (triage/route-for "low" triage/QUALITY-AUTO-ACCEPT "")))
  (is (= "auto-accept" (triage/route-for "low" 0.95 ""))))

(deftest test-route-low-but-thin-goes-to-vote
  (is (= "vote" (triage/route-for "low" (- triage/QUALITY-AUTO-ACCEPT 0.01) ""))))

(deftest test-route-high-risk-always-votes
  (is (= "vote" (triage/route-for "high" 1.0 "")))
  (is (= "vote" (triage/route-for "medium" 1.0 ""))))

(deftest test-route-rider-hit-is-refused-regardless
  ;; even a "low risk, perfect quality" shape is refused if a Rider token is present
  (is (= "refused" (triage/route-for "low" 1.0 "advertis"))))

;; ── route-for TOTALITY + PRIORITY — the G2 structural guarantee ──────────────────
(def ^:private QGRID
  [0.0 0.15 0.5 (- triage/QUALITY-AUTO-ACCEPT 1e-9) triage/QUALITY-AUTO-ACCEPT 0.85 1.0])

(deftest test-route-for-is-total-and-closed-over-domain
  (doseq [risk triage/RISKS
          q QGRID
          rider ["" "advertis" "兵器"]]
    (let [r (triage/route-for risk q rider)]
      (is (some #{r} triage/ROUTES)
          (str "route-for(" (pr-str risk) "," q "," (pr-str rider) ")=" (pr-str r) " not in " triage/ROUTES)))))

(deftest test-route-rider-dominates-every-risk-and-quality
  (doseq [risk triage/RISKS q QGRID]
    (is (= "refused" (triage/route-for risk q "advertis")))))

(deftest test-route-auto-accept-requires-low-risk-clean-and-high-quality
  (doseq [risk triage/RISKS
          q QGRID
          rider ["" "advertis"]]
    (let [got (triage/route-for risk q rider)
          expected-auto (and (= risk "low") (not (seq rider)) (>= q triage/QUALITY-AUTO-ACCEPT))]
      (is (= (= got "auto-accept") expected-auto)
          (str "auto-accept leak: route-for(" (pr-str risk) "," q "," (pr-str rider) ")=" (pr-str got))))))

(deftest test-route-is-monotone-at-low-risk-no-demotion-as-quality-rises
  (let [rank {"vote" 0 "auto-accept" 1}]
    (loop [qs (sort QGRID) prev -1]
      (when (seq qs)
        (let [r (triage/route-for "low" (first qs) "")]
          (is (contains? rank r) (str "unexpected low-risk route " (pr-str r)))
          (is (>= (rank r) prev) (str "non-monotone at q=" (first qs) ": " (pr-str r)))
          (recur (rest qs) (rank r)))))))

(deftest test-route-invariant-risk-never-optimistic-accepts
  (doseq [q QGRID]
    (is (= "council-lv7" (triage/route-for "invariant" q "")))))

;; ── full scoring over the seed batch ────────────────────────────────────────────
#?(:clj
   (deftest test-seed-e1-kgfact-sourced-auto-accepts
     (let [t (triage/score-edit (get (seed) "e1"))]
       (is (= ":low" (get t ":triage/risk")))
       (is (= ":auto-accept" (get t ":triage/route")))
       (is (>= (get t ":triage/quality") triage/QUALITY-AUTO-ACCEPT)))))

#?(:clj
   (deftest test-seed-e2-status-change-is-high-and-votes
     (let [t (triage/score-edit (get (seed) "e2"))]
       (is (= ":high" (get t ":triage/risk")))    ;; :status is a sensitive attr
       (is (= ":vote" (get t ":triage/route"))))))

#?(:clj
   (deftest test-seed-e3-profile-edit-is-medium-and-votes
     (let [t (triage/score-edit (get (seed) "e3"))]
       (is (= ":medium" (get t ":triage/risk")))
       (is (= ":vote" (get t ":triage/route"))))))

#?(:clj
   (deftest test-seed-e4-license-attr-is-invariant-and-council
     (let [t (triage/score-edit (get (seed) "e4"))]
       (is (= ":invariant" (get t ":triage/risk")))   ;; :license ∈ INVARIANT-ATTRS
       (is (= ":council-lv7" (get t ":triage/route"))))))

#?(:clj
   (deftest test-seed-e5-advertising-is-rider-refused
     (let [t (triage/score-edit (get (seed) "e5"))]
       (is (= ":refused" (get t ":triage/route")))
       (is (= 0.0 (get t ":triage/quality")))
       (is (seq (get t ":triage/rider-token"))))))   ;; a token was found

;; ── validation gates (G1/G3/G4) raise, they don't silently pass ─────────────────
#?(:clj
   (deftest test-score-edit-refuses-non-member-author
     (let [e (assoc (get (seed) "e1") ":edit/author-kind" ":server")]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1" (triage/score-edit e))))))

#?(:clj
   (deftest test-score-edit-refuses-server-held-key
     (let [e (assoc (get (seed) "e1") ":edit/server-held-key" true)]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-server-key" (triage/score-edit e))))))

#?(:clj
   (deftest test-score-edit-refuses-unsourced
     (let [e (assoc (get (seed) "e1") ":edit/provenance" "")]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4" (triage/score-edit e))))))

#?(:clj
   (deftest test-score-edit-refuses-bad-target-kind
     (let [e (assoc (get (seed) "e1") ":edit/target-kind" ":entity-speech")]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3" (triage/score-edit e))))))

#?(:clj
   (deftest test-score-edit-refuses-a-smuggled-decision
     (let [e (assoc (get (seed) "e1") "decision" "accept")]
       (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2" (triage/score-edit e))))))

;; ── rider + quality helpers ─────────────────────────────────────────────────────
(deftest test-rider-hit-finds-jp-and-en-tokens
  (is (or (= "広告" (triage/rider-hit "includes 広告")) (seq (triage/rider-hit "includes 広告"))))
  (is (= "" (triage/rider-hit "clean technical note"))))

(deftest test-quality-rewards-verifiable-provenance
  (let [base {":edit/op" ":assert" ":edit/proposed-value" "x"
              ":edit/rationale" "a clear reason here" ":edit/provenance" "https://example.com/x"}
        bad (assoc base ":edit/provenance" "trust me")]
    (is (> (triage/assess-quality base "") (triage/assess-quality bad "")))))

;; ── assess-quality SCORE BREAKDOWN — pin every additive branch of the ORES analogue ──
;; sourcing(0/.15/.5) + rationale(0/.2) + plausibility(0/.3), clamped to 1.0. Mirror of the
;; Python test_quality_* sweep so the canonical path locks the same auto-accept boundary.
(def ^:private q-base
  {":edit/op" ":assert" ":edit/proposed-value" "x"
   ":edit/rationale" "a clear reason here" ":edit/provenance" "https://example.com/x"})

(deftest test-quality-full-marks-verifiable-rationale-assert-value
  (is (= 1.0 (triage/assess-quality q-base ""))))

(deftest test-quality-nonverifiable-provenance-scores-partial-sourcing
  (is (= 0.65 (triage/assess-quality (assoc q-base ":edit/provenance" "trust me") ""))))

(deftest test-quality-empty-provenance-scores-zero-sourcing
  (is (= 0.5 (triage/assess-quality (assoc q-base ":edit/provenance" "") ""))))

(deftest test-quality-short-rationale-earns-no-clarity-credit
  (is (= 0.8 (triage/assess-quality (assoc q-base ":edit/rationale" "short") ""))))

(deftest test-quality-retract-and-challenge-need-no-value
  (is (= 1.0 (triage/assess-quality (assoc q-base ":edit/op" ":retract" ":edit/proposed-value" "") "")))
  (is (= 1.0 (triage/assess-quality (assoc q-base ":edit/op" ":challenge" ":edit/proposed-value" "") ""))))

(deftest test-quality-oversized-value-earns-no-plausibility-credit
  (is (= 0.7 (triage/assess-quality (assoc q-base ":edit/proposed-value" (apply str (repeat 4001 "z"))) ""))))

(deftest test-quality-rider-hit-zeroes-the-whole-score
  (is (= 0.0 (triage/assess-quality q-base "advertis"))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-triage)))
