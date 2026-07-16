(ns matsurigoto.methods.social
  "social.cljc — 政 (matsurigoto) DRY-RUN self-publication projection. ADR-2606272355.

  Projects matsurigoto's own STATECRAFT HISTORY (executed service slices — e.g. a tax-collect
  源泉徴収納付 remittance run, a civil-registry issuance batch; aggregate + transparent) and its
  PROCEDURES (the COFOG-classed service standards it implements, each with its OFFICIAL PUBLIC
  spec-basis) into social posts (app.bsky.feed.post-shaped), enforcing matsurigoto's three
  structural invariants in their projection home (mirror of the egov-execution-ontology
  :server-held-authority/:spec-derived/:operated-by gates + the social_post membrane):

    G3 authority-bearing — every post is the Kingdom's OWN statecraft (or the portable standard),
         published AUTHORITY-BEARING (authorityBearing=true) with :operated-by naming who governs
         (:etzhayyim-council = the Kingdom rules for real, Council-gated, Transparent §1.12, 1
         SBT=1 vote, on-chain | :adopting-government = a nation-state runs it). It NEVER disclaims
         being a government, and NEVER impersonates ANOTHER government (ooyake's mirror role is
         separate). 統治機構 (Charter §0.1), not a 'NOT the government' mirror.
    G1 no-operator-master-key — serverHeldKey=false; authority is the Council multisig + member
         signatures OR the adopting state's own keys — never an etzhayyim platform/operator key
         (ADR-2605231525). The actor self-custodies its own did:key in its kotoba-mesh WASM
         runtime and signs THERE; the server never does.
    R0-gate — status is 'dry-run' only; `published` is unrepresentable. A live e-Government post
         needs Council Lv6+ + operator + an EXTERNAL authority signature (build-live raises).
    G2 spec-derived-only — every post carries ≥2 official-public spec-basis citations (COFOG /
         ICAO 9303 / eIDAS / ISO 20022 / OpenCRVS / ISO 17442 / X-Road…). Proprietary GovTech
         vendor code is never a basis.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only — the growth
  (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【統治機構 / statecraft of the Kingdom of God (神の王国) — "
       "Transparent Religious Force §1.12, 1 SBT=1 vote, 完全 on-chain・open-source。"
       "公式仕様から導出した普遍サービス標準(G2)。権威 = Council multisig か採用国家の鍵(G1, platform key 不可)。】"))

(def operated-by-allowed #{":etzhayyim-council" ":adopting-government"})

;; ── private helpers (Clojure gotcha: defined BEFORE first use) ──

(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

(defn- add-colon [s]
  (let [t (str s)]
    (if (str/starts-with? t ":") t (str ":" t))))

(defn- enough-sources
  "G2 — a post needs ≥2 non-blank official-public spec-basis / source citations."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G2 spec-derived-only: a post needs ≥2 official-public spec-basis/source citations" {})))
    s))

(defn- check-operated-by
  "G3 — authority must be borne by a named governing organ ∈ {council, adopting-state}."
  [operated-by]
  (let [ob (add-colon operated-by)]
    (when-not (operated-by-allowed ob)
      (throw (ex-info "G3 authority-bearing: :operated-by must be :etzhayyim-council or :adopting-government" {:operated-by ob})))
    ob))

(defn- post
  "Assemble an authority-bearing networkPost record with every invariant pinned.
  status is ALWAYS dry-run; serverHeldKey is ALWAYS false."
  [subject body sources operated-by author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"                  ;; R0-gate — published is unrepresentable
   ":post/authority-bearing" true             ;; G3 — borne, never disclaimed
   ":post/spec-derived" true                  ;; G2
   ":post/operated-by" (check-operated-by operated-by) ;; G3 — names WHO governs
   ":post/server-held-key" false              ;; G1 no-operator-master-key (ADR-2605231525)
   ":post/author" author                      ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})                  ;; G2

(defn draft-procedure-post
  "PROCEDURE post — a COFOG-classed service STANDARD matsurigoto implements, with its official
  public spec-basis (G2). Authority-bearing: the Kingdom's own statecraft procedure (or the
  portable standard a nation-state may adopt). `svc` is a service map from
  data/cofog-standard.kotoba.edn (namespaced string keys)."
  ([svc] (draft-procedure-post svc ":etzhayyim-council" ""))
  ([svc operated-by author]
   (let [srcs (enough-sources (get svc ":egov.service/spec-basis"))
         body (str DISCLAIMER "\n\n"
                   "【手続/procedure】" (get svc ":egov.service/ja") " ("
                   (get svc ":egov.service/en") ") · COFOG " (get svc ":egov.service/cofog")
                   " · module " (get svc ":egov.service/module")
                   " · 成熟度 " (lstrip-colon-id (get svc ":egov.service/maturity")) "。"
                   "仕様根拠(G2) " (count srcs) " 件: " (str/join " / " srcs) "。")]
     (post (str "procedure:" (get svc ":egov.service/id")) body srcs operated-by author))))

(defn draft-statecraft-post
  "HISTORY post — an EXECUTED statecraft slice (aggregate, transparent): e.g. a tax-collect
  源泉徴収納付 remittance run or a civil-registry issuance batch. Authority-bearing, on-chain,
  source-cited. `slice` carries string keys; `sources` are the spec-basis the slice executed under."
  ([slice sources] (draft-statecraft-post slice sources ":etzhayyim-council" ""))
  ([slice sources operated-by author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【施政/statecraft】" (get slice "label") " (COFOG " (get slice "cofog") ") "
                   (when-let [p (get slice "period")] (str "FY" p " ")) ": "
                   (get slice "summary")
                   (when-let [n (get slice "count")] (str " · " n " 件")) "。"
                   "仕様根拠(G2) " (count srcs) " 件。")]
     (post (str "statecraft:" (get slice "id")) body srcs operated-by author))))

(defn build-live
  "live posting is outward-gated. Refuses by construction at R0; the live signature is the
  actor's own mesh-runtime key (present-only, never server-held) OR the adopting state's own
  key — under Council Lv6+ + operator gate (§1.12 / G11). The Kingdom governs THROUGH its
  constitutional organs (Council multisig + 1 SBT=1 vote), never through a platform/operator key."
  [& _args]
  (throw (ex-info (str "matsurigoto R0: live e-Government social posting is Council Lv6+ + operator + "
                       "external-authority-signature gated (§1.12/G11). Authority = the Council multisig "
                       "(principal A :etzhayyim-council) OR the adopting state's own key (principal B "
                       ":adopting-government) — NEVER a server/operator platform key (G1, ADR-2605231525). "
                       "Only dry-run drafts are producible offline; the live signature happens actor-side "
                       "in the kotoba-mesh runtime.") {})))
