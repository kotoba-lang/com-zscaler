(ns saisei.methods.filing-plan
  "saisei 再生 — self-filing debt-relief plan builder (ADR-2607061800).

  Classifies a member's OWN declared jurisdiction against the coded procedure
  registry and discloses ALL registered procedures for that jurisdiction as
  distinct 'tracks' — never picks one on the member's behalf (G2 non-adjudicating:
  choosing between e.g. Chapter 7 vs Chapter 13, or 自己破産 vs 個人再生, is exactly
  the kind of individualized judgment saisei must not make).

  CONSTITUTIONAL (read before any change):
    G2 — non-adjudicating. Eligibility SIGNALS are disclosed verbatim per track;
      saisei never says 'you qualify' or 'file this one'.
    G3 — UPL. make-option THROWS on :representation — in EVERY jurisdiction.
      The member assembles, signs and files THEMSELVES.
    G4 — timeline honesty. Never computes a calendar date; every timeline rule is
      the DISCLOSED statutory text + anchor.
    G5 — mandatory-precondition honesty. A jurisdiction's required pre-filing step
      (:proc/mandatory-preconditions) is surfaced as a blocking step, not an
      optional referral — the plan's self-file option never appears before it.
    G7 — referral-forward. Every plan carries the jurisdiction's free/public
      insolvency-counseling directory, unconditionally.
    G10 — jurisdiction honesty. A situation from an uncovered jurisdiction
      degrades to :unknown-jurisdiction; saisei never guesses foreign insolvency law.

  House style: ':…' strings stay strings; pure fns; I/O at #?(:clj) edges. Portable .cljc."
  (:require [saisei.methods.edn :as edn]))

