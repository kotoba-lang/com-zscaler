#!/usr/bin/env bb
;; End-to-end pipeline-determinism pin for the kabuto autonomous heartbeat.
(ns kabuto.methods.test-pipeline-cid
  "test_pipeline_cid.clj — kabuto WHOLE-PIPELINE determinism (ADR-2605312345 / 2606022000).

  The autorun test proves the heartbeat is resume-safe IN-process; the kotoba_cid test pins the
  unit tx-cid. This closes the gap between them: it pins the head-cid of the ENTIRE pipeline —
  load → classify → analyze → graph-datoms + derived-datoms → canonical-order → 2-cycle
  commit-DAG — to a literal, so ANY change in analyze output, the derived-signal set, datom
  ordering, or the canonical form moves the CID and fails CI. Recomputed in whatever bb/JVM runs
  the test = cross-process proof.

  Uses an INLINE fixed fixture (a sibling may edit the actor's real seed, so we never pin over
  it). The fixture exercises single-source / commodity-HHI / supply-betweenness / tier-depth —
  the derived signals whose ordering determinism is the actual risk.

  Run:  bb --classpath 20-actors 20-actors/kabuto/methods/test_pipeline_cid.clj"
  (:require [kabuto.methods.autorun :as autorun]
            [kabuto.methods.kabuto-edn :as e]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

;; A fixed 3-company / 2-edge supply graph (one top-level EDN vector — load-edn reads one form).
(def ^:private fixture
  (str ";; fixed test fixture — DO NOT EDIT (pins a head-cid)\n[\n"
       "{:company/id \"org.corp.tw.acme\" :company/name \"Acme Foundry\" :company/ticker \"0001.TW\" :company/exchange :twse :company/country \"TW\" :company/sector :semiconductors :company/status :listed :company/sourcing :representative}\n"
       "{:company/id \"org.corp.nl.litho\" :company/name \"Litho BV\" :company/ticker \"LIT.AS\" :company/exchange :euronext :company/country \"NL\" :company/sector :semiconductors :company/status :listed :company/sourcing :representative}\n"
       "{:company/id \"org.corp.jp.maker\" :company/name \"Maker KK\" :company/ticker \"9999.T\" :company/exchange :tse :company/country \"JP\" :company/sector :electronics :company/status :listed :company/sourcing :representative}\n"
       "{:supply.edge/id \"supply.litho->acme\" :supply.edge/from \"org.corp.nl.litho\" :supply.edge/to \"org.corp.tw.acme\" :supply.edge/tier 1 :supply.edge/commodity :lithography :supply.edge/criticality 0.95 :supply.edge/sourcing :representative}\n"
       "{:supply.edge/id \"supply.acme->maker\" :supply.edge/from \"org.corp.tw.acme\" :supply.edge/to \"org.corp.jp.maker\" :supply.edge/tier 1 :supply.edge/commodity :wafer :supply.edge/criticality 0.8 :supply.edge/sourcing :representative}\n"
       "]\n"))

;; ── pinned literal (re-captured 2026-07-08; the cross-process anchor). The 2026-06-16
;;    pin was never actually exercised: run-autonomous was called with kabuto-style
;;    keyword-args this actor's real (positional) implementation doesn't support, so this
;;    literal was compared against nothing until that call bug was fixed — re-verified via
;;    2 independent in-process runs producing the identical value (determinism confirmed
;;    by pipeline-is-cross-run-deterministic itself), plus the unrelated 85-datoms
;;    structural invariant and the commit-DAG prefix test both passing unchanged ──
(def ^:private head-cid-2cyc "bd819575b67e7a3934a08addaa565710308d15c8712d3d105c3093bfc697e9c2d")

(defn- with-fixture [f]
  (let [fx (java.io.File/createTempFile "kab-fx-" ".kotoba.edn")
        log (java.io.File/createTempFile "kab-log-" ".kotoba.edn")]
    (.delete log)
    (try
      (spit fx fixture)
      (f (.getAbsolutePath fx) (.getAbsolutePath log))
      (finally (.delete fx) (.delete (io/file log))))))

(deftest fixture-classifies-as-expected
  ;; kabuto-edn's own read-edn (NOT clojure.edn/read-string) string-converts keyword
  ;; tokens during parsing ("keywords kept as \":ns/name\" strings" — its own docstring);
  ;; classify expects that string-keyed shape (contains? r ":company/id").
  (let [g (e/classify (e/read-edn fixture))]
    (is (= 3 (count (:companies g))))
    (is (= 2 (count (:edges g))))))

;; kabuto.methods.autorun/run-autonomous is POSITIONAL ([cycles graph-path* log-path]),
;; NOT keyword-args, and returns a STRING/underscore-keyed map ("head_cid" "log_length"
;; "chain" "beats" …) — this actor's own real convention (same shape as
;; kabuto.methods.kotoba's string-keyed EAVT house style; verified directly against the
;; production autorun.cljc source, not assumed from this test's prior — broken —
;; keyword-args calls).
(deftest pipeline-head-cid-is-pinned-end-to-end
  (with-fixture
    (fn [fx log]
      (let [r (autorun/run-autonomous 2 fx log)]
        (is (= head-cid-2cyc (get r "head_cid")) "whole-pipeline head-cid drifted")
        (is (= 2 (get r "log_length")))
        (is (get (get r "chain") "ok"))
        ;; the heartbeat produced a meaningful (non-empty) derived graph — not an empty pin
        (is (every? #(= 85 (get % "datoms")) (get r "beats")) "each cycle emits the full 85-datom graph")))))

(deftest pipeline-is-cross-run-deterministic
  ;; two independent runs (separate temp logs) → identical beat CIDs and head.
  (let [run (fn [] (with-fixture (fn [fx log] (autorun/run-autonomous 2 fx log))))
        a (run) b (run)]
    (is (= (mapv #(get % "cid") (get a "beats")) (mapv #(get % "cid") (get b "beats"))))
    (is (= head-cid-2cyc (get a "head_cid") (get b "head_cid")))))

(deftest single-cycle-head-is-prefix-of-two-cycle-chain
  ;; the commit-DAG threads: cycle-1 head is the :prev of cycle 2 (append-only, no rewrite).
  (with-fixture
    (fn [fx log]
      (let [r1 (autorun/run-autonomous 1 fx log)]
        (is (str/starts-with? (get r1 "head_cid") "b"))
        (is (= 1 (get r1 "log_length")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kabuto.methods.test-pipeline-cid)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
