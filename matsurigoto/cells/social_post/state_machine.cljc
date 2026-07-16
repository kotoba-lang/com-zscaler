(ns matsurigoto.cells.social-post.state-machine
  "Phase state machine for the 政 (matsurigoto) social_post cell — the publication membrane
  that lets the actor self-publish its STATECRAFT HISTORY and PROCEDURES to the mesh/AT-proto
  WITHOUT any operator/platform master key. ADR-2606272355 (actor self-publication seed).

  matsurigoto is NOT a non-adjudicating observational mirror (that is ooyake's read-side role).
  It is the **e-Government EXECUTION stack (政)** — the STATECRAFT EXECUTION of the Kingdom of
  God (神の王国, Charter §0.1): etzhayyim really governs its OWN covenant-members, and the same
  COFOG-derived standard is portable for an adopting nation-state. So a post here is
  AUTHORITY-BEARING (it publishes the Kingdom's own statecraft / the portable standard,
  transparently), NOT a disclaimer that 'etzhayyim isn't a government'.

  A record (an executed statecraft slice or a COFOG service-standard procedure) enters; it is
  DRAFTED into a dry-run post ONLY if matsurigoto's three structural invariants hold:

    G2 spec-derived-only — ≥2 OFFICIAL PUBLIC spec-basis / source citations are present
                (COFOG / ICAO 9303 / eIDAS / ISO 20022 / OpenCRVS / ISO 17442 / X-Road…);
    G3 authority-bearing — :operated-by names WHO governs, ∈ {:etzhayyim-council (the Kingdom
                rules for real, Council-gated, Transparent §1.12) | :adopting-government (the
                nation-state runs it)}; authority is borne, never disclaimed;
    G1 no-operator-master-key — server_held_key is false (= :server-held-authority const false).
                Authority is ALWAYS the Council multisig + 1 SBT=1 vote member signatures, OR the
                adopting state's own keys — never an etzhayyim platform/operator key
                (ADR-2605231525). The Council is a member-elected organ, not 'the server';
    R0-gate — the status is dry-run (a 'published' request REFUSES — live e-Government
                publication needs Council Lv6+ + operator + an EXTERNAL authority signature,
                §1.12 / G11).

  Self-contained. Stdlib only. Deterministic — the seed grows on the mesh, not here."
  (:require [clojure.string :as str]))

(def disclaimer
  (str "【統治機構 / statecraft of the Kingdom of God (神の王国) — "
       "Transparent Religious Force §1.12, 1 SBT=1 vote, 完全 on-chain・open-source。"
       "権威 = Council multisig か採用国家の鍵(platform key 不可, G1)。公式仕様から導出(G2)。】"))

(def operated-by-allowed #{":etzhayyim-council" ":adopting-government"})

(def phase-init "init")
(def phase-drafted "drafted")
(def phase-refused "refused")

(def state-defaults
  {"phase"            phase-init
   "subject"          ""
   "sources"          []
   "operated_by"      ":etzhayyim-council"
   "requested_status" "dry-run"
   "server_held_key"  false
   "payload"          {}
   "refusal"          ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- lstrip-colon [s]
  (str/replace (str s) #"^:+" ""))

(defn- add-colon [s]
  (let [t (str s)]
    (if (str/starts-with? t ":") t (str ":" t))))

(defn transition-to-drafted
  "Drive one record toward a dry-run post payload, or refuse with the failed invariant.
  Pure: (state) -> {\"cell_state\" {…}}."
  [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "subject"          (get state "subject" (get cs0 "subject"))
                   "sources"          (get state "sources" (get cs0 "sources"))
                   "operated_by"      (add-colon (get state "operated_by" (get cs0 "operated_by")))
                   "requested_status" (lstrip-colon (get state "requested_status" (get cs0 "requested_status")))
                   "server_held_key"  (boolean (get state "server_held_key" (get cs0 "server_held_key"))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (< (count (get cs "sources")) 2)
      (refuse "G2 spec-derived-only: a post needs ≥2 official-public spec-basis/source citations")

      (get cs "server_held_key")
      (refuse "G1 no-operator-master-key: server-held-key must be false; authority = Council multisig or adopting-state keys, never a platform/operator key (ADR-2605231525)")

      (not (operated-by-allowed (get cs "operated_by")))
      (refuse "G3 authority-bearing: :operated-by must name WHO governs ∈ {:etzhayyim-council, :adopting-government} — authority is borne, never disclaimed")

      (not= (get cs "requested_status") "dry-run")
      (refuse "R0-gate: only dry-run posts; live e-Government publication is Council Lv6+ + operator + external-authority-signature gated (§1.12/G11)")

      :else
      (let [payload {":post/subject" (get cs "subject")
                     ":post/body" (str disclaimer " " (get cs "subject"))
                     ":post/status" ":dry-run"
                     ":post/authority-bearing" true
                     ":post/spec-derived" true
                     ":post/operated-by" (get cs "operated_by")
                     ":post/server-held-key" false
                     ":post/sources" (get cs "sources")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))
