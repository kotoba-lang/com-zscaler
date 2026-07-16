#!/usr/bin/env bb
;; kafun 花粉 — Murakumo-narrated digest tests (fail-open + G6/G8 invariants).
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_digest.cljc
(ns kafun.methods.test-digest
  (:require [kafun.methods.kafun-edn :as ke]
            [kafun.methods.remediate :as rem]
            [kafun.methods.digest :as dg]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/kafun/kotoba/seed.edn")
(defn- d [] (dg/digest-data (rem/assess (ke/stands seed-path))))

;; ── digest data (pure fold) ──────────────────────────────────────────────────

(deftest digest-data-tallies-routes
  (let [x (d)]
    (is (= 3 (get-in x [:bottlenecks :l3-reforest])) "3 reforest-priority stands")
    (is (= 1 (get-in x [:bottlenecks :l1-await-sapling])) "1 awaiting 無花粉苗木")
    (is (= 2 (get-in x [:bottlenecks :protected])) "2 protected-selective")
    (is (= 2 (:refused x)) "2 refused")
    (is (<= 3 (count (:top-priority x))) "top-priority list present")
    (is (number? (:throughput x)))))

;; ── template narration (the fail-open default) ───────────────────────────────

(deftest template-is-deterministic-and-restoration-framed
  (let [t1 (dg/template-narration (d))
        t2 (dg/template-narration (d))]
    (is (= t1 t2) "deterministic")
    (is (str/includes? t1 "主伐再造林") "names the L3-1 bottleneck")
    (is (str/includes? t1 "無花粉苗木") "names the L1-1 bottleneck")
    (is (str/includes? t1 "伐採も植林もせず") "G5 — kafun never cuts/plants, stated in words")
    (is (str/includes? t1 "復元") "撲滅 = restoration, stated")))

;; ── narrate: injected infer + FAIL-OPEN (G6) ─────────────────────────────────

(deftest narrate-uses-injected-infer
  (let [out (dg/narrate (d) {:infer (fn [_] "むらくも要約")})]
    (is (= "むらくも要約" out) "with a working infer, its output is used")))

(deftest narrate-fails-open-on-error
  (let [out (dg/narrate (d) {:infer (fn [_] (throw (ex-info "fleet offline" {})))})]
    (is (str/includes? out "kafun 花粉") "a throwing infer falls open to the template (G6)")
    (is (= out (dg/template-narration (d))))))

(deftest narrate-fails-open-on-blank
  (is (= (dg/template-narration (d)) (dg/narrate (d) {:infer (fn [_] "  ")}))
      "blank narration falls open to the template"))

(deftest narrate-without-infer-is-template
  (is (= (dg/template-narration (d)) (dg/narrate (d)))))

;; ── G6 Murakumo-only: non-loopback refused ───────────────────────────────────

(deftest murakumo-only-non-loopback-refused
  (is (thrown? Exception (dg/murakumo-infer "x" "evil.example.com"))
      "G6 — a non-loopback Murakumo host is refused"))

;; ── G8: dry-run only, never published ────────────────────────────────────────

(deftest digest-datoms-are-dry-run-only
  (let [ds (dg/digest-datoms (d) "narration text" "test-1")
        edn (pr-str ds)]
    (is (str/includes? edn ":dry-run"))
    (is (not (str/includes? edn ":published")) "G8 — :published is unrepresentable")
    (is (str/includes? edn ":kafun/derived"))
    (is (some #(= ":digest/status" (nth % 2)) ds))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-digest)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
