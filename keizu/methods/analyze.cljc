(ns keizu.methods.analyze
  "analyze.cljc — 系図 (keizu) end-to-end membrane (dry-run). ADR-2606066000.
  1:1 Clojure port of `methods/analyze.py` (same house style as inochi/rasen).

  Load seed → weave + validate → aggregate concentration → dry-run social posts → render
  `out/intel-report.md` + `out/relation-graph.kotoba.edn` + `out/kanae-render.json`. Aggregate-first
  (G3), edge-primary (G4), non-adjudicating (G2), mirror-not-target (G5). No live posting (G8).

  The CORE (edn loader + weave/concentration) is the already-ported `keizu.methods.edn` +
  `keizu.methods.weave`; this driver reuses them verbatim (it never re-implements the metrics).
  The dry-run social-post projection (DISCLAIMER + draft-committee-post + draft-money-post,
  social.py) and the kanae export (to-kanae-flows, export.py) are the membrane's own surface and
  are ported inline here so the end-to-end report is reproducible.

  CONSTITUTIONAL (preserved by construction):
    G2 — non-adjudicating: the report narrates ties/shares, never a verdict (no VERDICT-TOKEN
      string is ever emitted; the report carries the explicit '不正の断定もしません' / 'NOT an
      allegation' framing on the most sensitive sections).
    G3 — ≥2 public-source citations on every post (`enough-sources` throws otherwise).
    G5 — every post opens with the mirror / accountability-map DISCLAIMER (is-mirror true).
    G7 — server-held-key false; the member signs, the server never does.
    G8 — every post status is ':dry-run' (':published' unrepresentable); no live I/O.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at the #?(:clj)
  edge. Byte-parity: `-main` writes the SAME bytes analyze.py writes to out/intel-report.md."
  (:require [clojure.string :as str]
            [keizu.methods.edn :as kedn]
            [keizu.methods.weave :as w]))

;; ── float formatting (Python f-string :.Nf — ROUND_HALF_EVEN on the exact double) ────────────
(defn- fmt-f
  "Python f\"{x:.Nf}\" — fixed-point with n fractional digits, HALF_EVEN over the exact binary value."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.toPlainString))
     :cljs (.toFixed (double x) n)))   ;; cljs path; report -main is :clj only

;; ── Python list repr (for the committee post body: finding['organs']) ─────────────────────────
(defn- py-repr-str
  "repr(str) for the ASCII-quoting Python uses in a list repr: single-quoted, ' and \\ escaped.
  keizu organ labels contain no quotes/backslashes, but mirror Python's rule faithfully."
  [s]
  (str "'" (-> (str s) (str/replace "\\" "\\\\") (str/replace "'" "\\'")) "'"))

(defn- py-list-repr
  "repr(list-of-str) — `['a', 'b']`, the exact shape the Python committee post embeds."
  [xs]
  (str "[" (str/join ", " (map py-repr-str xs)) "]"))

;; ── dry-run social-post projection (1:1 of social.py) ─────────────────────────────────────────
(def DISCLAIMER
  (str "【観測ミラー / accountability map — NOT the government, non-adjudicating】 "
       "公開情報から編んだ関係グラフの集計です。特定個人を名指しせず、不正の断定もしません。"))

(defn- enough-sources
  "G3 — a post needs ≥2 non-blank public-source citations; refuse a prohibited gov-intel terminal."
  [sources]
  (let [s (vec (filter #(seq (str/trim (str %))) (or sources [])))]
    (when (< (count s) 2)
      (throw (ex-info "G3: a post needs ≥2 public-source citations" {})))
    (let [d (w/source-denied s)]
      (when (seq d)
        (throw (ex-info (str "Rider §2(e)/N5: source '" d "' is a commercial gov-intel terminal — a post may not cite it") {}))))
    s))

(defn- post-record
  "Assemble a networkPost record with every invariant pinned. status is ALWAYS :dry-run."
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
                   (get finding "distinct_organs") " organ(s) " (py-list-repr (get finding "organs")) ". "
                   "出典 " (count srcs) " 件。")]
     (post-record (str "committee:" (get finding "committee")) body srcs author))))

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
     (post-record "money:concentration" body srcs author))))

