(ns himotoki.methods.request
  "himotoki 繙き — DSAR/FOIA disclosure-request draft generator
  (1:1 Clojure port of methods/request.py, ADR-2605302130). Turns a consenting
  member's own-data request against a coded disclosureTarget into a ready-to-send
  DRAFT — never a live dispatch.

  Structural charter invariants (raised in build-request / can-dispatch / build-batch):
    G3  DSAR is OWN-DATA-ONLY (member must assert ownDataOnly=true).
    G4  TRUE requester, no pretext/sockpuppet/impersonation field.
    G6  PII NEVER inline — a com.etzhayyim.encrypted:* envelope ref only.
    G8  no mass-filing (≤ MAX_BATCH targets per batch).
    G14 verify-before-dispatch (refused vs unverified/stale target).
    G10 outbound-gated (live dispatch needs the operator gate).

  Maps use string keys (mirroring the Python dicts / parsed JSON). JSON registry
  load lives at the #?(:clj) edge."
  (:require [clojure.string :as str]))

(def MAX-BATCH 5)
(def ^:private dsar-regime-prefixes ["gdpr" "ccpa" "cpra" "appi" "lgpd" "pipeda" "pdpa" "pipl"])
(def ^:private forbidden-pretext-fields ["pretext" "sockpuppet" "impersonat" "alias" "false-identity"])

