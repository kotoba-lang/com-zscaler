(ns ake.methods.revision
  "revision.cljc — 朱 (ake) append-only revision history. ADR-2606052100.
  Clojure port of `methods/revision.py`.

  The 'view history' tab, as data. Every accepted edit APPENDS one revision datom;
  nothing is ever overwritten or deleted (G5, 非終末論). The latest `as-of` for an
  (entity, attr) is the current value; the full ordered stream is the history. A
  `:promote-sourcing` op appends a new authoritative revision and LEAVES the prior
  representative one in place (both remain, at their own `as-of`).

  Every mutating helper RETURNS A NEW VECTOR — it never mutates its input — so a
  caller can never shrink the log. `current` / `as-of` / `history-of` are the read
  side.

  Convention (root CLAUDE.md): map keys are \":…\" STRINGS (the Python `:`-strings
  stay strings), datoms/revisions are plain maps; closed-vocab violations → ex-info.
  Pure fns; no I/O.

  `_kw` + `verifiable-provenance?` are inlined from triage.py (the Python module
  imports them from triage); they carry the same semantics."
  (:require [clojure.string :as str]))

;; ── helpers (mirror triage.py `_kw` + `_verifiable_provenance`) ───────────

(defn kw
  "Normalize an edn keyword/string to a bare lowercase token (':actor/status' → 'status')."
  [v]
  (-> (str (or v ""))
      (str/replace #"^:+" "")
      (str/split #"/")
      last
      str/lower-case))

(defn verifiable-provenance?
  "True if `p` looks like a verifiable URL/CID (http(s)/ipfs/cid:/bafy/ '://')."
  [p]
  (let [p (-> (str (or p "")) str/trim str/lower-case)]
    (boolean
     (or (some #(str/starts-with? p %) ["http://" "https://" "ipfs://" "cid:" "bafy"])
         (str/includes? p "://")))))

;; ── revision record builder (mirror `_rev`) ──────────────────────────────

(defn- rev
  "Build one revision map. `sourcing`/`op` are normalized through `kw` then re-prefixed
  with ':' (matching the Python `\":\" + _kw(...)`)."
  [{:keys [entity attr value sourcing as-of by op edit-id]}]
  {":revision/entity"   entity
   ":revision/attr"     attr
   ":revision/value"    value
   ":revision/sourcing" (if (and sourcing (not (str/blank? (str sourcing))))
                          (str ":" (kw sourcing))
                          ":representative")
   ":revision/as-of"    (long as-of)
   ":revision/by"       by
   ":revision/op"       (str ":" (kw op))
   ":revision/edit"     edit-id})

;; ── append (the G5 primitive) ────────────────────────────────────────────

(defn append-revision
  "Append a revision for an accepted edit. Returns a NEW vector (G5 — never mutates/shrinks)."
  [history edit as-of]
  (let [r (rev {:entity   (get edit ":edit/target-entity" "?")
                :attr     (kw (get edit ":edit/target-attr" ""))
                :value    (str (get edit ":edit/proposed-value" ""))
                :sourcing (get edit ":edit/sourcing" ":representative")
                :as-of    as-of
                :by       (get edit ":edit/author" "?")
                :op       (get edit ":edit/op" ":assert")
                :edit-id  (get edit ":edit/id" "?")})]
    (conj (vec history) r)))

;; ── read side ─────────────────────────────────────────────────────────────

(defn history-of
  "Full ordered revision history for (entity, attr) — the 'view history' tab."
  [history entity attr]
  (let [attr (kw attr)]
    (->> history
         (filter (fn [r]
                   (and (= (get r ":revision/entity") entity)
                        (= (kw (get r ":revision/attr" "")) attr))))
         (sort-by (fn [r] (long (get r ":revision/as-of" 0))))
         vec)))

(defn as-of
  "The value of (entity, attr) as it stood at instant `ts` (time-travel read)."
  [history entity attr ts]
  (let [rows (filter (fn [r] (<= (long (get r ":revision/as-of" 0)) ts))
                     (history-of history entity attr))]
    (last rows)))

(defn current
  "The latest revision for (entity, attr), or nil if none."
  [history entity attr]
  (last (history-of history entity attr)))

;; ── G4 promotion + edit-war rollback (faithful port; unused by ingest tests) ──

(defn promote-sourcing
  "Promote the current value of (entity, attr) :representative → :authoritative (G4).
  Appends a NEW authoritative revision carrying the current value; the prior
  representative revision is left untouched. Raises if provenance is not verifiable,
  or if there is no current value to promote."
  [history entity attr provenance as-of by edit-id]
  (when-not (verifiable-provenance? provenance)
    (throw (ex-info "G4: promotion to :authoritative requires verifiable provenance (URL/CID)"
                    {:provenance provenance})))
  (let [cur (current history entity (kw attr))]
    (when (nil? cur)
      (throw (ex-info (str "nothing to promote: no current revision for " entity ":" (kw attr))
                      {:entity entity :attr (kw attr)})))
    (conj (vec history)
          (rev {:entity entity :attr (kw attr)
                :value (get cur ":revision/value" "")
                :sourcing ":authoritative"
                :as-of as-of :by by :op ":promote-sourcing" :edit-id edit-id}))))

(defn revert
  "Roll back the CURRENT revision of (entity, attr) to its predecessor (edit-war
  resolution). Append-only: appends a NEW revision (op :retract) restoring the
  predecessor's value; nothing is deleted (danjo-observable). Reverting the first
  revision restores the empty pre-existence state. Raises if nothing to revert."
  [history entity attr by edit-id as-of]
  (let [hist (history-of history entity (kw attr))]
    (when (empty? hist)
      (throw (ex-info (str "nothing to revert: no revision for " entity ":" (kw attr))
                      {:entity entity :attr (kw attr)})))
    (let [prior (when (>= (count hist) 2) (nth hist (- (count hist) 2)))
          restored-value (if prior (get prior ":revision/value" "") "")
          restored-sourcing (if prior (get prior ":revision/sourcing" ":representative") ":representative")]
      (conj (vec history)
            (rev {:entity entity :attr (kw attr)
                  :value restored-value :sourcing restored-sourcing
                  :as-of as-of :by by :op ":retract" :edit-id edit-id})))))
