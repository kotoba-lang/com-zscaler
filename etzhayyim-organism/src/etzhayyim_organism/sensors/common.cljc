#!/usr/bin/env bb
;; etzhayyim-organism sensors — shared helpers (clj-native port of common.py).
;; Faithful 1:1 port — no IO beyond java.io.File / java.nio.file.
;;
;; Classpath: 20-actors/etzhayyim-organism/src
;; Load:  bb --classpath 20-actors/etzhayyim-organism/src
;;        -e "(require 'etzhayyim-organism.sensors.common)"
(ns etzhayyim-organism.sensors.common
  "Shared types and helpers for organism sensors.
   Port of src/etzhayyim_organism/sensors/common.py")

;; ---------------------------------------------------------------------------
;; AxisReading record (mirrors the Python dataclass)
;; ---------------------------------------------------------------------------

(defrecord AxisReading
  [axis          ; String  — axis name
   score         ; Long    — 0..10
   evidence      ; Vec<String>
   next-action   ; String
   leverage])    ; Long    — 1..3

(defn axis-reading
  "Construct an AxisReading with defaults.
   Equivalent to the Python @dataclass AxisReading."
  [{:keys [axis score evidence next-action leverage]
    :or   {evidence [] next-action "" leverage 1}}]
  (->AxisReading axis score evidence next-action leverage))

;; ---------------------------------------------------------------------------
;; File-system helpers (1:1 with count_glob / has / read_text in common.py)
;; ---------------------------------------------------------------------------

(defn- glob-walk-root
  "Return [walk-root effective-pattern] for an optimised walk.
   Strips the longest literal prefix of `pattern` (up to the first wildcard
   component) and starts the walk from that sub-directory.
   e.g. \"50-infra/**/Constitution.sol\" → walk from \"50-infra/\",
        effective pattern stays \"50-infra/**/Constitution.sol\" (repo-relative)."
  [repo pattern]
  (let [sep     (java.io.File/separator)
        ;; Split on / or \\ to get components
        parts   (clojure.string/split pattern #"[/\\]")
        ;; Take the leading literal (no wildcard) components
        literal (take-while #(not (clojure.string/includes? % "*")) parts)
        prefix  (clojure.string/join sep literal)
        sub     (java.io.File. (str repo) prefix)
        root    (if (and (seq literal) (.isDirectory sub)) sub (java.io.File. (str repo)))]
    root))

(defn count-glob
  "Count paths matching a **glob** relative to `repo` (string or Path-like).
   Equivalent to `sum(1 for _ in repo.glob(pattern))` in Python.
   Pattern: standard Java glob (e.g. \"_observations/*-cycle-*.md\",
   \"90-docs/adr/*.md\", \"**/NOTICE\").

   Optimisations:
   - Strips the literal prefix to walk a sub-tree, not the whole repo root.
   - For non-** patterns caps walk depth to the pattern component count."
  [repo pattern]
  (let [base (java.io.File. (str repo))]
    (try
      (let [repo-path (.toPath base)
            walk-root (.toPath (glob-walk-root repo pattern))
            fs        (java.nio.file.FileSystems/getDefault)
            ;; Matcher always evaluates the repo-relative path
            matcher   (.getPathMatcher fs (str "glob:" pattern))
            deep?     (clojure.string/includes? pattern "**")
            parts     (count (clojure.string/split pattern #"[/\\]"))
            depth     (if deep? Integer/MAX_VALUE parts)
            walk-opts (into-array java.nio.file.FileVisitOption [])]
        (->> (java.nio.file.Files/walk walk-root depth walk-opts)
             (.iterator)
             iterator-seq
             ;; relativize against the repo root (not the walk root) for matching
             (map #(.relativize repo-path %))
             (remove #(= (.toString %) ""))
             (filter #(.matches matcher %))
             count))
      (catch Exception _ 0))))

(defn has?
  "True if `repo/rel` exists (file or directory).
   Equivalent to `(repo / rel).exists()` in Python."
  [repo rel]
  (.exists (java.io.File. (str repo) (str rel))))

(defn read-text
  "Return the UTF-8 text of `repo/rel`, or \"\" if the file is absent.
   Equivalent to `p.read_text(encoding='utf-8') if p.exists() else \"\"`."
  [repo rel]
  (let [f (java.io.File. (str repo) (str rel))]
    (if (.exists f)
      (slurp f)
      "")))
