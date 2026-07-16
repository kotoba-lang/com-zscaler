(ns junkan.methods.social
  "social.cljc — 循環 (junkan) DRY-RUN self-publication projection. ADR-2606272355.

  junkan is ANALYSIS-ONLY and has NO outward channel by its own discipline (G4). This
  projection is the narrowest possible exception: it projects junkan's own HISTORY
  (on-record governance-asymmetry instruments — the law/institution + 誰が定めたか
  :enactor + 経緯 :origin + 関係者 :stakeholders, as on-the-record PUBLIC FACTS) and its
  FINDINGS (DISCLOSED-HYPOTHESIS loop read-offs — which feedback loop is spinning
  好循環/悪循環 — and Meadows leverage CANDIDATES) into social posts
  (app.bsky.feed.post-shaped), enforcing the publication invariants in their projection
  home (mirror of the ontology.junkan-gov constraints + the social_post membrane
  state-machine):

    G7 — every post opens with the analysis-only mirror disclaimer (isMirror=true),
         never speaks AS a government, never asserts a crime/violation/不正/verdict,
         never proven causation, never a directive (nonAdjudicatingNotice=true). It
         narrates an on-record FACT or a DISCLOSED HYPOTHESIS — never a verdict.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R0-gate — status is 'dry-run' only; `published` is unrepresentable. A live post
         needs Council Lv6+ + operator + a member/actor signature and is performed via
         ossekai/kataribe on junkan's behalf, never by junkan (build-live raises).
    G5 — the post carries ≥2 public primary-source/on-record citations.

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here.
  Clojure gotcha respected: every private helper is defined BEFORE its use."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【分析ミラー / systems-dynamics read-off — 分析のみ・断定なし・開示された仮説 (proven causation ではない)】 "
       "公開された一次記録から編んだ事実 + 開示された仮説です。因果の断定も指示もしません。"))

;; ── private helpers (defined BEFORE use — no forward references) ──
(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

;; fixed-point %  (f-string :.1f, HALF_EVEN over the exact binary value)
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (primary URLs / on-record refs)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G5: a post needs ≥2 public primary-source/on-record citations" {})))
    s))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"             ;; R0-gate — published is unrepresentable
   ":post/is-mirror" true                ;; G7
   ":post/non-adjudicating-notice" true  ;; G7 — never a verdict, never proven causation
   ":post/server-held-key" false         ;; no-server-key (ADR-2605231525)
   ":post/author" author                 ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})             ;; G5

(def ^:private regime-ja
  {:virtuous      "好循環 (virtuous — narrowing the citizen↔state asymmetry)"
   :vicious       "悪循環 (vicious — widening the citizen↔state asymmetry)"
   :neutral       "中立 (neutral)"
   :transitioning "遷移中 (transitioning — the stock is contested)"})

(defn- regime-label [r]
  (get regime-ja (keyword (lstrip-colon-id (name (or r :neutral)))) (str r)))

(defn draft-instrument-post
  "HISTORY post — a single on-record governance-asymmetry instrument (a law/institution
  + 誰が定めたか :enactor + 経緯 :origin + 関係者 :stakeholders), narrated as the
  on-the-record PUBLIC FACTS they are (not a verdict). Drawn from
  kotoba/seed.governance-asymmetry.edn."
  ([instr sources] (draft-instrument-post instr sources ""))
  ([instr sources author]
   (let [srcs (enough-sources sources)
         stk  (->> (or (:stakeholders instr) []) (map str) (str/join " / "))
         body (str DISCLAIMER "\n\n"
                   "【制度】" (:name instr) " (" (:jurisdiction instr) ", " (:year instr) ")。"
                   "定めた主体(誰が): " (:enactor instr) "。"
                   "経緯: " (:origin instr) "。"
                   (when (seq stk) (str "関係者: " stk "。"))
                   "出典 " (count srcs) " 件。")]
     (post (str "instrument:" (lstrip-colon-id (str (:id instr)))) body srcs author))))

(defn draft-loop-post
  "FINDING post — a single DISCLOSED-HYPOTHESIS feedback-loop read-off (好循環/悪循環/
  中立/遷移). It states the loop's current regime as junkan's HYPOTHESIS, never proven
  causation, never a verdict."
  ([lp sources] (draft-loop-post lp sources ""))
  ([lp sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【ループ仮説】" (:name lp) " ("
                   (name (keyword (lstrip-colon-id (str (:type lp))))) " loop, "
                   "dominant stock: " (name (keyword (lstrip-colon-id (str (:dominant lp))))) ")。"
                   "現在の regime(仮説): " (regime-label (:regime lp)) "。"
                   "drive(net 圧力, 仮説): " (fmt-f (or (:drive lp) 0) 3) "。"
                   "相関・構造符号のみ — 因果は断定しません。"
                   "出典 " (count srcs) " 件。")]
     (post (str "loop:" (lstrip-colon-id (str (:id lp)))) body srcs author))))

(defn draft-leverage-post
  "FINDING post — a single Meadows leverage CANDIDATE (with uncertainty; never a
  directive, never a prescription, G11). It surfaces where a loop could be amplified
  or flipped, as a candidate the reader may weigh — never an instruction."
  ([cand sources] (draft-leverage-post cand sources ""))
  ([cand sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【leverage 候補】" (:name cand) " (" (:jurisdiction cand) ")。"
                   "役割: " (name (keyword (lstrip-colon-id (str (:role cand))))) "。"
                   "Meadows level: " (:meadows cand) " (深いほど低番号)。"
                   "開示スコア: " (fmt-f (or (:score cand) 0) 3) "。"
                   "不確実性つきの CANDIDATE であって directive ではありません (prescription なし)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "leverage:" (lstrip-colon-id (str (:id cand)))) body srcs author))))

(defn build-live
  "live posting is outward-gated. Refuses by construction at R0; junkan has NO outward
  channel of its own (G4) — the live signature is the actor's own mesh-runtime key,
  presented (never server-held) under a Council Lv6+ + operator gate, and the broadcast
  is performed via ossekai/kataribe on junkan's behalf, never by junkan (§1.12/G11/G13)."
  [& _args]
  (throw (ex-info (str "junkan R0: live social posting is Council Lv6+ + operator + member/actor-signature "
                       "gated (§1.12/G11/G13) and is performed via ossekai/kataribe on junkan's behalf, never "
                       "by junkan (G4 analysis-only). Only dry-run posts are producible offline; the live "
                       "signature happens actor-side in the kotoba-mesh runtime, never with a server key.") {})))