;; ── default-path loaders (#?(:clj) edge) ──────────────────────────────────
#?(:clj
   (defn load-procs
     ([] (load-procs (clojure.java.io/file (edn/here) "data" "procedure-registry.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":proc/id")) vec))))

#?(:clj
   (defn load-jurisdictions
     ([] (load-jurisdictions (clojure.java.io/file (edn/here) "data" "jurisdictions.edn")))
     ([path]
      (->> (edn/load-edn path)
           (filter #(contains? % ":juris/id"))
           (reduce (fn [m j] (assoc m (get j ":juris/id") j)) {})))))

#?(:clj
   (defn load-situations
     ([] (load-situations (clojure.java.io/file (edn/here) "data" "seed-member-docs.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":sit/id")) vec))))

#?(:clj
   (defn load-legal-directory
     "Real licensed-professional/court directories (LINK ONLY — saisei does not
     perform or supervise legal work; it connects the member to who can)."
     ([] (load-legal-directory (clojure.java.io/file (edn/here) "data" "legal-directory.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":dir/id")) vec))))

(defn make-option
  "The only option constructor. Representation is unrepresentable (G3) — globally."
  [opt]
  (when (= (get opt ":opt/kind") ":representation")
    (throw (ex-info (str "G3/UPL: representation is unrepresentable in saisei "
                         "(弁護士法72条 / state UPL / RDG / LSA 2007)")
                    {:opt opt})))
  {"id" (get opt ":opt/id") "kind" (get opt ":opt/kind") "label" (get opt ":opt/label")
   "protective" (boolean (get opt ":opt/protective")) "mode" "dry-run" "filed_by" "member"})

(defn classify
  "[tracks status] — status is \":covered\" or \":unknown-jurisdiction\" (G10)."
  ([situation procs] (classify situation procs (load-jurisdictions)))
  ([situation procs jurisdictions]
   (let [juris (get situation ":sit/jurisdiction" ":jp")]
     (if-not (contains? jurisdictions juris)
       [[] ":unknown-jurisdiction"]
       (let [matched (filterv #(= (get % ":proc/jurisdiction" ":jp") juris) procs)]
         [matched ":covered"])))))

(defn- track-plan
  "One disclosed track (procedure) — eligibility signals, blocking preconditions,
  required docs, fee, timeline, and the self-file option. Never adjudicates (G2).

  :proc/mandatory-preconditions / :proc/fee / :proc/timeline-rules /
  :proc/options are non-scalar (nested map / vector-of-maps) so the
  datomize-transform tx-data shape (manifest/edn-datomize.cljs convention)
  pr-str's them into blob-string attribute values on disk — `edn/unblob`
  reconstitutes the structured shape before this fn iterates/destructures
  them (a no-op on pre-transform data, since it only touches string values)."
  [proc]
  (let [preconditions (vec (for [p (edn/unblob (get proc ":proc/mandatory-preconditions" []))]
                              {"label" (get p ":pre/label") "detail" (get p ":pre/detail")
                               "anchor" (get p ":pre/anchor") "blocking" true}))
        blocked? (boolean (seq preconditions))]
    {"proc" (get proc ":proc/id")
     "label" (get proc ":proc/label")
     "forum" (get proc ":proc/forum")
     ;; LINK ONLY to the official government/court form portal (verified live,
     ;; not guessed — same provenance discipline as :tl/source-url). saisei
     ;; never drafts, pre-fills, or characterizes the form itself (G3/N2) — the
     ;; member downloads the official form and completes it themselves.
     "official_forms_url" (get proc ":proc/official-forms-url")
     "eligibility_signals" (vec (get proc ":proc/eligibility-signals" []))
     "mandatory_preconditions" preconditions
     "required_docs" (vec (get proc ":proc/required-docs" []))
     "fee" (edn/unblob (get proc ":proc/fee"))
     "fee_waiver" (get proc ":proc/fee-waiver")
     "timelines" (vec (for [tl (edn/unblob (get proc ":proc/timeline-rules" []))]
                         {"label" (get tl ":tl/label") "rule" (get tl ":tl/rule")
                          "anchor" (get tl ":tl/anchor") "source_url" (get tl ":tl/source-url")}))
     ;; G5: options are visible for member reading regardless, but every option is
     ;; :self-submit already (G3 — representation is unrepresentable); the blocking
     ;; flag on preconditions is what tells the member self-file isn't reachable yet.
     "options" (vec (map make-option (edn/unblob (get proc ":proc/options" []))))
     "blocked_on_precondition" blocked?
     "refer_when" (vec (get proc ":proc/refer-when" []))}))

(defn- directory-entry [d]
  {"id" (get d ":dir/id") "kind" (get d ":dir/kind") "label" (get d ":dir/label")
   "url" (get d ":dir/url") "note" (get d ":dir/note")})

(defn build-plan
  ([situation procs]
   (build-plan situation procs (load-jurisdictions) (load-legal-directory)))
  ([situation procs jurisdictions]
   (build-plan situation procs jurisdictions (load-legal-directory)))
  ([situation procs jurisdictions legal-directory]
   (let [juris-id (get situation ":sit/jurisdiction" ":jp")
         juris (get jurisdictions juris-id {})
         [tracks status] (classify situation procs jurisdictions)
         ;; real licensed-professional/court directories for this jurisdiction —
         ;; LINK ONLY (saisei performs/supervises no legal work itself); present
         ;; regardless of :covered/:unknown-jurisdiction, since a bar/court
         ;; registry is useful even when saisei's own procedure registry isn't
         ;; there yet (naturally empty if legal-directory.edn has no entries for
         ;; the declared jurisdiction id either).
         directory (vec (map directory-entry
                              (filterv #(= (get % ":dir/jurisdiction") juris-id) legal-directory)))
         base {"situation" (get situation ":sit/id")
               "situation_label" (get situation ":sit/label" (get situation ":sit/id"))
               "jurisdiction" juris-id
               "status" status
               "forum" (get juris ":juris/forum")
               "upl_anchor" (get juris ":juris/upl-anchor")
               "tracks" []
               ;; G7: referral directory is ALWAYS present, unconditional on status
               "referrals" (vec (get juris ":juris/referrals" ["local free/public legal aid directory"]))
               "legal_directory" directory
               "mode" "dry-run"}]
     (if (= status ":unknown-jurisdiction")
       (assoc base
              "tracks" []
              "referrals" ["local free/public legal aid directory — jurisdiction not yet covered by saisei"])
       (assoc base "tracks" (vec (map track-plan tracks)))))))

(defn plans
  [situations procs]
  (let [jurisdictions (load-jurisdictions)]
    (vec (for [s situations] (build-plan s procs jurisdictions)))))

(defn report
  [ps]
  (let [L (transient ["# saisei 再生 — self-filing debt-relief plans (dry-run; member self-files — G3 UPL, all jurisdictions)" ""])]
    (doseq [p ps]
      (conj! L (str "## " (get p "situation_label") " [" (get p "jurisdiction") "] — " (get p "status")))
      (when (= (get p "status") ":unknown-jurisdiction")
        (conj! L "- 管轄未収載 (coverage_report.cljc 参照) — 現地法を推測しない"))
      (doseq [t (get p "tracks")]
        (conj! L (str "### " (get t "label") " (" (get t "proc") ")"))
        (conj! L (str "- forum: " (get t "forum")))
        (doseq [sig (get t "eligibility_signals")]
          (conj! L (str "- signal: " sig)))
        (doseq [pre (get t "mandatory_preconditions")]
          (conj! L (str "- ⛔ BLOCKING precondition [" (get pre "label") "]: " (get pre "detail")
                        " (" (get pre "anchor") ")")))
        (doseq [tl (get t "timelines")]
          (conj! L (str "- timeline [" (get tl "label") "]: " (get tl "rule") " (" (get tl "anchor") ")")))
        (doseq [o (get t "options")]
          (conj! L (str "- option: " (get o "label")))))
      (conj! L (str "- referrals: " (clojure.string/join ", " (get p "referrals"))))
      (conj! L ""))
    (str (clojure.string/join "\n" (persistent! L)) "\n")))
