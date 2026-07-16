#!/usr/bin/env bb
;; iriai 入会 — DRY-RUN self-publication membrane (social posts). ADR-2606272355 + 2606272200.
(ns iriai.methods.social
  "social.cljc — iriai 入会 DRY-RUN self-publication projection (ADR-2606272355,
  the actor self-publication seed; mirrors danjo.methods.social adapted to the
  lifeline-commons posture).

  Projects iriai's OWN readout — the lifeline COVERAGE map (infra), the §1.16 IN-KIND
  FUNDING plan (資金), and the predictive UPKEEP schedule (運用/予測) — into social posts
  (app.bsky.feed.post-shaped), enforcing the publication invariants in their projection
  home (mirror of the iriai gates + the social_post membrane state-machine):

    G1 — every post is a COMMONS COVERAGE MAP (isCommonsMap=true), NEVER a target-list
         or a shut-off list. A lifeline is a commons right (入会権) — never withheld as
         leverage, never disconnected. Shut-off / per-person vocab is refused at draft.
    G2 — a commons, never a market: cashZero=true. Upkeep + delivery are §1.16 IN-KIND;
         the consumer is never billed. No tariff/price is ever stated.
    G5 — assessment / SIMULATION only: simOnly=true; the post narrates the MAP/plan,
         never an actuation. status is 'dry-run' only; `published` is unrepresentable.
    no-server-key — serverHeldKey=false; the actor self-custodies its key in its
         kotoba-mesh WASM runtime and signs THERE; the server never does (ADR-2605231525).
    sources — a post carries ≥2 provenance refs (the ADR + the actor's own committed
         ledger/DID) so a reader can verify what it summarizes.

  A live post needs Council Lv6+ + operator + a member/actor signature (build-live raises).
  Pure fns; deterministic; string-keyed post records (house style). Stdlib only — the
  growth (live signing/broadcast) happens actor-side on the mesh, not here."
  (:require [clojure.string :as str]
            [iriai.methods.infra :as infra]
            [iriai.methods.fund :as fund]
            [iriai.methods.maintain :as maintain]))

(def DISCLAIMER
  (str "【コモンズ被覆マップ — NOT a utility, NOT a shut-off list / 非断定】 "
       "ライフライン(電気・水道・ガス・通信・道路)は入会権(commons)であり、"
       "決して遮断・leverage しません。§1.16 現物給付・cash≡0・シミュレーションのみ。"))

;; ── G1 structural guard: a post body must never carry shut-off / per-person vocab ──
(def ^:private forbidden-tokens
  ["shutoff" "shut-off" "disconnect" "遮断" "停止通知" "個人名" "住所" "per-person" "target-list"])

(defn- assert-no-withhold
  "G1: refuse any post whose CONTENT contains shut-off / per-person vocab. A commons coverage
  map narrates aggregate reach, never a withholding or a person. The fixed DISCLAIMER (which
  itself says 'NOT a shut-off list') is controlled boilerplate and exempt from the scan."
  [body]
  (let [lc (-> (str body) (str/replace DISCLAIMER "") str/lower-case)]
    (when-let [bad (some (fn [t] (when (str/includes? lc (str/lower-case t)) t)) forbidden-tokens)]
      (throw (ex-info (str "G1 violation: a lifeline commons post is a COVERAGE MAP, never a shut-off / "
                           "per-person record — found forbidden token " (pr-str bad))
                      {:gate "G1" :token bad}))))
  body)

(defn- enough-sources
  "A post needs ≥2 non-blank provenance citations (the ADR + the actor's committed ledger/DID)."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "social: a post needs ≥2 provenance citations (ADR + committed ledger/DID)" {})))
    s))

(defn- post
  "Assemble a commons post record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  (assert-no-withhold body)
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"            ;; G5/R0 — published is unrepresentable
   ":post/is-commons-map" true          ;; G1 — a coverage map, never a shut-off/target list
   ":post/cash-zero" true               ;; G2 — §1.16 in-kind, the consumer is never billed
   ":post/sim-only" true                ;; G5 — narrates the map/plan, never an actuation
   ":post/server-held-key" false        ;; no-server-key (ADR-2605231525)
   ":post/author" author                ;; member/actor DID (required only for a gated live post)
   ":post/sources" sources})            ;; ≥2 provenance refs

(def default-sources
  "Default provenance: the authorizing ADRs + the actor DID. Callers add the committed
  ledger head CID for a richer citation."
  ["ADR-2606272200" "did:web:etzhayyim.com:actor:iriai"])

