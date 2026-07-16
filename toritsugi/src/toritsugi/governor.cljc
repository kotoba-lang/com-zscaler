(ns toritsugi.governor
  "ProcedureGovernor — the independent legal/charter layer that earns the
  concierge intelligence the right to *guide / assist / relay*. The contained
  advisor has no binding notion of which procedure is verified, of the 行政書士法
  UPL reserve, of PII confidentiality, of consent binding, of the lawful-channel
  rule, or of the member-self-submission default — so this MUST be a separate
  system (rules over the EAVT ground datoms) able to *reject* a proposal and fall
  back to HOLD (no human can approve past a HARD violation), the toritsugi analog
  of kyoninka's permit hold / robotaxi's MRC / itonami's airworthiness hold.

  Charter (ADR-2605312030): the actor is 案内 + 伴走 + 本人提出支援 by default. It
  renders NO legal/tax advice and performs NO 官公署提出書類の作成代理 (G5), and the
  member-self-submit is the default — 代行 (agent-on-behalf) active-outbound is
  the gated R3 exception that ALWAYS routes to a human/Council sign-off
  (interrupt-before :request-approval).

  HARD invariants (constitutional gates, ADR-2605312030 §4):
    G3  Consent-gated + identity-bound, OWN procedure only.
    G4  Transparent + non-pretextual — member is the named 申請者本人.
    G5  行政書士法 / UPL boundary — 案内 + 入力補助 + 伴走 ONLY; NO advice; NO 作成代理.
    G6  PII confidentiality — encrypted.* DID-bound envelopes ONLY.
    G8  Non-fabrication — cite legal-basis + provenance.
    G10 Lawful-channel-only — official channel + member authorization.
    G14 Verified-procedure-only submission — refuse unverified-seed OR stale.
    G15 Member-self-submission default — 代行 is the gated exception → high-stakes + escalate.
  SOFT:
    S1  Confidence floor → escalate.

  Op scope:
    :guide/build     G3,G4,G5,G8,G14.
    :draft/assist    G3,G4,G5,G6,G8,G14.
    :submit/transmit G3,G4,G6,G8,G10,G14,G15. 代行 → ALWAYS high-stakes."
  (:require [clojure.string :as str]
            [toritsugi.store :as store]))

(def confidence-floor 0.6)
(def default-today 20260709)

