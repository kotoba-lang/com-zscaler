(ns matsurigoto.methods.test-lexicons
  "test_lexicons.py — 3-place invariant drift-lock.
  1:1 Clojure port (stdlib unittest-style → clojure.test).

  Proves the structural invariants are encoded identically in all THREE places —
    (1) the schema EDN  00-contracts/schemas/egov-execution-ontology.kotoba.edn
    (2) the lexicons    00-contracts/lexicons/com/etzhayyim/matsurigoto/*.json
    (3) the code        methods/datoms.cljc / sign_capability.cljc

  The lexicon JSONs are read via an INLINED minimal JSON reader (maps string-keyed, Python
  json.loads shapes); the schema EDN via the sibling matsurigoto.methods._edn reader. File I/O
  is behind #?(:clj ...). *file*-relative paths up to the repo root, exactly like the Python
  test used REPO = HERE.parent.parent.parent. The __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [matsurigoto.methods._edn :as edn]
            [matsurigoto.methods.datoms :as D]
            [matsurigoto.methods.sign-capability :as S]
            [matsurigoto.methods.modules.tax-assess :as tax]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal JSON reader (subset sufficient for lexicon JSONs); maps string-keyed ──
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

;; ── *file*-relative paths (REPO = HERE.parent.parent.parent, i.e. methods → matsurigoto →
;; 20-actors → root) ──
#?(:clj
   (def ^:private repo
     (-> *file* io/file .getParentFile .getParentFile .getParentFile .getParentFile)))

#?(:clj
   (def ^:private lex-dir
     (io/file repo "00-contracts" "lexicons" "com" "etzhayyim" "matsurigoto")))

#?(:clj
   (defn- lex [name]
     (parse-json (slurp (io/file lex-dir (str name ".json"))))))

#?(:clj
   (defn- props [name]
     (get-in (lex name) ["defs" "main" "record" "properties"])))

#?(:clj
   (defn- onto []
     (edn/load-edn (io/file repo "00-contracts" "schemas" "egov-execution-ontology.kotoba.edn"))))

(defn- lstrip-colon [x] (if (str/starts-with? x ":") (subs x 1) x))

(deftest test-g1-server-held-authority-const-false-everywhere
  ;; (2) lexicons
  (is (= (get-in (props "serviceExecution") ["serverHeldAuthority" "const"]) false))
  (is (= (get-in (props "unsignedArtifact") ["serverHeldAuthority" "const"]) false))
  ;; (3) code — modules hold no key
  (is (= tax/SERVER-HELD-AUTHORITY false))
  (is (= S/SIGNER-HELD-PRIVATE-KEY false)))

(deftest test-g3-operated-by-enum-matches-code-and-schema
  (let [lex-enum (set (get-in (props "serviceExecution") ["operatedBy" "enum"]))
        code (set (map lstrip-colon D/allowed-operated-by))]
    (is (= lex-enum code) [lex-enum code])
    ;; (1) schema EDN
    (let [inv (get-in (onto) [":invariants" ":g3-operated-by" ":allowed"])
          schema (set (map lstrip-colon inv))]
      (is (= lex-enum schema) [lex-enum schema]))))

(deftest test-g3-authority-mode-enum-matches-code
  (let [lex-enum (set (get-in (props "serviceExecution") ["authorityMode" "enum"]))
        code (set (map lstrip-colon D/allowed-authority-mode))]
    (is (= lex-enum code) [lex-enum code])))

(deftest test-g5-immutable-const-true-in-lexicon-and-schema
  (is (= (get-in (props "vitalRecord") ["immutable" "const"]) true))
  (is (= (get-in (onto) [":invariants" ":g5-append-only" ":allowed"]) [true])))

(deftest test-lexicons-are-valid-json-with-ids
  (doseq [name ["serviceExecution" "unsignedArtifact" "vitalRecord"]]
    (let [d (lex name)]
      (is (= (get d "id") (str "com.etzhayyim.matsurigoto." name)))
      (is (= (get d "lexicon") 1)))))

#?(:clj (defn -main [& _] (run-tests 'matsurigoto.methods.test-lexicons)))