;; ── COVERAGE post (infra) — aggregate reach, a commons map ─────────────────────
(defn draft-coverage-post
  "HISTORY post — the lifeline-commons coverage/resilience tally (aggregate; a MAP, never
  a shut-off list). `assessment` is iriai.methods.infra/assess output."
  ([assessment sources] (draft-coverage-post assessment sources ""))
  ([assessment sources author]
   (let [srcs (enough-sources sources)
         tally (get assessment "tally")
         body (str DISCLAIMER "\n\n"
                   "【被覆】" (apply + (vals tally)) " 区域×ライフラインを評価: "
                   "provision " (get assessment "provision" 0) " / reinforce " (get assessment "reinforce" 0)
                   " / redundancy " (get assessment "redundancy" 0) " / maintain " (get assessment "maintain" 0)
                   " / await-consent " (get assessment "await_consent" 0) " / monitor " (get assessment "monitor" 0)
                   "。未到達 " (get assessment "unserved_pop" 0) " 人 → §1.16 で閉じる目標。"
                   "出典 " (count srcs) " 件。")]
     (post "coverage:lifeline-commons" body srcs author))))

;; ── FUNDING post (資金) — §1.16 in-kind, cash≡0 ────────────────────────────────
(defn draft-funding-post
  "FUNDING post — the §1.16 in-kind funding plan summary (cash≡0). `plan` is
  iriai.methods.fund/plan output."
  ([plan sources] (draft-funding-post plan sources ""))
  ([plan sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   "【資金】" (get plan "count") " 件の §1.16 現物給付提案 "
                   "(donation→TitheRouter 10%→Public Fund→grant/escrow/in-kind, 1 SBT=1 vote が決定)。"
                   "imputed 市場等価 " (long (get plan "imputed_annual_usd_total" 0)) " USD/yr。"
                   "消費者への現金 0(billing なし)。出典 " (count srcs) " 件。")]
     (post "funding:in-kind-§1.16" body srcs author))))

;; ── UPKEEP post (運用/予測) — aggregate predictive maintenance, sim-only ────────
(defn draft-maintenance-post
  "UPKEEP post — the maintenance plan summary (aggregate, sim-only). `plan` is
  iriai.methods.maintain/plan output."
  ([plan sources] (draft-maintenance-post plan sources ""))
  ([plan sources author]
   (let [srcs (enough-sources sources)
         tally (get plan "tally")
         body (str DISCLAIMER "\n\n"
                   "【運用】" (get plan "count") " 資産の保全計画 " (pr-str tally) "。"
                   "安全床優先(unsafe は decommission/corrective、コストで先送りしない)。"
                   "年間 OpEx " (long (get plan "opex_annual_usd" 0)) " USD は §1.16 現物・cash≡0。"
                   "設計のみ(actuation は producer + Council Lv7+)。出典 " (count srcs) " 件。")]
     (post "upkeep:maintenance" body srcs author))))

(defn drafts-from-seed
  "Convenience: load the seed, run infra/fund/maintain, return the 3 dry-run posts."
  [cells assets]
  [(draft-coverage-post (infra/assess cells) default-sources)
   (draft-funding-post (fund/plan cells) default-sources)
   (draft-maintenance-post (maintain/plan assets) default-sources)])

(defn build-live
  "Live posting is outward-gated. Refuses by construction at R0; the live signature is the
  actor's own mesh-runtime key, presented (never server-held) under Council Lv6+ + operator
  gate (§1.12 / G6)."
  [& _args]
  (throw (ex-info (str "iriai R0: live social posting is Council Lv6+ + operator + member/actor-signature "
                       "gated (§1.12/G6). Only dry-run posts are producible offline; the live signature "
                       "happens actor-side in the kotoba-mesh runtime, never with a server key.") {})))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           rows (clojure.edn/read-string (slurp seed))
           cells (vec (filter #(= (:type %) :lifeline-cell) rows))
           assets (vec (filter #(= (:type %) :asset) rows))
           posts (drafts-from-seed cells assets)]
       (doseq [p posts]
         (println (str "── " (get p ":post/subject") " [" (get p ":post/status") "] "
                       "commons-map=" (get p ":post/is-commons-map")
                       " cash-zero=" (get p ":post/cash-zero")
                       " server-key=" (get p ":post/server-held-key")))
         (println (get p ":post/body")) (println))
       (println (str "-- " (count posts) " dry-run posts (no broadcast; live = Council Lv6+ gated) --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
