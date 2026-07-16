(ns ossekai.methods.test-agent
  "ossekai 御節介 — agent logic tests. 1:1 port of py/test_agent.py. Pure-logic over the 8 cells
  (datalog/llm degrade to nil). Verifies the invariants that distinguish ossekai from a marketing/
  CRM engine: G3 passive-only, gap = benefit × (1−accessibility) notable ≥0.5, G4 aggregate-first,
  G7 ceilings, G1/G6 cleanliness, G9 signed DID, G13/G15 mention gating, G8 encrypted digest,
  G14/G5 kaizen halts, G10 no-panic emergency."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [ossekai.methods.agent :as agent]))

(defn- items []
  [{"topic" "クーリングオフ" "benefit" 0.9 "accessibility" 0.2 "publicRight" true "sourceClass" "legal-corpus"}
   {"topic" "確定申告期限" "benefit" 0.8 "accessibility" 0.9 "sourceClass" "open-dataset"}
   {"topic" "domain-owner" "benefit" 0.9 "accessibility" 0.1 "sourceClass" "whois"}])

;; ── arbitrage_observer ──
(deftest test-observer-refuses-active-probe-g3
  (let [out (agent/handle-arbitrage-observer {"items" (items)})]
    (is (contains? (set (map #(get % "topic") (get out "refused"))) "domain-owner"))
    (is (not (contains? (set (map #(get % "topic") (get out "reports"))) "domain-owner")))))

(deftest test-observer-emits-notable-gap-only
  (let [topics (set (map #(get % "topic") (get (agent/handle-arbitrage-observer {"items" (items)}) "reports")))]
    (is (contains? topics "クーリングオフ"))
    (is (not (contains? topics "確定申告期限")))))

(deftest test-gap-score-formula
  (is (= 0.72 (#'agent/gap-score {"benefit" 0.9 "accessibility" 0.2}))))

(deftest test-reports-are-aggregate-shaped-g4
  (is (every? #(= "aggregate" (get % "shape")) (get (agent/handle-arbitrage-observer {"items" (items)}) "reports"))))

;; ── aggregate_publisher ──
(defn- reports [] (get (agent/handle-arbitrage-observer {"items" (items)}) "reports"))

(deftest test-publisher-default-is-draft-signed-aggregate
  (let [out (agent/handle-aggregate-publisher {"reports" (reports)})
        p (first (get out "posts"))]
    (is (seq (get out "posts")))
    (is (= "draft" (get p "state")))
    (is (= "aggregate" (get p "shape")))
    (is (nil? (get p "targetedHandle")))
    (is (= agent/OSSEKAI-DID (get p "signedDid")))
    (is (= false (get p "nudge")))
    (is (= 100 (get out "aggregateSharePct")))))

(deftest test-publisher-posts-with-operator
  (let [out (agent/handle-aggregate-publisher {"reports" (reports) "operatorRef" "op:council-123"})]
    (is (= true (get out "broadcast")))
    (is (= "posted" (get (first (get out "posts")) "state")))))

(deftest test-publisher-weekly-ceiling-g7
  (let [out (agent/handle-aggregate-publisher {"reports" (reports) "postedThisWeek" agent/WEEKLY-CEILING})]
    (is (= [] (get out "posts")))
    (is (some #(str/includes? (get % "reason") "ceiling") (get out "skipped")))))

(deftest test-publisher-refuses-dark-pattern-and-charter-trips
  (is (= true (agent/no-dark-pattern "落ち着いて確認してください")))
  (is (= false (agent/no-dark-pattern "今すぐ確認 last chance")))
  (is (= true (agent/charter-rider-clean "公共の制度の案内")))
  (is (= false (agent/charter-rider-clean "predatory-loan offer"))))

(deftest test-composed-advisory-is-clean
  (let [post (agent/compose-advisory {"topic" "クーリングオフ"})]
    (is (= true (get post "clean")))
    (is (= true (get post "wellbecomingPositive")))))

;; ── consent_registry (G15) ──
(deftest test-consent-block-overrides-contactable
  (let [out (agent/handle-consent-registry {"now" 100 "events"
                                            [{"handle" "alice" "kind" "consent" "at" 10 "expiry" 1000}
                                             {"handle" "alice" "kind" "block" "at" 20}]})
        a (get-in out ["consentState" "alice"])]
    (is (= true (get a "blocked")))
    (is (= false (get a "contactable")))))

(deftest test-consent-validity-window
  (let [out (agent/handle-consent-registry {"now" 500 "events"
                                            [{"handle" "bob" "kind" "consent" "at" 400 "expiry" 600}
                                             {"handle" "carol" "kind" "consent" "at" 10 "expiry" 100}]})]
    (is (= true (get-in out ["consentState" "bob" "consentValid"])))
    (is (= false (get-in out ["consentState" "carol" "consentValid"])))))

(deftest test-consent-revoke-clears
  (let [out (agent/handle-consent-registry {"now" 100 "events"
                                            [{"handle" "dave" "kind" "consent" "at" 10 "expiry" 1000}
                                             {"handle" "dave" "kind" "revoke" "at" 20}]})]
    (is (= false (get-in out ["consentState" "dave" "consentValid"])))))

(deftest test-consent-expiry-clamped-to-365d
  (let [out (agent/handle-consent-registry {"now" 0 "events" [{"handle" "eve" "kind" "consent" "at" 0 "expiry" 10000}]})]
    (is (= agent/CONSENT-MAX-DAYS (get-in out ["consentState" "eve" "consentExpiry"])))))

;; ── mention_dispatcher ──
(def ATT-OK {"councilLevel" 6 "signers" 3 "ref" "att:cid-123"})

(deftest test-dispatcher-refuses-without-council-attestation
  (let [out (agent/handle-mention-dispatcher {"handles" ["a"] "attestation" {} "memberImpactAttestationCid" "mi:1"})]
    (is (= true (get out "campaignRefused")))
    (is (= [] (get out "dispatches")))))

(deftest test-dispatcher-large-campaign-needs-four-signers
  (let [handles (mapv #(str "h" %) (range 51))
        out (agent/handle-mention-dispatcher {"handles" handles "attestation" ATT-OK "memberImpactAttestationCid" "mi:1"})]
    (is (= true (get out "campaignRefused")))))

(deftest test-dispatcher-rejects-blocked-before-composition-g15
  (let [cs (get (agent/handle-consent-registry {"now" 100 "events" [{"handle" "blk" "kind" "block" "at" 1}]}) "consentState")
        out (agent/handle-mention-dispatcher {"handles" ["blk"] "attestation" ATT-OK
                                              "memberImpactAttestationCid" "mi:1" "consentState" cs "now" 100})]
    (is (= [] (get out "dispatches")))
    (is (str/includes? (get (first (get out "rejected")) "reason") "G15"))))

(deftest test-dispatcher-needs-consent-or-member-impact
  (let [out (agent/handle-mention-dispatcher {"handles" ["nocon"] "attestation" ATT-OK "consentState" {} "now" 100})]
    (is (= [] (get out "dispatches")))
    (is (str/includes? (get (first (get out "rejected")) "reason") "G13"))))

(deftest test-dispatcher-rate-budget-g7
  (let [out (agent/handle-mention-dispatcher {"handles" ["recent"] "attestation" ATT-OK "memberImpactAttestationCid" "mi:1"
                                              "consentState" {} "lastMentionAt" {"recent" 80} "now" 100})]
    (is (= [] (get out "dispatches")))
    (is (str/includes? (get (first (get out "rejected")) "reason") "G7"))))

(deftest test-dispatcher-allows-with-member-impact-default-draft
  (let [out (agent/handle-mention-dispatcher {"handles" ["ok"] "attestation" ATT-OK "memberImpactAttestationCid" "mi:1"
                                              "consentState" {} "now" 100 "topic" "クーリングオフ"})
        d (first (get out "dispatches"))]
    (is (= 1 (count (get out "dispatches"))))
    (is (= "draft" (get d "state")))
    (is (= "targeted" (get d "shape")))
    (is (= agent/OSSEKAI-DID (get d "signedDid")))))

(deftest test-dispatcher-posts-with-operator
  (let [out (agent/handle-mention-dispatcher {"handles" ["ok"] "attestation" ATT-OK "memberImpactAttestationCid" "mi:1"
                                              "consentState" {} "now" 100 "operatorRef" "op:1"})]
    (is (= "posted" (get (first (get out "dispatches")) "state")))))

;; ── intel_analyzer ──
(deftest test-analyzer-emits-community-framed-advisory
  (let [out (agent/handle-intel-analyzer {"reports" [{"topic" "図書館の無料サービス" "gapScore" 0.7}]})
        adv (first (get out "advisories"))]
    (is (= 1 (count (get out "advisories"))))
    (is (= true (get adv "framingAuditPassed")))
    (is (= true (get adv "communityContext")))
    (is (nil? (get adv "domain")))
    (is (nil? (get adv "crossActorCitation")))))

(deftest test-analyzer-requires-cross-actor-citation-for-legal
  (let [adv (first (get (agent/handle-intel-analyzer {"reports" [{"topic" "クーリングオフ" "gapScore" 0.7}]}) "advisories"))]
    (is (= "legal" (get adv "domain")))
    (is (= "chigiri" (get adv "crossActorCitation")))))

(deftest test-analyzer-classify-domain
  (is (= ["pharma" "yakushi"] (agent/classify-domain "処方薬の話")))
  (is (= ["financial" "toritate"] (agent/classify-domain "投資の話")))
  (is (= [nil nil] (agent/classify-domain "天気"))))

(deftest test-framing-audit-rejects-fear
  (is (= true (agent/framing-audit "落ち着いて確認しましょう")))
  (is (= false (agent/framing-audit "恐怖を煽る punish message"))))

;; ── analyzer → publisher pipeline ──
(deftest test-publisher-consumes-advisories-with-citation
  (let [advisories (get (agent/handle-intel-analyzer {"reports" [{"topic" "クーリングオフ" "gapScore" 0.7}]}) "advisories")
        out (agent/handle-aggregate-publisher {"advisories" advisories})
        p (first (get out "posts"))]
    (is (= 1 (count (get out "posts"))))
    (is (= "chigiri" (get p "crossActorCitation")))
    (is (str/includes? (get p "text") "chigiri"))
    (is (= "draft" (get p "state")))))

(deftest test-publisher-refuses-domain-advisory-without-citation
  (let [bad [{"topic" "クーリングオフ" "text" "…" "shape" "aggregate" "framingAuditPassed" true "domain" "legal" "crossActorCitation" nil}]
        out (agent/handle-aggregate-publisher {"advisories" bad})]
    (is (= [] (get out "posts")))
    (is (some #(str/includes? (get % "reason") "UPL") (get out "skipped")))))

;; ── kaizen_observer ──
(deftest test-kaizen-healthy-quarter-no-halt-no-throttle
  (let [out (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 80 "targetedDispatches" 20 "deliveries" 100
                                                      "reEngagementAfterOptOut" 0 "commercialCrmPenetrationPct" 0.0
                                                      "unsubscribeCount" 1 "framingAuditFailures" 0}})]
    (is (= false (get out "halt")))
    (is (= 1.0 (get out "throttleMentionCapFactor")))
    (is (= 8000 (get-in out ["review" "aggregateSharePctIntegerHundredths"])))))

(deftest test-kaizen-re-engagement-is-critical-halt
  (let [out (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 90 "targetedDispatches" 10 "deliveries" 100 "reEngagementAfterOptOut" 1}})]
    (is (= true (get out "halt")))
    (is (some #(and (= "R12" (get % "rule")) (= "critical" (get % "severity"))) (get out "proposals")))
    (is (some #(str/includes? (get % "action") "disputeMediation") (get out "proposals")))))

(deftest test-kaizen-commercial-crm-is-critical-halt
  (let [out (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 90 "targetedDispatches" 10 "deliveries" 100 "commercialCrmPenetrationPct" 3.0}})]
    (is (= true (get out "halt")))
    (is (some #(= "R13" (get % "rule")) (get out "proposals")))))

(deftest test-kaizen-low-aggregate-share-halves-mention-cap
  (let [out (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 30 "targetedDispatches" 70 "deliveries" 100}})]
    (is (= false (get out "halt")))
    (is (= 0.5 (get out "throttleMentionCapFactor")))
    (is (some #(= "R14" (get % "rule")) (get out "proposals")))))

(deftest test-kaizen-unsubscribe-and-framing-spikes-warn
  (let [out (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 90 "targetedDispatches" 10 "deliveries" 100 "unsubscribeCount" 10 "framingAuditFailures" 5}})
        rules (set (map #(get % "rule") (get out "proposals")))]
    (is (and (contains? rules "R15") (contains? rules "R16")))
    (is (= false (get out "halt")))))

(deftest test-kaizen-review-record-carries-const-zero-invariants
  (let [r (get (agent/handle-kaizen-observer {"metrics" {"aggregatePosts" 60 "targetedDispatches" 40 "deliveries" 100}}) "review")]
    (is (= 0 (get r "reEngagementAfterOptOutCount")))
    (is (= 0.0 (get r "commercialIntelCrmSoftwarePenetrationPct")))
    (is (>= (get r "aggregateSharePctIntegerHundredths") 5000))))

;; ── member_digest (G8) ──
(def ADV [{"topic" "図書館サービス" "category" "civic"} {"topic" "健康診断補助" "category" "health"}])

(deftest test-member-digest-encrypts-no-plaintext-g8
  (let [out (agent/handle-member-digest {"members" [{"did" "did:m:1" "sbtActive" true "optedIn" true "categories" ["civic"]}]
                                         "advisories" ADV "now" 100})
        d (first (get out "digests"))]
    (is (= 1 (count (get out "digests"))))
    (is (str/starts-with? (get-in d ["envelope" "envelopeRef"]) "com.etzhayyim.encrypted:"))
    (is (some #{"topics"} (get-in d ["envelope" "sealedFields"])))
    (is (= "draft" (get d "state")))))

(deftest test-member-digest-requires-active-sbt-and-optin
  (let [out (agent/handle-member-digest {"members" [{"did" "did:m:2" "sbtActive" false "optedIn" true}
                                                    {"did" "did:m:3" "sbtActive" true "optedIn" false}]
                                         "advisories" ADV "now" 100})]
    (is (= [] (get out "digests")))))

(deftest test-member-digest-weekly-rate-limit
  (let [out (agent/handle-member-digest {"members" [{"did" "did:m:4" "sbtActive" true "optedIn" true "lastDigestAt" 96}]
                                         "advisories" ADV "now" 100})]
    (is (= [] (get out "digests")))
    (is (some #(str/includes? (get % "reason") "period") (get out "skipped")))))

(deftest test-member-digest-roster-cap-500
  (let [members (mapv (fn [i] {"did" (str "did:m:" i) "sbtActive" true "optedIn" true}) (range 505))
        out (agent/handle-member-digest {"members" members "advisories" ADV "now" 100})]
    (is (= agent/MEMBER-OPT-IN-CAP (get out "rosterSize")))
    (is (some #(str/includes? (get % "reason") "cap") (get out "skipped")))))

(deftest test-member-digest-category-filter
  (let [out (agent/handle-member-digest {"members" [{"did" "did:m:5" "sbtActive" true "optedIn" true "categories" ["health"]}]
                                         "advisories" ADV "now" 100})]
    (is (= 1 (get-in out ["digests" 0 "itemCount"])))))

;; ── emergency_advisory (G10) ──
(deftest test-emergency-refused-without-kazaori-attestation
  (let [out (agent/handle-emergency-advisory {"attestation" {"valid" false} "topic" "x"})]
    (is (= true (get out "refused")))
    (is (str/includes? (get out "reason") "self-declare"))))

(deftest test-emergency-posts-calm-advisory-with-attestation
  (let [out (agent/handle-emergency-advisory {"attestation" {"valid" true "declarer" "kazaori"} "topic" "避難所情報"})]
    (is (= false (get out "refused")))
    (is (= true (get-in out ["post" "expedited"])))
    (is (= "kazaori" (get-in out ["post" "declarer"])))
    (is (= "draft" (get-in out ["post" "state"])))))

(deftest test-emergency-refuses-panic-framing-g10
  (let [out (agent/handle-emergency-advisory {"attestation" {"valid" true "declarer" "kazaori"} "text" "panic now, catastrophe is here"})]
    (is (= true (get out "refused")))
    (is (str/includes? (get out "reason") "G10"))))

(deftest test-emergency-posts-with-operator
  (let [out (agent/handle-emergency-advisory {"attestation" {"valid" true "declarer" "kazaori"} "topic" "避難所情報" "operatorRef" "op:emergency-1"})]
    (is (= "posted" (get-in out ["post" "state"])))))
