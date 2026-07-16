(ns tasuke.methods.test-consistency
  "SSoT-consistency / drift-lock tests for 助 (tasuke) — ADR-2606060900.
  1:1 port of `methods/test_consistency.py` (pytest → clojure.test).

  Bind the manifest, cell tree, lexicons, ontology, code, seed, and registry to ONE source of truth."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.triage :as triage]))

;; ROOT/20-actors/tasuke via *file* (…/tasuke/methods/test_consistency.cljc → up 2 = tasuke)
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private repo-dir (-> actor-dir .getParentFile .getParentFile)))   ;; up 2 = repo root
#?(:clj (def ^:private lex-dir (io/file actor-dir "lex")))
#?(:clj (def ^:private cells-dir (io/file actor-dir "cells")))
#?(:clj (def ^:private ontology-file (io/file repo-dir "00-contracts" "schemas" "cybercrime-victim-support-ontology.kotoba.edn")))
#?(:clj (def ^:private profile-seed-file (io/file repo-dir "00-contracts" "schemas" "actor-profile-seed.kotoba.edn")))

#?(:clj (defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (io/file actor-dir "manifest.edn"))))))

;; ── manifest cells ↔ cell tree ──────────────────────────────────────────────
(deftest test-manifest-cells-have-dirs-and-state-machines
  (doseq [cell (get (manifest) "cells")]
    (let [d (io/file cells-dir (get cell "name"))]
      (is (.isFile (io/file d "state_machine.cljc")) (str "missing " (get cell "name") "/state_machine.cljc")))))

(deftest test-every-cell-dir-is-in-the-manifest
  (let [declared (set (map #(get % "name") (get (manifest) "cells")))
        on-disk (set (->> (.listFiles cells-dir)
                          (filter #(and (.isDirectory %) (.isFile (io/file % "state_machine.cljc"))))
                          (map #(.getName %))))]
    (is (= on-disk declared) (str "cell tree " on-disk " != manifest " declared))))

;; ── manifest lexicons ↔ lex/*.edn ───────────────────────────────────────────
(deftest test-manifest-lexicons-resolve-to-files-with-matching-id
  (doseq [ns (get (manifest) "lexiconNamespaces")]
    (let [last-seg (last (str/split (get ns "id") #"\."))
          f (io/file lex-dir (str last-seg ".edn"))]
      (is (.isFile f) (str "missing lexicon file for " (get ns "id")))
      (is (= (get (tedn/load-edn f) ":id") (get ns "id"))))))

(deftest test-every-lex-file-is-declared-in-manifest
  (let [declared (set (map #(get % "id") (get (manifest) "lexiconNamespaces")))
        on-disk (set (->> (.listFiles lex-dir)
                          (filter #(str/ends-with? (.getName %) ".edn"))
                          (map #(get (tedn/load-edn %) ":id"))))]
    (is (= on-disk declared))))

;; ── manifest gate/non-goal counts ───────────────────────────────────────────
(deftest test-manifest-declares-ten-gates-and-seven-nongoals
  (let [m (manifest)]
    (is (= 10 (count (get-in m ["constitutionalGates" "gates"]))))
    (is (= 7 (count (get-in m ["nonGoals" "goals"]))))))

;; ── ontology ≡ code scam-kind vocab ─────────────────────────────────────────
(deftest test-ontology-scam-kinds-equal-code
  (let [onto (set (map #(str/replace % #"^:+" "") (get (tedn/load-edn ontology-file) ":ontology/scam-kinds")))]
    (is (= (set triage/SCAM-KINDS) onto))))

;; ── seed ↔ ontology (no seed case uses an out-of-vocab scam-kind) ────────────
(deftest test-seed-cases-use-only-ontology-vocab
  (let [onto (tedn/load-edn ontology-file)
        kinds (set (get onto ":ontology/scam-kinds"))]
    (doseq [c (get (tedn/load-edn (io/file actor-dir "data" "seed-cybercrime-cases.kotoba.edn")) ":case/batch")]
      (is (contains? kinds (get c ":case/scam-kind")) (str (get c ":case/scam-kind")))
      (is (= 0 (long (get c ":case/support-cost-jpy"))))   ;; G1 — every seed case is free
      (is (= true (get c ":case/consent"))))))             ;; G7

;; ── seed registry is reachable + within ontology vocab (the stray-brace guard) ─
(deftest test-seed-registry-windows-reachable-and-in-vocab
  (let [onto (tedn/load-edn ontology-file)
        allowed (set (get onto ":ontology/referral-windows"))
        seed (tedn/load-edn (io/file actor-dir "data" "seed-cybercrime-cases.kotoba.edn"))
        windows (get seed ":registry/windows" [])]
    (is (seq windows) "seed :registry/windows is unreachable (top-map brace bug?)")
    (doseq [w windows]
      (is (contains? allowed (get w ":registry/window")) (str (get w ":registry/window")))
      (is (contains? #{":representative" ":authoritative"} (get w ":registry/sourcing"))))))

;; ── actor-profile seed registration matches the manifest ────────────────────
(deftest test-actor-profile-seed-has-tasuke
  (let [blob (slurp profile-seed-file)
        m (manifest)]
    (is (str/includes? blob "did:web:etzhayyim.com:actor:tasuke"))
    (is (str/includes? (str/replace blob "\"" "")
                       (str/replace (get-in m ["references" "schema"]) #"^/+" "")))
    (is (str/includes? blob "com.etzhayyim.tasuke"))))

;; ── ADR file referenced by the manifest exists ──────────────────────────────
(deftest test-adr-file-exists
  (let [adr (io/file repo-dir (str/replace (get-in (manifest) ["references" "adr" "master"]) #"^/+" ""))]
    (is (.isFile adr) (str "ADR not found: " adr))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-consistency)))
