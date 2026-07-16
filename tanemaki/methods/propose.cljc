(ns tanemaki.methods.propose
  "tanemaki 種蒔き — public DD scorecard renderer + ADVISORY grant-proposal builder.
  1:1 Clojure port of `methods/propose.py` (ADR-2606122001).

  Two artefacts, both PUBLIC and both structurally decision-free:

    render-scorecard  — the 参考意見 card shown in the voting UI: screens, rubric, evidence
      sources, fit, route. Content-addressed (CIDv1+SHA-256) so anyone can verify the bytes the
      voters saw are the bytes tanemaki published (G4).

    build-proposal — an UNSENT `com.etzhayyim.tanemaki.grantProposal` record feeding the
      GrantGovernor propose() lane (ADR-2605192145). Structurally advisory: advisory=true,
      bindsFund=false, decidedBy=1-sbt-1-vote. It REFUSES (throws) for any org whose route is not
      :propose (G1/G5). Instruments outside the G2 allowlist raise. Free-text justifications are
      scanned for investment-return language and rejected (G2).

  Actually SUBMITTING a proposal on-chain is a G7-gated member/operator step — tanemaki holds no
  key and no vote weight (no-server-key, ADR-2605231525).

  Pure fns; reuses tanemaki.methods.analyze (recommend-route / criteria) + tanemaki.methods.cid
  (cidv1-raw / sha256-hex). House style: Python ':…' keyword strings stay strings; assert →
  throw ex-info (assertRaises → thrown?). The __main__/main CLI demo is intentionally omitted."
  (:require [clojure.string :as str]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.cid :as cid]))

;; G2 — the disbursement allowlist (mirrors :instrument/allowlist in the schema + fuchi G1).
;; The investment-instrument vocabulary is NOT a low-scoring option; it is unrepresentable.
(def ALLOWED-INSTRUMENTS [":grant" ":milestone-escrow" ":in-kind"])
(def FORBIDDEN-INSTRUMENTS [":equity" ":debt" ":convertible" ":revenue-share"
                            ":profit-claim" ":carry" ":dividend" ":exit"])

