(ns kabuto.methods.test-charter-gates
  "kabuto 兜 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). kabuto is the world public-company
  supply-chain KG — a RESILIENCE map, never a target-list. Its G1–G12 gates are declared in the
  manifest `gates` dict and encoded as const/enum across the 5 central AT-Proto lexicons (XRPC
  procedures) at 00-contracts/lexicons/com/etzhayyim/kabuto/. This suite pins them so a future
  cell wave cannot silently drift them:

    G3  aggregate-first — publishIntelReport.aggregateFirstNotice const true
    G4  no adjudication — publishIntelReport.nonAdjudicatingNotice const true (UPL boundary)
    G5  sourcing-honesty — every node/edge/report carries sourcing ∈ {authoritative,
        representative, synthesized}
    G2  resilience-not-interdiction — the viz kind is {supply-graph, concentration-heatmap,
        jurisdiction-map}; no target/raid/weakness map representable

  Reads manifest + central lexicons via cheshire (string keys). Properties are gathered from a
  record OR an XRPC procedure input/output schema. It weakens no gate; it asserts them.
  No-server-key + Murakumo-only (G6) + outward-gating (G7/G11) are manifest-level and untouched."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; kabuto/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/kabuto"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
  (let [e (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))
        gm (into {} (map (fn [g] [(:gate/id g) g]) (:actor/gates e)))]
    {"constitutionalGates" {"gates" gm}
     "gates" gm
     "nonGoals" (:actor/non-goals e)
     "cells" (:actor/cells e)
     "name" (:actor/id e)
     "purpose" (:actor/purpose e)
     "tier" "Tier-B"
     "status" (some-> (:actor/status e) name)}))))

;; gather properties from a record OR a procedure input/output schema
(defn- props-of [doc]
  (let [main (get-in doc ["defs" "main"])]
    (reduce merge {}
            (keep #(get % "properties")
                  [(get main "record")
                   (get-in main ["input" "schema"])
                   (get-in main ["output" "schema"])
                   main]))))
(defn- const-of [doc field] (get-in (props-of doc) [field "const"]))
(defn- enum-of [doc field] (set (get-in (props-of doc) [field "enum"])))

(def SOURCING #{"authoritative" "representative" "synthesized"})

;; ── G1–G12 declared ──
(deftest all-12-gates-declared
  (let [nums (->> (keys (get (manifest) "gates"))
                  (keep #(second (re-matches #"G(\d+)" %)))
                  (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 13)) nums) "manifest must declare G1–G12")))

;; ── G3/G4 — aggregate-first + non-adjudicating intel report ──
(deftest g3-g4-aggregate-non-adjudicating
  (let [r (lex "publishIntelReport")]
    (is (= true (const-of r "aggregateFirstNotice")) "G3: aggregateFirstNotice const true")
    (is (= true (const-of r "nonAdjudicatingNotice")) "G4: nonAdjudicatingNotice const true")))

;; ── G5 — sourcing-honesty on every node/edge/report ──
(deftest g5-sourcing-honesty
  (doseq [n ["publishIntelReport" "registerSupplyEdge" "publishSupplyChainViz"]]
    (is (= SOURCING (enum-of (lex n) "sourcing"))
        (str "G5: " n ".sourcing must be {authoritative, representative, synthesized}"))))

;; ── G2 — resilience viz, never a target/raid map ──
(deftest g2-resilience-not-target
  (let [k (enum-of (lex "publishSupplyChainViz") "kind")]
    (is (= #{"supply-graph" "concentration-heatmap" "jurisdiction-map"} k)
        (str "G2: viz kind must be the resilience set, got " k))
    (is (not (some #(re-find #"(?i)target|raid|weakness|kill|strike" %) k))
        "G2: no target/raid/weakness map kind representable")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'kabuto.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
