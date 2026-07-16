(ns ake.methods._edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil). ADR-2606052100.
  Clojure port of `methods/_edn.py` (itself ported from noroshi/watatsuna).

  The fidelity invariant this preserves: keywords are kept as their \":ns/name\"
  STRINGS, NOT as Clojure keywords. ingest.py / revision.py key every record on
  string keys (\":actor/handle\", \":edit/op\", …) and the Python `:`-strings stay
  strings (root CLAUDE.md convention) — so the loader must yield the same string
  shape the Python `load_edn` does, byte-for-byte, or the genesis-revision bridge
  would key on the wrong thing.

  Stdlib only (regex tokenizer); file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; ── tokenizer (mirror of the Python _TOK regex) ───────────────────────────
;; Matches: whitespace/commas | ; comment | one of [ ] { } | "string" | bare atom.

(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens
  "Lazy seq of significant tokens (capture group 1; whitespace/comments dropped)."
  [s]
  (let [m (re-seq token-re s)]
    (keep (fn [match]
            (cond
              (vector? match) (second match)   ;; capture group present
              :else nil))
          m)))

(defn- unescape-string
  "Strip the surrounding quotes and unescape \\\" and \\\\ (mirrors the Python atom path)."
  [t]
  (-> (subs t 1 (dec (count t)))
      (str/replace "\\\"" "\"")
      (str/replace "\\\\" "\\")))

(defn- parse-long* [^String t]
  #?(:clj (try (Long/parseLong t) (catch Exception _ nil))
     :cljs (let [n (js/parseInt t 10)] (when (and (not (js/isNaN n)) (re-matches #"[-+]?\d+" t)) n))))

(defn- parse-double* [^String t]
  #?(:clj (try (Double/parseDouble t) (catch Exception _ nil))
     :cljs (let [n (js/parseFloat t)] (when (and (not (js/isNaN n)) (re-matches #"[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?" t)) n))))

(defn- atom* [t]
  (cond
    (str/starts-with? t "\"") (unescape-string t)
    (= t "true")  true
    (= t "false") false
    (= t "nil")   nil
    (str/starts-with? t ":") t           ;; keyword kept as ":ns/name" STRING
    :else (or (parse-long* t) (parse-double* t) t)))

;; ── recursive-descent parser over a mutable token cursor ──────────────────
;; `state` is an atom holding the remaining token seq; matches the Python `next(it)`.

(declare parse-form)

(def ^:private END ::end)

(defn- next-tok! [state]
  (let [ts @state]
    (when (empty? ts)
      (throw (ex-info "ake._edn: unexpected end of input" {})))
    (reset! state (rest ts))
    (first ts)))

(defn- parse-form [state]
  (let [t (next-tok! state)]
    (cond
      (= t "[") (loop [out []]
                  (let [x (parse-form state)]
                    (if (= x END) out (recur (conj out x)))))
      (= t "{") (loop [out {}]
                  (let [k (parse-form state)]
                    (if (= k END)
                      out
                      (let [v (parse-form state)]
                        (recur (assoc out k v))))))
      (or (= t "]") (= t "}")) END
      :else (atom* t))))

(defn parse-edn
  "Parse a full EDN string into nested vectors/maps/atoms (keywords as \":…\" strings)."
  [s]
  (parse-form (atom (tokens s))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file at `path` (string or java.io.File). The Clojure
     equivalent of `_edn.load_edn` — keywords kept as \":ns/name\" strings."
     [path]
     (parse-edn (slurp path))))

;; ── tx-data reconstitution (edn-datomize, Phase 4 fan-out) ────────────────
;; `edn-datomize.bb wrap-map-preserve` rewrites a file's top-level bare map into a
;; single-entity Datomic/Datascript tx-data vector `[{:db/id -1 <ns>/<key> <blob-or-live> ...}]`
;; so the FILE ON DISK is directly queryable tx-data. This actor's own methods/tests read that
;; same file with THIS namespace's string-keyword parser and expect the ORIGINAL bare-map shape
;; (`(get seed \":edit/batch\")`, `(get-in lex [\":defs\" \":main\" \":record\"])`, …) — `reconstitute`
;; bridges the two: it detects the tx-data shape and rebuilds the original un-namespaced,
;; string-keyed map transparently, so every existing call site keeps working unchanged. A file
;; NOT (yet, or ever) transformed — still a bare map, e.g. the shared repo ontology / real
;; actor-profile-seed SSoT — passes through untouched (safe no-op / idempotent).

(defn tx-data?
  "True if a loaded top-level form is edn-datomize tx-data: a single-element vector whose
  one map has a \":db/id\" key (the wrap-map-preserve! output shape)."
  [d]
  (and (vector? d) (= 1 (count d)) (map? (first d)) (contains? (first d) ":db/id")))

(defn- unblob
  "A wrap-map-preserve! non-scalar attribute value is a pr-str'd blob string (standard Clojure
  syntax, real keywords, #:ns{} shorthand disabled at write time — see edn-datomize.bb). Re-
  parsing it through THIS reader (not clojure.edn/read-string) yields the same \":ns/name\"-as-
  string convention every ake.methods.* call site expects. A live (non-blob) scalar value that
  happens to be a plain string is returned unchanged (parse-edn on it fails or yields a
  non-coll, so it falls through)."
  [v]
  (if (string? v)
    (let [parsed (try (parse-edn v) (catch #?(:clj Exception :cljs :default) _ ::fail))]
      (if (and (not= parsed ::fail) (coll? parsed)) parsed v))
    v))

(defn reconstitute
  "Rebuild the original bare, string-keyed map from an edn-datomize wrap-map-preserve! tx-data
  vector. `ns-prefix` (a plain string, e.g. \"lex.editProposal\" or \"data.seed-edit-graph\") MUST
  be the exact namespace edn-datomize was given for THIS file's bare keys at transform time
  (`wrap-map-preserve! <path> <ns-prefix>`) — only a key of the exact shape
  \":<ns-prefix>/<local>\" is stripped back to \":<local>\"; this is an explicit match, never a
  guess, so a key that was ALREADY namespaced before the transform (wrap-map-preserve keeps
  those as-is verbatim, e.g. \":edit/batch\" — a DIFFERENT namespace than any file's ns-prefix)
  simply does not match the prefix and passes through unchanged, correctly. `:db/id` is always
  dropped. If `d` is not tx-data shape (`tx-data?`), `d` is returned unchanged — safe / idempotent
  on an untransformed file (e.g. the shared repo ontology or the real actor-profile-seed SSoT),
  regardless of what `ns-prefix` is passed."
  [d ns-prefix]
  (if (tx-data? d)
    (let [prefix (str ":" ns-prefix "/")]
      (into {}
            (map (fn [[k v]]
                   [(if (and (string? k) (str/starts-with? k prefix))
                      (str ":" (subs k (count prefix)))
                      k)
                    (unblob v)]))
            (dissoc (first d) ":db/id")))
    d))
