(ns tsumugi.methods.social
  "social.cljc — 紡ぎ (tsumugi) DRY-RUN self-publication projection. ADR-2606272355.

  Projects tsumugi's own HISTORY — the 取-concentration findings over public power-entities
  (each entity's 取-holding = the integral of its incident 縁, computed on read, N1) and the
  RELEASE routing (release-target = max(0, held−1), routed toward 解放) — and its 産官学報
  scale clusters into social posts (app.bsky.feed.post-shaped), enforcing the publication
  invariants in their projection home (mirror of the tsumugi gates G1/G2/G4/G5/G7 + the
  social_post membrane state-machine):

    G2 — every post opens with the 取-concentration release-map disclaimer (isMirror=true),
         narrates the edge-integral of incident 縁, never a per-soul score, never a verdict
         (nonAdjudicatingNotice=true), never a target-list. It narrates facts, never a verdict.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    R0-gate(G7) — status is 'dry-run' only; `published` is unrepresentable. A live post
         needs Council Lv6+ + operator + a member/actor signature (build-live raises).
    G5 — the post carries the same ≥2 public primary-source/authoritative citations as the
         underlying record (sourcing honesty; no fabricated coverage).
    G1 — power-only / person-excluded: a subject is an org / public-seat / locality id, never
         a private individual (no-doxxing).

  Pure fns; deterministic; string-keyed post records (house style). Stdlib only —
  the growth (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]))

(def DISCLAIMER
  (str "【観測ミラー / 取-concentration release map — NOT a target-list, 非断定・person-excluded】 "
       "公開された一次記録から編んだ 縁 の集計(取の集中度は incident edge の積分、read 時計算)です。"
       "不正・有罪の断定はしません。解放へ routed する地図であって、対象リストではありません。"))

(defn- lstrip-colon-id [s]
  (str/replace (str s) #"^:+" ""))

;; ── fixed-point %  (f-string :.1f, HALF_EVEN over the exact binary value) ──
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G5 — a post needs ≥2 non-blank public-source citations (primary URLs / authoritative CIDs / QIDs)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G5: a post needs ≥2 public primary-source/authoritative citations" {})))
    s))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"             ;; R0-gate(G7) — published is unrepresentable
   ":post/is-mirror" true                ;; G2
   ":post/non-adjudicating-notice" true  ;; G2
   ":post/server-held-key" false         ;; no-server-key (ADR-2605231525)
   ":post/author" author                 ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})             ;; G5

(defn draft-concentration-post
  "HISTORY post — a single 取-concentration finding (aggregate, edge-primary, source-cited).
  `held` is the entity's 取-holding = the integral of its incident 縁 (N1, computed on read);
  the subject is an institutional/public-seat/locality id, never a private person (G1)."
  ([finding sources] (draft-concentration-post finding sources ""))
  ([finding sources author]
   (let [srcs (enough-sources sources)
         held (double (get finding "held" 0.0))
         body (str DISCLAIMER "\n\n"
                   "【取-concentration】" (get finding "label" (get finding "id")) ": "
                   "取-holding(edge-integral) = " (fmt-f held 2)
                   (when-let [r (get finding "rank")] (str " (rank #" r ")")) "。"
                   "release-target = max(0, held−1) = " (fmt-f (max 0.0 (- held 1.0)) 2)
                   " → 解放(release)へ routed。"
                   "出典 " (count srcs) " 件。")]
     (post (str "concentration:" (lstrip-colon-id (get finding "id"))) body srcs author))))

(defn draft-release-post
  "PROCEDURE post — the RELEASE routing for one 取-holder. tsumugi's honest disclosure of how
  concentrated 取-holding is folded onto release-priority (the rectifier), never a verdict."
  ([finding sources] (draft-release-post finding sources ""))
  ([finding sources author]
   (let [srcs (enough-sources sources)
         held (double (get finding "held" 0.0))
         route (lstrip-colon-id (get finding "route" "release"))
         body (str DISCLAIMER "\n\n"
                   "【release routing】" (get finding "label" (get finding "id")) " の 取-holding "
                   (fmt-f held 2) " は route=" route " として 解放-priority に整流(edge-primary, N1)。"
                   "対象リストではなく、集中の解放へ向かう順序です。"
                   "出典 " (count srcs) " 件。")]
     (post (str "release:" (lstrip-colon-id (get finding "id"))) body srcs author))))

(defn draft-cluster-post
  "HISTORY post — a 産官学報 cross-sector concentration cluster (ADR-2606092000), aggregate +
  seat/institution-level (S2 person-excluded), routed to OPENING. Factual, source-cited."
  ([cluster sources] (draft-cluster-post cluster sources ""))
  ([cluster sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【産官学報】" (get cluster "locality" (get cluster "id")) " cross-sector cluster: "
                   "concentration = " (fmt-f (double (get cluster "concentration" 0.0)) 2)
                   (when-let [b (get cluster "broker")] (str "、top broker(seat/org) = " b)) "。"
                   "席・組織レベルの集計(私人不在 S2)、OPENING へ routed(non-adjudicating S5)。"
                   "出典 " (count srcs) " 件。")]
     (post (str "cluster:" (lstrip-colon-id (get cluster "id"))) body srcs author))))

(defn build-live
  "live posting is outward-gated. Refuses by construction at R0; the live signature is
  the actor's own mesh-runtime key, presented (never server-held) under Council Lv6+ +
  operator gate (§1.12 / G11)."
  [& _args]
  (throw (ex-info (str "tsumugi R0: live social posting is Council Lv6+ + operator + member/actor-signature "
                       "gated (§1.12/G11). Only dry-run posts are producible offline; the live signature "
                       "happens actor-side in the kotoba-mesh runtime, never with a server key.") {})))
