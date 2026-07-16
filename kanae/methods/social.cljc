(ns kanae.methods.social
  "social.cljc — 鼎 (kanae) DRY-RUN self-publication projection. ADR-2606272355.

  Projects kanae's own HISTORY (source-cited fund-flow edges + aggregate fund-flow
  summaries assembled from danjo's fiscal datoms) and PROCEDURES (how a fund flow is
  traced — appropriation→outlay→recipient) into social posts (app.bsky.feed.post-shaped),
  enforcing the publication invariants in their projection home (mirror of the kanae
  fundFlowEdge :db/allowed + the social_post membrane state-machine). danjo finds,
  kanae renders:

    G4 — every post opens with the fiscal-flow-viz mirror disclaimer (isMirror=true),
         never speaks AS a government/auditor, never asserts a crime/violation/不正
         (nonAdjudicatingNotice=true). It narrates DISCLOSED amounts/flows as facts,
         never a verdict — a visualization may imply nothing the narrative does not state.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R0-gate — status is 'dry-run' only; `published` is unrepresentable. A live post
         needs Council Lv6+ + operator + a member/actor signature (build-live raises).
    G5 — the post carries the same ≥2 public gov.dataset.*/primary-source (sourceRecordCids)
         citations as the underlying record.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【観測ミラー / fiscal-flow map — NOT the government, NOT an auditor, 非断定】 "
       "国家が既に公開した一次会計記録から編んだ資金フローの集計です。danjo finds, kanae renders。"
       "不正の断定はしません。"))

;; ── private helpers — defined BEFORE use (Clojure forward-reference gotcha) ──

(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

;; fixed-point %  (f-string :.1f, HALF_EVEN over the exact binary value)
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (gov.dataset.* CIDs / sourceRecordCids)."
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

(def ^:private flow-class-ja
  {"appropriation"              "歳出予算の充当 (treasury→省庁)"
   "outlay"                     "実支出 (省庁→受給クラス)"
   "subaward"                   "再委託 (subaward)"
   "procurement-award"          "調達契約 (procurement award)"
   "intergovernmental-transfer" "政府間移転 (donor-juris→recipient-juris)"
   "aid-disbursement"           "援助支出 (aid disbursement)"
   "loan"                       "貸付 (loan)"
   "repayment"                  "返済 (repayment)"})

;; ── public projections ──

(defn draft-procedure-post
  "PROCEDURE post — how a single fund flow is traced per-stage: appropriation→outlay→recipient.
  kanae's honest disclosure that a flow is an assembled CHAIN of disclosed records, not a verdict."
  ([flow-class sources] (draft-procedure-post flow-class sources ""))
  ([flow-class sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【手続】資金フローの追跡: 歳出予算(appropriation: 国庫→省庁) → "
                   "実支出(outlay: 省庁→受給クラス) → 受給者(recipient)。"
                   "本フロー区分: " (get flow-class-ja flow-class (str flow-class)) "。"
                   "各 edge は ≥2 の公開一次記録(sourceRecordCids)に接地され、集計優先で描画。"
                   "出典 " (count srcs) " 件。")]
     (post (str "procedure:flow:" (lstrip-colon-id flow-class)) body srcs author))))

(defn draft-flow-edge-post
  "HISTORY post — a single fundFlowEdge (aggregate, factual, source-cited). Renders a
  disclosed appropriation/outlay/transfer as a from→to amount, never a per-individual element (G10)."
  ([edge sources] (draft-flow-edge-post edge sources ""))
  ([edge sources author]
   (let [srcs (enough-sources sources)
         from (get-in edge ["fromEndpoint" "label"] "—")
         to   (get-in edge ["toEndpoint" "label"] "—")
         fc   (get edge "flowClass")
         body (str DISCLAIMER "\n\n"
                   "【資金フロー】" (get flow-class-ja fc (str fc)) " " (get edge "period" "") ": "
                   from " → " to " "
                   (get edge "amount" "—") " " (get edge "currency" "")
                   " (" (get edge "jurisdiction" "—") ")。"
                   "出典 " (count srcs) " 件。")]
     (post (str "flow-edge:" fc ":" (get edge "period" "")) body srcs author))))

(defn draft-net-flow-post
  "HISTORY post — an aggregate net-flow summary for one fiscal-authority endpoint
  (inflow − outflow). A sum of DISCLOSED amounts, never a verdict on whether the flow
  is right or wrong (G4); aggregate-first by construction (G10)."
  ([net-flow sources] (draft-net-flow-post net-flow sources ""))
  ([net-flow sources author]
   (let [srcs (enough-sources sources)
         ep   (get net-flow :endpoint (get net-flow "endpoint" "—"))
         inf  (double (get net-flow :inflow (get net-flow "inflow" 0)))
         out  (double (get net-flow :outflow (get net-flow "outflow" 0)))
         net  (double (get net-flow :net (get net-flow "net" (- inf out))))
         posture (if (>= net 0.0) "純受給 (net receiver)" "純支出 (net source)")
         body (str DISCLAIMER "\n\n"
                   "【純フロー】" ep ": 流入 " (fmt-f inf 0) " / 流出 " (fmt-f out 0)
                   " → 純 " (fmt-f net 0) " (" posture ")。"
                   "出典 " (count srcs) " 件。")]
     (post (str "net-flow:" (lstrip-colon-id (str ep))) body srcs author))))

(defn build-live
  "live posting is outward-gated. Refuses by construction at R0; the live signature is
  the actor's own mesh-runtime key, presented (never server-held) under Council Lv6+ +
  operator gate (§1.12 / G11)."
  [& _args]
  (throw (ex-info (str "kanae R0: live social posting is Council Lv6+ + operator + member/actor-signature "
                       "gated (§1.12/G11). Only dry-run posts are producible offline; the live signature "
                       "happens actor-side in the kotoba-mesh runtime, never with a server key.") {})))
