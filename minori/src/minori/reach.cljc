(ns minori.reach
  "Charter-clean CAPPED reach observation (§1.13 / ibuki attention-cap pattern). Observes whether
   minori's gift actually REACHED humanity, capped so it can NEVER become an optimization target
   (anti-engagement-maximisation, anti-addictive-design — Charter §1.13/§1.10).

   TWO HARD INVARIANTS, enforced structurally + by test:
     1. REACTION RATE is unrepresentable — there is NO like/repost/reply/engagement field here; only
        'did it arrive' (reach), bounded by reach-cap. Optimizing for reactions is a §1.13 violation.
     2. Reach is OBSERVED + recorded (transparency), NEVER a reward gradient — `apply-to-G` does not
        exist; the social-capital G keeps rewarding η (order-export), never reach. Reach only informs
        the social-action worklist ('did the gift reach? if not, a MEMBER sends it, G7-member')."
  (:require [clojure.set :as set]))

(def reach-cap
  "Hard ceiling on reach units counted per beat — structurally prevents reach from ever dominating
   (anti-§1.13). Mirrors ibuki's attention-cap."
  3)

(def forbidden-fields
  "Reaction-rate fields that must NEVER appear in a reach reading (engagement-maximiser surface)."
  #{:likes :reposts :replies :like-count :repost-count :reply-count :engagement :engagement-rate})

(defn observe
  "Real reach evidence, read-only + capped. Source = member-attributed SEND RECEIPTS of the prepared
   digest (a member actually sent it, and it arrived). None yet ⇒ reach 0 (honest: the digest is
   prepared-unsent). Live public-feed reach (getAuthorFeed) is a separate G7-gated leg, not wired.
   Returns ONLY {:reach :raw :cap :capped? :note} — no reaction-rate field (unrepresentable)."
  [{:keys [send-receipts]}]
  (let [raw    (count (or send-receipts []))
        capped (min reach-cap raw)]
    {:reach   capped
     :raw     raw
     :cap     reach-cap
     :capped? (> raw reach-cap)
     :note    (if (zero? raw)
                "digest unsent — no real reach yet (a MEMBER sends it, G7-member); reach is capped, never optimized"
                "capped reach observed — §1.13: reach helps the gift arrive, but is NEVER the target")}))

(defn clean?
  "True iff a reach reading contains no reaction-rate (engagement-maximiser) field."
  [reading]
  (empty? (set/intersection (set (keys reading)) forbidden-fields)))
