(ns ossekai.methods.test-charter-gates
  "ossekai — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
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
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir name))))

(defn- consts [doc]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x "const")) (swap! acc assoc parent (get x "const")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-15-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 16))))))

;; ── no re-engagement after opt-out + no commercial CRM/intel software ──
(deftest test-no-reengagement-no-commercial-crm
  (let [c (consts (lex "silenOssekaiReview.json"))]
    (is (= (get c "reEngagementAfterOptOutCount") 0))
    (is (= (get c "commercialIntelCrmSoftwarePenetrationPct") 0))
    (is (= (get c "framingAuditWellbecomingPreservationCompliant") true))))

;; ── opt-out is immediate ──
(deftest test-unsubscribe-effective-immediately
  (is (= (get (consts (lex "unsubscribeRecord.json")) "effectiveImmediatelyAttested") true)))

;; ── G15 — every emitted post: mute/block check + framing audit + signed AS ossekai ──
(deftest test-g15-feed-post-mute-block-and-framing
  (let [c (consts (lex "feedPostAttestation.json"))]
    (is (= (get c "muteBlockCheckPass") true))
    (is (= (get c "framingAuditPass") true))
    (is (= (get c "senderDidConst") "did:web:ossekai.etzhayyim.com"))
    (is (contains? (required-union (lex "feedPostAttestation.json")) "postSignature"))))

;; ── passive observation only ──
(deftest test-arbitrage-passive-only
  (is (= (get (consts (lex "arbitrageGapReport.json")) "passiveOnlyAttested") true)))

;; ── G13 — non-member mention is consented + Council-gated + rate-limited ──
(deftest test-g13-non-member-consent-and-rate-limit
  (let [cons (required-union (lex "externalMentionConsent.json"))]
    (doseq [field ["consentMethod" "expiresAtUtc" "consentedCategories"]]
      (is (contains? cons field) (str "G13: externalMentionConsent must require " field)))
    (let [disp (lex "mentionDispatchAttestation.json")]
      (is (contains? (required-union disp) "attestingCouncilDids"))
      (is (= (get (consts disp) "rateLimitWindowAttested") true)))))

;; ── wellbecoming advisory routes advice to the proper boundary actor ──
(deftest test-advisory-boundary-routing
  (let [req (required-union (lex "wellbecomingAdvisory.json"))]
    (doseq [field ["crossActorDid" "boundaryKind" "sourceGapReportCids"]]
      (is (contains? req field) (str "advisory must require " field)))))

;; ── member digest is opt-in + encrypted ──
(deftest test-member-digest-opt-in-encrypted
  (is (contains? (required-union (lex "memberDigestRecord.json")) "encryptedPayloadCid"))
  (is (contains? (required-union (lex "memberDigestSubscription.json")) "subscriptionStatus")))
