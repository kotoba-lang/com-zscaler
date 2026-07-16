(ns tasuke.methods.packet
  "packet.cljc — 助 (tasuke) victim packet generator: the \"誰でも使える\" entry point. ADR-2606060900.
  1:1 Clojure port of `methods/packet.py`.

  Turns a victim's case into a complete, ready-to-print document packet a real person can take
  to the police / their bank / a platform. This is the usable surface over the R0 engines.

  It writes, into `out/packet-<caseId>/`:
    00-COVER.md           the action checklist + free public windows + deadlines + ¥0 statement
    NN-<doc-kind>.txt     each member-authored filing, ready to review · sign · submit

  Every invariant the engines enforce is preserved here by construction — the cover restates that
  the packet is FREE (G1), member-authored + member-submitted (G2/G3), and draft-only at R0 (G9);
  `build-packet` runs `report-gen/assert-member-authored` on every document. 助 generates; the
  member signs and submits.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at the #?(:clj)
  edge. (The interactive `__main__`/CLI demo is intentionally omitted from the port.)"
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.report-gen :as rg]
            [tasuke.methods.triage :as triage]))

(defn- kw* [v]
  (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") (last) (str/lower-case)))

;; ── _ja_kind reaches into report_gen's private JA map (mirrors Python rg._ja_kind) ──
(defn- ja-kind [kind] ((deref #'tasuke.methods.report-gen/ja-kind) kind))

#?(:clj (def ^:private actor-root (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private seed-file (io/file actor-root "data" "seed-cybercrime-cases.kotoba.edn")))

(defn- to-int
  [v]
  (cond
    (nil? v) 0
    (false? v) 0
    (number? v) (long v)
    (string? v) (if (str/blank? v) 0 (try (Long/parseLong (str/trim v)) (catch Exception _ 0)))
    :else 0))

(defn documents-for-kind
  "The single source of truth for which member-authored documents a scam KIND warrants.

  Always the police core (被害届 / 被害状況報告書 / 証拠目録 / 被害額算定書); plus a bank組戻し
  for money-moved cases, a platform request for account/identity cases, and a recovery plan when
  credentials were exposed."
  ([case kind] (documents-for-kind case kind nil))
  ([case kind evidence]
   (let [ev (or evidence [])
         docs [(rg/damage-report case)
               (rg/incident-statement case)
               (rg/evidence-index-doc case ev)
               (rg/damage-calculation case)]
         ;; Python: kind == "unauthorized-transfer"
         ;;         or int(loss) > 0 and kind in (investment-scam, fake-billing, support-scam)
         ;; (`and` binds tighter than `or`)
         add-bank? (or (= kind "unauthorized-transfer")
                       (and (> (to-int (get case ":case/loss-jpy" 0)) 0)
                            (contains? #{"investment-scam" "fake-billing" "support-scam"} kind)))
         docs (cond-> docs
                add-bank?
                (conj (rg/bank-freeze-request case))
                (contains? #{"account-takeover" "impersonation" "sns-fraud" "phishing" "leak-extortion"} kind)
                (conj (rg/platform-request case :purpose "凍結・復旧"))
                (contains? #{"account-takeover" "phishing" "support-scam"} kind)
                (conj (rg/recovery-plan case :service (get case ":case/service" "（対象サービス）"))))]
     docs)))

#?(:clj
   (def ^:private window-registry
     (delay
       (let [seed (tedn/load-edn seed-file)]
         (reduce (fn [acc w] (assoc acc (get w ":registry/window") w))
                 {}
                 (get seed ":registry/windows" []))))))

(defn build-packet
  "Assemble the full packet for a case. Raises (G1/G7) if the case isn't free/consented."
  ([case] (build-packet case nil))
  ([case evidence]
   (let [tri (triage/triage case)                       ;; G1/G7 gate
         kind (kw* (get tri ":triage/scam-kind"))
         docs (documents-for-kind case kind evidence)]
     (doseq [d docs]
       (rg/assert-member-authored d))                   ;; G1/G2/G3/G9 on every doc
     (let [reg #?(:clj @window-registry :default {})
           windows (mapv (fn [w]
                           (let [r (get reg w {})]
                             {"code" (kw* w)
                              "name" (get r ":registry/name" (kw* w))
                              "contact" (get r ":registry/contact" "")
                              "basis" (get r ":registry/basis" "")}))
                         (get tri ":triage/windows"))]
       {"caseId" (get case ":case/id" "case")
        "kind" kind
        "severity" (kw* (get tri ":triage/severity"))
        "cost" (get tri ":triage/support-cost-jpy")
        "documents" docs
        "windows" windows
        "actions" (get tri ":triage/actions")
        "deadlines" (get tri ":triage/deadlines")}))))

(defn cover
  "Render the 00-COVER.md markdown byte-for-byte with packet.py's _cover."
  [p]
  (let [head [(str "# 助 (tasuke) 被害対応パケット — " (get p "caseId") "\n")
              (str "**被害類型**: " (ja-kind (get p "kind")) "　**緊急度**: " (get p "severity") "　"
                   "**あなたの負担: ¥" (get p "cost") "（全て無料）**\n")
              (str "> この一式は **あなた本人が作成・署名・提出** する書類です（助 は作成を手伝うだけで、"
                   "提出はあなたが行います）。弁護士費用も利用料も一切かかりません。\n")
              "## まず行うこと（上から順に）\n"]
        actions (map-indexed (fn [i a] (str (inc i) ". " a)) (get p "actions"))
        deadlines (when (seq (get p "deadlines"))
                    (cons "\n## ⏰ 期限の注意\n"
                          (map #(str "- " %) (get p "deadlines"))))
        windows (cons "\n## 無料の相談・通報窓口\n"
                      (map (fn [w]
                             (str "- **" (get w "name") "** — " (get w "contact")
                                  (when-not (str/blank? (get w "basis")) (str "（" (get w "basis") "）"))))
                           (get p "windows")))
        docs (cons "\n## 同梱書類（印刷して署名のうえ提出）\n"
                   (map-indexed (fn [i d]
                                  (str "- `" (format "%02d" (inc i)) "-"
                                       (str/replace (get d ":doc/kind") #"^:+" "") ".txt` — 宛先: "
                                       (get d ":doc/addressed-to")))
                                (get p "documents")))
        out (concat head actions deadlines windows docs)]
    (str (str/join "\n" out) "\n")))

#?(:clj
   (defn write-packet
     "Write the cover + each document into `outdir` (or out/packet-<caseId>). Returns the dir."
     ([p] (write-packet p nil))
     ([p outdir]
      (let [d (or outdir (io/file actor-root "methods" "out" (str "packet-" (get p "caseId"))))]
        (.mkdirs (io/file d))
        (spit (io/file d "00-COVER.md") (cover p))
        (doseq [[i doc] (map-indexed vector (get p "documents"))]
          (spit (io/file d (str (format "%02d" (inc i)) "-"
                                (str/replace (get doc ":doc/kind") #"^:+" "") ".txt"))
                (get doc ":doc/body")))
        d))))
