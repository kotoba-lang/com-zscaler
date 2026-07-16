(ns ake.methods.triage
  "triage.cljc — 朱 (ake) edit triage: risk + quality scoring and routing. ADR-2606052100.
  Clojure port of `methods/triage.py`.

  THE HEART of the membrane and the G2 anchor. Given a member-signed proposal it produces:

    - risk     ∈ {low, medium, high, invariant}   — a SCORE, never a verdict
    - quality  ∈ [0, 1]                            — sourcing + plausibility + Rider-clean
                                                      (the Wikipedia \"ORES\" analogue)
    - route    ∈ {auto-accept, vote, council-lv7, refused}

  The single invariant that makes this charter-clean: **route is a PURE FUNCTION of
  (risk, quality)** computed by `route-for`. An LLM may, at R1, refine the risk/quality
  SCORES (Murakumo-only, G6) — but it never emits a route and never accepts or rejects (非裁定,
  G2). `score-edit` REFUSES to carry a `decision` field; the only acceptance authorities are the
  optimistic threshold rule, a 1 SBT = 1 vote, or Council.

  Validation enforced here (mirrors the schema :db/allowed + lexicon :const):
    G1 — author-kind must be `member`; an unsourced server/anon author is an ex-info.
    G1 — server-held-key must be falsey (no-server-key, ADR-2605231525).
    G4 — provenance is mandatory; an unsourced proposal is an ex-info (not merely low-quality).
    G3 — target-kind must be kg-fact | actor-profile (impersonation unrepresentable upstream).

  Convention (root CLAUDE.md): Python ':…' keyword strings stay strings; pure fns; closed-vocab
  / gate → ex-info (the Python ValueError). Deterministic at R0 (honest: no live LLM here)."
  (:require [clojure.string :as str]))

;; ── closed vocab (mirror of the ontology :db/allowed) ───────────────────────────
(def TARGET-KINDS ["kg-fact" "actor-profile"])
(def RISKS ["low" "medium" "high" "invariant"])
(def ROUTES ["auto-accept" "vote" "council-lv7" "refused"])

;; Attributes whose edit touches a constitutional invariant → never optimistic-accept (G7).
(def INVARIANT-ATTRS
  #{"license" "charter" "force-class" "forceclass" "server-held-key" "serverheldkey"
    "verification-method" "verificationmethod" "vm" "gates" "is-mirror" "ismirror"})

