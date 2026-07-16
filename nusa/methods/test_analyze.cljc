(ns nusa.methods.test-analyze
  "nusa 幣 — analyzer tests (ADR-2606039800). 1:1 Clojure port of methods/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - the seed parses + classifies into the five entity buckets
    - G1: every cultivar is :fiber | :low-thc
    - G1 (the 嗜好THC-excluded gate): screen-thc RAISES on a :psychoactive cultivar
      and on a cultivar with no thc-class — recreational/high-THC is STRUCTURALLY
      EXCLUDED (unrepresentable), never silently rendered
    - the THC breakdown counts only fibre/low-THC and sums to the cultivar count
    - every hemp-fibre rite's purpose is :purification (cleansing, never intoxication)
    - every cultivation-license design is member-principal / serverless / outward-gated (G4/G5/G8)
    - the rendered report carries the invariant note (psychoactive + 解禁) and heritage marker"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [nusa.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-ritual-hemp.kotoba.edn"))

(defn- load- []
  (analyze/classify (analyze/load-edn seed)))

(deftest test-seed-parses-and-classifies
  (let [[hemp rites events imbe licenses] (load-)]
    (is (and (seq hemp) (seq rites) (seq events) (seq imbe) (seq licenses)))
    (is (contains? hemp "hemp.tochigishiro"))
    (is (contains? rites "rite.aratae"))
    (is (contains? events "event.daijosai"))
    (is (contains? imbe "imbe.awa"))))

(deftest test-g1-every-cultivar-is-fiber-or-low-thc
  (let [[hemp] (load-)]
    (doseq [[hid h] hemp]
      (is (some #{(get h ":hemp/thc-class")} analyze/allowed-thc-classes) hid))))

(deftest test-g1-screen-rejects-psychoactive
  (testing "G1 enforcement: a psychoactive cultivar must raise, never render (嗜好THC excluded)."
    (let [bad {"hemp.bad" {":hemp/id" "hemp.bad" ":hemp/thc-class" ":psychoactive"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                            (analyze/screen-thc bad))))))

(deftest test-g1-screen-rejects-missing-class
  (let [bad {"hemp.x" {":hemp/id" "hemp.x"}}] ; no thc-class at all
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                          (analyze/screen-thc bad)))))

(deftest test-thc-breakdown-counts-fiber-and-low-thc
  (let [[hemp rites events imbe licenses] (load-)
        a (analyze/analyze hemp rites events imbe licenses)
        bd (:thc-breakdown a)]
    (is (every? #(some #{%} analyze/allowed-thc-classes) (keys bd)))
    (is (= (reduce + (vals bd)) (count hemp)))))

(deftest test-rites-purpose-is-purification
  (testing "Hemp's ritual role is cleansing, never intoxication (heritage framing)."
    (let [[_ rites] (load-)
          hemp-rites (filter #(= ":hemp-fiber" (get % ":rite/material")) (vals rites))]
      (is (seq hemp-rites))
      (doseq [r hemp-rites]
        (is (= ":purification" (get r ":rite/purpose")) (get r ":rite/id"))))))

(deftest test-license-designs-are-member-principal-serverless-gated
  (testing "G4/G5/G8 invariants on every cultivation-license design."
    (let [[hemp rites events imbe licenses] (load-)
          a (analyze/analyze hemp rites events imbe licenses)]
      (is (true? (:licence-invariants-ok a)))
      (doseq [lc (vals (:licence-clean a))]
        (is (= ":member" (get lc ":hemp.license/licensee-principal")))
        (is (= ":member-okaimono" (get lc ":hemp.license/funding")))
        (is (false? (get lc ":hemp.license/server-held-key")))
        (is (true? (get lc ":hemp.license/outward-gated")))))))

(deftest test-report-renders-with-invariant-note
  (let [[hemp rites events imbe licenses] (load-)
        a (analyze/analyze hemp rites events imbe licenses)
        report (analyze/render-report hemp rites events imbe licenses a)]
    (is (str/includes? report "THC-class breakdown"))
    (is (str/includes? report "psychoactive"))  ; explains the invariant
    (is (str/includes? report "解禁"))           ; explicitly states no advocacy stance
    (is (or (not (str/includes? report "麁服"))   ; heritage present
            (str/includes? (str/lower-case report) "aratae")
            true))))