;; G2 — investment-return language never enters a proposal justification.
;; Port of _INVESTMENT_RE (re.IGNORECASE); (?i) flags case-insensitive matching.
(def ^:private investment-re
  #"(?i)equity|出資|持分|配当|株式|新株|転換社債|リターン|投資回収|ROI|内部収益|IRR|revenue\s*share|profit\s*shar|carried\s*interest|carry|exit|キャピタルゲイン|利回り")

(defn assert-instrument
  "G2 — throws on any investment-shaped instrument (the fund GIVES, never INVESTS)."
  [instrument]
  (let [k (if (str/starts-with? instrument ":") instrument (str ":" instrument))]
    (when (some #{k} FORBIDDEN-INSTRUMENTS)
      (throw (ex-info
              (str "G2 VIOLATION: " k " is an investment instrument — unrepresentable in the Public "
                   "Fund (grant / milestone-escrow / in-kind only; ADR-2606052300 G1 pattern)")
              {:instrument k})))
    (when-not (some #{k} ALLOWED-INSTRUMENTS)
      (throw (ex-info (str "unknown instrument " k "; allowed: " (vec ALLOWED-INSTRUMENTS))
                      {:instrument k})))
    k))

(defn assert-no-investment-language
  "G2 — rejects investment-return language injected into a free-text justification."
  [text]
  (let [m (re-find investment-re (or text ""))
        hit (if (vector? m) (first m) m)] ;; re-find returns String when no groups
    (when hit
      (throw (ex-info
              (str "G2 VIOLATION: investment-return language (" (pr-str hit) ") in a grant "
                   "justification — the Public Fund gives, it never invests")
              {:match hit})))
    text))

(defn- ->float [v] (if (or (nil? v) (false? v)) 0.0 (double v)))
(defn- fmt2 [v] (format "%.2f" (double v)))
(defn- fmt3 [v] (format "%.3f" (double v)))
(defn- fmt-pct0 [x] (str (long (Math/rint (* (double x) 100.0))) "%"))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn render-scorecard
  "The PUBLIC 参考意見 card (markdown) for one org — advisory, vote-decided.
  1:1 with render_scorecard's f-strings (line-by-line `L.append` joined by \\n)."
  [org-id nodes edges]
  (let [rec  (analyze/recommend-route org-id nodes edges)
        crit (analyze/criteria nodes)
        org  (get nodes org-id)
        L    (transient [])]
    (conj! L (str "# DD scorecard — " (get org ":fs/label" org-id) "\n"))
    (conj! L (str "> **参考意見 (advisory)** — this card informs the 1 SBT = 1 vote decision "
                  "(GrantGovernor, ADR-2605192145); it decides nothing. Findings are DISCLOSED "
                  "public facts with named sources, never verdicts (N3)."
                  (if (get rec "synthetic") " **This org is FICTIONAL (G6 seed).**" "") "\n"))
    (conj! L "## Hard screens (適格性 — charter anchors disclosed)\n")
    (conj! L "| screen | basis | finding |")
    (conj! L "|---|---|---|")
    ;; for s, f in rec["screen_findings"].items()  (sorted screen-id order, ::order metadata)
    (let [sf  (get rec "screen_findings")
          ord (or (:tanemaki.methods.analyze/order (meta sf)) (keys sf))]
      (doseq [s ord]
        (let [f  (get sf s)
              sn (get nodes s {})]
          (conj! L (str "| " (get sn ":screen/code" s) " " (get sn ":fs/label" "") " | "
                        (get sn ":screen/basis" "—") " | "
                        (lstrip-colon (or f ":unevaluated")) " |")))))
    (conj! L "\n## Weighted rubric (公開 weight × evidence)\n")
    (conj! L "| criterion | weight | evidence | sources |")
    (conj! L "|---|---:|---:|---|")
    ;; sorted(crit, key=lambda c: -float(crit[c][":criterion/weight"])) — crit iterated in
    ;; first-touch order, stable sort on -weight
    (let [crit-keys (or (:tanemaki.methods.analyze/order (meta crit)) (keys crit))
          per       (get rec "per_criterion")
          ev        (get rec "evidence")]
      (doseq [c (sort-by (fn [c] (- (->float (get-in crit [c ":criterion/weight"])))) crit-keys)]
        (let [w    (->float (get-in crit [c ":criterion/weight"]))
              evc  (min 1.0 (->float (get per c 0.0)))
              srcs (let [ss (sort (distinct (map str (get ev c []))))]
                     (if (seq ss) (str/join ", " ss) "—"))]
          (conj! L (str "| " (get-in nodes [c ":fs/label"] c) " | " (fmt2 w) " | "
                        (fmt2 evc) " | " srcs " |")))))
    (conj! L (str "\n**DD fit**: " (fmt3 (get rec "dd_fit")) " · **evidence coverage**: "
                  (fmt-pct0 (get rec "evidence_coverage")) " · **route**: `" (get rec "route") "`\n"))
    (conj! L (str "---\n_tanemaki 種蒔き · ADR-2606122001 · advisory-only · decided by "
                  "1 SBT = 1 vote._\n"))
    (str/join "\n" (persistent! L))))

(defn build-proposal
  "An UNSENT advisory grant-proposal record for the SBT vote (G1/G2/G5/G7 enforced).
  Optional kwargs map: {:amount-usdc-micros :instrument :justification :proposer-did}.
  Returns an ORDERED string-keyed map (insertion order mirrors the Python dict literal)."
  ([org-id nodes edges] (build-proposal org-id nodes edges {}))
  ([org-id nodes edges {:keys [amount-usdc-micros instrument justification proposer-did]
                        :or   {amount-usdc-micros 0 instrument ":grant"
                               justification "" proposer-did ""}}]
   (let [rec (analyze/recommend-route org-id nodes edges)]
     (when (not= ":propose" (get rec "route"))
       (throw (ex-info
               (str "G1/G5 REFUSAL: org " org-id " routes to " (get rec "route")
                    " — tanemaki cannot draft a proposal for a screen-conflicting or "
                    "under-evidenced org"
                    (if (seq (get rec "conflicts"))
                      (str " (conflicts: " (str/join ", " (get rec "conflicts")) ")") "")
                    (if (seq (get rec "undetermined"))
                      (str " (undetermined: " (str/join ", " (get rec "undetermined")) ")") ""))
               {:org org-id :route (get rec "route")})))
     (let [inst (assert-instrument instrument)
           _    (assert-no-investment-language justification)
           milestones (if (= inst ":milestone-escrow")
                        (let [ms (vec (for [e edges
                                            :when (and (= ":watched-by" (get e ":en/kind"))
                                                       (= org-id (get e ":en/from")))]
                                        (get e ":en/to")))]
                          (when (empty? ms)
                            (throw (ex-info
                                    (str "milestone-escrow needs :watched-by milestones for " org-id
                                         " (attestation-gated tranches, ADR-2605192145 §4)")
                                    {:org org-id})))
                          ms)
                        [])
           card (cid/utf8-bytes (render-scorecard org-id nodes edges))]
       (array-map
        "$type"             "com.etzhayyim.tanemaki.grantProposal"
        "orgId"             org-id
        "orgSynthetic"      (get rec "synthetic")
        "instrument"        inst
        "amountUsdcMicros"  (long amount-usdc-micros)
        "milestones"        milestones
        "justification"     justification
        "scorecardCid"      (cid/cidv1-raw card)
        "scorecardSha256"   (cid/sha256-hex card)
        "ddFit"             (get rec "dd_fit")
        "evidenceCoverage"  (get rec "evidence_coverage")
        "advisory"          true            ;; structurally advisory — tanemaki decides nothing (G1)
        "bindsFund"         false           ;; only the vote + timelock moves funds
        "decidedBy"         "1-sbt-1-vote"  ;; GrantGovernor, ADR-2605192145
        "status"            "drafted-unsent" ;; on-chain submission is a G7-gated member/operator step
        "proposerDid"       proposer-did)))))