;; Attributes that are materially sensitive (state, identity) → escalate to a vote (G).
(def SENSITIVE-ATTRS
  #{"status" "did" "handle" "owner" "operator" "controller" "adr" "primary-lexicon"})

;; Charter-Rider §2(a)-(h) hard-gate tokens (a local mirror of charter_rider.scan(),
;; ADR-2605192200). A hit cannot be promoted by ANY vote → route :refused.
(def RIDER-FORBIDDEN
  ["advertis" "affiliate" "adsense" "meta pixel" "ga4"            ;; §2 ad/affiliate
   "weapon" "munition" "fire-control" "directed-energy"           ;; §2(a) force
   "surveillance" "biometric" "pattern-of-life"                   ;; §2 surveillance
   "addictive" "dark-pattern" "engagement-maxim"                  ;; §1.13 Wellbecoming
   "広告" "アフィリエイト" "兵器"])

(def QUALITY-AUTO-ACCEPT 0.7)   ;; optimistic fast-path threshold

(defn kw*
  "Normalize an edn keyword/string to a bare lowercase token (':actor/status' → 'status')."
  [v]
  (-> (str (or v ""))
      (str/replace #"^:+" "")
      (str/split #"/")
      last
      str/lower-case))

(defn verifiable-provenance?
  "True if `p` looks like a verifiable URL/CID (http(s)/ipfs/cid:/bafy/ '://').
  (Python `_verifiable_provenance`; promote cell imports this.)"
  [p]
  (let [p (-> (str (or p "")) str/trim str/lower-case)]
    (boolean
     (or (some #(str/starts-with? p %) ["http://" "https://" "ipfs://" "cid:" "bafy"])
         (str/includes? p "://")))))

(defn rider-hit
  "Return the first Charter-Rider §2 forbidden token found, or \"\" if clean."
  [& texts]
  (let [blob (str/lower-case (str/join " " (map #(str (or % "")) texts)))]
    (or (some #(when (str/includes? blob %) %) RIDER-FORBIDDEN) "")))

;; ── round(min(q, 1.0), 4) — HALF_EVEN over the exact double, mirroring Python round() ──
(defn- round4 [x]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale 4 java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (/ (js/Math.round (* (double x) 10000)) 10000)))

(defn edit-op [edit]
  (kw* (get edit ":edit/op" "assert")))

(defn assess-quality
  "0..1 sourcing + plausibility + Rider-clean score (the ORES analogue)."
  [edit rider]
  (if (seq rider)
    0.0   ;; a Charter-Rider violation is unpromotable, full stop
    (let [q0 0.0
          ;; provenance — Python: if verifiable +0.5 elif (truthy provenance) +0.15
          prov (get edit ":edit/provenance")
          q1 (cond
               (verifiable-provenance? (get edit ":edit/provenance" "")) (+ q0 0.5)
               ;; Python truthiness: a non-empty string / nonzero is truthy; "" / nil / false falsy
               (and prov (not= prov "") (not (false? prov))) (+ q0 0.15)
               :else q0)
          ;; rationale ≥ 10 chars (stripped)
          rationale (str (get edit ":edit/rationale" ""))
          q2 (if (>= (count (str/trim rationale)) 10) (+ q1 0.2) q1)
          ;; plausibility
          op (edit-op edit)
          val (str (get edit ":edit/proposed-value" ""))
          q3 (cond
               (and (or (= op "assert") (= op "promote-sourcing"))
                    (< 0 (count val) 4001)) (+ q2 0.3)
               (or (= op "retract") (= op "challenge")) (+ q2 0.3)
               :else q2)]
      (round4 (min q3 1.0)))))

(defn assess-risk
  [edit rider]
  (let [attr (kw* (get edit ":edit/target-attr" ""))]
    (cond
      (seq rider) "invariant"            ;; a Rider hit is treated as the highest risk class
      (contains? INVARIANT-ATTRS attr) "invariant"
      (or (= (edit-op edit) "challenge") (contains? SENSITIVE-ATTRS attr)) "high"
      (= (kw* (get edit ":edit/target-kind" "")) "actor-profile") "medium"  ;; subjective prose → community eyes
      :else "low")))                     ;; a sourced KG fact

(defn route-for
  "G2 INVARIANT — route is a PURE FUNCTION of (risk, quality, rider). No model decides this."
  [risk quality rider]
  (cond
    (seq rider) "refused"                ;; Charter-Rider §2 hit: no vote can promote it
    (= risk "invariant") "council-lv7"   ;; constitutional-adjacent → Council Lv7+ (G7)
    (and (= risk "low") (>= quality QUALITY-AUTO-ACCEPT)) "auto-accept"  ;; optimistic fast-path
    :else "vote"))                       ;; everything else → 1 SBT = 1 vote

(defn- has-key? [edit k]
  (contains? edit k))

(defn score-edit
  "Validate (G1/G3/G4) then score + route a proposal. Raises (ex-info) on a hard gate.

  Returns a triage map with risk + quality + route + by. It NEVER returns a decision (G2)."
  ([edit] (score-edit edit "murakumo:gemma3:4b"))
  ([edit by]
   (when (or (has-key? edit "decision") (has-key? edit ":triage/decision"))
     (throw (ex-info "G2: triage scores risk+quality and routes; it never accepts/rejects (非裁定)" {})))
   (let [kind (kw* (get edit ":edit/target-kind" ""))]
     (when-not (some #{kind} TARGET-KINDS)
       (throw (ex-info (str "G3: target-kind '" kind "' not in " (pr-str TARGET-KINDS)
                            " — entity-speech/impersonation unrepresentable") {}))))
   (when-not (= (kw* (get edit ":edit/author-kind" "")) "member")
     (throw (ex-info "G1: author-kind must be 'member' (no-server-key + 信者-gated; server/anon refused)" {})))
   (when (get edit ":edit/server-held-key" false)
     (throw (ex-info "G1/no-server-key: server-held-key must be false (ADR-2605231525)" {})))
   (when (str/blank? (str (get edit ":edit/provenance" "")))
     (throw (ex-info "G4: an unsourced proposal is refused — provenance (URL/CID) is mandatory" {})))
   (let [rider (rider-hit (get edit ":edit/proposed-value" "") (get edit ":edit/rationale" ""))
         risk (assess-risk edit rider)
         quality (assess-quality edit rider)
         route (route-for risk quality rider)]
     {":triage/edit" (get edit ":edit/id" "?")
      ":triage/risk" (str ":" risk)
      ":triage/quality" quality
      ":triage/route" (str ":" route)
      ":triage/by" by
      ":triage/rider-token" rider})))   ;; diagnostic; "" when clean
