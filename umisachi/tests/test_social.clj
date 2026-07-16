#!/usr/bin/env bb
(ns umisachi.tests.test-social
  "umisachi 海幸 — restoration-advisory composer + Charter/G1 gate invariants (ADR-2606074200).

  Guards the public-facing delivery layer: aggregate-first restoration framing (G1 — never a
  catch-target list), the Charter Rider §2 + G1 deny-scan on every body, deterministic compose,
  and dry-run / no-server-key (no live posting on the clj surface).

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_social.clj"
  (:require [umisachi.methods.social :as s]
            [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn") .getAbsolutePath))
(defn- posts [limit]
  (let [[nodes edges] (a/load-file (seed-path))]
    (s/compose nodes (a/analyze nodes edges) limit)))

(deftest charter-rider-clean-blocks-deny-and-g1
  (is (s/charter-rider-clean "routed to RESTORATION, aggregate-first"))
  (is (not (s/charter-rider-clean "best spot to net the last bluefin")))   ; G1 catch-target
  (is (not (s/charter-rider-clean "fishing ground at 35.6N 139.7E")))      ; G1 coordinates
  (is (not (s/charter-rider-clean "weapon design for the fleet"))))        ; Charter §2

(deftest first-post-is-the-aggregate-restoration-map
  (let [[id kind text] (first (posts 9))]
    (is (= "umisachi.report.restoration-map" id))
    (is (= "intel-report" kind))
    (is (str/includes? text "restoration map, never a catch-target list"))
    (is (str/includes? text "aggregate-first"))))

(deftest every-body-is-restoration-framed-and-clean
  (doseq [[_ _ text] (posts 9)]
    (is (str/includes? (str/lower-case text) "restoration") "every body is restoration-framed (G1)")
    (is (s/charter-rider-clean text) "every body passes the Charter §2 + G1 deny-scan")
    ;; no coordinate token leaks into any body
    (is (not (re-find #"\b\d+\.\d+[NSEW]\b" text)) "no lat/lon coordinate in the body")))

(deftest compose-is-deterministic-and-load-sorted
  (is (= (mapv first (posts 9)) (mapv first (posts 9))))
  ;; the advisories (after the aggregate report) are depletion-load desc → sanma leads
  (let [advisories (rest (posts 9))]
    (is (= :stock.sanma-pacific (ffirst advisories)) "highest-depletion bearer leads")))

(deftest post-record-shape-and-rkey
  (let [rec (s/post-record "routed to RESTORATION" ["en"])]
    (is (= "app.bsky.feed.post" (get rec "$type")))
    (is (<= (count (get rec "text")) 300))
    (is (str/ends-with? (get rec "createdAt") "Z")))
  ;; rkey is a stable sha256-derived key, namespaced to the actor
  (is (= (s/rkey "x") (s/rkey "x")))
  (is (str/starts-with? (s/rkey "x") "umisachi-")))

(deftest limit-caps-composed-count
  ;; compose yields the aggregate report + up to (limit-1) advisories = ≤ limit posts.
  (is (<= (count (posts 3)) 3))
  (is (= "umisachi.report.restoration-map" (ffirst (posts 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-social)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
