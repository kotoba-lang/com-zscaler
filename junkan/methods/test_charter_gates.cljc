(ns junkan.methods.test-charter-gates
  "junkan 循環 — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). junkan is a societal-systems-dynamics
  OBSERVER — it reads which feedback loops are spinning 好循環/悪循環 + Meadows leverage points,
  and it may ONLY look, never touch. Its G1..G13 gates are const-encoded across the 5 central
  AT-Proto lexicons at 00-contracts/lexicons/com/etzhayyim/junkan/, and the headline gate (G4 no
  actuation) is additionally enforced by the ABSENCE of any outward-channel cell in the manifest.
  This suite pins both so a future R-phase cell wave cannot silently drift them:

    G4  ANALYSIS-ONLY / NO ACTUATION — every finding actuationTaken const false; review
        actuationEventsCount + outwardChannelAcquiredCount const 0; AND no dispatch/post/mention/
        email/transaction/actuator cell exists in the manifest (enforced by absence)
    G5  no causal overclaim — causalLoopFinding.hypothesisOnly const true; review zero
    G11 no prescription / no prediction-as-fact — leveragePointFinding.prescriptionGiven const false
    G6  aggregate-only / no individual modeling — societalStockObservation.individualModeled false
    G3  passive-only collection — societalStockObservation.passiveOnlyAttested const true
    G7  non-eschatological framing — regimeShiftEvent.framingNonEschatological const true
    G9  append-only / immutable — societalStockObservation.mutable const false
    G1  Charter-Rider scan pass on every finding; Murakumo-only attested in review

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  Touches neither the substrate-wide no-server-key nor Murakumo-only — junkan holds no key
  (it has no outward channel at all) and the review pins murakumoOnlyComplianceAttested."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; junkan/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/junkan"))
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

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- const-of [doc field] (get-in (record-node doc) ["properties" field "const"]))

;; ── G4 — ANALYSIS-ONLY / NO ACTUATION (const fields + zero counters + cell-absence) ──
(deftest g4-analysis-only-no-actuation
  (doseq [n ["causalLoopFinding" "leveragePointFinding" "regimeShiftEvent"]]
    (is (= false (const-of (lex n) "actuationTaken"))
        (str "G4: " n ".actuationTaken const false")))
  (let [r (lex "silenJunkanReview")]
    (is (= 0 (const-of r "actuationEventsCount")) "G4: review actuationEventsCount const 0")
    (is (= 0 (const-of r "outwardChannelAcquiredCount")) "G4: review outwardChannelAcquiredCount const 0")))

;; ── G4 structural — NO outward-channel cell exists in the manifest (enforced by absence) ──
(deftest g4-no-outward-channel-cell
  (let [cells (get (manifest) "cells")
        forbidden #"(?i)dispatch|post|mention|email|smtp|transact|actuat|broadcast|submit|send|publish"
        bad (filter (fn [c]
                      (let [tag (str (get c "name") " " (get c "module"))]
                        (re-find forbidden tag)))
                    cells)]
    (is (empty? bad)
        (str "G4: junkan must have NO outward-channel cell; found " (mapv #(get % "name") bad)))))

;; ── G5 — no causal overclaim ──
(deftest g5-no-causal-overclaim
  (is (= true (const-of (lex "causalLoopFinding") "hypothesisOnly"))
      "G5: causalLoopFinding.hypothesisOnly const true")
  (is (= 0 (const-of (lex "silenJunkanReview") "causalOverclaimEventsCount"))
      "G5: review causalOverclaimEventsCount const 0"))

;; ── G11 — no prescription / prediction-as-fact ──
(deftest g11-no-prescription
  (is (= false (const-of (lex "leveragePointFinding") "prescriptionGiven"))
      "G11: leveragePointFinding.prescriptionGiven const false")
  (is (= 0 (const-of (lex "silenJunkanReview") "prescriptionGivenEventsCount"))
      "G11: review prescriptionGivenEventsCount const 0"))

;; ── G6 aggregate-only + G3 passive-only + G9 immutable ──
(deftest g6-g3-g9-stock-invariants
  (let [s (lex "societalStockObservation")]
    (is (= false (const-of s "individualModeled")) "G6: individualModeled const false")
    (is (= true (const-of s "passiveOnlyAttested")) "G3: passiveOnlyAttested const true")
    (is (= false (const-of s "mutable")) "G9: mutable const false (append-only)"))
  (is (= 0 (const-of (lex "silenJunkanReview") "individualModelingEventsCount"))
      "G6: review individualModelingEventsCount const 0"))

;; ── G7 — non-eschatological framing on a regime shift ──
(deftest g7-non-eschatological
  (is (= true (const-of (lex "regimeShiftEvent") "framingNonEschatological"))
      "G7: regimeShiftEvent.framingNonEschatological const true"))

;; ── G1 Charter-Rider scan + Murakumo-only attested ──
(deftest g1-charter-rider-and-murakumo
  (doseq [n ["causalLoopFinding" "leveragePointFinding" "regimeShiftEvent" "societalStockObservation"]]
    (is (= true (const-of (lex n) "charterRiderScanPass"))
        (str "G1: " n ".charterRiderScanPass const true")))
  (is (= true (const-of (lex "silenJunkanReview") "murakumoOnlyComplianceAttested"))
      "Murakumo-only: review murakumoOnlyComplianceAttested const true"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
