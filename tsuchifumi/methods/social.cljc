#!/usr/bin/env bb
;; tsuchifumi 土踏み — DRY-RUN atproto おせっかい (ossekai) post projection (clj-native).
(ns tsuchifumi.methods.social
  "social.cljc — tsuchifumi 土踏み DRY-RUN social-proto / atproto post projection
  (ADR-2606212000). Projects a relief verdict / leverage point into an
  app.bsky.feed.post-shaped おせっかい (ossekai) nudge, enforcing the post invariants
  in their second home (after analyze's verdict layer):

    G1 (non-diagnostic) — the body is SCANNED for diagnosis/treatment tokens and
        REFUSED if any appear; tsuchifumi never diagnoses/treats/cures.
    G2 (evidence-honesty) — every post carries ≥1 evidence citation WITH its tier;
        an EMF-harm assertion is refused (only the honesty post may NAME the contested
        claim, framed as 未確立). A contested claim is never stated as established.
    G4 (no fear / no manipulation) — the body is SCANNED for fear/alarm tokens and
        REFUSED if any appear; the nudge is a NO-REGRET, low-risk practice only.
    G5 (no commerce) — the body is SCANNED for sales/product tokens and REFUSED.
    no-server-key — serverHeldKey=false; the MEMBER signs, the server never does.
    dry-run only — status is ':dry-run'; ':published' is unrepresentable; build-live raises.
    ossekai handoff — every post is a PROPOSAL routed to ossekai (御節介), which CARRIES
        it consent-bound + on-chain-logged; tsuchifumi only drafts (shiori→ossekai pattern).

  The DISCLAIMER NAMES the prohibited concepts in order to disclaim them, so it is
  STRIPPED before every token scan (shionome pattern). Stdlib only. Deterministic."
  (:require [clojure.string :as str]
            [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.tsuchifumi-edn :as te]))

(def DISCLAIMER
  (str "【おせっかい / Wellbecoming nudge — 医療助言ではありません・診断も治療もしません】 "
       "アーシングや電磁波の健康影響は科学的に未確立(contested)で、害を断定することはしません。"
       "確立しているのは緑地・屋外時間そのものの wellbeing です。無理のない範囲でどうぞ。"))

;; ── token blocklists (the body, with DISCLAIMER stripped, must contain NONE) ──
(def fear-tokens
  ["危険" "恐ろしい" "必ず病気" "がんになる" "癌になる" "死ぬ" "警告!!" "手遅れ" "今すぐやめないと"
   "cancer" "deadly" "dangerous radiation" "will harm" "will kill" "you are being poisoned"])

(def sales-tokens
  ["購入" "ご注文" "アーシングマット" "アーシングシート" "商品" "割引" "限定価格" "今すぐ注文" "セール"
   "buy now" "discount" "affiliate" "$" "￥" "add to cart" "shop"])

(def diagnosis-tokens
  ["あなたを診断" "診断します" "治療します" "治します" "治癒します" "あなたの症状は" "処方"
   "diagnose you" "treat your" "cure your" "we can heal"])

;; an unhedged EMF→health harm assertion (G2 — never claim established harm)
(def emf-harm-assertion-tokens
  ["電磁波で病気になる" "電磁波が原因で" "電磁波は有害だと証明" "5gは危険"
   "emf causes disease" "wifi causes" "proven harmful"])

(defn- scan
  "Return the first blocklist token present in `body` (DISCLAIMER stripped), or nil."
  [body tokens]
  (let [scanned (str/lower-case (str/replace (str body) DISCLAIMER ""))]
    (some (fn [t] (when (str/includes? scanned (str/lower-case t)) t)) tokens)))

(defn guard-no-fear [body]
  (when-let [t (scan body fear-tokens)]
    (throw (ex-info (str "G4: post body contains the fear/alarm token " (pr-str t)
                         " — refused. ossekai is a no-regret nudge, never a fear appeal.") {:token t}))))

(defn guard-no-sales [body]
  (when-let [t (scan body sales-tokens)]
    (throw (ex-info (str "G5: post body contains the sales/product token " (pr-str t)
                         " — refused. tsuchifumi sells nothing (no earthing mat/device/affiliate).") {:token t}))))

(defn guard-no-diagnosis [body]
  (when-let [t (scan body diagnosis-tokens)]
    (throw (ex-info (str "G1: post body contains the diagnosis/treatment token " (pr-str t)
                         " — refused. tsuchifumi is non-diagnostic; care routes to mitate/iyashi.") {:token t}))))

(defn guard-no-emf-harm-claim [body]
  (when-let [t (scan body emf-harm-assertion-tokens)]
    (throw (ex-info (str "G2: post asserts an UNHEDGED EMF→health harm (" (pr-str t)
                         ") — refused. Non-thermal EMF harm is NOT established; never claim it.") {:token t}))))

(defn guard-sources
  "G2 — ≥1 evidence citation, each carrying a tier; none below :emerging may be cited
  as SUPPORT for a practice (a :contested/:anecdotal source may only be NAMED in the
  honesty post, which passes allow-contested? true)."
  ([sources] (guard-sources sources false))
  ([sources allow-contested?]
   (let [s (vec (remove (fn [x] (str/blank? (str (:claim x)))) (or sources [])))]
     (when (< (count s) 1)
       (throw (ex-info "G2: an ossekai post needs ≥1 evidence citation with a tier" {})))
     (when-not allow-contested?
       (when-let [bad (some (fn [x] (when (#{:contested :anecdotal} (:tier x)) x)) s)]
         (throw (ex-info (str "G2: a practice nudge may not rest on a :contested/:anecdotal "
                              "citation (" (pr-str (:tier bad)) ") — only the honesty post may name it")
                         {:tier (:tier bad)}))))
     s)))

(defn- run-all-guards [body]
  (guard-no-fear body) (guard-no-sales body)
  (guard-no-diagnosis body) (guard-no-emf-harm-claim body))

(defn- post
  "Assemble an ossekai networkPost record with every invariant pinned. status is
  ALWAYS :dry-run; every post is a PROPOSAL routed to ossekai (御節介)."
  [subject body sources proposal author]
  {":post/subject"         subject
   ":post/body"            body
   ":post/status"          ":dry-run"                 ; dry-run only
   ":post/non-diagnostic"  true                       ; G1
   ":post/evidence-honest" true                       ; G2
   ":post/no-fear-notice"  true                       ; G4
   ":post/no-commerce"     true                       ; G5
   ":post/server-held-key" false                      ; no-server-key
   ":post/route"           ":ossekai"                 ; carrier (御節介)
   ":post/proposal"        proposal                   ; the consent-bound nudge ossekai carries
   ":post/author"          author                     ; member DID (required only for a gated live carry)
   ":post/sources"         (mapv (fn [s] {":src/claim" (:claim s) ":src/tier" (str (:tier s))
                                          ":src/source" (:source s)}) sources)})

;; ── drafters ─────────────────────────────────────────────────────────────────
(defn draft-relief-post
  "A dry-run おせっかい nudge for a :relief-priority / :infrastructure-gap region —
  a NO-REGRET practice resting on ESTABLISHED greenspace/outdoor-time evidence."
  ([region-row established-sources] (draft-relief-post region-row established-sources ""))
  ([region-row established-sources author]
   (let [srcs (guard-sources established-sources false)
         nm (get region-row "name")
         body (str DISCLAIMER "\n\n"
                   nm "は緑地・接地アクセスが不足ぎみと観測されました(institutional gap)。"
                   "無理のない範囲で、近くの公園の芝生や砂浜・土の上を素足で歩いたり、"
                   "日中に少し外で過ごす時間を増やしてみるのはどうでしょう。"
                   "緑地・屋外時間そのものの心身の効果は確立しています。出典 "
                   (count srcs) " 件(tier 明記)。")
         proposal {":proposal/kind" ":outdoor-greenspace-time"
                   ":proposal/region" (get region-row "id")
                   ":proposal/no-regret" true
                   ":proposal/consent-required" true}]
     (run-all-guards body)
     (post "relief-nudge" body srcs proposal author))))

(defn draft-evening-light-post
  "A dry-run nudge for a screen/device-dominant (:emerging) cohort — evening-light /
  outdoor-time, resting on the EMERGING circadian/sleep evidence (routes to suimin)."
  ([region-row emerging-sources] (draft-evening-light-post region-row emerging-sources ""))
  ([region-row emerging-sources author]
   (let [srcs (guard-sources emerging-sources false)
         body (str DISCLAIMER "\n\n"
                   "夜遅くの画面の光は体内時計と寝つきに影響しうる、という研究があります(tier: emerging)。"
                   "寝る前のスクリーン時間を少し減らし、その分を日中の屋外時間にあててみるのはどうでしょう。"
                   "睡眠の相談は suimin(睡眠)へ。出典 " (count srcs) " 件。")
         proposal {":proposal/kind" ":evening-light-hygiene"
                   ":proposal/region" (get region-row "id")
                   ":proposal/route-care" ":suimin"
                   ":proposal/no-regret" true ":proposal/consent-required" true}]
     (run-all-guards body)
     (post "evening-light-nudge" body srcs proposal author))))

(defn draft-honesty-post
  "A dry-run honesty post — NAMES the contested earthing/EMF claim, framed as 未確立,
  to inoculate against pseudoscience + product-marketing (G2). The ONLY post allowed
  to cite a :contested source (allow-contested? true), and only to disclaim it."
  ([contested-sources] (draft-honesty-post contested-sources ""))
  ([contested-sources author]
   (let [srcs (guard-sources contested-sources true)
         body (str DISCLAIMER "\n\n"
                   "整理すると: アーシング製品の『炎症が減る』等の主張や、ICNIRP基準以下の非熱的な電磁波の"
                   "健康影響は、現時点で科学的に未確立(contested)です。tsuchifumi はこれらを断定も否定も"
                   "せず、確立した no-regret な行動(屋外時間・緑地・素足での土の感触)だけをおすすめします。"
                   "製品の販売は一切しません。出典 " (count srcs) " 件(tier 明記)。")
         proposal {":proposal/kind" ":evidence-honesty"
                   ":proposal/anti-pseudoscience" true
                   ":proposal/no-regret" true ":proposal/consent-required" false}]
     (run-all-guards body)
     (post "evidence-honesty" body srcs proposal author))))

(defn draft-leverage-post
  "A dry-run institutional post — the highest-leverage, no-regret intervention
  (grounding + greenspace ACCESS standards), routed to ossekai for transparent
  civic advocacy. Aggregate/structural; names no person (G1)."
  ([leverage-row established-sources] (draft-leverage-post leverage-row established-sources ""))
  ([leverage-row established-sources author]
   (let [srcs (guard-sources established-sources false)
         body (str DISCLAIMER "\n\n"
                   "最大のレバレッジ点は制度: 公共の緑地アクセスと接地(grounding)・屋外時間の標準です"
                   "(Meadows structure/rules)。個人を責めるのではなく、誰もが土や緑に触れられる環境を"
                   "整えること。これは健康論争に関係なく成り立つ no-regret な公共財です。出典 "
                   (count srcs) " 件。")
         proposal {":proposal/kind" ":institutional-grounding-standard"
                   ":proposal/leverage" (get leverage-row "leverage_band")
                   ":proposal/aggregate-only" true
                   ":proposal/no-regret" true ":proposal/consent-required" false}]
     (run-all-guards body)
     (post "leverage-institutional" body srcs proposal author))))

;; ── build the full ossekai proposal batch from an assessment + evidence rows ──
(defn ossekai-batch
  "Build the dry-run ossekai post batch from an analyze assessment + the evidence
  catalog. Returns {:posts [...] :count n}. Picks the top relief-priority region,
  the top infrastructure-gap region, an emerging cohort, the honesty post, and the
  institutional leverage post — each fully guarded. author optional (member DID)."
  ([assessment evidence-rows risk-assessment] (ossekai-batch assessment evidence-rows risk-assessment ""))
  ([assessment evidence-rows risk-assessment author]
   (let [regions (get assessment "regions")
         by-v (fn [v] (filter #(= v (get % "verdict")) regions))
         top (fn [rs] (first (sort-by #(- (get % "earthing_deficit")) rs)))
         est (filter #(= :established (:tier %)) evidence-rows)
         emg (filter #(= :emerging (:tier %)) evidence-rows)
         con (filter #(#{:contested :anecdotal} (:tier %)) evidence-rows)
         lev (first (get risk-assessment "leverage_points"))
         posts (cond-> []
                 (seq (by-v :relief-priority))
                 (conj (draft-relief-post (top (by-v :relief-priority)) est author))
                 (seq (by-v :infrastructure-gap))
                 (conj (draft-relief-post (top (by-v :infrastructure-gap)) est author))
                 (and (seq emg) (seq regions))
                 (conj (draft-evening-light-post (top (by-v :relief-priority)) emg author))
                 (seq con)
                 (conj (draft-honesty-post con author))
                 lev
                 (conj (draft-leverage-post lev est author)))]
     {:posts (vec posts) :count (count posts)})))

;; ── live carry is gated (no-server-key) ──────────────────────────────────────
(defn build-live
  "Outward carry is gated. tsuchifumi NEVER publishes; the MEMBER signs and the
  ossekai (御節介) actor carries the proposal consent-bound + on-chain-logged.
  Refuses by construction (no-server-key)."
  [& _]
  (throw (ex-info (str "tsuchifumi: live carry is member-signed + consent-bound + Council/operator "
                       "gated and is performed by ossekai (御節介), never tsuchifumi (no-server-key). "
                       "Only dry-run proposals are producible offline.")
                  {:gate :no-server-key})))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           rows (te/reconstitute-rows (clojure.edn/read-string (slurp seed)))
           regions (vec (filter #(= (:type %) :region) rows))
           evidence (vec (filter #(= (:type %) :evidence) rows))
           drivers (vec (filter #(= (:type %) :driver) rows))
           assessment (an/assess regions evidence)
           risk ((requiring-resolve 'tsuchifumi.methods.risk/assess) drivers)
           batch (ossekai-batch assessment evidence risk)]
       (println (str "# tsuchifumi 土踏み — DRY-RUN ossekai (御節介) post batch ("
                     (:count batch) " posts)\n"))
       (doseq [p (:posts batch)]
         (println (str "── [" (get p ":post/subject") "] status=" (get p ":post/status")
                       " route=" (get p ":post/route") " server-held-key=" (get p ":post/server-held-key")))
         (println (get p ":post/body"))
         (println (str "sources: " (count (get p ":post/sources")) " · proposal: "
                       (get-in p [":post/proposal" ":proposal/kind"]) "\n"))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
