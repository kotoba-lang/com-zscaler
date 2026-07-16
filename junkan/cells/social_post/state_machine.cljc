(ns junkan.cells.social-post.state-machine
  "Phase state machine for the 循環 (junkan) social_post cell — the publication membrane
  that lets the analysis-only observer self-publish its HISTORY and FINDINGS to the
  mesh/AT-proto WITHOUT a server-held key. ADR-2606272355 (actor self-publication seed).

  Mirror of the constellation membrane (keizu.cells.social-post.state-machine,
  danjo social_post) adapted to junkan's ANALYSIS-ONLY / disclosed-hypothesis posture.
  junkan has NO outward channel by its own discipline (G4); this membrane is the ONE
  careful exception, and it is deliberately the narrowest possible: a record (an
  on-record governance-asymmetry instrument, or a disclosed-hypothesis loop / leverage
  read-off) enters; it is DRAFTED into a dry-run MIRROR post ONLY if:

    G5(junkan) — ≥2 public primary-source / on-record citations are present;
    G7(junkan) — the post is a non-adjudicating mirror (isMirror), opening with the
                 analysis-only disclaimer; it narrates an on-record FACT or a DISCLOSED
                 HYPOTHESIS, never a verdict, never proven causation, never a directive;
    no-server-key — server-held-key is false (the actor self-custodies its own key in
                 its kotoba-mesh WASM runtime and signs there; the server never does,
                 ADR-2605231525);
    R0-gate — the status is dry-run (a 'published' request REFUSES — live publication
                 needs Council Lv6+ + operator + a member/actor signature, §1.12 / G11,
                 and is performed via ossekai/kataribe on junkan's behalf, never junkan).

  Self-contained. Stdlib only. Deterministic — the seed grows on the mesh, not here."
  (:require [clojure.string :as str]))

(def disclaimer
  "【分析ミラー / systems-dynamics read-off — 分析のみ・断定なし・開示された仮説 (proven causation ではない)】")

(def phase-init "init")
(def phase-drafted "drafted")
(def phase-refused "refused")

(def state-defaults
  {"phase"            phase-init
   "subject"          ""
   "sources"          []
   "requested_status" "dry-run"
   "server_held_key"  false
   "payload"          {}
   "refusal"          ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- lstrip-colon [s]
  (str/replace (str s) #"^:+" ""))

(defn transition-to-drafted
  "Drive one record toward a dry-run post payload, or refuse with the failed invariant.
  Pure: (state) -> {\"cell_state\" {…}}."
  [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "subject"          (get state "subject" (get cs0 "subject"))
                   "sources"          (get state "sources" (get cs0 "sources"))
                   "requested_status" (lstrip-colon (get state "requested_status" (get cs0 "requested_status")))
                   "server_held_key"  (boolean (get state "server_held_key" (get cs0 "server_held_key"))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (< (count (get cs "sources")) 2)
      (refuse "G5(junkan): a post needs ≥2 public primary-source/on-record citations")

      (get cs "server_held_key")
      (refuse "no-server-key: server-held-key must be false; the actor self-signs in its mesh runtime (ADR-2605231525)")

      (not= (get cs "requested_status") "dry-run")
      (refuse "R0-gate: only dry-run posts; live publication is Council Lv6+ + operator + member/actor-signature gated and is performed via ossekai/kataribe, never by junkan (§1.12/G11/G13)")

      :else
      (let [payload {":post/subject" (get cs "subject")
                     ":post/body" (str disclaimer " " (get cs "subject"))
                     ":post/status" ":dry-run"
                     ":post/is-mirror" true
                     ":post/non-adjudicating-notice" true
                     ":post/server-held-key" false
                     ":post/sources" (get cs "sources")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))
