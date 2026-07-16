(ns tasuke.methods.analyze
  "analyze.cljc — 助 (tasuke) end-to-end membrane (dry-run). ADR-2606060900.
  1:1 Clojure port of `methods/analyze.py`.

  Runs each seed case through the full free support pipeline:
    intake → triage (scam-kind + severity + free windows + action checklist)
           → generate member-authored documents (被害届 / 被害状況報告書 / 証拠目録 /
             被害額算定書 / 銀行組戻し依頼 / プラットフォーム依頼 / 復旧手順)
           → assert every document is FREE, member-authored, signature-required, draft-only

  and emits an offline scorecard (Markdown). NO live filing / submission / send — all of that is
  G9 (Council Lv6+ + operator). A dry-run demonstration that the whole journey costs ¥0.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at the #?(:clj)
  edge. Byte-parity: `-main` writes the SAME bytes analyze.py writes to out/support-dryrun.md."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.report-gen :as rg]
            [tasuke.methods.triage :as triage]))

#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))

;; a tiny encrypted-evidence stand-in (G6 — ref + hash only, never plaintext)
(def ^:private DEMO-EVIDENCE
  [{":evidence/id" "ev1" ":evidence/kind" ":screenshot"
    ":evidence/envelope-ref" "ipfs://bafyEVIDENCE1" ":evidence/bytes" "screenshot-bytes"
    ":evidence/captured-at" 1717500100}
   {":evidence/id" "ev2" ":evidence/kind" ":transaction-record"
    ":evidence/envelope-ref" "ipfs://bafyEVIDENCE2" ":evidence/bytes" "tx-record-bytes"
    ":evidence/captured-at" 1717500200}])

(defn- lstrip-colon [s]
  (str/replace (str s) #"^:+" ""))

(defn docs-for
  "Pick the document set that fits the scam KIND — always police core + kind-specific extras."
  [case kind]
  (let [cid (get case ":case/id")
        ev (mapv #(assoc % ":evidence/case" cid) DEMO-EVIDENCE)
        docs [(rg/damage-report case)
              (rg/incident-statement case)
              (rg/evidence-index-doc case ev)
              (rg/damage-calculation case)]]
    (cond-> docs
      (= kind "unauthorized-transfer")
      (conj (rg/bank-freeze-request case))
      (#{"account-takeover" "impersonation" "sns-fraud" "phishing"} kind)
      (conj (rg/platform-request case :purpose "凍結・復旧"))
      (#{"account-takeover" "phishing"} kind)
      (conj (rg/recovery-plan case :service "（対象サービス）")))))

(defn run
  "Seed → per-case triage + doc generation → rows + total cost. Pure over a parsed seed."
  [seed]
  (let [rows (mapv (fn [case]
                     (let [tri (triage/triage case)           ;; raises if not free / not consented (G1/G7)
                           kind (lstrip-colon (get tri ":triage/scam-kind"))
                           docs (docs-for case kind)]
                       (doseq [d docs]
                         (rg/assert-member-authored d))       ;; G1/G2/G3/G9 guard on every generated doc
                       {"case" (get case ":case/id")
                        "kind" kind
                        "severity" (lstrip-colon (get tri ":triage/severity"))
                        "cost" (get tri ":triage/support-cost-jpy")
                        "windows" (mapv lstrip-colon (get tri ":triage/windows"))
                        "docs" (mapv #(lstrip-colon (get % ":doc/kind")) docs)
                        "actions" (get tri ":triage/actions")
                        "deadlines" (get tri ":triage/deadlines")
                        "paid_referral" (get tri ":triage/paid-referral")}))
                   (get seed ":case/batch"))
        total-cost (reduce + 0 (map #(get % "cost") rows))]
    {"rows" rows "total_cost" total-cost}))

(defn report
  "Render the dry-run scorecard markdown byte-for-byte with analyze.py's _report."
  [res]
  (let [rows (get res "rows")
        total (get res "total_cost")
        L (transient
           ["# 助 (tasuke) — free cybercrime-victim-support membrane dry-run\n"
            (str "End-to-end pipeline over the `:representative` victim cases. No live filing / send "
                 "(G9). Every case costs the victim **¥0** (G1), every document is **member-authored** "
                 "(G3) and **awaits the member's signature** (G2).\n")
            "## Cases\n"
            "| case | scam-kind | severity | victim cost | generated documents | free windows |"
            "|---|---|---|---|---|---|"])]
    (doseq [r rows]
      (conj! L (str "| " (get r "case") " | " (get r "kind") " | " (get r "severity") " | ¥" (get r "cost") " | "
                    (str/join ", " (get r "docs")) " | " (str/join ", " (get r "windows")) " |")))
    (conj! L (str "\n**Total victim cost across all cases: ¥" total " "
                  "(" (if (= total 0) "FREE — G1 holds" "NON-ZERO — G1 VIOLATED") ").**\n"))
    (conj! L "## First-response checklist (sample — first case)\n")
    (when (seq rows)
      (doseq [a (get (first rows) "actions")]
        (conj! L (str "- " a)))
      (when (seq (get (first rows) "deadlines"))
        (conj! L "\n**期限の注意:**")
        (doseq [d (get (first rows) "deadlines")]
          (conj! L (str "- ⏰ " d)))))
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn load-seed
     "Read the default seed-cybercrime-cases.kotoba.edn relative to this file."
     []
     (tedn/load-edn (io/file actor-dir "data" "seed-cybercrime-cases.kotoba.edn"))))

#?(:clj
   (defn -main
     "CLI: run the pipeline → write out/support-dryrun.md + print it; assert ¥0 (G1)."
     [& argv]
     (let [res (run (load-seed))
           rep (report res)
           outdir (io/file actor-dir "methods" "out")]
       (.mkdirs outdir)
       (spit (io/file outdir "support-dryrun.md") rep)
       (print rep)
       (when (not= (get res "total_cost") 0)
         (throw (ex-info "G1 全て無料 violated" {})))
       0)))
