(ns tasuke.methods.intake
  "intake.cljc — 助 (tasuke) plain-language intake: 誰でも使える, no EDN required. ADR-2606060900.
  1:1 Clojure port of `methods/intake.py`.

  Lets a non-technical victim build their case by answering plain questions, then hands the case to
  `packet/build-packet` / `packet/write-packet`. The mapping from answers → a `:case/*` map is a
  PURE FUNCTION (`build-case-from-answers`) so it is fully testable without stdin; the interactive
  loop is a thin shell around it.

  The charter invariants are baked into the mapping, not left to the asker:
    G1 — :case/support-cost-jpy is hard-set to 0 (the victim is never asked for, and cannot enter, a fee).
    G7 — :case/consent comes from an explicit yes/no; without a clear yes, no case is built (raises).
    G7 — :case/server-held-key is hard-set to false.

  House style: Python ':…' keyword strings stay strings; pure fns. The loss parser accepts
  「480000」「48万」「48万円」「480,000円」 and returns yen as a long. sha1 at the #?(:clj) edge.
  (The interactive `__main__` demo is intentionally omitted from the port.)"
  (:require [clojure.string :as str]))

;; (field, prompt, kind) — shared by the interactive loop AND the tests.
(def QUESTIONS
  [["consent"    "この内容で被害対応を進めてよいですか? (はい/いいえ)"                 "yesno"]
   ["narrative"  "何が起きましたか? できるだけ具体的に教えてください"                  "text"]
   ["occurred"   "いつ起きましたか? (例: 2026-06-03 朝)"                              "text"]
   ["loss"       "金銭被害はいくらですか? (例: 48万 / 480000 / なし)"                  "yen"]
   ["service"    "関係するサービス名は? (例: ○○銀行 / LINE / なければ空欄)"           "text"]
   ["account_id" "対象のアカウントID・口座・URL があれば教えてください"               "text"]])

(def ^:private YES #{"はい" "yes" "y" "はい。" "ok" "進める" "true" "1"})
(def ^:private NO  #{"いいえ" "no" "n" "やめる" "false" "0" ""})

(defn parse-yesno [s]
  (contains? YES (str/lower-case (str/trim (str s)))))

(defn parse-yen
  "「48万」「480,000円」「なし」→ long yen. Best-effort; returns 0 when none/unparseable."
  [s]
  (let [t (-> (str s) (str/trim) (str/replace "," "") (str/replace "円" ""))]
    (cond
      (or (str/blank? t) (contains? #{"なし" "無し" "ない" "0"} t)) 0
      :else
      (if-let [m (re-matches #"\s*(\d+(?:\.\d+)?)\s*万\s*(\d+)?\s*" t)]
        (let [man (* (#?(:clj Double/parseDouble :cljs js/parseFloat) (nth m 1)) 10000)
              rest-man (if-let [r (nth m 2)] (#?(:clj Long/parseLong :cljs js/parseInt) r) 0)]
          (long (+ man rest-man)))
        (if-let [m2 (re-matches #"\s*(\d+)\s*" t)]
          (#?(:clj Long/parseLong :cljs js/parseInt) (nth m2 1))
          0)))))

(defn- sha1-hex
  "Hex sha1 of the UTF-8 bytes of a string."
  [^String s]
  #?(:clj (let [d (.digest (java.security.MessageDigest/getInstance "SHA-1") (.getBytes s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) d)))
     :default (throw (ex-info "bind a sha-1 impl on this host" {}))))

(defn- case-id [narrative occurred]
  (str "case-" (subs (sha1-hex (str narrative "|" occurred)) 0 8)))

(defn build-case-from-answers
  "Map plain answers → a :case/* map. PURE. Raises (G7) if consent is not clearly given."
  ([answers] (build-case-from-answers answers "did:web:etzhayyim.com:member:self"))
  ([answers subject]
   (when-not (parse-yesno (get answers "consent" ""))
     (throw (ex-info "G7: 被害者の明確な同意がなければ case は作成しません (はい/yes が必要)" {})))
   (let [narrative (str/trim (str (get answers "narrative" "")))
         occurred  (str/trim (str (get answers "occurred" "")))
         loss      (parse-yen (get answers "loss" ""))
         case {":case/id" (or (not-empty (get answers "case_id")) (case-id narrative occurred))
               ":case/subject" subject
               ":case/narrative" narrative
               ":case/occurred-at-text" occurred
               ":case/loss-jpy" loss
               ":case/service" (str/trim (str (get answers "service" "")))
               ":case/account-id" (str/trim (str (get answers "account_id" "")))
               ;; ── invariants baked in, never asked ──
               ":case/consent" true            ;; G7 (we got here only via an explicit yes)
               ":case/support-cost-jpy" 0      ;; G1 全て無料
               ":case/server-held-key" false}] ;; G7 no-server-key
     (if (> loss 0)
       (assoc case ":case/loss-breakdown" [{":label" "被害額" ":jpy" loss}])
       case))))

(defn interactive
  "Ask the questions on the console and return the built case. `ask` is injectable for tests.
  Aborts (throws) when consent is refused (Python's SystemExit)."
  [ask]
  (let [answers
        (reduce (fn [acc [field prompt _kind]]
                  (let [a (ask (str prompt "\n> "))]
                    (when (and (= field "consent") (not (parse-yesno a)))
                      (throw (ex-info "同意が得られませんでした。対応を中止します。" {:abort true})))
                    (assoc acc field a)))
                {}
                QUESTIONS)]
    (build-case-from-answers answers)))