;; The constitutional gates the ProcedureGovernor enforces as HARD (unoverridable
;; HOLD — no human can approve past a HARD violation). Exposed as data so the
;; charter-gate suite can pin it (ADR-2605312030 §4): a future R-phase wave cannot
;; silently drop a gate from the governor's HARD surface without breaking the
;; machine-verified charter contract.
(def hard-gates
  "HARD gate surface — exactly the 8 gates the ProcedureGovernor makes
  unoverridable. ADR-2605312030 §4. Mirrored structurally by the 7 cell
  membranes (a cell refusal is folded into the governor verdict as HARD)."
  #{:G3 :G4 :G5 :G6 :G8 :G10 :G14 :G15})

(def ^:private verified-statuses #{"maintainer-verified" "council-verified"})
(def ^:private lawful-channels #{"online" "in-person" "postal"})
(def ^:private submit-modes #{:member-self-submit :agent-on-behalf})
(def ^:private encrypted-prefix "com.etzhayyim.encrypted")

;; ───────────────────────── day arithmetic (Howard Hinnant, pure) ─────────────────────────

(defn- days-from-civil [y m d]
  (let [y   (if (<= m 2) (dec y) y)
        era (quot (if (neg? y) (inc y) y) 400)
        yoe (- y (* era 400))
        moy (if (<= m 2) (+ m 9) (- m 3))
        doy (quot (+ (* 153 moy) 2) 5)
        doe (+ (* yoe 365) (quot yoe 4) (* -1 (quot yoe 100)) doy (dec d))]
    (+ (* era 146097) doe 719468)))

(defn- ymd->days [ymd]
  (days-from-civil (long (quot ymd 10000))
                   (long (quot (rem ymd 10000) 100))
                   (long (rem ymd 100))))

;; ───────────────────────── helpers ─────────────────────────

(defn- kw->str [x] (if (keyword? x) (name x) (str x)))

(defn- blank-ref? [ref]
  (let [s (str/trim (str ref))]
    (or (str/blank? s) (not (str/starts-with? s encrypted-prefix)))))

(defn- rules
  "Build a violation vector from interleaved tests→maps; nils dropped. Each arg
  is either a map (kept) or nil (dropped), so callers wrap each candidate in a
  `when`. Simpler to balance than cond-> over multi-line maps."
  [& xs]
  (filterv some? (vec xs)))

;; ───────────────────────── invariant checks ─────────────────────────

(defn- consent-violations [st {:keys [member consent-ref]}]
  (let [m (store/member st member)]
    (rules
     (when (or (nil? consent-ref) (str/blank? (kw->str consent-ref)))
       {:rule :no-consent :detail "G3: consentRef 必須"})
     (when (and (some? consent-ref) (or (nil? m) (not (contains? (:consent-refs m) consent-ref))))
       {:rule :consent-not-bound :detail "G3: consentRef が member の active consent に非結合"})
     (when (nil? m)
       {:rule :unknown-member :detail "G4: 申請者本人 未特定"}))))

(defn- identity-violations [st {:keys [member]}]
  ;; G4 — the member is the named 申請者本人. no-impersonation is structural.
  (rules
   (when (nil? (store/member st member))
     {:rule :unknown-member :detail "G4: member 未登録・申請者本人として特定不能"})))

(defn- upl-violations [op proposal]
  ;; G5 — 案内 + 入力補助 + 伴走 ONLY. :draft-for-member / :advise is ALWAYS HARD.
  (let [effect         (:effect proposal)
        allowed        (case op :guide/build #{:guide} :draft/assist #{:input-assist}
                                :submit/transmit #{:submit} #{})
        forbidden-ever #{:draft-for-member :advise}]
    (rules
     (when (contains? forbidden-ever effect)
       {:rule :upl-reserve :detail "G5: 行政書士法/UPL 予約 — chigiri+licensed"})
     (when (and (seq allowed) (not (contains? allowed effect)))
       {:rule :effect-out-of-scope :detail "G5: op の effect が許可集合外"}))))

(defn- pii-violations [request proposal]
  ;; G6 — PII ONLY in com.etzhayyim.encrypted.* DID-bound envelopes.
  (let [ref        (or (:encrypted-pii-ref request) (:encrypted-pii-ref proposal))
        plain-keys [:plaintext-pii :draft-body :plaintext-result :raw-pii]
        plaintext? (reduce (fn [acc k] (or acc (get request k) (get proposal k))) nil plain-keys)]
    (rules
     (when plaintext?
       {:rule :plaintext-pii :detail "G6: 平文 PII は表現不可 — encrypted.* のみ"})
     (when (blank-ref? ref)
       {:rule :unencrypted-pii :detail "G6: 暗号化 PII 参照必須 — com.etzhayyim.encrypted.*"}))))

(defn- fabrication-violations [proc]
  ;; G8 — cite legal-basis + provenance; never invent.
  (let [lb (:legal-basis proc)
        pv (:provenance proc)]
    (rules
     (when (or (nil? lb) (str/blank? lb))
       {:rule :no-legal-basis :detail "G8: 根拠法令 欠落 — fabrication 禁止"})
     (when (or (nil? pv) (str/blank? pv))
       {:rule :no-provenance :detail "G8: provenance 欠落 — fabrication 禁止"}))))

(defn- channel-violations [request]
  ;; G10 — lawful official channel only.
  (let [ch (some-> (:channel request) kw->str str/trim)]
    (rules
     (when (and ch (not (contains? lawful-channels ch)))
       {:rule :unlawful-channel :detail "G10: 公式 channel のみ — online/in-person/postal"}))))

(defn- freshness-stale? [today proc]
  (when (and today (:last-verified proc) (:freshness-window-days proc))
    (> (- (ymd->days today) (ymd->days (:last-verified proc)))
       (:freshness-window-days proc))))

(defn freshness-ok?
  "Public helper: is `proc` within its freshness window as of `today` (yyyymmdd)?
  True when inputs are absent (conservative only when all three are present)."
  ([proc] (freshness-ok? default-today proc))
  ([today proc] (not (freshness-stale? today proc))))

(defn- verification-violations [today proc]
  ;; G14 — verified-procedure-only: refuse unverified-seed OR stale.
  (rules
   (when (not (contains? verified-statuses (:verification-status proc)))
     {:rule :unverified-procedure :detail "G14: verificationStatus が verified でない"})
   (when (freshness-stale? today proc)
     {:rule :stale-procedure :detail "G14: lastVerified が鮮度Window切れ"})))

(defn- submission-mode-violations [request]
  ;; G15 — member-self-submission default; 代行 is the gated exception.
  (let [mode (:mode request)]
    (rules
     (when (and (some? mode) (not (contains? submit-modes mode)))
       {:rule :invalid-submission-mode :detail "G15: mode が有効集合外"}))))

(defn- agent-on-behalf? [request] (= :agent-on-behalf (:mode request)))

(defn check
  "Censors a concierge proposal for a toritsugi op. Returns
  {:ok? :violations :confidence :hard? :escalate? :high-stakes?}.

  Hard violations force HOLD (unoverridable). A 代行 submit is ALWAYS high-stakes
  → human/Council sign-off even when clean. opts: {:today yyyymmdd-int}."
  ([request proposal st] (check request proposal st nil))
  ([request proposal st {:keys [today] :or {today default-today}}]
   (let [proc  (store/procedure st (:procedure request))
         base  (if (nil? proc)
                 [{:rule :unknown-procedure :detail (str "G8/G14: 手続き未登録 — procedure=" (:procedure request))}]
                 (into (fabrication-violations proc) (verification-violations today proc)))
         g3    (consent-violations st request)
         g4    (identity-violations st request)
         g5    (upl-violations (:op request) proposal)
         pii   (pii-violations request proposal)
         chan  (channel-violations request)
         mode  (submission-mode-violations request)
         hard  (case (:op request)
                 :guide/build    (into [] cat [base g3 g4 g5])
                 :draft/assist   (into [] cat [base g3 g4 g5 pii])
                 :submit/transmit (into [] cat [base g3 g4 g5 pii chan mode])
                 (into [] cat [base g3 g4]))
         conf    (:confidence proposal 0.0)
         low?    (< conf confidence-floor)
         stakes? (and (= :submit/transmit (:op request)) (agent-on-behalf? request))
         hard?   (boolean (seq hard))]
     {:ok?          (and (not hard?) (not low?) (not stakes?))
      :violations   hard
      :confidence   conf
      :hard?        hard?
      :escalate?    (and (not hard?) (or low? stakes?))
      :high-stakes? stakes?})))

(defn hold-fact [request verdict]
  {:t         :procedure-hold
   :op        (:op request)
   :procedure (:procedure request)
   :session   (:session request)
   :member    (:member request)
   :disposition :hold
   :basis     (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
