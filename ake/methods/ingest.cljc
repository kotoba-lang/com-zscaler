(ns ake.methods.ingest
  "ingest.cljc — 朱 (ake) genesis-revision bridge over the REAL actor-profile SSoT.
  ADR-2606052100. Clojure port of `methods/ingest.py`.

  The membrane's 'view history' must start from *reality*, not an empty log: every
  actor profile that exists today has a current committed value, and that value is the
  **genesis revision** on top of which member edits later append. This bridge reads the
  REAL repo SSoT (`00-contracts/schemas/actor-profile-seed.kotoba.edn`) and seeds the
  append-only revision history with one genesis `:revision/*` per actor-profile field.

  Honest scope: a READ over a committed repo file + an offline genesis-history build. It
  does NOT ingest into the canonical kotoba Datom log (G8). Genesis revisions are
  `:authoritative` because they mirror the committed SSoT; they are `:assert`s by the
  genesis operator (not member edits, no vote).

  Pure fns; `as-of` is passed in (deterministic, no wall clock). I/O (file read, report
  write) at the #?(:clj) edge."
  (:require [ake.methods._edn :as edn]
            [ake.methods.revision :as rev]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(def genesis-by "did:web:etzhayyim.com:operator:genesis")

;; the profile fields the membrane treats as editable (actor-profile target-kind)
(def genesis-fields [":actor/description" ":actor/display-name-ja" ":actor/display-name-en"])

(def genesis-as-of-base 1000000)

;; ── repo-relative SSoT path (Python `_REPO` = file.parents[3]) ────────────

#?(:clj
   (defn- repo-root []
     ;; methods file lives at 20-actors/ake/methods/ ; parents[3] = repo root.
     ;; bb runs from the worktree root (bb.edn cwd) → resolve from there.
     (let [cwd (io/file (System/getProperty "user.dir"))
           candidate (io/file cwd "00-contracts" "schemas" "actor-profile-seed.kotoba.edn")]
       (if (.exists candidate)
         cwd
         ;; fall back to walking up from the methods dir if cwd isn't the repo root
         (loop [d cwd]
           (cond
             (nil? d) cwd
             (.exists (io/file d "00-contracts" "schemas" "actor-profile-seed.kotoba.edn")) d
             :else (recur (.getParentFile d))))))))

#?(:clj
   (defn profile-seed-path []
     (io/file (repo-root) "00-contracts" "schemas" "actor-profile-seed.kotoba.edn")))

;; ── genesis edit (mirror `_genesis_edit`) ─────────────────────────────────

(defn genesis-edit
  "The synthetic genesis :edit/* map for one (handle, attr, value)."
  [handle attr value]
  {":edit/id" (str "genesis:" handle ":" (last (str/split attr #"/")))
   ":edit/target-kind" ":actor-profile"
   ":edit/target-entity" handle
   ":edit/target-attr" attr
   ":edit/op" ":assert"
   ":edit/proposed-value" value
   ":edit/author" genesis-by
   ":edit/author-kind" ":member"
   ":edit/provenance" "00-contracts/schemas/actor-profile-seed.kotoba.edn"
   ":edit/sourcing" ":authoritative"})   ;; mirrors the committed SSoT

;; ── core build (mirror `genesis_revisions`) ───────────────────────────────

(defn genesis-revisions-from-seed
  "Pure core: build the genesis revision history from an already-loaded seed map.
  Returns {:history [...] :actors [...] :records n}.

  Mirrors the Python loop: only records that are maps carrying :actor/handle count;
  for each, append one revision per non-blank GENESIS_FIELD (description, ja, en),
  incrementing `as-of` by 1 per emitted revision (deterministic ordering)."
  ([seed] (genesis-revisions-from-seed seed genesis-as-of-base))
  ([seed as-of-base]
   (let [records (filter (fn [r] (and (map? r) (get r ":actor/handle")))
                         (get seed ":seed" []))]
     (loop [recs records
            history []
            as-of as-of-base
            actors []]
       (if (empty? recs)
         {:history history :actors actors :records (count records)}
         (let [rec (first recs)
               handle (get rec ":actor/handle")
               [history* as-of*]
               (reduce (fn [[hist a] attr]
                         (let [val (get rec attr)]
                           (if (or (nil? val) (and (string? val) (str/blank? val)))
                             [hist a]
                             (let [a' (inc a)]
                               [(rev/append-revision hist (genesis-edit handle attr (str val)) a') a']))))
                       [history as-of]
                       genesis-fields)]
           (recur (rest recs) history* as-of* (conj actors handle))))))))

#?(:clj
   (defn genesis-revisions
     "Build the genesis revision history from the REAL actor-profile SSoT (or a given path).
     (genesis-revisions) | (genesis-revisions path) | (genesis-revisions path as-of-base)."
     ([] (genesis-revisions (profile-seed-path) genesis-as-of-base))
     ([profile-seed-path] (genesis-revisions profile-seed-path genesis-as-of-base))
     ([profile-seed-path as-of-base]
      (genesis-revisions-from-seed
       (edn/reconstitute (edn/load-edn profile-seed-path) "data.sample-profile-seed")
       as-of-base))))

;; ── report (mirror `_report`) ─────────────────────────────────────────────

(defn report
  "Render the genesis-revision summary markdown for a `genesis-revisions` result."
  [res]
  (let [{:keys [history actors records]} res
        head ["# 朱 (ake) — genesis revision history from the REAL actor-profile SSoT\n"
              (str "Bootstrapped " (count history) " genesis revisions across " records
                   " actor profiles (read from `00-contracts/schemas/actor-profile-seed.kotoba.edn`).\n")
              (str "Member edits via the membrane append ON TOP of these (the log only grows). NO ingest "
                   "into the canonical kotoba Datom log (G8).\n")
              "| actor | description revisions | current sourcing |"
              "|---|---|---|"]
        rows (map (fn [h]
                    (let [n (count (rev/history-of history h "description"))
                          cur (rev/current history h "description")
                          src (get cur ":revision/sourcing" "—")]
                      (str "| " h " | " n " | " src " |")))
                  actors)]
    (str (str/join "\n" (concat head rows)) "\n")))

#?(:clj
   (defn -main [& _]
     (let [res (genesis-revisions)
           out-dir (io/file (System/getProperty "user.dir")
                            "20-actors" "ake" "methods" "out")]
       (.mkdirs out-dir)
       (spit (io/file out-dir "genesis-revisions.md") (report res))
       (print (report res)))))
