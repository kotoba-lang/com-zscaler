(ns ossekai.methods.agent
  "ossekai 御節介 — information-arbitrage observer + aggregate-first publisher. 1:1 port of
  py/agent.py (8 cells). Eliminates INFORMATION arbitrage (a buried beneficial public fact) and
  delivers Wellbecoming-nudges. Constitutional posture: G1 Charter-Rider-clean · G3 passive-only
  (active-probe source refused) · G4 aggregate-first · G6 no dark patterns · G7 rate limit · G9
  signed sender DID · G10 Wellbecoming framing · G12 Murakumo-only · G13 Council-attested @mention ·
  G15 block/mute honored before composition. The optional `from kotoba import datalog, llm` host
  binding is unused, so _narrate returns nil (the omitted Murakumo leg) and nothing is transacted."
  (:require [clojure.string :as str]))

(defn- roundn [x n] (let [f (Math/pow 10.0 n)] (/ (Math/round (* (double x) f)) f)))

(def WEEKLY-CEILING 100)
(def CONSENT-MAX-DAYS 365)
(def ^:private NONMEMBER-RATE-DAYS 90)
(def ^:private NOTABLE-GAP 0.5)
(def ^:private PASSIVE-SOURCE-CLASSES
  #{"pre-published-archive" "public-broadcast" "voluntary-atproto" "open-dataset" "legal-corpus"})
(def ^:private ACTIVE-PROBE-CLASSES
  #{"live-dns" "traceroute" "whois" "rdap" "doh" "handle-enum" "port-scan"})
(def ^:private CHARTER-RIDER-TRIP
  ["weapon" "surveillance-for-hire" "addictive" "gore" "non-consensual" "deceptive-ad" "predatory-loan"])
(def ^:private DARK-PATTERN-WORDS
  ["hurry" "act now" "limited time" "last chance" "only today" "don't miss" "urgent" "急いで" "今すぐ" "残りわずか"])
(def ^:private NEGATIVE-FRAMING-WORDS
  ["fear" "shame" "guilt" "punish" "loser" "victim" "blood" "kill" "destroy" "恐怖" "罰" "負け組" "搾取される"])
;; ordered (insertion-order first-match, mirroring the Python dict)
(def ^:private DOMAIN-KEYWORDS
  [["クーリングオフ" "legal" "chigiri"] ["返金" "legal" "chigiri"] ["契約" "legal" "chigiri"] ["訴" "legal" "chigiri"]
   ["薬" "pharma" "yakushi"] ["処方" "pharma" "yakushi"] ["診断" "diagnostic" "mitate"]
   ["症状" "medical" "iyashi"] ["治療" "medical" "iyashi"]
   ["投資" "financial" "toritate"] ["税" "financial" "toritate"] ["決算" "financial" "toritate"]])

(def OSSEKAI-DID "did:web:ossekai.etzhayyim.com")

(def ^:private AGGREGATE-SHARE-FLOOR 50.0)
(def ^:private UNSUBSCRIBE-RATE-WARN 0.05)
(def ^:private FRAMING-FAILURE-WARN 0.02)
(def MEMBER-OPT-IN-CAP 500)
(def ^:private DIGEST-PERIOD-DAYS 7)
(def ^:private PANIC-WORDS ["panic" "doom" "catastrophe" "flee" "終わりだ" "パニック" "絶望"])

;; ── shared gate helpers ───────────────────────────────────────────────────────
(defn charter-rider-clean [text]
  (let [low (str/lower-case text)] (not (some #(str/includes? low %) CHARTER-RIDER-TRIP))))
(defn no-dark-pattern [text]
  (let [low (str/lower-case text)] (not (some #(str/includes? low %) DARK-PATTERN-WORDS))))
(defn framing-audit [text]
  (let [low (str/lower-case text)] (not (some #(str/includes? low %) NEGATIVE-FRAMING-WORDS))))

(defn classify-domain
  "Return [domain actor] for a topic, or [nil nil] if general."
  [topic]
  (let [t (or topic "")]
    (or (some (fn [[kw domain actor]] (when (str/includes? t kw) [domain actor])) DOMAIN-KEYWORDS)
        [nil nil])))

;; ── arbitrage_observer (G3 passive-only) ──────────────────────────────────────
(defn- gap-score [item]
  (let [benefit (max 0.0 (min 1.0 (double (get item "benefit" 0.0))))
        accessibility (max 0.0 (min 1.0 (double (get item "accessibility" 0.0))))]
    (roundn (* benefit (- 1.0 accessibility)) 4)))

(defn handle-arbitrage-observer [state]
  (let [acc (reduce (fn [a item]
                      (let [sclass (get item "sourceClass" "unknown")]
                        (cond
                          (contains? ACTIVE-PROBE-CLASSES sclass)
                          (update a :refused conj {"topic" (get item "topic") "reason" (str "active-probe source refused (G3): " sclass)})
                          (not (contains? PASSIVE-SOURCE-CLASSES sclass))
                          (update a :refused conj {"topic" (get item "topic") "reason" (str "non-passive source skipped (G3): " sclass)})
                          :else
                          (let [score (gap-score item)]
                            (if (< score NOTABLE-GAP)
                              a
                              (update a :reports conj {"topic" (get item "topic") "gapScore" score "notable" true
                                                       "publicRight" (boolean (get item "publicRight" false))
                                                       "shape" "aggregate" "sourceClass" sclass}))))))
                    {:reports [] :refused []} (get state "items" []))]
    (merge state {"reports" (:reports acc) "refused" (:refused acc)})))

;; ── intel_analyzer (G1/G10/G11/G12) ───────────────────────────────────────────
(defn- community-framing [topic]
  (str "「" topic "」は誰でも使える公共の制度です。"
       "ご家族や周りの世代と共有すると、みんなが落ち着いて活用できます。"))

(defn handle-intel-analyzer [state]
  (let [acc (reduce (fn [a r]
                      (let [topic (get r "topic" "")
                            body (community-framing topic)]
                        (cond
                          (not (framing-audit body)) (update a :dropped conj {"topic" topic "reason" "framing audit failed (G10)"})
                          (not (charter-rider-clean body)) (update a :dropped conj {"topic" topic "reason" "Charter-Rider refusal (G1)"})
                          :else (let [[domain actor] (classify-domain topic)]
                                  (update a :advisories conj
                                          {"topic" topic "text" body "shape" "aggregate"
                                           "framingAuditPassed" true "communityContext" true
                                           "domain" domain "crossActorCitation" actor
                                           "narration" nil "gapScore" (get r "gapScore")})))))
                    {:advisories [] :dropped []} (get state "reports" []))]
    (merge state {"advisories" (:advisories acc) "dropped" (:dropped acc)})))

;; ── aggregate_publisher (G4/G6/G7/G9/G10) ─────────────────────────────────────
(defn compose-advisory [report]
  (let [topic (get report "topic" "a public benefit")
        text (str "知っておくと役立つ公共情報: 「" topic "」は利用できる権利/制度ですが、"
                  "情報が見つけにくい状態です。落ち着いて確認してみてください。")]
    {"text" text "shape" "aggregate" "lexicon" "app.bsky.feed.post" "signedDid" OSSEKAI-DID
     "targetedHandle" nil "nudge" false "wellbecomingPositive" true
     "clean" (and (charter-rider-clean text) (no-dark-pattern text))}))

(defn- post-from-advisory [adv]
  (let [cite (get adv "crossActorCitation")
        text (let [t (get adv "text" "")] (if cite (str t "（詳しくは " cite " へ）") t))]
    {"text" text "shape" "aggregate" "lexicon" "app.bsky.feed.post" "signedDid" OSSEKAI-DID
     "targetedHandle" nil "nudge" false "wellbecomingPositive" true "crossActorCitation" cite
     "clean" (and (charter-rider-clean text) (no-dark-pattern text) (framing-audit text))}))

(defn- server-or-synthetic-signer? [sig]
  (let [s (str/lower-case (str/trim (or sig "")))]
    (or (empty? s) (= s "anon") (str/includes? s "server") (str/includes? s "autonomous_system_signature"))))

(defn- outward-authorized [state]
  (if-let [op (get state "operatorRef")]
    [true op]
    (let [cap (or (get state "memberCapability") {})
          principal (get cap "memberDid") sig (get cap "memberSignature")]
      (if (and principal sig (not (server-or-synthetic-signer? sig))) [true principal] [false nil]))))

(defn handle-aggregate-publisher [state]
  (let [advisories (get state "advisories")
        posted-this-week (long (get state "postedThisWeek" 0))
        [authorized _] (outward-authorized state)
        live-state (if authorized "posted" "draft")
        ceiling? (fn [a] (>= (+ posted-this-week (count (:posts a))) WEEKLY-CEILING))
        acc (if (some? advisories)
              (reduce (fn [a adv]
                        (let [topic (get adv "topic")]
                          (cond
                            (ceiling? a) (update a :skipped conj {"topic" topic "reason" (str "weekly ceiling " WEEKLY-CEILING " reached (G7)")})
                            (not (get adv "framingAuditPassed")) (update a :skipped conj {"topic" topic "reason" "framing audit not passed (G10)"})
                            (and (get adv "domain") (not (get adv "crossActorCitation")))
                            (update a :skipped conj {"topic" topic "reason" (str "domain '" (get adv "domain") "' requires cross-actor citation (UPL boundary)")})
                            :else (let [post (post-from-advisory adv)]
                                    (if-not (get post "clean")
                                      (update a :skipped conj {"topic" topic "reason" "Charter-Rider/dark-pattern/framing refusal (G1/G6/G10)"})
                                      (update a :posts conj (assoc post "state" live-state)))))))
                      {:posts [] :skipped []} advisories)
              (reduce (fn [a r]
                        (cond
                          (ceiling? a) (update a :skipped conj {"topic" (get r "topic") "reason" (str "weekly ceiling " WEEKLY-CEILING " reached (G7)")})
                          :else (let [post (compose-advisory r)]
                                  (if-not (get post "clean")
                                    (update a :skipped conj {"topic" (get r "topic") "reason" "Charter-Rider/dark-pattern refusal (G1/G6)"})
                                    (update a :posts conj (assoc post "state" live-state))))))
                      {:posts [] :skipped []} (get state "reports" [])))
        posts (:posts acc)]
    (merge state {"posts" posts "skipped" (:skipped acc)
                  "broadcast" (boolean (and authorized (seq posts)))
                  "aggregateSharePct" (if (and (seq posts) (every? #(= (get % "shape") "aggregate") posts)) 100 0)})))

;; ── consent_registry (G15) ────────────────────────────────────────────────────
(defn handle-consent-registry [state]
  (let [now (long (get state "now" 0))
        st (reduce (fn [st ev]
                     (let [h (get ev "handle")]
                       (if (nil? h)
                         st
                         (let [cur (get st h {"handle" h "blocked" false "muted" false "consentExpiry" nil})
                               cur (case (get ev "kind")
                                     "block" (assoc cur "blocked" true)
                                     "unblock" (assoc cur "blocked" false)
                                     "mute" (assoc cur "muted" true)
                                     "unmute" (assoc cur "muted" false)
                                     "consent" (let [exp (get ev "expiry")
                                                     cap (+ (long (get ev "at" now)) CONSENT-MAX-DAYS)]
                                                 (assoc cur "consentExpiry" (if (some? exp) (min (long exp) cap) cap)))
                                     "revoke" (assoc cur "consentExpiry" nil)
                                     cur)]
                           (assoc st h cur)))))
                   {} (sort-by #(long (get % "at" 0)) (get state "events" [])))
        st (into {} (map (fn [[h cur]]
                           [h (assoc cur
                                     "consentValid" (and (some? (get cur "consentExpiry")) (> (get cur "consentExpiry") now))
                                     "contactable" (and (not (get cur "blocked")) (not (get cur "muted"))))])
                         st))]
    (merge state {"consentState" st})))

;; ── mention_dispatcher (G13/G15/G7) ───────────────────────────────────────────
(defn- attestation-ok [attestation campaign-size]
  (let [level (long (get attestation "councilLevel" 0))
        signers (long (get attestation "signers" 0))
        needed (if (> campaign-size 50) 4 3)]
    (cond (< level 6) [false "Council Lv6+ attestation required (G13)"]
          (< signers needed) [false (str "campaign of " campaign-size " handles needs ≥" needed " signers (G13)")]
          :else [true "attested"])))

(defn handle-mention-dispatcher [state]
  (let [handles (vec (get state "handles" []))
        attestation (get state "attestation" {})
        consent-state (get state "consentState" {})
        member-impact-cid (get state "memberImpactAttestationCid")
        last-mention (get state "lastMentionAt" {})
        now (long (get state "now" 0))
        [authorized _] (outward-authorized state)
        topic (get state "topic" "a public benefit")
        [ok reason] (attestation-ok attestation (count handles))]
    (if-not ok
      (merge state {"dispatches" [] "rejected" (mapv (fn [h] {"handle" h "reason" reason}) handles) "campaignRefused" true})
      (let [acc (reduce (fn [a h]
                          (let [cs (get consent-state h {})
                                last (get last-mention h)]
                            (cond
                              (or (get cs "blocked") (get cs "muted"))
                              (update a :rejected conj {"handle" h "reason" "blocked/muted — rejected before composition (G15)"})
                              (and (not member-impact-cid) (not (get cs "consentValid")))
                              (update a :rejected conj {"handle" h "reason" "no member-impact attestation and no valid externalMentionConsent (G13)"})
                              (and (some? last) (< (- now (long last)) NONMEMBER-RATE-DAYS))
                              (update a :rejected conj {"handle" h "reason" (str "within " NONMEMBER-RATE-DAYS "d rate budget (G7)")})
                              :else
                              (let [text (str "@" h " 参考情報として: 「" topic "」が利用できる可能性があります。"
                                              " 不要な場合はブロック/ミュートで今後お送りしません。")]
                                (if-not (and (charter-rider-clean text) (no-dark-pattern text))
                                  (update a :rejected conj {"handle" h "reason" "Charter-Rider/dark-pattern refusal (G1/G6)"})
                                  (update a :dispatches conj {"handle" h "text" text "lexicon" "app.bsky.feed.post"
                                                              "signedDid" OSSEKAI-DID "shape" "targeted"
                                                              "attestation" (get attestation "ref")
                                                              "state" (if authorized "posted" "draft")}))))))
                        {:dispatches [] :rejected []} handles)]
        (merge state {"dispatches" (:dispatches acc) "rejected" (:rejected acc)
                      "campaignRefused" false "broadcast" (boolean (and authorized (seq (:dispatches acc))))})))))

;; ── kaizen_observer (G4/G5/G14) ───────────────────────────────────────────────
(defn- aggregate-share-pct [aggregate targeted]
  (let [total (+ aggregate targeted)]
    (if (zero? total) 100.0 (roundn (/ (* 100.0 aggregate) total) 2))))

(defn handle-kaizen-observer [state]
  (let [m (get state "metrics" {})
        aggregate (long (get m "aggregatePosts" 0))
        targeted (long (get m "targetedDispatches" 0))
        re-engage (long (get m "reEngagementAfterOptOut" 0))
        crm-pct (double (get m "commercialCrmPenetrationPct" 0.0))
        deliveries (let [d (long (get m "deliveries" 0))] (if (zero? d) 1 d))
        unsubscribes (long (get m "unsubscribeCount" 0))
        framing-failures (long (get m "framingAuditFailures" 0))
        share (aggregate-share-pct aggregate targeted)
        critical (cond-> []
                   (> re-engage 0) (conj "reEngagementAfterOptOut > 0 (G14 const 0)")
                   (> crm-pct 0.0) (conj "commercialCrmPenetrationPct > 0 (G5 const 0)"))
        throttle-factor (if (< share AGGREGATE-SHARE-FLOOR) 0.5 1.0)
        unsub-rate (/ (double unsubscribes) deliveries)
        framing-rate (/ (double framing-failures) deliveries)
        proposals (cond-> []
                    (> re-engage 0) (conj {"rule" "R12" "severity" "critical" "finding" (str re-engage " post-opt-out re-engagement(s)") "action" "halt + chigiri.disputeMediation"})
                    (> crm-pct 0.0) (conj {"rule" "R13" "severity" "critical" "finding" (str "commercial CRM penetration " crm-pct "%") "action" "halt + purge commercial-CRM dependency"})
                    (< share AGGREGATE-SHARE-FLOOR) (conj {"rule" "R14" "severity" "structural" "finding" (str "aggregate-share " share "% < " AGGREGATE-SHARE-FLOOR "%") "action" "next-quarter mention_dispatcher cap × 0.5 until recovery"})
                    (> unsub-rate UNSUBSCRIBE-RATE-WARN) (conj {"rule" "R15" "severity" "warn" "finding" (str "unsubscribe rate " (roundn unsub-rate 4) " > " UNSUBSCRIBE-RATE-WARN) "action" "review advisory framing + cadence"})
                    (> framing-rate FRAMING-FAILURE-WARN) (conj {"rule" "R16" "severity" "warn" "finding" (str "framing-audit failure rate " (roundn framing-rate 4) " > " FRAMING-FAILURE-WARN) "action" "review intel_analyzer framing prompts (G10)"}))
        halt (boolean (seq critical))
        review {"aggregateSharePctIntegerHundredths" (long (Math/round (* share 100.0)))
                "reEngagementAfterOptOutCount" re-engage
                "commercialIntelCrmSoftwarePenetrationPct" crm-pct
                "halt" halt "throttleMentionCapFactor" throttle-factor "criticalFindings" critical}]
    (merge state {"review" review "proposals" proposals "halt" halt "throttleMentionCapFactor" throttle-factor})))

;; ── member_digest (G8) + emergency_advisory (G10) ─────────────────────────────
(defn seal-encrypted [fields recipient-did]
  (let [keysig (str/join "+" (sort (keys fields)))
        ref (str "com.etzhayyim.encrypted:" (format "%08x" (bit-and (hash keysig) 0xFFFFFFFF)))]
    {"envelopeRef" ref "recipientDid" recipient-did "sealedFields" (vec (sort (keys fields)))}))

(defn handle-member-digest [state]
  (let [members (vec (get state "members" []))
        advisories (get state "advisories" [])
        now (long (get state "now" 0))
        [authorized _] (outward-authorized state)
        opted-in (filterv #(get % "optedIn") members)
        over-cap (vec (drop MEMBER-OPT-IN-CAP opted-in))
        roster (vec (take MEMBER-OPT-IN-CAP opted-in))
        init-skipped (mapv (fn [m] {"memberDid" (get m "did") "reason" (str "opt-in roster cap " MEMBER-OPT-IN-CAP " exceeded (G7)")}) over-cap)
        acc (reduce (fn [a m]
                      (let [last (get m "lastDigestAt")]
                        (cond
                          (not (get m "sbtActive")) (update a :skipped conj {"memberDid" (get m "did") "reason" "not an active Adherent SBT holder (§3)"})
                          (and (some? last) (< (- now (long last)) DIGEST-PERIOD-DAYS))
                          (update a :skipped conj {"memberDid" (get m "did") "reason" (str "within " DIGEST-PERIOD-DAYS "d digest period")})
                          :else
                          (let [cats (set (get m "categories" []))
                                items (filterv (fn [adv] (or (empty? cats) (contains? cats (get adv "category")) (nil? (get adv "category")))) advisories)]
                            (if (empty? items)
                              (update a :skipped conj {"memberDid" (get m "did") "reason" "no advisory in subscribed categories"})
                              (let [envelope (seal-encrypted {"topics" (mapv #(get % "topic") items)} (get m "did"))]
                                (update a :digests conj {"recipientDid" (get m "did") "envelope" envelope "itemCount" (count items)
                                                         "signedDid" OSSEKAI-DID "state" (if authorized "sent" "draft")})))))))
                    {:digests [] :skipped init-skipped} roster)]
    (merge state {"digests" (:digests acc) "skipped" (:skipped acc) "rosterSize" (count roster)
                  "broadcast" (boolean (and authorized (seq (:digests acc))))})))

(defn no-panic-framing [text]
  (let [low (str/lower-case text)]
    (and (not (some #(str/includes? low %) PANIC-WORDS)) (framing-audit text))))

(defn handle-emergency-advisory [state]
  (let [att (get state "attestation" {})]
    (if-not (get att "valid")
      {"refused" true "reason" "no valid kazaori emergencyDeclarationAttestation — ossekai cannot self-declare an emergency" "post" nil}
      (let [topic (get state "topic" "緊急のお知らせ")
            text (or (get state "text")
                     (str "【お知らせ】" topic "。落ち着いて、安全と必要な手順をご確認ください。周りの方とも共有してください。"))]
        (cond
          (not (no-panic-framing text)) {"refused" true "reason" "fear/panic framing refused (G10)" "post" nil}
          (not (charter-rider-clean text)) {"refused" true "reason" "Charter-Rider refusal (G1)" "post" nil}
          :else (let [[authorized _] (outward-authorized state)
                      post {"text" text "shape" "aggregate" "lexicon" "app.bsky.feed.post" "signedDid" OSSEKAI-DID
                            "expedited" true "declarer" (get att "declarer") "state" (if authorized "posted" "draft")}]
                  (merge state {"post" post "refused" false "broadcast" (boolean authorized)})))))))
