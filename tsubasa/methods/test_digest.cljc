#!/usr/bin/env bb
;; tsubasa 翼 — Murakumo digest tests (G6 fail-open, anti-dark).
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_digest.cljc
(ns tsubasa.methods.test-digest
  (:require [tsubasa.methods.digest :as d]
            [tsubasa.methods.analyze :as a]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private seed-path
  (str (fs/file (fs/parent (fs/absolutize *file*)) ".." "data" "seed-fares.kotoba.edn")))
(def ^:private rows (edn/read-string (slurp seed-path)))
(def ^:private analysis (a/analyze rows))
(def ^:private coverage (a/coverage rows))

(deftest template-digest-is-honest-and-anti-dark   ; G3 / G4 / G1
  (let [t (d/template-digest analysis coverage)]
    (is (string? t))
    (is (str/includes? t "CO₂"))                 ; emissions surfaced (G4)
    (is (str/includes? t "手数料は取らない"))     ; no inflow stated (G1)
    ;; no dark-pattern urgency language (G3)
    (doseq [bad ["今すぐ" "値上がり" "残りわずか" "急いで"]]
      (is (not (str/includes? t bad)) (str "anti-dark violated: '" bad "'")))))

(deftest digest-uses-murakumo-when-available
  (let [r (d/digest analysis coverage {:infer-fn (fn [_] "静かな観測の要約です。")})]
    (is (= "murakumo" (:source r)))
    (is (= "静かな観測の要約です。" (:text r)))))

(deftest digest-fails-open-to-template-when-murakumo-blank
  (let [r (d/digest analysis coverage {:infer-fn (fn [_] "")})]
    (is (= "template" (:source r)))
    (is (str/includes? (:text r) "路線"))))

(deftest digest-fails-open-to-template-when-murakumo-nil
  (let [r (d/digest analysis coverage {:infer-fn (fn [_] nil)})]
    (is (= "template" (:source r)))))

(deftest digest-fails-open-when-murakumo-throws   ; never blocks the heartbeat
  (let [r (d/digest analysis coverage {:infer-fn (fn [_] (throw (ex-info "down" {})))})]
    (is (= "template" (:source r)))
    (is (not (str/blank? (:text r))))))

(deftest prompt-forbids-pushy-language   ; G3 baked into the prompt
  (let [msgs (d/build-messages analysis coverage)
        sys (:content (first msgs))]
    (is (str/includes? sys "煽り"))
    (is (str/includes? sys "禁止"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-digest)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
