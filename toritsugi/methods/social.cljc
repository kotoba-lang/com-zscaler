(ns toritsugi.methods.social
  "social.cljc — 取次 (toritsugi) DRY-RUN self-publication projection. ADR-2606272355.

  Projects toritsugi's own HISTORY (source-cited guidance/relay records — what procedures it
  walked a consenting member through, in aggregate) and PROCEDURES (the coded government
  procedures in its registry: 名称 / 所管 agency / 必要書類 forms / self-submit steps / 根拠法令)
  into social posts (app.bsky.feed.post-shaped), enforcing the publication invariants in
  their projection home (mirror of the toritsugi procedure registry boundary + the
  social_post membrane state-machine):

    G4 — every post opens with the concierge wayfinding disclaimer (isMirror=true). It is
         案内 (information + wayfinding), never advice, never 作成代理, never poses AS an
         official 自治体 channel or AS the government (nonAdjudicatingNotice=true). It tells
         the member where the 窓口 is and that THEY submit — never a verdict, never a 代理提出.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R1-gate (social_post only, founder Council Lv7+ 1/1 ratify 2026-07-16) — a `published`
         post still requires a non-blank member/actor-signed author (emit raises without one);
         `:dry-run` never needs one. External relay stays :pending-operator-transport absent
         a real `transport`.
    G5 — the post carries the same ≥2 public 根拠法令/official-source/provenance citations
         as the underlying record.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【行政手続き案内ミラー / wayfinding map — NOT the government, NOT an official 自治体 channel, NOT 法的助言, 本人提出が原則】 "
       "公開された 制度/手続き の 案内 (情報提供+道案内+入力補助) です。最終的な 提出・署名 は 申請者本人。"))

;; ── G5 source gate (define BEFORE the fns that use it — no forward refs in one file) ──
(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (根拠法令 / official portal URL / provenance)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G5: a post needs ≥2 public 根拠法令/official-source/provenance citations" {})))
    s))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"             ;; R0-gate — published is unrepresentable
   ":post/is-mirror" true                ;; G4
   ":post/non-adjudicating-notice" true  ;; G4
   ":post/server-held-key" false         ;; no-server-key (ADR-2605231525)
   ":post/author" author                 ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})             ;; G5

(defn- docs-line
  "Render a procedure's 必要書類 list (or a placeholder when resolve-at-guide-time)."
  [docs]
  (let [d (vec (remove str/blank? (map str (or docs []))))]
    (if (seq d)
      (str/join " / " d)
      "(必要書類は手続き時に resolve)")))

(defn draft-procedure-post
  "PROCEDURE post — one coded government procedure as wayfinding: 名称 / 所管 agency /
  channel / 必要書類 / 手数料 / 法定処理期間 / self-submit note, drawn from the procedure
  registry. toritsugi tells the member WHERE to go and that THEY submit (G15)."
  ([proc sources] (draft-procedure-post proc sources ""))
  ([proc sources author]
   (let [srcs (enough-sources sources)
         pid  (or (get proc "procedureId") (get proc :procedureId))
         body (str DISCLAIMER "\n\n"
                   "【手続】" (get proc "title" "(無題)")
                   " — 所管: " (get proc "authority" "—") "。"
                   "channel: " (get proc "channelType" "—")
                   (when-let [u (get proc "onlineUrl")] (when-not (str/blank? u) (str " (" u ")"))) "。"
                   "必要書類: " (docs-line (get proc "requiredDocuments")) "。"
                   "手数料: " (get proc "feeJpy" "—") " 円。"
                   "法定処理: " (get proc "statutoryProcessingDays" "—") " 日。"
                   "根拠法令: " (get proc "legalBasis" "—") "。"
                   "提出・署名は 申請者本人 (本人提出支援, G15)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "procedure:" (lstrip-colon-id pid)) body srcs author))))

(defn draft-guidance-post
  "HISTORY post — an aggregate guidance/relay record (how many members toritsugi walked
  through a given procedure category, factual, source-cited; no member PII — G6)."
  ([rec sources] (draft-guidance-post rec sources ""))
  ([rec sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【案内実績】" (get rec "label") ": "
                   (get rec "guidance_count" 0) " 件の 案内/伴走 を実施"
                   (when-let [c (get rec "category")] (str " (区分: " c ")")) "。"
                   "本人提出 default; 代行は gated R3 (G15)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "guidance:" (get rec "id")) body srcs author))))

(defn draft-eligibility-post
  "HISTORY post — a non-adjudicating eligibility/benefit-match note (you-may-be-eligible
  wayfinding, aggregate, cited; never a determination — the authority decides)."
  ([m sources] (draft-eligibility-post m sources ""))
  ([m sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【制度案内】" (get m "label") ": "
                   (get m "benefit") " の対象になりうる旨を案内"
                   " ↔ 受給可否の判断は 所管庁 (toritsugi は断定しない)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "eligibility:" (get m "id")) body srcs author))))

(defn emit
  "Emit an authorized post (R1, ADR-2606272355 + founder Council Lv7+ 1/1 ratify 2026-07-16 —
  social_post ONLY; the procedure-concierge cells (submit/代行 esp.) remain R0/R3-gated per
  their own manifest roadmap — this authorization does NOT touch toritsugi_submit/G15).
  Persisted to the canonical kotoba Datom log by the autorun caller; the EXTERNAL relay
  (aozora PDS / AT-Proto firehose) is delivered by `transport` only when an operator
  credential is present — absent one, the post stays :pending-operator-transport. Re-applies
  G4/G5 at the emission boundary. A :published status still requires a non-blank
  member/actor-signed author (G11); :dry-run never does."
  ([post] (emit post nil))
  ([post transport]
   (enough-sources (get post ":post/sources"))
   (when (and (= (get post ":post/status") ":published")
              (empty? (str (get post ":post/author"))))
     (throw (ex-info "G11: refuse to emit a :published post without a member/actor-signed author." {})))
   (let [relay (when transport (transport post))]
     {"subject" (get post ":post/subject")
      "status" (get post ":post/status")
      "substrate" "kotoba-datom-log"
      "external_relay" (or relay ":pending-operator-transport")
      "guards" ["G4:non-adjudicating-mirror" "G5:source-provenance" "G11:member-or-actor-signed"]})))
