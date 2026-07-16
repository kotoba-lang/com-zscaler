(ns aburi.tests.test-analyze
  "aburi 炙り — analyzer + Datom-emit tests (ADR-2606161630). 1:1 Clojure port of
  tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - G1 — own-data only: no individual / PII / biometric / raw-identifier attribute anywhere
    - G8 — the datom emitter's fixed attr allowlist holds no credential / raw-identifier attr
    - G3/N3 — collectors are catalogued facts (public provenance + org), never aburi verdicts
    - edge-primary (N1): collector tracking-exposure is the integral of incident inbound :flows-to
      × disclosed sensitivity — recomputed independently and asserted equal; and NO stored
      per-collector :bond/* / :aburi/score-of-collector key exists (edge-primary only, G2)
    - the top net-exposure node is a collector receiving ≥1 sensitive/critical flow
    - surface_leak is the two-hop integral (grant × downstream permission flow), ranking a surface
    - the reciprocity gap = leaking permissions with no :routes-to relief
    - net_exposure = exposure − relief
    - datom_emit emits ground :add + flagged-transient derived; output is deterministic

  House style mirrors shiori.tests.test-analyze (clojure.test deftest/is)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [aburi.methods.analyze :as analyze]
            [aburi.methods.datom-emit :as datom-emit]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-tracker-exposure.kotoba.edn"))

(defn load-seed [] (datom-emit/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 30) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 40) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":organism/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":surface" ":permission" ":collector" ":datatype" ":relief"} kinds)
          (str "missing core kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-g1-own-data-no-other-person
  (testing "G1: own-data only — no individual / PII / biometric / raw-identifier attr anywhere.
    The graph models SURFACES/PERMISSIONS/COLLECTORS/DATA-TYPES, never a person or a raw value."
    (let [{:keys [nodes]} (load-seed)
          banned [":person/id" ":user/id" ":email" ":phone" ":imei" ":idfa" ":gaid"
                  ":device/serial" ":name/full" ":geo/lat" ":geo/lon" ":biometric"
                  ":profile" ":individual" ":raw-value" ":value/raw"]]
      (doseq [[nid n] nodes]
        (doseq [b banned]
          (is (not (contains? n b))
              (str "G1 violation: per-person / raw-identifier attr " b " on " nid)))))))

(deftest test-g8-no-credential-or-raw-id-attr-in-emit-schema
  (testing "G8: the datom emitter can only project a fixed attr allowlist; no credential / raw-id
    attribute exists in it, so none can ever be written to the substrate."
    (let [forbidden ["password" "token" "secret" "credential" "cookie/value" "idfa" "gaid"
                     "imei" "raw" "pan" "email"]]
      (doseq [a (concat datom-emit/node-attrs datom-emit/edge-attrs)]
        (let [low (str/lower-case a)]
          (doseq [f forbidden]
            (is (not (str/includes? low f))
                (str "G8 violation: emit attr " a " looks like a credential / raw id"))))))))

(deftest test-g3-collectors-are-catalogued-facts-not-verdicts
  (testing "G3/N3: every collector carries a PUBLIC catalogue provenance (:collector/catalog) and an
    org — it is a disclosed fact, never an aburi verdict. No collector carries a judgement attr."
    (let [{:keys [nodes]} (load-seed)
          cols (filter #(= ":collector" (get % ":organism/kind")) (vals nodes))
          valid #{":exodus" ":apple-privacy" ":play-data-safety" ":sellers-json" ":iab"}]
      (is (seq cols) "no collectors in seed")
      (doseq [c cols]
        (is (contains? valid (get c ":collector/catalog"))
            (str "collector " (get c ":organism/id") " lacks a public catalogue provenance (G3/G5)"))
        (is (get c ":collector/org")
            (str "collector " (get c ":organism/id") " lacks a disclosed org"))
        (doseq [verdict [":aburi/verdict" ":guilty" ":wrongdoing" ":score-of-collector"]]
          (is (not (contains? c verdict))
              (str "G3 violation: collector carries a verdict attr " verdict)))))))

(deftest test-edge-primary-exposure-integral
  (testing "N1: collector tracking-exposure MUST equal the independent integral of incident inbound
    :flows-to 縁 × disclosed permission-sensitivity weight."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          expect (reduce
                  (fn [m e]
                    (if (= ":flows-to" (get e ":en/kind"))
                      (let [dst (get e ":en/to")
                            sens (get-in nodes [(get e ":en/from") ":permission/sensitivity"])
                            w (get analyze/sensitivity-weight sens 0.5)]
                        (update m dst (fnil + 0.0)
                                (* (double (get e ":en/load")) w)))
                      m))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["exposure" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["exposure" nid]) " != " v)))
      ;; G2: no stored per-collector score on any ground node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":aburi/score-of-collector"))
                       (keys n))))))))

(deftest test-top-tracker-is-a-real-collector-with-sensitive-inflow
  (testing "the top net-exposure node must be a collector that receives ≥1 :sensitive/:critical
    permission's flow (the lens is not mis-weighted toward a low-sensitivity collector)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          top (key (apply max-key val (get res "net_exposure")))]
      (is (= ":collector" (get-in nodes [top ":organism/kind"]))
          (str "top tracker " top " is not a collector"))
      (let [incident-sens (set (for [e edges
                                     :when (and (= ":flows-to" (get e ":en/kind"))
                                                (= top (get e ":en/to")))]
                                 (get-in nodes [(get e ":en/from") ":permission/sensitivity"])))]
        (is (seq (clojure.set/intersection incident-sens #{":critical" ":sensitive"}))
            (str "top tracker " top " receives no sensitive/critical flow — lens mis-weighted"))))))

(deftest test-surface-leak-is-two-hop-and-ranks-a-real-surface
  (testing "surface_leak is the two-hop integral (grant × downstream permission flow); the leakiest
    node must be a surface, and Facebook/Google (heavy ToS/ad-id grants) should rank high."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)]
      (is (seq (get res "surface_leak")) "no surface leak computed")
      (let [top (key (apply max-key val (get res "surface_leak")))]
        (is (= ":surface" (get-in nodes [top ":organism/kind"]))
            (str "top leak " top " is not a surface")))
      ;; independent recompute of the two-hop integral
      (let [perm-leak (reduce (fn [m e]
                                (if (= ":flows-to" (get e ":en/kind"))
                                  (update m (get e ":en/from") (fnil + 0.0) (double (get e ":en/load")))
                                  m))
                              {} edges)
            expect (reduce (fn [m e]
                             (if (= ":grants" (get e ":en/kind"))
                               (update m (get e ":en/from") (fnil + 0.0)
                                       (* (double (get e ":en/load"))
                                          (get perm-leak (get e ":en/to") 0.0)))
                               m))
                           {} edges)]
        (doseq [[nid v] expect]
          (is (< (Math/abs (- (get-in res ["surface_leak" nid]) v)) 1e-9)
              (str nid ": leak mismatch")))))))

(deftest test-reciprocity-gap-are-unrouted-leaking-permissions
  (testing "a permission that leaks (outbound :flows-to > 0) but has no :routes-to relief is surfaced
    as the reciprocity gap (route coverage = 0)."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)]
      (doseq [nid (get res "unrouted_permissions")]
        (is (= ":permission" (get-in nodes [nid ":organism/kind"])))
        (is (= 0.0 (get (get res "route_coverage") nid 0.0)))
        (is (> (get (get res "permission_leak") nid 0.0) 0.0)))
      ;; the seed deliberately leaves tos-data-sharing / coarse-location / microphone / photos unrouted
      (is (some #{"ax.perm.tos-data-sharing"} (get res "unrouted_permissions"))
          "expected the ToS data-sharing clause to be an unrouted reciprocity gap"))))

(deftest test-net-exposure-is-gross-minus-relief
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (doseq [[cid x] (get res "exposure")]
      (is (< (Math/abs (- (get-in res ["net_exposure" cid])
                          (- x (get (get res "relief") cid 0.0)))) 1e-9)))))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":collector/catalog") "collector provenance missing from datoms (G3/G5)")
    (is (str/includes? out ":en/load") "edge attribute datoms missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/tracking-exposure"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]")
            (str "derived readout not flagged transient: " line))))
    (is (str/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [{n1 :nodes e1 :edges} (load-seed)
        a (datom-emit/emit n1 e1 (analyze/analyze n1 e1) 1)
        {n2 :nodes e2 :edges} (load-seed)
        b (datom-emit/emit n2 e2 (analyze/analyze n2 e2) 1)]
    (is (= a b) "Datom emit is not deterministic")))