;; ── kanae export (1:1 of export.py to_kanae_flow / to_kanae_flows) ────────────────────────────
(def KEIZU-KIND-TO-KANAE
  {"budget-outlay" "outlay" "subsidy" "subsidy" "grant" "grant" "procurement-award" "procurement"})

(defn- to-kanae-flow
  "One keizu :money → one kanae fundFlowEdge. Throws if the kind is not a govt fiscal flow."
  [m]
  (let [kind (#'w/kw* (get m ":money/kind"))]
    (when-not (contains? KEIZU-KIND-TO-KANAE kind)
      (throw (ex-info (str "export: '" kind "' is not a kanae fiscal flow (e.g. political-donation excluded)") {})))
    {"edgeId" (str "keizu:" (str (get m ":money/id" "?")))
     "flowType" (get KEIZU-KIND-TO-KANAE kind)
     "donor" (get m ":money/payer" "")
     "recipient" (get m ":money/payee" "")
     "amount" (#'w/to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))
     "currency" (get m ":money/currency" "")
     "asOf" (long (get m ":money/as-of" 0))
     "sources" (vec (get m ":money/sources" []))}))

(defn to-kanae-flows
  "All fiscal :money → kanae flows; non-fiscal kinds (political-donation) skipped + counted."
  [g]
  (let [[flows skipped]
        (reduce (fn [[fs sk] m]
                  (if (contains? KEIZU-KIND-TO-KANAE (#'w/kw* (get m ":money/kind")))
                    [(conj fs (to-kanae-flow m)) sk]
                    [fs (conj sk (get m ":money/id"))]))
                [[] []] (get g "money"))]
    {"flows" flows "skipped" skipped "skipped_count" (count skipped)}))

;; ── the membrane (1:1 of analyze.run) ─────────────────────────────────────────────────────────
(defn run
  "Weave the seed graph → concentration → dry-run posts + kanae flows. Pure over a parsed graph.
  Returns {\"concentration\" c \"posts\" posts \"kanae_flows\" kf} (the #?(:clj) -main does the I/O)."
  [graph]
  (let [g (w/weave graph)
        c (w/concentration g)
        committees (get g "committees")
        cco (get c "committee_cross_organ")
        posts (cond-> []
                (seq cco)
                (conj (let [f (first cco)
                            comm (get committees (get f "committee") {})
                            srcs (concat (vec (get comm ":committee/sources" []))
                                         ["https://www.mof.go.jp/"])]
                        (draft-committee-post f srcs))))
        allsrcs (vec (sort (set (mapcat #(get % ":money/sources" []) (get g "money")))))
        posts (cond-> posts
                (seq allsrcs)
                (conj (draft-money-post (get c "money_concentration") allsrcs)))]
    {"graph" g
     "concentration" c
     "posts" posts
     "kanae_flows" (to-kanae-flows g)}))

;; ── report rendering (1:1 of analyze._write_report f-strings) ─────────────────────────────────
(defn report-md
  "Render the intel-report markdown byte-for-byte with analyze.py's _write_report."
  [c posts]
  (let [L (transient
           ["# 系図 (keizu) — government power-relations intel (dry-run)\n"
            "_Accountability map, NOT a target-list. Non-adjudicating. :representative seed._\n"
            (str "\nnodes=" (get c "node_count") " committees=" (get c "committee_count")
                 " rels=" (get c "rel_count") " money=" (get c "money_count")
                 " statements=" (get c "statement_count") "\n")
            (str "\n_referential integrity: " (get-in c ["integrity" "dangling_count"])
                 " dangling reference(s)._\n")
            "\n## By jurisdiction\n"])]
    (doseq [j (get c "by_jurisdiction")]
      (conj! L (str "- **" (get j "jurisdiction") "** — " (get j "nodes") " nodes, "
                    (get j "committees") " committees, money " (fmt-f (get j "money_total") 0))))
    (conj! L "\n## Committee cross-organ concentration\n")
    (doseq [r (get c "committee_cross_organ")]
      (conj! L (str "- **" (get r "label") "** — " (get r "member_count") " seats from "
                    (get r "distinct_organs") " organ(s): " (str/join ", " (get r "organs")))))
    (conj! L "\n## Cross-committee seats (co-membership)\n")
    (doseq [r (get c "cross_committee_seats")]
      (conj! L (str "- `" (get r "seat") "` sits on " (get r "committee_count")
                    " committees: " (str/join ", " (get r "committees")))))
    (when-not (seq (get c "cross_committee_seats"))
      (conj! L "- (none in seed)"))
    (conj! L "\n## Cross-organ connector seats\n")
    (doseq [r (get c "connector_seats")]
      (conj! L (str "- `" (get r "seat") "` bridges " (get r "organs_bridged")
                    " organs: " (str/join ", " (get r "organs")))))
    (when-not (seq (get c "connector_seats"))
      (conj! L "- (none in seed)"))
    (let [mc (get c "money_concentration")]
      (conj! L (str "\n## Money concentration (by payee) — HHI=" (w/to-json (get mc "hhi"))
                    " over total " (fmt-f (get mc "total") 0) "\n"))
      (doseq [[payee share] (get mc "shares")]
        (conj! L (str "- `" payee "`: " (fmt-f (* share 100) 1) "%"))))
    (let [pc (get c "payer_concentration")]
      (conj! L (str "\n## Money concentration (by payer) — HHI=" (w/to-json (get pc "hhi")) "\n"))
      (doseq [[payer share] (get pc "shares")]
        (conj! L (str "- `" payer "`: " (fmt-f (* share 100) 1) "%"))))
    (conj! L "\n## Revolving-door chains\n")
    (doseq [r (get c "revolving_door")]
      (conj! L (str "- " (get r "from_label") " → " (get r "to_label")
                    " (as-of " (get r "as_of") ")")))
    (when-not (seq (get c "revolving_door"))
      (conj! L "- (none in seed)"))
    (conj! L "\n## Award-and-fund co-occurrence (FACTUAL, non-adjudicating)\n")
    (conj! L "_A node that both received public money and made a political donation. A co-occurrence of two disclosed flows — NOT an allegation of wrongdoing._\n")
    (doseq [r (get c "award_and_fund")]
      (conj! L (str "- `" (get r "node") "` — received " (fmt-f (get r "received_total") 0)
                    " from " (str/join ", " (get r "received_from")) "; "
                    "donated " (fmt-f (get r "donated_total") 0)
                    " to " (str/join ", " (get r "donated_to")))))
    (when-not (seq (get c "award_and_fund"))
      (conj! L "- (none in seed)"))
    (let [si (get c "statement_index")]
      (conj! L (str "\n## Statements (発言) — " (get si "count") " indexed\n"))
      (conj! L "_Indexed by speaker + topic from public record; never rated true/false (ake/danjo own truth)._\n")
      (doseq [[speaker n] (get si "by_speaker")]
        (conj! L (str "- `" speaker "`: " n " statement(s)")))
      (doseq [t (get si "by_topic")]
        (conj! L (str "  - topic _" (get t "topic") "_ — " (str/join ", " (get t "speakers"))))))
    (conj! L "\n## Dry-run social posts\n")
    (doseq [p posts]
      (conj! L (str "> " (get p ":post/body") "\n>\n> _status=" (get p ":post/status")
                    " isMirror=" (if (get p ":post/is-mirror") "True" "False")
                    " serverHeldKey=" (if (get p ":post/server-held-key") "True" "False")
                    " sources=" (count (get p ":post/sources")) "_\n")))
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── derived relation-graph edn (1:1 of analyze._write_graph) ──────────────────────────────────
(defn graph-edn
  "Emit the validated relation graph as derived edn (the kotoba-ingest body shape)."
  [g]
  (let [head [";; GENERATED by keizu analyze.py — validated relation graph (do not hand-edit)"
              "{:graph {:name \"keizu-relations-v1\" :visibility :public}"
              " :rels ["]
        rels (map (fn [r]
                    (str "  {:rel/id \"" (get r ":rel/id") "\" :rel/source \"" (get r ":rel/source") "\" "
                         ":rel/target \"" (get r ":rel/target") "\" :rel/kind " (get r ":rel/kind") " "
                         ":rel/non-adjudicating-notice true}"))
                  (get g "rels"))
        lines (concat head rels [" ]}"])]
    (str (str/join "\n" lines) "\n")))

;; ── render payload JSON (1:1 of export.render_payload / render_json) ──────────────────────────
;; sort_keys=True over a JSON-safe payload; tuples flattened to [k v]. Provided for the
;; kanae-render.json artifact + the test that loads it.
(defn- render-payload [c]
  {"actor" "keizu"
   "isMirror" true
   "nonAdjudicating" true
   "counts" (into {} (map (fn [k] [k (get c k)])
                          ["node_count" "committee_count" "rel_count" "money_count" "statement_count"]))
   "money_by_payee" (mapv vec (get-in c ["money_concentration" "shares"]))
   "money_by_payer" (mapv vec (get-in c ["payer_concentration" "shares"]))
   "money_hhi" {"payee" (get-in c ["money_concentration" "hhi"])
                "payer" (get-in c ["payer_concentration" "hhi"])}
   "by_jurisdiction" (get c "by_jurisdiction")
   "committee_cross_organ" (get c "committee_cross_organ")
   "cross_committee_seats" (get c "cross_committee_seats")
   "connector_seats" (get c "connector_seats")
   "revolving_door" (get c "revolving_door")
   "award_and_fund" (get c "award_and_fund")
   "statement_index" {"count" (get-in c ["statement_index" "count"])
                      "by_speaker" (mapv vec (get-in c ["statement_index" "by_speaker"]))
                      "by_topic" (get-in c ["statement_index" "by_topic"])}})

(defn- json-sorted
  "json.dumps(..., ensure_ascii=False, sort_keys=True) — recursively sort map keys, then reuse
  the weave to-json scalar/float repr. (::order metadata is ignored; keys go alphabetical.)"
  [v]
  (cond
    (map? v) (str "{" (str/join ", " (map (fn [[k val]]
                                            (str (#'w/json-str k) ": " (json-sorted val)))
                                          (sort-by key (seq v)))) "}")
    (sequential? v) (str "[" (str/join ", " (map json-sorted v)) "]")
    :else (w/to-json v)))

(defn render-json
  "The render payload as a sort_keys=True JSON string (proves it is fully serializable)."
  [c]
  (json-sorted (render-payload c)))

#?(:clj
   (defn -main
     "CLI: weave the seed → write out/intel-report.md + out/relation-graph.kotoba.edn +
     out/kanae-render.json. Byte-parity target = intel-report.md (matches analyze.py)."
     [& argv]
     (let [argv (vec argv)
           methods-dir (delay (-> *file* clojure.java.io/file .getParentFile))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file @methods-dir ".." "data" "seed-relation-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file @methods-dir "out"))
           res (run (kedn/load-edn seed))
           c (get res "concentration")
           g (get res "graph")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md") (report-md c (get res "posts")))
       (spit (clojure.java.io/file outdir "relation-graph.kotoba.edn") (graph-edn g))
       (spit (clojure.java.io/file outdir "kanae-render.json") (render-json c))
       (println (str "# keizu analyze — nodes=" (get c "node_count")
                     " committees=" (get c "committee_count")
                     " rels=" (get c "rel_count") " money=" (get c "money_count")))
       0)))
