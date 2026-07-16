;; ported from 20-actors/hakoniwa/methods/social.py — real port replacing the unit_refactor
;; stage-0 "TODO: port-failed" stubs. NS fixed root.hakoniwa.* → hakoniwa.* (20-actors source root).
(ns hakoniwa.methods.social
  "social.py — hakoniwa 箱庭 social-emission cell. 1:1 Clojure port. ADR-2606111500 (R1).

  Projects a distribution finding into a social post (app.bsky.feed.post-shaped) and EMITS it.
  The charter invariants are enforced here in their emission home:
    G2 — DISTRIBUTION-ONLY (非終末論): the body states a distribution (p10/p50/p90), never a
         point; guard-no-point scans for certainty/foretelling tokens and REFUSES.
    G3 — NON-STEERING: guard-no-steer scans for action-steering tokens and REFUSES.
    G1 — synthetic box (:post/synthetic-box true); names no person.
    G4 — opens with the observational disclaimer; plaintext-public (相互監視).
    G7 — :post/server-held-key false; a :published post REQUIRES a member-DID author.

  Pure + deterministic body. The Python `__main__` demo printer is omitted."
  (:require [clojure.string :as str]))

(def disclaimer
  (str "【箱庭シミュレーション / 架空ペルソナによる可能性分布 — 予測の断定ではありません。"
       "備えの計画材料であり、特定の行動を推奨しません。実在の個人は登場しません。】"))

;; G2 — certainty / single-foretold-future tokens.
(def point-tokens
  ["必ず" "確実に" "間違いなく" "絶対に" "確定" "断言" "100%"
   "will definitely" "is guaranteed" "for certain" "the future is" "we predict that"
   "確実な予測" "必ず起こる"])

;; G3 — action-steering / persuasion tokens.
(def steer-tokens
  ["買え" "売れ" "買うべき" "売るべき" "購入し" "投票し" "投票しよう" "投票せよ"
   "支持しよう" "支持せよ" "ボイコット" "反対しよう" "賛成しよう" "今すぐ行動"
   "you should" "you must" "vote for" "vote against" "buy " "sell " "boycott"
   "sign up now" "act now" "purchase"])

(defn- scan
  "Return the first matching token (case-insensitive), or nil. Strips the disclaimer first
  (it NAMES the tokens to disclaim them) — mirrors social._scan."
  [body tokens]
  (let [scanned (str/replace body disclaimer "")
        low (str/lower-case scanned)]
    (some (fn [t] (when (str/includes? low (str/lower-case t)) t)) tokens)))

(defn- guard-no-point [body]
  (when-let [t (scan body point-tokens)]
    (throw (ex-info (str "G2: post body asserts a point/certain future via " (pr-str t)
                         " — refused. hakoniwa states a DISTRIBUTION, never a single foretold "
                         "outcome (非終末論).")
                    {:token t}))))

(defn- guard-no-steer [body]
  (when-let [t (scan body steer-tokens)]
    (throw (ex-info (str "G3: post body steers behaviour via " (pr-str t) " — refused. hakoniwa "
                         "informs resilience planning; it never tells anyone what to do "
                         "(non-steering).")
                    {:token t}))))

(defn- f2 [v] (format "%.2f" (double v)))

(defn draft-distribution-post
  "A post narrating the outcome DISTRIBUTION (resilience framing). Guards G2/G3 before returning.
  opts: :narration :author :status (:dry-run | :published — :published needs an author, G7)."
  ([scenario dist] (draft-distribution-post scenario dist {}))
  ([scenario dist {:keys [narration author status]
                   :or {narration "" author "" status ":dry-run"}}]
   (let [q (get dist "quantiles")
         line (str "箱庭「" scenario "」: 町全体の採用スタンスは分布として "
                   "p10=" (f2 (get q ":p10")) " / 中央値p50=" (f2 (get q ":p50")) " / p90=" (f2 (get q ":p90")) " "
                   "(平均" (f2 (get dist "mean")) "・幅±" (f2 (get dist "stdev")) ")。可能性の分布であり予測の断定ではありません。")
         body (str disclaimer "\n\n" line)
         body (if (not= "" narration) (str body "\n\n" narration) body)]
     (guard-no-point body)
     (guard-no-steer body)
     (when (and (= status ":published") (= "" author))
       (throw (ex-info (str "G7: a :published post requires a member-DID author (the member signs, "
                            "never the server). Supply author or use status :dry-run.") {})))
     {":post/subject" "distribution"
      ":post/body" body
      ":post/status" status
      ":post/synthetic-box" true
      ":post/distribution-only" true
      ":post/non-steering" true
      ":post/is-mirror" true
      ":post/server-held-key" false
      ":post/author" author
      ":post/narration-via" ""})))

(defn emit
  "Emit an authorized post. Persisted to the canonical kotoba Datom log by the autorun caller; the
  EXTERNAL relay (AT Proto firehose) is delivered by `transport` when an operator credential is
  present. Re-applies G2/G3 at the emission boundary. Returns the emit receipt."
  ([post] (emit post nil))
  ([post transport]
   (guard-no-point (get post ":post/body"))
   (guard-no-steer (get post ":post/body"))
   (when (and (= (get post ":post/status") ":published") (= "" (get post ":post/author" "")))
     (throw (ex-info "G7: refuse to emit a :published post with no member-DID author." {})))
   (let [relay (when transport (transport post))]
     {"subject" (get post ":post/subject")
      "status" (get post ":post/status")
      "substrate" "kotoba-datom-log"
      "external_relay" (or relay ":pending-operator-transport")
      "guards" ["G2:distribution-only" "G3:non-steering" "G7:member-signed"]})))
