(ns fuchi.methods.live-gate
  "live_gate.cljc — 扶持 (fuchi) live outward-action gate. ADR-2606052300 + the
  member-signed-capability resolution of FINDING 260617 (R2-autonomous live-gate removal).

  WHAT THIS GATE IS (and the 2026-06-17 charter restoration):
  An earlier 'R2 Autonomous' edit had made this gate ALWAYS admissible and substituted a
  server-held synthetic credential (`member-signature \"autonomous_system_signature\"`,
  `council-level 7`) for the member/operator/Council signoff — removing the G7/G9 (no-server-key),
  G10 (outward-gating) and G2 refusals. That CONTRADICTS fuchi's own CLAUDE.md G10 hard rule
  ('every live leg REFUSES by default … fires only when the operator flag + an operator
  attestation + Council Lv6+ (Lv7+ for couple) + a member signature are ALL present … Never sign
  server-side to satisfy it.'). Per ADR-2605231525 (no-server-key) and the ibuki/mimamori
  member-signed-capability precedent (ADR-2606111400), it is restored here:

    - the gate REFUSES by default (no operator process flag / no attestation / insufficient
      Council / a server-held or blank/anon signer all raise);
    - it is admissible ONLY when a MEMBER presents: the operator process flag (the member's own
      authorized runtime — never a platform CronJob), an operator attestation (non-blank DID),
      a sufficient Council level, AND a real member signature (a member-signed capability /
      Ed25519 — NOT a server/synthetic credential).

  R2 autonomy is preserved WITHOUT a server key: the member pre-signs a scoped, revocable
  capability (the `member-signature`) that the autonomous runtime PRESENTS; the gate still
  refuses if it is absent, server-held, or synthetic. No platform-held key is ever accepted.

  House style: ':…' strings stay strings; closed-vocab/gate → ex-info. Portable .cljc."
  (:require [clojure.string :as str]))

(def LEG-POLICY
  (array-map
   "provision" ["FUCHI_ALLOW_LIVE_PROVISION" 6]
   "vote"      ["FUCHI_ALLOW_LIVE_VOTE" 6]
   "book"      ["FUCHI_ALLOW_LIVE_BOOK" 6]
   "couple"    ["FUCHI_ALLOW_LIVE_COUPLE" 7]))

;; LiveGateRefused — carried as an ex-info with this ::kind for catch-by-data.
(def live-gate-refused ::live-gate-refused)

(defn- refuse [msg gate]
  (throw (ex-info msg (merge {::kind live-gate-refused} (when (map? gate) gate)))))

(defn make-live-gate
  "Build a live gate. NO autonomous defaults: a bare gate (operator-did \"\", council-level 0,
  member-signature \"\") is REFUSED. Admissibility requires the member to present operator-did
  + sufficient council-level + a real member-signature (capability), plus the env process flag."
  [{:keys [leg operator-did council-level member-signature]
    :or {operator-did "" council-level 0 member-signature ""}}]
  (when-not (contains? LEG-POLICY leg)
    (throw (ex-info (str "unknown live leg '" leg "'") {:leg leg})))
  {:leg leg :operator-did operator-did :council-level council-level
   :member-signature member-signature})

;; A signer is a SERVER/synthetic (G7/G9-refused) credential if blank, "anon", or anything
;; mentioning "server" (covers "server" / ":server" / "did:server:x") or the prior
;; "autonomous_system_signature" platform credential. A real member signature must be a
;; member-signed capability, never one of these.
(defn- server-or-blank-signer? [sig]
  (let [s (str/trim (str sig))
        l (str/lower-case s)]
    (or (str/blank? s)
        (= l "anon")
        (str/includes? l "server")
        (str/includes? l "autonomous_system_signature"))))

(defn- gate-failure
  "Return the first refusal reason (string) for a gate+env, or nil if admissible.
  Order: operator process flag → operator attestation → Council level → member signature."
  [gate env]
  (let [[flag min-council] (get LEG-POLICY (:leg gate))]
    (cond
      (not= "1" (get env flag))
      (str "missing operator process flag '" flag "'")
      (str/blank? (str (:operator-did gate)))
      "missing operator attestation (operator-did)"
      (< (or (:council-level gate) 0) min-council)
      (str "insufficient Council level — requires Lv" min-council)
      (server-or-blank-signer? (:member-signature gate))
      "missing member signature (member-signed capability required; server/synthetic refused)"
      :else nil)))

(defn gate-status
  "Non-raising status. admissible=false unless the member presents the full capability."
  ([gate] (gate-status gate nil))
  ([gate env]
   (let [[flag min-council] (get LEG-POLICY (:leg gate))
         env (or env {})
         fail (gate-failure gate env)]
     {"leg" (:leg gate) "env_flag" flag "min_council" min-council
      "conditions" {"operator_flag" (= "1" (get env flag))
                    "operator_attestation" (not (str/blank? (str (:operator-did gate))))
                    "council_ok" (>= (or (:council-level gate) 0) min-council)
                    "member_signature_ok" (not (server-or-blank-signer? (:member-signature gate)))}
      "admissible" (nil? fail)})))

(defn require-gate
  "Raise LiveGateRefused (ex-info) unless the member-signed capability fully satisfies the gate.
  (Named require-gate; `require` is core.)"
  ([gate] (require-gate gate nil))
  ([gate env]
   (let [env (or env {})]
     (if-let [fail (gate-failure gate env)]
       (refuse fail gate)
       (gate-status gate env)))))
