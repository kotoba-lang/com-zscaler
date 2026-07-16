(ns kaname.tests.test-sos
  "kaname 要 — leverage-synthesis tests (ADR-2606172100). Verifies the constitutional + mathematical
  invariants empirically:
    - the synthetic seed parses into a multilayer graph (nodes + domain-tagged 縁)
    - N1/G4: leverage is an integral of incident 縁, computed on read (no stored score)
    - the 要 = argmax L is the cross-domain Accreditation Interface
    - THE DISCRIMINATOR: concentration ALONE is not leverage — a single-domain hoarder
      (Capital Concentrator) has higher concentration than the Doctrine instrument yet LOWER
      leverage (V=1); and an already-OPEN commons scores leverage 0 despite high concentration
    - rank is deterministic (tie-break by id) and drops non-positive values
    - the report carries the G1/G2 framing (map-not-target / opening-only)"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

#?(:clj
   (defn- load- []
     (let [{:keys [nodes node-order edges]} (sos/load-file* seed)]
       [nodes node-order edges])))

(defn- approx [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest test-seed-parses
  (let [[nodes _ edges] (load-)]
    (is (seq nodes))
    (is (seq edges))
    (is (contains? nodes "kaname.if.accred"))
    (is (contains? nodes "kaname.ent.cap-hoard"))
    (is (= ":sos/interface" (get-in nodes ["kaname.if.accred" ":organism/kind"])))
    (testing "source-actors parse as a vector (cross-actor provenance)"
      (is (vector? (get-in nodes ["kaname.if.accred" ":sos/source-actors"]))))))

(deftest test-domain-versatility
  (testing "versatility V = # distinct domains a node bears load in"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)]
      ;; accred spans economy/ai/organization/ideology + politics (couple) = 5
      (is (= 5 (get-in res [:V "kaname.if.accred"])))
      ;; the capital hoarder lives in ONE domain only
      (is (= 1 (get-in res [:V "kaname.ent.cap-hoard"]))))))

(deftest test-kaname-point-is-the-interface
  (testing "the 要 = argmax leverage = the cross-domain Accreditation Interface"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)
          kp (sos/kaname-point res nodes)]
      (is (= "kaname.if.accred" (first kp)))
      ;; L = C·(V/D)·(1+B)·(1−open). Adding the :energy domain (ADR-2606212000) took D 10→11,
      ;; rescaling every L by 10/11 uniformly (11.7 → 10.6363…). The argmax — the 要 — is invariant.
      (is (approx 10.636363636363637 (nth kp 2))))))

(deftest test-concentration-alone-is-not-leverage
  (testing "THE SoS discriminator — high concentration without versatility is NOT the 要"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)
          C  (:C res)
          L  (:leverage res)]
      ;; the hoarder out-concentrates the doctrine instrument …
      (is (> (get C "kaname.ent.cap-hoard") (get C "kaname.inst.doctrine")))
      ;; … yet has LOWER leverage (V=1 vs spanning domains)
      (is (< (get L "kaname.ent.cap-hoard") (get L "kaname.inst.doctrine"))))))

(deftest test-openness-discount
  (testing "an already-OPEN position scores leverage 0 even with high concentration (nothing to dissolve)"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)]
      ;; open-commons has the 2nd-highest concentration in the whole graph …
      (is (> (get-in res [:C "kaname.if.open-commons"]) 1.5))
      ;; … but :sos/open? true ⇒ leverage 0
      (is (approx 0.0 (get-in res [:leverage "kaname.if.open-commons"]))))))

(deftest test-leverage-is-on-read-not-stored
  (testing "N1/G4 — leverage lives on edges; there is no stored per-entity score in the seed"
    (let [[nodes _ _] (load-)]
      (is (every? (fn [[_ n]] (not (contains? n ":kaname/score-of-entity"))) nodes))
      (is (every? (fn [[_ n]] (not (contains? n ":bond/leverage"))) nodes)))))

(deftest test-rank-deterministic
  (testing "rank sorts by (-value, id) and drops non-positive"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)
          r (sos/rank (:leverage res) nodes)]
      (is (= "kaname.if.accred" (first (first r))))
      (is (apply >= (map #(nth % 2) r)))
      (is (every? #(> (nth % 2) 0.0) r)))))

(deftest test-report-framing
  (testing "the report carries the constitutional framing"
    (let [[nodes _ edges] (load-)
          res (sos/leverage nodes edges)
          md (sos/report-md nodes edges res)]
      (is (str/includes? md "NEVER a target-list"))
      (is (str/includes? md "OPENING"))
      (is (str/includes? md "要"))
      (is (str/includes? md "Versatility discriminator")))))
