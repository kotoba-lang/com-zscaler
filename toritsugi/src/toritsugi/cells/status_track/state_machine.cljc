(ns toritsugi.cells.status-track.state-machine
  "Phase state machine for the 取次 (toritsugi) status_track cell — the
  処理状況 / 法定処理期間 clock + 結果 intake membrane. After a submission it tracks
  the statutory window and ingests the 結果, refusing any plaintext result:

    G6(toritsugi) — 結果 (結果通知 / 証明書 / 決定通知) is ingested ONLY as an
                encrypted ref (com.etzhayyim.encrypted.*); plaintext PII never
                lands on the MST. A plaintext result is refused by construction.
    G11(toritsugi) — Transparent Religious Force discipline: status-track OBSERVES
                the procedure's processing status; it never coerces / expedites /
                pressures the 窓口. A refusal result routes to the lawful 不服申立
                (審査請求) path via chigiri — tracked here, executed there.

  Pure: (state) -> {\"cell_state\" {…}}. Stdlib only. Self-contained."
  (:require [clojure.string :as str]))

(def phase-init "init")
(def phase-tracked "tracked")
(def phase-refused "refused")

(def encrypted-prefix "com.etzhayyim.encrypted")

(def state-defaults
  {"phase"               phase-init
   "session_id"          ""
   "procedure_id"        ""
   "statutory_days"      0
   "status"              "processing"
   "encrypted_result_ref" ""
   "plaintext_result"    nil             ; MUST stay nil (G6)
   "appeal_route"        ""              ; set when status == "refused" (→ chigiri)
   "refusal"             ""})

(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))

(defn- blank-ref? [ref]
  (let [r (str/trim (str ref))]
    (or (str/blank? r) (not (str/starts-with? r encrypted-prefix)))))

(defn transition
  "Track one submission's status + (optionally) ingest an encrypted result, or
  refuse plaintext PII. Pure: (state) -> {\"cell_state\" {…}}.

  Expected state keys: session_id, procedure_id, statutory_days, status
  (processing|accepted|refused|completed), encrypted_result_ref, plaintext_result."
  [state]
  (let [cs0 (cell-state state)
        status (str/trim (str (get state "status" (get cs0 "status"))))
        result-ref (str/trim (str (get state "encrypted_result_ref" (get cs0 "encrypted_result_ref"))))
        plaintext (get state "plaintext_result" (get cs0 "plaintext_result"))
        cs  (assoc cs0
                   "session_id"   (get state "session_id" (get cs0 "session_id"))
                   "procedure_id" (get state "procedure_id" (get cs0 "procedure_id"))
                   "statutory_days" (get state "statutory_days" (get cs0 "statutory_days"))
                   "status"       status
                   "encrypted_result_ref" result-ref
                   "plaintext_result" plaintext
                   "appeal_route" (if (= "refused" status) "chigiri/不服申立" ""))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (some? plaintext)
      (refuse "G6: 平文 結果は表現不可 — com.etzhayyim.encrypted.* 参照のみ")

      ;; a result intake (accepted/refused/completed) REQUIRES an encrypted ref;
      ;; bare 'processing' status does not (no result to ingest yet).
      (and (not= "processing" status) (blank-ref? result-ref))
      (refuse (str "G6: 結果(" status ")取り込みには encrypted_result_ref が必須 (com.etzhayyim.encrypted.*)"))

      (and (not= "processing" status) (not (blank-ref? result-ref)) (str/blank? result-ref))
      (refuse "G6: 結果参照が空")

      :else
      (let [payload {":status/session"        (get cs "session_id")
                     ":status/procedure"      (get cs "procedure_id")
                     ":status/statutory-days" (get cs "statutory_days")
                     ":status/state"          (get cs "status")
                     ":status/encrypted-result-ref" (get cs "encrypted_result_ref")
                     ":status/appeal-route"   (get cs "appeal_route")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-tracked)}))))