(defn is-dsar
  "DSAR (own-data) vs FOIA (public records), inferred from the regime."
  [target]
  (let [regime (str/lower-case (str (get target "regime" "")))]
    (cond
      (some #(str/starts-with? regime %) dsar-regime-prefixes) true
      (or (str/includes? regime "foia") (str/includes? regime "情報公開") (str/ends-with? regime "-foia")) false
      :else (boolean (some (fn [r] (let [rl (str/lower-case (str r))]
                                     (some #(str/starts-with? rl %) dsar-regime-prefixes)))
                           (get target "altRegimes" []))))))

(defn is-verified [target]
  (= (str (get target "verificationStatus" "")) "verified"))

(defn build-request
  "Build a disclosure-request draft. RAISES on a charter violation (G3/G4/G6)."
  [target member]
  (let [requester (or (get member "requesterDid") "")]
    (when (str/blank? requester)
      (throw (ex-info "G4: every request must identify the true requester DID (no pretext)" {})))
    ;; G4 — no pretext/sockpuppet/alias field may be supplied.
    (doseq [k (keys member)]
      (let [kl (str/lower-case (str k))]
        (when (some #(str/includes? kl %) forbidden-pretext-fields)
          (throw (ex-info (str "G4: pretext field '" k "' is unrepresentable; the true requester must file") {})))))
    (let [dsar (is-dsar target)]
      (when (and dsar (not (true? (get member "ownDataOnly"))))
        (throw (ex-info "G3: a DSAR is own-data-only; member must assert ownDataOnly=true" {})))
      ;; G6 — PII must be an encrypted envelope ref, never plaintext.
      (let [env (or (get member "subjectEnvelopeRef") "")]
        (when-not (str/starts-with? env "com.etzhayyim.encrypted:")
          (throw (ex-info (str "G6: member identity must be a com.etzhayyim.encrypted:* envelope ref, "
                               "never plaintext PII in the draft") {})))
        (doseq [forbidden ["name" "email" "address" "phone"]]
          (when (and (contains? member forbidden) (get member forbidden))
            (throw (ex-info (str "G6: plaintext PII '" forbidden "' must not be in the request; use the envelope") {}))))
        (array-map
         "type" "himotoki.disclosureRequest"
         "kind" (if dsar "DSAR" "FOIA")
         "regime" (get target "regime")
         "organization" (get target "organization")
         "jurisdiction" (get target "jurisdiction")
         "channelType" (get target "channelType")
         "requesterDid" requester
         "subjectEnvelopeRef" env
         "ownDataOnly" (boolean dsar)
         "statutoryDeadlineDays" (get target "statutoryDeadlineDays")
         "targetVerified" (is-verified target)
         "dispatchReady" false
         "sourcing" ":representative")))))

(defn can-dispatch
  "G14 + G10: a draft may transmit ONLY against a verified target AND with the operator
  gate. Returns [allowed reason-if-refused]."
  [target operator-gate]
  (cond
    (not (is-verified target))
    [false (str "G14: target is unverified-seed / stale; verify (and re-check within the "
                "freshness window) before any dispatch")]
    (not operator-gate)
    [false "G10: live dispatch needs HIMOTOKI_OPERATOR_GATE=1 (Council + operator)"]
    :else [true ""]))

(defn deadline-status
  "Operational status of an OUTSTANDING request's response clock — the deadline_tracker's read, for
  chasing / appeal routing. Pure arithmetic over the request's OWN figures (the statutory + extension
  day-counts come from the verified target registry / chigiri — himotoki TRACKS, chigiri owns the
  law; nothing statutory is hardcoded here). Given days elapsed since dispatch it returns the days
  remaining and a status ∈ {:open, :due-soon (within the notice window of the statutory deadline),
  :overdue (past the deadline but still inside any documented extension), :appeal-eligible (past
  deadline + extension — the member may now escalate via appeal_route)}. A request whose regime sets
  no fixed day-count (APPI 遅滞なく / PIPL timely → statutoryDeadlineDays nil) is :no-fixed-deadline.
  Deterministic (elapsed-days is supplied — no wall clock); operational request-management, never
  legal advice (G5/UPL). Returns {:status :remaining :elapsed :statutory-deadline-days :extension-days}."
  ([request elapsed-days] (deadline-status request elapsed-days 7))
  ([request elapsed-days notice-days]
   (let [d (get request "statutoryDeadlineDays")
         e (long elapsed-days)]
     (if (nil? d)
       {:status :no-fixed-deadline :elapsed e}
       (let [d (long d)
             x (long (or (get request "extensionDays") 0))
             remaining (- d e)]
         {:status (cond
                    (> e (+ d x)) :appeal-eligible
                    (> e d)       :overdue
                    (<= remaining notice-days) :due-soon
                    :else         :open)
          :remaining remaining
          :elapsed e
          :statutory-deadline-days d
          :extension-days x})))))

(defn build-batch
  "Build drafts for several targets. RAISES (G8) if more than MAX-BATCH — no mass-filing."
  [target-ids member registry]
  (when (> (count target-ids) MAX-BATCH)
    (throw (ex-info (str "G8: no mass-filing — at most " MAX-BATCH " targets per batch, got " (count target-ids)) {})))
  (mapv #(build-request (get registry %) member) target-ids))

(defn render-edn
  "Render disclosure-request DRAFTS to a .kotoba.edn string (never dispatched)."
  [drafts]
  (let [head [";; himotoki-request-drafts.kotoba.edn — disclosure-request DRAFTS (never dispatched)."
              ";; G3 own-data-only DSAR · G4 true-requester (no pretext) · G6 PII = encrypted"
              ";; envelope ref (never plaintext) · G14 dispatch refused vs unverified target ·"
              ";; G10 outbound-gated. DERIVED :representative. ADR-2605302130." "" "["]
        rows (map (fn [d]
                    (str " {:himotoki.req/kind :" (get d "kind")
                         " :himotoki.req/regime \"" (get d "regime") "\""
                         " :himotoki.req/organization \"" (get d "organization") "\""
                         " :himotoki.req/requester-did \"" (get d "requesterDid") "\""
                         " :himotoki.req/subject-envelope-ref \"" (get d "subjectEnvelopeRef") "\""
                         " :himotoki.req/own-data-only " (str (get d "ownDataOnly"))
                         " :himotoki.req/target-verified " (str (get d "targetVerified"))
                         " :himotoki.req/dispatch-ready false :himotoki.req/sourcing :representative}"))
                  drafts)]
    (str (str/join "\n" (concat head rows ["]"])) "\n")))

#?(:clj
   (defn load-registry
     "Return {targetId target}; targetId = '<organization>:<regime>'. JSON I/O edge.
     `path` points at registry/targets.seed.json."
     [path]
     (let [parse @(requiring-resolve 'cheshire.core/parse-string)
           d (parse (slurp (str path)))]
       (reduce (fn [m t] (assoc m (str (get t "organization") ":" (get t "regime")) t))
               {} (get d "targets" [])))))
