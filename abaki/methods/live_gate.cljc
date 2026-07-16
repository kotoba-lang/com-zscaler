(ns abaki.methods.live-gate
  "live_gate.cljc — 暴 (abaki) live outward-action gate for the route-around broadcast.
  ADR-2606073100 + the member-signed-capability resolution of FINDING 260617
  (R2-autonomous live-gate removal).

  WHAT THIS GATE IS (and the 2026-06-17 charter restoration):
  An earlier 'R2 Autonomous' edit had made this gate ALWAYS admissible and substituted a
  server-held synthetic credential (`member-signature \"autonomous_system_signature\"`,
  `council-level 0`, `operator-did \"…:abaki:autonomous\"`) for the member/operator/Council
  signoff — `require-gate` never raised, so the route-around broadcast / Datom publish proceeded
  with no member signature. Publishing the Non-Aligned-Entity graph + the route-around blocklist
  to the public substrate is an OUTWARD action; doing it with a synthetic server-held credential
  implies a platform-held key, which the substrate-wide no-server-key invariant forbids (root
  CLAUDE.md substrate boundary; ADR-2605231525). It also collided with abaki's own
  NO_SECRET_BLACKLISTS invariant — a broadcast no human member signed is exactly the unaccountable
  blacklist the charter forbids.

  Per ADR-2605231525 (no-server-key) and the ibuki/mimamori member-signed-capability precedent
  (ADR-2606111400), it is restored here. R2 autonomy is PRESERVED without a server key: a MEMBER
  pre-signs a scoped, revocable capability (the `member-signature`) in their own runtime; the
  autonomous loop PRESENTS it (never holds a key) and the publish is attributed to that member.
  The gate REFUSES by default and admits ONLY when the operator process flag
  `ABAKI_ALLOW_LIVE_PUBLISH=1` + an operator attestation (non-blank DID) + Council Lv6+ + a real
  member signature (NOT a server/synthetic credential) are ALL presented.

  House style: ':…' strings stay strings; closed-vocab/gate → ex-info. Portable .cljc."
  (:require [clojure.string :as str]))

;; The single live leg: the route-around broadcast / Datom publish. Operator process flag +
;; minimum Council level.
(def PUBLISH-FLAG "ABAKI_ALLOW_LIVE_PUBLISH")
(def MIN-COUNCIL 6)

;; LiveGateRefused — carried as an ex-info with this ::kind for catch-by-data (publish-live
;; catches on this key). The Python class subclassed RuntimeError; here it is data on ex-info.
(def live-gate-refused ::live-gate-refused)

(defn- refuse [msg gate]
  (throw (ex-info msg (merge {::kind live-gate-refused} (when (map? gate) gate)))))

(defn make-live-gate
  "Build the live gate. NO autonomous defaults: a bare gate (operator-did \"\", council-level 0,
  member-signature \"\") is REFUSED. Admissibility requires the member to present operator-did +
  Council Lv6+ + a real member-signature (a member-signed capability), plus the env process flag."
  ([] (make-live-gate {}))
  ([{:keys [operator-did council-level member-signature]
     :or {operator-did "" council-level 0 member-signature ""}}]
   {:operator-did operator-did :council-level council-level
    :member-signature member-signature}))

;; A signer is a SERVER/synthetic (no-server-key-refused) credential if blank, "anon", or
;; anything mentioning "server", or the prior "autonomous_system_signature" platform credential.
;; A real member signature must be a member-signed capability, never one of these.
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
  (cond
    (not= "1" (get env PUBLISH-FLAG))
    (str "missing operator process flag '" PUBLISH-FLAG "'")
    (str/blank? (str (:operator-did gate)))
    "missing operator attestation (operator-did)"
    (< (or (:council-level gate) 0) MIN-COUNCIL)
    (str "insufficient Council level — requires Lv" MIN-COUNCIL)
    (server-or-blank-signer? (:member-signature gate))
    "missing member signature (member-signed capability required; server/synthetic refused)"
    :else nil))

(defn gate-status
  "Non-raising status. admissible=false unless the member presents the full capability."
  ([gate] (gate-status gate nil))
  ([gate env]
   (let [env (or env {})
         fail (gate-failure gate env)]
     {"min_council" MIN-COUNCIL "env_flag" PUBLISH-FLAG
      "conditions" {"operator_flag" (= "1" (get env PUBLISH-FLAG))
                    "operator_attestation" (not (str/blank? (str (:operator-did gate))))
                    "council_ok" (>= (or (:council-level gate) 0) MIN-COUNCIL)
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
