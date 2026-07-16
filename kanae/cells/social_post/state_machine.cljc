(ns kanae.cells.social-post.state-machine
  "Phase state machine for the 鼎 (kanae) social_post cell — the publication membrane
  that lets the actor self-publish its HISTORY and PROCEDURES to the mesh/AT-proto
  WITHOUT a server-held key. ADR-2606272355 (actor self-publication seed).

  Mirror of the constellation membrane (danjo.cells.social-post.state-machine,
  keizu / kosatsu social_post) adapted to kanae's fiscal-flow-VISUALIZATION posture
  (danjo finds, kanae renders). A record (a fund-flow edge, an aggregate fund-flow
  summary, or a flow-tracing procedure note) enters; it is DRAFTED into a dry-run
  post ONLY if:

    G5(kanae) — ≥2 public gov.dataset.* / primary-source (sourceRecordCids) citations
                are present;
    G4(kanae) — the post is a non-adjudicating mirror (isMirror), opening with the
                fiscal-flow-viz disclaimer; it narrates DISCLOSED amounts/flows as
                facts, never a verdict (danjo finds, kanae renders);
    no-server-key — server-held-key is false (the actor self-custodies its own key in
                its kotoba-mesh WASM runtime and signs there; the server never does,
                ADR-2605231525);
    R0-gate — the status is dry-run (a 'published' request REFUSES — live publication
                needs Council Lv6+ + operator + a member/actor signature, §1.12 / G11).

  Self-contained. Stdlib only. Deterministic — the seed grows on the mesh, not here."
  (:require [clojure.string :as str]))

(def disclaimer
  "【観測ミラー / fiscal-flow map — NOT the government, NOT an auditor, 非断定（danjo finds, kanae renders）】")

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
      (refuse "G5(kanae): a post needs ≥2 public gov.dataset.*/primary-source citations")

      (get cs "server_held_key")
      (refuse "no-server-key: server-held-key must be false; the actor self-signs in its mesh runtime (ADR-2605231525)")

      (not= (get cs "requested_status") "dry-run")
      (refuse "R0-gate: only dry-run posts; live publication is Council Lv6+ + operator + member/actor-signature gated (§1.12/G11)")

      :else
      (let [payload {":post/subject" (get cs "subject")
                     ":post/body" (str disclaimer " " (get cs "subject"))
                     ":post/status" ":dry-run"
                     ":post/is-mirror" true
                     ":post/non-adjudicating-notice" true
                     ":post/server-held-key" false
                     ":post/sources" (get cs "sources")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))
