(ns tate.methods.terms-scan
  "tate 盾 — disadvantageous-clause scanner over the member's OWN contracts / ToS.
  1:1 Clojure port of `methods/terms_scan.py` (ADR-2606112301 + worldwide ADR-2606112400).

  Matches the member's contract texts (consumer ToS / credit-card member agreement /
  B2B 法人契約) against the coded clause-pattern registry and emits FLAGS: pattern +
  DISCLOSED statutory anchor + risk + route. 不利な契約をしていないか — surfaced, not
  adjudicated.

  CONSTITUTIONAL (read before any change):
    G1 — member-principal, own documents only (R0 = synthetic seed).
    G2 — non-adjudicating (UPL). A flag is {pattern, anchor, risk, route} — a pointer to a
      DISCLOSED statute, never a validity verdict. No :flag/verdict.
    G5 — context honesty. Consumer-protection anchors are NEVER applied to :b2b documents.
    G10 — jurisdiction honesty. A pattern's jurisdiction must match the document's
      (default :jp) — anchors never cross jurisdictions.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O at the
  #?(:clj) edge. Portable .cljc."
  (:require [clojure.string :as str]
            [tate.methods.edn :as edn]))

(def risk-order {":high" 0 ":mid" 1 ":info" 2})

;; ── default-path helpers (#?(:clj) edge — mirror Python HERE/data/*.edn) ──────
#?(:clj
   (def ^:private here-dir
     ;; Captured at namespace-load time: *file* is this source file here, NOT at call-time
     ;; (sci rebinds *file* to the caller). Actor root = methods/../ (= Python HERE).
     (-> *file* clojure.java.io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn here
     "Actor root dir (= Python HERE = methods/../)."
     []
     here-dir))

#?(:clj
   (defn load-patterns
     "Clause patterns from data/clause-patterns.edn (entries with :clause/id)."
     ([] (load-patterns (clojure.java.io/file (here) "data" "clause-patterns.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":clause/id")) vec))))

#?(:clj
   (defn load-docs
     "Returns [docs notices] from data/seed-member-docs.edn (or a given path)."
     ([] (load-docs (clojure.java.io/file (here) "data" "seed-member-docs.edn")))
     ([path]
      (let [forms (edn/load-edn path)
            docs (filterv #(and (map? %) (contains? % ":doc/id")) forms)
            notices (filterv #(and (map? %) (contains? % ":notice/id")) forms)]
        [docs notices]))))

(defn scan-doc
  "Flags for one document. G5: pattern context must match the document context.
  G10: pattern jurisdiction must match the document jurisdiction (default :jp)."
  [doc patterns]
  (let [text (str/lower-case (get doc ":doc/text" ""))   ;; casefold ≈ lower-case
        ctx (get doc ":doc/context")
        juris (get doc ":doc/jurisdiction" ":jp")
        flags
        (for [p patterns
              :when (= (get p ":clause/context") ctx)            ;; G5
              :when (= (get p ":clause/jurisdiction" ":jp") juris) ;; G10
              :let [hit (some (fn [k] (when (str/includes? text (str/lower-case k)) k))
                              (get p ":clause/keywords"))]
              :when (some? hit)]
          {"doc" (get doc ":doc/id")
           "doc_label" (get doc ":doc/label" (get doc ":doc/id"))
           "jurisdiction" juris
           "clause" (get p ":clause/id")
           "clause_label" (get p ":clause/label")
           "matched" hit
           "risk" (get p ":clause/risk")
           "anchor" (get p ":clause/anchor")            ;; DISCLOSED pointer — never a verdict (G2)
           "route" (get p ":clause/route")
           "disclosed" true
           "verify_current_law" true})]
    (vec (sort-by (juxt #(get risk-order (get % "risk") 9) #(get % "clause")) flags))))

(defn scan
  [docs patterns]
  (let [flags (vec (mapcat #(scan-doc % patterns) docs))
        by-route (reduce (fn [m f] (update m (get f "route") (fnil inc 0))) {} flags)]
    {"flags" flags
     "docs_scanned" (count docs)
     "counts_by_route" (into (sorted-map) by-route)}))

(defn- edn-str [s]
  (str "\"" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))

(defn make-kaiyaku-handoff
  "Machine-readable handoff to kaiyaku 解約: every :kaiyaku-routed flag as ingestable EDN.
  1:1 with Python make_kaiyaku_handoff."
  [res]
  (let [L (transient
           [";; tate 盾 → kaiyaku 解約 handoff — GENERATED (ADR-2606112301/2606112201). DO NOT hand-edit."
            ";; :kaiyaku-routed clause flags only — 自動更新/解約窓 candidates for the 縁-ledger."
            ""
            "["])]
    (doseq [f (get res "flags")
            :when (= (get f "route") ":kaiyaku")]
      (conj! L (str " {:handoff/doc " (edn-str (get f "doc"))))
      (conj! L (str "  :handoff/jurisdiction " (get f "jurisdiction")))
      (conj! L (str "  :handoff/clause " (edn-str (get f "clause"))))
      (conj! L (str "  :handoff/matched " (edn-str (get f "matched"))))
      (conj! L (str "  :handoff/anchor " (edn-str (get f "anchor"))))
      (conj! L "  :handoff/action :calendar-notice-window}"))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

(defn- py-dict-str
  "Render a string-keyed/int-valued sorted map as a Python dict repr: {'k': v, ...}."
  [m]
  (str "{" (str/join ", " (map (fn [[k v]] (str "'" k "': " v)) m)) "}"))

(defn report
  [res]
  (let [L (transient ["# tate 盾 — 不利条項 readout (non-adjudicating, G2)" ""])]
    (conj! L (str "- docs scanned: " (get res "docs_scanned") " · flags: " (count (get res "flags"))
                  " · routes: " (py-dict-str (get res "counts_by_route"))))
    (conj! L "")
    (conj! L "| doc | juris | clause | risk | 開示アンカー | route |")
    (conj! L "|---|---|---|---|---|---|")
    (doseq [f (get res "flags")]
      (conj! L (str "| " (get f "doc_label") " | " (get f "jurisdiction") " | "
                    (get f "clause_label") " | " (get f "risk") " | " (get f "anchor")
                    " | " (get f "route") " |")))
    (conj! L "")
    (conj! L (str "各フラグは「該当する **可能性** のある条項パターン + 開示済み法令アンカー」です。"
                  "有効/無効の判断はしません — 高リスクは法テラス・弁護士会の専門家確認へ (G2 UPL)。"))
    (str (str/join "\n" (persistent! L)) "\n")))
