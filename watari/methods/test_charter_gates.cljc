(ns watari.methods.test-charter-gates
  "watari 渡り — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). watari is the live ship + aircraft
  real-time position KG — situational awareness, NOT surveillance and NEVER a targeting aid; a
  craft is a craft, not a person (no person-tracking / no pattern-of-life). Its 9 gates are
  declared in the manifest `gates` dict and the position sourcing is enum-bounded across the 4
  central AT-Proto lexicons (XRPC procedures) at 00-contracts/lexicons/com/etzhayyim/watari/.
  This suite pins them so a future cell wave cannot silently drift them:

    G1  public transponder broadcasts ONLY — recordFix.source ∈ {ais, adsb} (no covert/radar/
        satellite tracking source representable)
    G2  situational-awareness, NOT targeting — gate text pins the not-targeting framing (doc-drift)
    G4  no person-tracking / no pattern-of-life — gate text pins the person-tracking prohibition
    G5  sourcing-honesty — every fix/leg/lane carries sourcing ∈ {authoritative, representative,
        synthesized}
    bounded vocab — craft ∈ {vessel, aircraft}; lane ∈ {strait, canal, sea-lane, air-corridor,
        approach} (chokepoint → safety framing)

  Reads manifest + central lexicons via cheshire (procedure-schema-aware). It weakens no gate;
  it asserts them. No-server-key + Murakumo-only (G6) + outward-gating (G7) are manifest-level."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; watari/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/watari"))
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

(defn- props-of [doc]
  (let [main (get-in doc ["defs" "main"])]
    (reduce merge {}
            (keep #(get % "properties")
                  [(get main "record")
                   (get-in main ["input" "schema"])
                   (get-in main ["output" "schema"])
                   main]))))
(defn- enum-of [doc field] (set (get-in (props-of doc) [field "enum"])))
(defn- gate-map []
  (let [g (get (manifest) "gates")] (or (get g "gates") g)))
(defn- gate-text [g] (str/lower-case (str (get (gate-map) g))))

(def SOURCING #{"authoritative" "representative" "synthesized"})

;; ── 9 gates declared ──
(deftest all-9-gates-declared
  (let [nums (->> (keys (gate-map)) (keep #(second (re-matches #"G(\d+)" %)))
                  (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 10)) nums) "manifest must declare G1–G9")))

;; ── G1 — public transponder broadcasts ONLY (AIS + ADS-B) ──
(deftest g1-public-broadcast-only
  (is (= #{"ais" "adsb"} (enum-of (lex "recordFix") "source"))
      "G1: recordFix.source must be {ais, adsb} (public transponder broadcasts only)"))

;; ── G2/G4 — situational-awareness not targeting; no person-tracking (doc-drift guard) ──
(deftest g2-g4-not-targeting-no-person-tracking
  (is (str/includes? (gate-text "G2") "targeting")
      "G2: gate text must pin the not-targeting framing")
  (let [g4 (gate-text "G4")]
    (is (str/includes? g4 "person-tracking") "G4: gate text must pin the no-person-tracking rule")
    (is (str/includes? g4 "pattern-of-life") "G4: gate text must pin the no-pattern-of-life rule")))

;; ── G5 — sourcing-honesty on every fix/leg/lane ──
(deftest g5-sourcing-honesty
  (doseq [n ["recordFix" "recordLeg" "registerLane"]]
    (is (= SOURCING (enum-of (lex n) "sourcing"))
        (str "G5: " n ".sourcing must be {authoritative, representative, synthesized}"))))

;; ── bounded vocab — craft + lane kinds (chokepoint → safety framing) ──
(deftest bounded-craft-and-lane
  (is (= #{"vessel" "aircraft"} (enum-of (lex "registerCraft") "kind"))
      "craft kind is {vessel, aircraft}")
  (is (= #{"strait" "canal" "sea-lane" "air-corridor" "approach"} (enum-of (lex "registerLane") "kind"))
      "lane kind is the bounded chokepoint set"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'watari.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
