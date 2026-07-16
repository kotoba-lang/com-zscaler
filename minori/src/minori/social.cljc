(ns minori.social
  "The :dry-run-social-action export (system.edn). minori PREPARES a charter-clean donor/contributor
   digest from the OBSERVED growth state and NEVER sends it (no-server-key; a MEMBER reviews + sends,
   G7-member). The digest is a TRANSPARENT INVITATION, not a campaign:
     - anti-class: giving earns nothing — no perks/tiers/priority (README invariant, ADR-2605172100)
     - non-manipulative: no urgency/scarcity/attention-exploitation
   Those mechanisms are guarded — `clean?` refuses any body containing a forbidden phrase, and the
   artifact records :charter-clean so a member (and a test) can verify before any send."
  (:require [clojure.string :as str]))

(def forbidden
  "Manipulative CONSTRUCTIONS a charter-clean invitation may never use (urgency / scarcity /
   exclusivity / quid-pro-quo / attention-exploitation). Note these are OFFERING phrases — the
   anti-class disclaimer ('no perks, no tiers, no priority') is REQUIRED separately, so we must not
   forbid the bare words 'perks'/'tiers'/'priority' (that would flag the disclaimer that negates them)."
  #{"limited time" "act now" "hurry" "scarcity" "only today" "reward you" "in return" "vip"
    "early access" "don't miss" "exclusive offer" "sign up now" "buy now" "while supplies last"
    "priority access" "claim your" "you deserve"})

(defn clean?
  "True iff the text contains no forbidden (manipulative) construction."
  [text]
  (let [t (str/lower-case (str text))]
    (not-any? #(str/includes? t %) forbidden)))

(defn anti-class?
  "True iff the text affirmatively states the anti-class invariant (giving earns nothing)."
  [text]
  (let [t (str/lower-case (str text))]
    (and (str/includes? t "earns you nothing")
         (str/includes? t "no perks, no tiers, no priority"))))

(defn digest
  "Prepare (never send) a transparent donor/contributor digest from observed numbers.
   `m` = {:eta :adopted :realized-phi :captured-note :next-step :next-gate}."
  [{:keys [eta adopted realized-phi next-step next-gate]}]
  (let [body (str
              "etzhayyim + kotoba — social-capital growth · transparent status (prepared by minori 稔り)\n\n"
              (format "Order-export η (共生): %.3f of 1.0 — the colony returns nearly as much order as it\n" (double eta))
              "draws, but is NOT yet a net giver (η<1). So this is an honest invitation, not a claim of surplus.\n"
              (format "SoS adoption: %d actors run their reward loop · energy-flow Φ realized = ln(adopted) ≈ %.2f.\n"
                      (long adopted) (double realized-phi))
              "Captured value: pre-revenue — etzhayyim sells nothing and operates only on donation.\n\n"
              "How giving helps (it is the negentropy SOURCE the colony lives on):\n"
              "  · donate compute — a browser tab / laptop / pod to the Murakumo mesh\n"
              "  · donate USDC on Base (90/10 tithe via TitheRouter)\n"
              "  · contribute to the Apache-2.0 kotoba engine\n"
              "Giving earns you nothing — no perks, no tiers, no priority (pure gift; anti-class invariant).\n\n"
              (format "Where this most helps next: %s (%s).\n" (str next-step) (name (or next-gate :none)))
              "DONATE.md · https://etzhayyim.com/donate · kotoba: github.com/com-junkawasaki/kotoba\n")]
    {:kind :dry-run-social-action
     :status :prepared-unsent              ; minori NEVER sends — a MEMBER sends (G7-member)
     :audience [:donors :contributors]
     :charter {:anti-class true :no-urgency true :transparent true
               :member-principal true :no-server-key true}
     :charter-clean (and (clean? body) (anti-class? body))   ; non-manipulative AND anti-class affirmed
     :bytes (count body)
     :body body}))
