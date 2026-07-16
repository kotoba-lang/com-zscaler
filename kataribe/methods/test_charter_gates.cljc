(ns kataribe.methods.test-charter-gates
  "kataribe — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
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
     "status" (some-> (:actor/status e) name)}))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- consts [doc]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x "const"))
                                   (swap! acc assoc parent (get x "const")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-13-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 14))))
        "manifest must declare G1–G13")))

;; ── G6 — non-eschatological + cross-doctrinal (no monopoly, no transubstantiation) ──
(deftest test-g6-non-eschatological-cross-doctrinal
  (is (= true (get (consts (lex "communityChronicleAttestation")) "nonEschatologicalAttested"))
      "G6: community chronicle must attest non-eschatological")
  (let [dc (consts (lex "doctrineCommentaryPublishing"))]
    (is (= false (get dc "doctrinalMonopolyAttested")) "G6: no doctrinal monopoly")
    (is (= false (get dc "transubstantiationDoctrineImposed")) "G6: no transubstantiation imposed (Reformed memorial view)")))

;; ── G12 — Murakumo-only, community-need-based translation (validates ADR-2606161536) ──
(deftest test-g12-murakumo-only-translation
  (let [c (consts (lex "translationAttestation"))]
    (is (= "murakumo-only" (get c "translationProvider")) "G12: translation provider must be const murakumo-only")
    (is (= false (get c "commercialAiTranslationToolUsed")) "G12: no commercial AI translation tool")
    (is (= true (get c "communityNeedBasedAttested")) "G12: translation is community-need-based")))

;; ── the quarterly-review const ledger (ads / paywall / commercial platform / surveillance) ──
(deftest test-silen-review-ledger
  (let [c (consts (lex "silenKataribeReview"))
        expected {"adSupportedRevenueUsdMillicents" 0
                  "apocalypticFramingEventsCount" 0
                  "commercialPublishingPlatformPenetrationPct" 0
                  "singleDoctrinalMonopolyEventsCount" 0
                  "commercialMarketDrivenTranslationPriorityEventsCount" 0
                  "surveillanceInvestigativeJournalismEventsCount" 0
                  "mandatoryPaywallEventsCount" 0
                  "commercialAiTranslationToolUsageCount" 0
                  "kataribeVocationFlowCompliantRatioPctIntegerHundredths" 10000
                  "publicationsApacheRiderLicensedPct" 10000}]
    (doseq [[field want] expected]
      (is (= (get c field) want) (str "silenKataribeReview." field " must be const " want ", got " (pr-str (get c field)))))))

;; ── G8 — whistleblower channel is NOT surveillance investigative journalism ──
(deftest test-g8-whistleblower-not-surveillance
  (let [c (consts (lex "whistleblowerReport"))]
    (is (= false (get c "surveillanceInvestigativeJournalismAttested")) "G8: not surveillance investigative journalism")
    (is (= false (get c "sensationalizedFramingAttested")) "G8: no sensationalized framing")))

;; ── G10 — whistleblower report encrypted + chigiri cross-emit ──
(deftest test-g10-whistleblower-encrypted
  (let [req (required-union (lex "whistleblowerReport"))]
    (doseq [field ["encryptedPayloadCid" "chigiriIpLicenseClaimCrossEmitCid"]]
      (is (contains? req field) (str "G10: whistleblowerReport must require " field)))))
