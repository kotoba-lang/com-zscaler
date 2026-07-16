(ns danjo.methods.social
  "social.cljc — 弾正 (danjo) DRY-RUN self-publication projection. ADR-2606272355.

  Projects danjo's own HISTORY (source-cited oversight observations + revenue-ledger
  lines) and PROCEDURES (how each tax's use is — or is not — traceable per-yen) into
  social posts (app.bsky.feed.post-shaped), enforcing the publication invariants in
  their projection home (mirror of danjo-oversight-ontology :db/allowed + the
  social_post membrane state-machine):

    G4 — every post opens with the accountability-mirror disclaimer (isMirror=true),
         never speaks AS a government, never asserts a crime/violation/不正
         (nonAdjudicatingNotice=true). It narrates facts, never a verdict.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R1-gate (social_post only, founder Council Lv7+ 1/1 ratify 2026-07-16) — a `published`
         post still requires a non-blank member/actor-signed author (emit raises without one);
         `:dry-run` never needs one. External relay stays :pending-operator-transport absent
         a real `transport`.
    G5 — the post carries the same ≥2 public gov.dataset.*/primary-source citations
         as the underlying record.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【観測ミラー / accountability map — NOT the government, NOT a prosecutor, 非断定】 "
       "国家が既に公開した一次記録から編んだ事実の集計です。不正の断定はしません。"))

(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

;; ── fixed-point %  (f-string :.1f, HALF_EVEN over the exact binary value) ──
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (gov.dataset.* CIDs / primary URLs)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G5: a post needs ≥2 public gov.dataset.*/primary-source citations" {})))
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

(def ^:private earmark-ja
  {":general"           "一般会計・普通税(資金は代替可能 — 特定1円の使途追跡 不可)"
   ":statutory-purpose" "目的税的(法が充当先を定めるが一般会計内のため資金は依然 fungible — 追跡 不可)"
   ":special-account"   "特別会計の特定財源(閉じた会計境界内で繰入→歳出を1円単位で照合 可)"})

(defn draft-procedure-post
  "PROCEDURE post — how a single tax's use is (or is not) traceable per-yen. danjo's
  honest middle-category disclosure, drawn from data/jp-national-taxes.edn."
  ([tax sources] (draft-procedure-post tax sources ""))
  ([tax sources author]
   (let [srcs (enough-sources sources)
         em   (get tax ":earmark")
         body (str DISCLAIMER "\n\n"
                   "【手続】" (get tax ":ja") " (" (get tax ":en") ") の使途追跡可能性: "
                   (get earmark-ja em (str em)) "。"
                   "徴収: " (get tax ":collected-by" "—") "。"
                   "出典 " (count srcs) " 件。")]
     (post (str "procedure:tax:" (lstrip-colon-id (get tax ":id"))) body srcs author))))

(defn draft-revenue-post
  "HISTORY post — a single revenue-ledger line (aggregate, factual, source-cited)."
  ([line sources] (draft-revenue-post line sources ""))
  ([line sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【歳入】" (get line "label") " FY" (get line "fiscal_year") ": "
                   (get line "amount_jpy") " 円"
                   (when-let [s (get line "share")] (str " (全体の " (fmt-f (* s 100) 1) "%)")) "。"
                   "出典 " (count srcs) " 件。")]
     (post (str "revenue:" (get line "id")) body srcs author))))

(defn draft-observation-post
  "HISTORY post — a non-adjudicating cross-reference discrepancy observation."
  ([obs sources] (draft-observation-post obs sources ""))
  ([obs sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【観測】" (get obs "label") ": "
                   (get obs "statement") " ↔ " (get obs "record")
                   " の差異を事実として記録(断定なし)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "observation:" (get obs "id")) body srcs author))))

(defn emit
  "Emit an authorized post (R1, ADR-2606272355 + founder Council Lv7+ 1/1 ratify 2026-07-16 —
  social_post ONLY; danjo's ingest/analysis cells remain R0 per their own Council Lv6+ ≥3
  per-cell gate). Persisted to the canonical kotoba Datom log by the autorun caller; the
  EXTERNAL relay (aozora PDS / AT-Proto firehose) is delivered by `transport` only when an
  operator credential is present — absent one, the post stays :pending-operator-transport.
  Re-applies G4/G5 at the emission boundary. A :published status still requires a non-blank
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
