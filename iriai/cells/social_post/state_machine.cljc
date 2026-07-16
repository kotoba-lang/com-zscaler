(ns iriai.cells.social-post.state-machine
  "Phase state machine for the iriai 入会 social_post cell — the publication membrane that
  lets the actor self-publish its lifeline-COMMONS readout (coverage / §1.16 funding /
  upkeep) to the mesh/AT-proto WITHOUT a server-held key. ADR-2606272355 (actor
  self-publication seed) + ADR-2606272200.

  Mirror of the constellation membrane (danjo/keizu/kosatsu social_post) adapted to the
  lifeline-commons posture. A record (a coverage tally, a funding-plan summary, an upkeep
  summary) enters; it is DRAFTED into a dry-run post ONLY if:

    G1 — the post is a COMMONS COVERAGE MAP (isCommonsMap), never a shut-off / target list;
         a lifeline is a commons right, never withheld. Shut-off / per-person vocab refused.
    G2 — a commons, never a market: cashZero (the consumer is never billed; §1.16 in-kind).
    G5 — assessment/SIMULATION only (simOnly); never an actuation.
    sources — ≥2 provenance citations (the ADR + the actor's committed ledger/DID);
    no-server-key — server-held-key is false (the actor self-custodies its own key in its
                kotoba-mesh WASM runtime and signs there; the server never does, ADR-2605231525);
    R0-gate — the status is dry-run (a 'published' request REFUSES — live publication needs
                Council Lv6+ + operator + a member/actor signature, §1.12 / G6).

  Self-contained. Stdlib only. Deterministic — the seed grows on the mesh, not here."
  (:require [clojure.string :as str]))

(def disclaimer
  "【コモンズ被覆マップ — NOT a utility, NOT a shut-off list / 非断定】")

(def phase-init "init")
(def phase-drafted "drafted")
(def phase-refused "refused")

(def ^:private forbidden-tokens
  ["shutoff" "shut-off" "disconnect" "遮断" "停止通知" "個人名" "住所" "per-person" "target-list"])

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
                   "body"             (get state "body" "")
                   "sources"          (get state "sources" (get cs0 "sources"))
                   "requested_status" (lstrip-colon (get state "requested_status" (get cs0 "requested_status")))
                   "server_held_key"  (boolean (get state "server_held_key" (get cs0 "server_held_key"))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})
        body-lc (str/lower-case (str (get cs "subject") " " (get cs "body")))]
    (cond
      (< (count (get cs "sources")) 2)
      (refuse "sources: a post needs ≥2 provenance citations (ADR + committed ledger/DID)")

      (some (fn [t] (str/includes? body-lc (str/lower-case t))) forbidden-tokens)
      (refuse "G1: a commons coverage map is never a shut-off / per-person record")

      (get cs "server_held_key")
      (refuse "no-server-key: server-held-key must be false; the actor self-signs in its mesh runtime (ADR-2605231525)")

      (not= (get cs "requested_status") "dry-run")
      (refuse "R0-gate: only dry-run posts; live publication is Council Lv6+ + operator + member/actor-signature gated (§1.12/G6)")

      :else
      (let [payload {":post/subject" (get cs "subject")
                     ":post/body" (str disclaimer " " (get cs "subject"))
                     ":post/status" ":dry-run"
                     ":post/is-commons-map" true
                     ":post/cash-zero" true
                     ":post/sim-only" true
                     ":post/server-held-key" false
                     ":post/sources" (get cs "sources")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))
