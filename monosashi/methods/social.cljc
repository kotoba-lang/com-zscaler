(ns monosashi.methods.social
  "monosashi 物差し — social-emission cell. ADR-2606271800. Mirrors hakoniwa.methods.social.

  Projects a skill-band finding into a social post (app.bsky.feed.post-shaped) and EMITS it.
  Charter invariants enforced here in their emission home:
    G1 — DISTRIBUTION-ONLY: the body states a skill DISTRIBUTION (p10/p50/p90), never a single
         grade; guard-no-point scans for certainty/foretelling tokens and REFUSES.
    G3 — ANTI-GOODHART / NON-STEERING: guard-no-steer scans for action-steering tokens (incl.
         'this model wins, adopt it / fund it' adoption-steering) and REFUSES.
    G4 — opens with the observational disclaimer; plaintext-public (相互監視).
    G7 — :post/server-held-key false; a :published post REQUIRES a member-DID author.

  Pure + deterministic body."
  (:require [clojure.string :as str]
            [monosashi.methods.score :as score]))

(def disclaimer
  (str "【物差し / 予測アクターのスキル測定 — モデル評価であり、断定でも投資助言でもありません。"
       "スキルは報酬の対象ではありません（物差しは測るためのもので、的にしてはならない）。】"))

;; G1 — certainty / single-foretold tokens (same family as hakoniwa point-tokens).
(def point-tokens
  ["必ず" "確実に" "間違いなく" "絶対に" "確定" "断言" "100%" "最も正確" "完璧な予測"
   "will definitely" "is guaranteed" "for certain" "we predict that" "確実な予測"])

;; G3 — action-steering / adoption-steering / persuasion tokens (anti-Goodhart: do not turn the
;; measure into a target by telling anyone to adopt/fund/reward the high-scoring model).
(def steer-tokens
  ["買え" "売れ" "買うべき" "売るべき" "投票し" "支持しよう" "今すぐ行動"
   "このモデルを採用せよ" "採用すべき" "予算を付けよう" "報酬を与えよ" "資金を出そう"
   "you should" "you must" "adopt this model" "fund this" "reward the" "buy " "sell " "act now"])

(defn- member-did?
  "G7 — a publishable author is a member DID (soft shape check; the cryptographic check is the PDS
  signature at the transport leg). Mirrors the did:…:member: convention."
  [author]
  (and (string? author) (str/starts-with? author "did:") (str/includes? author ":member:")))

(defn- scan
  "First matching token (case-insensitive) after stripping the disclaimer (which NAMES tokens to
  disclaim them). NOTE: a substring denylist is DEFENSE-IN-DEPTH only — the real G1/G3 firewall is
  structural (no point field, no reward attr, whitelist datom emit). Mirrors hakoniwa social/_scan."
  [body tokens]
  (let [low (str/lower-case (str/replace body disclaimer ""))]
    (some (fn [t] (when (str/includes? low (str/lower-case t)) t)) tokens)))

(defn- guard-no-point [body]
  (when-let [t (scan body point-tokens)]
    (throw (ex-info (str "G1: post asserts a point/certain skill via " (pr-str t)
                         " — refused. monosashi states a skill DISTRIBUTION, never a single grade.")
                    {:token t}))))

(defn- guard-no-steer [body]
  (when-let [t (scan body steer-tokens)]
    (throw (ex-info (str "G3: post steers behaviour/adoption via " (pr-str t)
                         " — refused. monosashi measures; it never tells anyone which model to "
                         "adopt, fund or reward (anti-Goodhart, non-steering).")
                    {:token t}))))

(defn draft-skill-post
  "A post narrating a predictive actor's skill BAND (model-assessment framing). Guards G1/G3 before
  returning. opts: :author :status (:dry-run | :published — :published needs an author, G7)."
  ([band] (draft-skill-post band {}))
  ([band {:keys [author status] :or {author "" status ":dry-run"}}]
   (let [line (score/narrative band)
         body (str disclaimer "\n\n" line)]
     (guard-no-point body)
     (guard-no-steer body)
     (when (and (= status ":published") (not (member-did? author)))
       (throw (ex-info (str "G7: a :published post requires a member-DID author (did:…:member:…; the "
                            "member signs, never the server). Got " (pr-str author)
                            " — supply a member DID or use status :dry-run.") {:author author})))
     {":post/subject" "skill-band"
      ":post/actor-evaluated" (:eval/actor band)
      ":post/body" body
      ":post/status" status
      ":post/distribution-only" true
      ":post/non-steering" true
      ":post/reward-coupled" false          ; G3 anti-Goodhart structural marker
      ":post/server-held-key" false
      ":post/author" author})))

(defn emit
  "Emit an authorized post. Persisted to the canonical kotoba Datom log by the autorun caller; the
  EXTERNAL relay (AT Proto firehose) is delivered by `transport` only when an operator credential
  is present. Re-applies G1/G3 at the emission boundary. Returns the emit receipt."
  ([post] (emit post nil))
  ([post transport]
   (guard-no-point (get post ":post/body"))
   (guard-no-steer (get post ":post/body"))
   (when (and (= (get post ":post/status") ":published") (not (member-did? (get post ":post/author"))))
     (throw (ex-info (str "G7: refuse to emit a :published post without a member-DID author; got "
                          (pr-str (get post ":post/author"))) {})))
   (let [relay (when transport (transport post))]
     {"subject" (get post ":post/subject")
      "actor_evaluated" (get post ":post/actor-evaluated")
      "status" (get post ":post/status")
      "substrate" "kotoba-datom-log"
      "external_relay" (or relay ":pending-operator-transport")
      "guards" ["G1:distribution-only" "G3:anti-goodhart-non-steering" "G7:member-signed"]})))
