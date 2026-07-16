(ns himotoki.methods.social
  "social.cljc — 繙き (himotoki) DRY-RUN self-publication projection. ADR-2606272355.

  Mirror of danjo.methods.social, projecting himotoki's OWN HISTORY (filed disclosure-
  request / disclosure-received records — aggregated, own-data-only) and PROCEDURES (the
  DSAR/FOIA disclosure procedures: the root-of-right statute, the coded target registry,
  and how a consenting member self-files) into social posts (app.bsky.feed.post-shaped),
  enforcing the publication invariants in their projection home (mirror of the himotoki
  manifest gates + the social_post membrane state-machine):

    G4 — every post opens with the disclosure-filer disclaimer (isMirror=true), never
         speaks AS a controller/agency, never asserts a crime/violation/不正
         (nonAdjudicatingNotice=true). It narrates its own procedures + own-data results,
         never a verdict.
    own-data-only — himotoki publishes ONLY about its OWN disclosure procedures and the
         requesting member's OWN-data results (ownDataOnly=true). A third party's personal
         data is NEVER projected into a post (G3/G6/N13); disclosed PII stays in the
         com.etzhayyim.encrypted.* DID-bound envelope, never inline, never here. The
         HISTORY projection is AGGREGATE counts only — no requester identity, no envelope
         contents, no disclosed records.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R1-gate (social_post only, founder Council Lv7+ 1/1 ratify 2026-07-16) — a `published`
         post still requires a non-blank member/actor-signed author (emit raises without one);
         `:dry-run` never needs one. External relay stays :pending-operator-transport absent
         a real `transport`.
    G5 — the post carries ≥2 public statute / target-registry / primary-source citations.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here.

  Clojure note: private helpers are defined BEFORE their first use (no forward refs)."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【開示請求ミラー / disclosure-filer map — consent-bound, own-data-only, 第三者PII非掲載, 非断定】 "
       "繙きが自分の開示請求 手続きと、同意した member 本人の OWN-data 結果(集計のみ)を編んだ事実です。"
       "第三者の個人データは掲載せず、不正の断定もしません。"))

(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

;; ── fixed-point %  (f-string :.1f, HALF_EVEN over the exact binary value) ──
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (root-of-right statute / verified
  target-registry CIDs / primary URLs)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G5: a post needs ≥2 public statute/target-registry/primary-source citations" {})))
    s))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"             ;; R0-gate — published is unrepresentable
   ":post/is-mirror" true                ;; G4
   ":post/non-adjudicating-notice" true  ;; G4
   ":post/own-data-only" true            ;; G3/G6/N13 — never a third party's PII
   ":post/server-held-key" false         ;; no-server-key (ADR-2605231525)
   ":post/author" author                 ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})             ;; G5

(def ^:private kind-ja
  {":DSAR" "個人情報開示請求 (DSAR — own-data-only, 本人の同意 + DID/SBT binding)"
   ":FOIA" "行政文書開示請求 (FOIA — 公的記録, 何人も請求可)"})

(defn draft-procedure-post
  "PROCEDURE post — how a consenting member self-files one disclosure request against a
  coded, verified target. himotoki's UPL-bounded disclosure of the route (files + tracks,
  never advises), drawn from the disclosureTarget registry."
  ([target sources] (draft-procedure-post target sources ""))
  ([target sources author]
   (let [srcs (enough-sources sources)
         dsar (boolean (get target ":dsar"))
         kw   (if dsar ":DSAR" ":FOIA")
         body (str DISCLAIMER "\n\n"
                   "【手続】" (get target ":organization") " への "
                   (get kind-ja kw (str kw)) "。"
                   "根拠法: " (get target ":legal-basis" "—") "。"
                   "窓口: " (get target ":channel-type" "—") "。"
                   "法定期限: " (or (get target ":statutory-deadline-days") "規定なし(遅滞なく/timely)") " 日。"
                   "member 本人が lawful な official channel で self-file (himotoki は files+tracks のみ; "
                   "法的助言は chigiri 経由)。出典 " (count srcs) " 件。")]
     (post (str "procedure:" (if dsar "dsar" "foia") ":"
                (lstrip-colon-id (get target ":regime"))) body srcs author))))

(defn draft-filing-post
  "HISTORY post — an AGGREGATE of filed disclosure requests over a window (factual,
  source-cited, own-data-only). No requester identity, no envelope contents — counts only."
  ([rollup sources] (draft-filing-post rollup sources ""))
  ([rollup sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【履歴・filed】" (get rollup "window") ": "
                   "DSAR " (get rollup "dsar_count" 0) " 件 / FOIA " (get rollup "foia_count" 0) " 件 を filing。"
                   (when-let [v (get rollup "verified_target_count")]
                     (str " verified target " v " 件に限定(G14)。"))
                   "(集計のみ — requester identity も開示記録も非掲載)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "history:filed:" (get rollup "id")) body srcs author))))

(defn draft-disclosure-post
  "HISTORY post — an AGGREGATE of disclosures RECEIVED back (factual, own-data-only). The
  member's own data lands ONLY in the encrypted DID-bound envelope; this post records only
  the count + statutory-response posture, never the disclosed content."
  ([rollup sources] (draft-disclosure-post rollup sources ""))
  ([rollup sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【履歴・disclosed】" (get rollup "window") ": "
                   "開示受領 " (get rollup "received_count" 0) " 件"
                   (when-let [o (get rollup "on_time_share")]
                     (str " (うち法定期限内 " (fmt-f (* o 100) 1) "%)")) "。"
                   "受領した本人データは com.etzhayyim.encrypted.* DID-bound envelope のみに格納 — "
                   "本文は非掲載・再開示なし(G6/N13)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "history:disclosed:" (get rollup "id")) body srcs author))))

(defn emit
  "Emit an authorized post (R1, ADR-2606272355 + founder Council Lv7+ 1/1 ratify 2026-07-16 —
  social_post ONLY; the disclosure-filing/dispatch cells remain R0, no cells run, no dispatch,
  pending their own Council Lv6+ ≥3 per-cell gate). Persisted to the canonical kotoba Datom
  log by the autorun caller; the EXTERNAL relay (aozora PDS / AT-Proto firehose) is delivered
  by `transport` only when an operator credential is present — absent one, the post stays
  :pending-operator-transport. Re-applies G4/G5 at the emission boundary. A :published status
  still requires a non-blank member/actor-signed author (G11); :dry-run never does."
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
