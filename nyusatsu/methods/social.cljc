(ns nyusatsu.methods.social
  "social.cljc — 入札 (nyusatsu) DRY-RUN multilingual social posts. ADR-2606271700.

  Projects validated `:bid/*` datoms into member-signable, NON-adjudicating dry-run posts
  (the kosatsu social.clj pattern). G8: status is always :dry-run (never published; outward-
  gated). G2: a post reports THAT an issuer published a notice, attributed — never a winner
  prediction or bidder ranking. G7: the server never signs (server-held-key false; a real
  post needs a member signature). G3: a post carries the bid's ≥1 primary-source citations.

  Multilingual: the summary line is rendered in the bid's source language AND English
  (langs = [source-lang \"en\"]). Stdlib only. Deterministic."
  (:require [clojure.string :as str]))

(def MIRROR_PREFIX
  "[mirror · not a verdict] nyusatsu reports, attributed, the procurement notices public
issuer agencies themselves published; it predicts no winner and ranks no bidder. ")

(defn- fmt-value [b]
  (let [amt (get b ":bid/value-amount")
        cur (get b ":bid/value-currency")]
    (if (and (number? amt) (pos? (double amt)))
      (str cur " " (long amt))
      "(value undisclosed)")))

(defn- method-label [b] (str/replace (str (get b ":bid/method")) #"^:" ""))

(defn- ja-line [b]
  (str "入札公告: " (get b ":bid/issuer-name") "（" (get b ":bid/jurisdiction") "）"
       " 方式=" (method-label b) " 予定価格=" (fmt-value b)
       " 締切=" (or (get b ":bid/tender-end") "未定")))

(defn- en-line [b]
  (str "Tender: " (get b ":bid/issuer-name") " (" (get b ":bid/jurisdiction") ")"
       " · " (method-label b) " · " (fmt-value b)
       " · closes " (or (get b ":bid/tender-end") "TBD")))

(defn bid->post
  "Build ONE dry-run networkPost record from a validated bid. Structural fields are
  const-locked (G2/G7/G8). Raises if the bid carries no source (G3)."
  [b]
  (let [sources (get b ":bid/sources")
        lang    (or (get b ":bid/source-lang") "en")]
    (when-not (and (sequential? sources) (seq sources))
      (throw (ex-info "G3: a post needs ≥1 primary-source citation" {:bid b})))
    {":post/id"                      (str "nyusatsu:" (get b ":bid/ocid"))
     ":post/subject"                 (get b ":bid/ocid")
     ":post/body"                    (str MIRROR_PREFIX (en-line b) " | " (ja-line b))
     ":post/embed-uri"               (get b ":bid/source-url")
     ":post/langs"                   (distinct [lang "en"])
     ":post/status"                  ":dry-run"   ;; G8 — never :published at R1
     ":post/is-mirror"               true          ;; G2 / mirror
     ":post/non-adjudicating-notice" true          ;; G2
     ":post/server-held-key"         false         ;; G7 — member signs, not the server
     ":post/sources"                 (vec sources)}))

(defn summary-post
  "A roll-up dry-run post over a batch of bids (counts by status + jurisdiction coverage)."
  [bids]
  (let [n (count bids)
        by-status (frequencies (map #(get % ":bid/status") bids))
        juris (sort (distinct (map #(get % ":bid/jurisdiction") bids)))]
    {":post/id"                      "nyusatsu:summary"
     ":post/subject"                 ":coverage"
     ":post/body"                    (str MIRROR_PREFIX
                                          "procurement coverage — " n " tenders across "
                                          (count juris) " jurisdictions " (pr-str (vec juris))
                                          "; by status " (pr-str by-status))
     ":post/langs"                   ["en"]
     ":post/status"                  ":dry-run"
     ":post/is-mirror"               true
     ":post/non-adjudicating-notice" true
     ":post/server-held-key"         false
     ":post/sources"                 (vec (distinct (mapcat #(get % ":bid/sources") bids)))}))

(defn posts
  "Build dry-run networkPost records from validated bids: 1 summary + 1 per bid. Deterministic
  (bids are emitted in ocid order)."
  [bids]
  (let [ordered (sort-by #(get % ":bid/ocid") bids)]
    (into [(summary-post ordered)] (map bid->post ordered))))
