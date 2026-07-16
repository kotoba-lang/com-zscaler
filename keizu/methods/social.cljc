(ns keizu.methods.social
  "social.cljc — 系図 (keizu) DRY-RUN social-post projection. ADR-2606066000.
  1:1 Clojure port of `methods/social.py`.

  Projects an AGGREGATE concentration finding into a social post (app.bsky.feed.post-shaped),
  enforcing the post invariants in their third home (mirror of the ontology :db/allowed +
  networkPost.edn :const):

    G5 — every post opens with the mirror / accountability-map disclaimer (isMirror=true),
         never speaks AS a government, never names a private individual.
    G2 — nonAdjudicatingNotice=true; the post narrates ties/shares, never a verdict.
    G7 — serverHeldKey=false; the member signs, the server never does (ADR-2605231525).
    G8 — status is 'dry-run' only at R0; `published` is unrepresentable. A live post needs
         Council Lv6+ + operator + a member signature (build-live raises here).
    G3 — the post carries the same ≥2 public-source citations as the finding.

  House style: Python ':…' keyword strings stay strings; source-denied + the float/list repr reused
  from / mirroring the keizu.methods.weave sibling; string-keyed post records; pure fns; deterministic.
  The Python __main__ demo is omitted (analyze.cljc -main drives the dry-run posts end-to-end).

  Stdlib only. Deterministic."
  (:require [clojure.string :as str]
            [keizu.methods.weave :as w]))

(def DISCLAIMER
  (str "【観測ミラー / accountability map — NOT the government, non-adjudicating】 "
       "公開情報から編んだ関係グラフの集計です。特定個人を名指しせず、不正の断定もしません。"))

;; ── Python repr helpers (committee post body embeds finding['organs'] as a Python list repr) ──
(defn- py-repr-str [s]
  (str "'" (-> (str s) (str/replace "\\" "\\\\") (str/replace "'" "\\'")) "'"))

(defn- py-list-repr
  "repr(list-of-str) — `['a', 'b']`, the exact shape the Python committee post embeds."
  [xs]
  (str "[" (str/join ", " (map py-repr-str xs)) "]"))

;; ── Python f-string :.1f — fixed-point, HALF_EVEN over the exact binary value ──
(defn- fmt-f [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))

(defn- enough-sources
  "G3 — a post needs ≥2 non-blank public-source citations; refuse a prohibited gov-intel terminal."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G3: a post needs ≥2 public-source citations" {})))
    (let [d (w/source-denied s)]
      (when (seq d)
        (throw (ex-info (str "Rider §2(e)/N5: source " (pr-str d)
                             " is a commercial gov-intel terminal — a post may not cite it") {}))))
    s))

(defn- post
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS dry-run."
  [subject body sources author]
  {":post/subject" subject
   ":post/body" body
   ":post/status" ":dry-run"             ;; G8 — published is unrepresentable
   ":post/is-mirror" true                ;; G5
   ":post/non-adjudicating-notice" true  ;; G2
   ":post/server-held-key" false         ;; G7 / no-server-key
   ":post/author" author                 ;; member DID (required only for a gated live post)
   ":post/sources" sources})             ;; G3

(defn draft-committee-post
  "A dry-run post about a committee's cross-organ concentration (aggregate, no person)."
  ([finding sources] (draft-committee-post finding sources ""))
  ([finding sources author]
   (let [srcs (enough-sources sources)
         body (str DISCLAIMER "\n\n"
                   (get finding "label") ": " (get finding "member_count") " seats drawn from "
                   (get finding "distinct_organs") " organ(s) "
                   (py-list-repr (get finding "organs")) ". "
                   "出典 " (count srcs) " 件。")]
     (post (str "committee:" (get finding "committee")) body srcs author))))

(defn draft-money-post
  "A dry-run post about per-payee money concentration (HHI), aggregate + factual."
  ([money-concentration sources] (draft-money-post money-concentration sources ""))
  ([money-concentration sources author]
   (let [srcs (enough-sources sources)
         shares (get money-concentration "shares")
         top (if (seq shares) (first shares) ["(none)" 0.0])
         body (str DISCLAIMER "\n\n"
                   "公開された資金フローの集中度 HHI=" (w/to-json (get money-concentration "hhi")) "。"
                   "最大受領 " (nth top 0) " = " (fmt-f (* (nth top 1) 100) 1) "%。"
                   "出典 " (count srcs) " 件。")]
     (post "money:concentration" body srcs author))))

(defn build-live
  "G8 — live posting is outward-gated. Refuses by construction at R0."
  [& _args]
  (throw (ex-info (str "keizu R0: live social posting is Council Lv6+ + operator + member-signature gated (G8). "
                       "Only dry-run posts are producible offline.") {})))
