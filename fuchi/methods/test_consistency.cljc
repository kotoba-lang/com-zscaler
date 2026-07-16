(ns fuchi.methods.test-consistency
  "test_consistency.py — SSoT drift-lock tests for 扶持 (fuchi): manifest ↔ files ↔ ontology
  ↔ seed. 1:1 Clojure port of methods/test_consistency.py (stdlib asserts → clojure.test).

  EDN fixtures (ontology schema / lexicons / seed) load via fuchi.methods.edn (keywords kept
  as \":…\" STRINGS, byte-for-byte the Python `load_edn` shape). The manifest is JSON
  (manifest.jsonld) — read with a self-contained minimal JSON reader (no cheshire/data.json),
  string-keyed exactly like Python `json.loads`. Fixtures are resolved *file*-relative behind
  #?(:clj …) (the test_pipeline.cljc / test_lexicons.cljc pattern):
    …/fuchi/methods/test_consistency.cljc → up 2 = fuchi (_ACTOR); up 2 more = repo root (_ROOT).

  The Python `_run` demo printer is omitted (clojure.test provides the runner)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [fuchi.methods.edn :as edn]))

;; ── minimal JSON reader (subset sufficient for manifest.jsonld) ───────────────
;; maps string-keyed, integers → long, literals → true/false/nil — Python json.loads shapes.
(declare json-value)

(defn- skip-ws [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))

(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\)
        (let [e (nth s (inc i))]
          (case e
            \" (do (.append sb \") (recur (+ i 2) sb))
            \\ (do (.append sb \\) (recur (+ i 2) sb))
            \/ (do (.append sb \/) (recur (+ i 2) sb))
            \b (do (.append sb \backspace) (recur (+ i 2) sb))
            \f (do (.append sb \formfeed) (recur (+ i 2) sb))
            \n (do (.append sb \newline) (recur (+ i 2) sb))
            \r (do (.append sb \return) (recur (+ i 2) sb))
            \t (do (.append sb \tab) (recur (+ i 2) sb))
            \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                 (.append sb (char cp)) (recur (+ i 6) sb))
            (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))

(defn- json-number [^String s i]
  (let [end (loop [j i]
              (if (and (< j (count s))
                       (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))

(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \])
      [out (inc i)]
      (let [[v i] (json-value s i)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) (conj out v))
          [(conj out v) (inc i)])))))

(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \})
      [out (inc i)]
      (let [[k i] (json-string s i)
            i (skip-ws s i)
            [v i] (json-value s (skip-ws s (inc i)))
            out (assoc out k v)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) out)
          [out (inc i)])))))

(defn- json-value [^String s i]
  (let [i (skip-ws s i), c (nth s i)]
    (cond
      (= c \{) (json-object s i)
      (= c \[) (json-array s i)
      (= c \") (json-string s i)
      (= c \t) [true (+ i 4)]
      (= c \f) [false (+ i 5)]
      (= c \n) [nil (+ i 4)]
      :else (json-number s i))))

(defn- parse-json [text] (first (json-value text 0)))

;; ── fixture locators (*file*-relative; repo root + actor root) ────────────────
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private root-dir (-> actor-dir .getParentFile .getParentFile)))
#?(:clj (def ^:private schema-file
          (io/file root-dir "00-contracts" "schemas" "maintainer-sustenance-ontology.kotoba.edn")))
#?(:clj (def ^:private manifest-file (io/file actor-dir "manifest.edn")))
#?(:clj (def ^:private seed-file (io/file actor-dir "data" "seed-sustenance-graph.kotoba.edn")))

#?(:clj (defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp manifest-file)))))

;; ── tests ─────────────────────────────────────────────────────────────────
(deftest test-manifest-cells-match-cell-dirs
  #?(:clj
     (let [declared (set (map #(get % "name") (get (manifest) "cells")))
           dirs (set (->> (.listFiles (io/file actor-dir "cells"))
                          (filter #(.isDirectory %))
                          (map #(.getName %))
                          (remove #(str/starts-with? % "__"))))]
       (is (= declared dirs) (str "manifest " declared " != dirs " dirs)))))

(deftest test-manifest-lexicons-match-lex-files
  #?(:clj
     (let [declared (set (map #(get % "id") (get (manifest) "lexiconNamespaces")))
           files (set (->> (.listFiles (io/file actor-dir "lex"))
                           (filter #(str/ends-with? (.getName %) ".edn"))
                           (map #(get (edn/load-edn %) ":id"))))]
       (is (= declared files) (str "manifest " declared " != files " files)))))

(deftest test-manifest-adr-matches-ontology
  #?(:clj
     (let [onto (edn/load-edn schema-file)]
       (is (= (get onto ":ontology/adr") "2606052300"))
       (is (str/ends-with? (get-in (manifest) ["adr" "master"]) "2606052300")))))

(deftest test-manifest-schema-pointer-exists
  #?(:clj
     (let [p (io/file root-dir (str/replace (get-in (manifest) ["references" "schema"]) #"^/" ""))]
       (is (.exists p) (str p)))))

(deftest test-every-seed-maintainer-has-envelope-or-is-excluded
  #?(:clj
     (let [seed (edn/load-edn seed-file)
           env-dids (set (map #(get % ":envelope/maintainer") (get seed ":envelope/batch")))]
       (doseq [m (get seed ":maintainer/batch")]
         (is (contains? env-dids (get m ":maintainer/did")) (get m ":maintainer/did"))))))

(deftest test-seed-cash-is-zero-everywhere
  #?(:clj
     (let [seed (edn/load-edn seed-file)]
       (doseq [e (get seed ":envelope/batch")]
         (is (= (get e ":envelope/cash-usd-micros") 0))))))

(deftest test-seed-no-maintainer-owns-payoff
  #?(:clj
     (let [seed (edn/load-edn seed-file)]
       (doseq [m (get seed ":maintainer/batch")]
         (is (false? (get m ":maintainer/owns-payoff" false)))))))

(deftest test-seed-covenants-are-valid-vocab
  #?(:clj
     (let [seed (edn/load-edn seed-file)
           vocab (set (get (edn/load-edn schema-file) ":ontology/covenants"))]
       (doseq [m (get seed ":maintainer/batch")]
         (is (contains? vocab (get m ":maintainer/covenant")))))))

(deftest test-seed-envelope-lines-are-valid-vocab
  #?(:clj
     (let [seed (edn/load-edn seed-file)
           vocab (set (get (edn/load-edn schema-file) ":ontology/envelope-lines"))]
       (doseq [e (get seed ":envelope/batch")]
         (is (contains? vocab (get e ":envelope/line")))))))
