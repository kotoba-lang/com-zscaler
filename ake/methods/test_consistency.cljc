(ns ake.methods.test-consistency
  "test_consistency.cljc — SSoT-consistency / drift-lock tests for 朱 (ake). 1:1 Clojure port
  of `methods/test_consistency.py` (clojure.test). Every Python assertion ported.

  These bind the manifest, the cell tree, the lexicons, the ontology, the code, the seed, and
  the registry to ONE source of truth, so a future edit that adds a cell but forgets the
  manifest (or renames a lexicon, or drifts the route vocab) fails loudly here instead of
  silently shipping.

  The manifest.jsonld is read via cheshire (matching the ake house style in test_ingest.cljc);
  lexicon / ontology / seed EDN via `ake.methods._edn`; the code route vocab from
  `ake.methods.triage`. All file / filesystem I/O sits behind #?(:clj). The __main__
  standalone runner is omitted.

  Paths are repo-root-relative (bb is run from the repo root, as the other ake test_*.cljc
  expect — cf. test_analyze.cljc / test_revision.cljc seed-path)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [ake.methods.triage :as triage]
            #?(:clj [ake.methods._edn :as edn])
            #?(:clj [clojure.java.io :as io])))

#?(:clj (def ^:private root "20-actors/ake"))
#?(:clj (def ^:private repo "."))
#?(:clj (def ^:private lex-dir (str root "/lex")))
#?(:clj (def ^:private cells-dir (str root "/cells")))
#?(:clj (def ^:private ontology (str repo "/00-contracts/schemas/community-edit-ontology.kotoba.edn")))
#?(:clj (def ^:private profile-seed (str repo "/00-contracts/schemas/actor-profile-seed.kotoba.edn")))

#?(:clj
   (defn- manifest []
     (let [parse-json (requiring-resolve 'cheshire.core/parse-string)]
       (:actor/manifest (clojure.edn/read-string (slurp (str root "/manifest.edn")))))))

#?(:clj
   (defn- load-edn [path] (edn/load-edn path)))

;; ── manifest cells ↔ cell tree ──────────────────────────────────────────────
#?(:clj
   (deftest test-manifest-cells-have-dirs-and-state-machines
     (doseq [cell (get (manifest) "cells")]
       (let [d (io/file cells-dir (get cell "name"))]
         (is (.isFile (io/file d "state_machine.cljc"))
             (str "missing " (get cell "name") "/state_machine.cljc"))))))

#?(:clj
   (deftest test-every-cell-dir-is-in-the-manifest
     (let [declared (set (map #(get % "name") (get (manifest) "cells")))
           on-disk (set (for [p (.listFiles (io/file cells-dir))
                              :when (and (.isDirectory p) (.isFile (io/file p "state_machine.cljc")))]
                          (.getName p)))]
       (is (= on-disk declared) (str "cell tree " on-disk " != manifest " declared)))))

;; ── manifest lexicons ↔ lex/*.edn ───────────────────────────────────────────
#?(:clj
   (deftest test-manifest-lexicons-resolve-to-files-with-matching-id
     (doseq [ns (get (manifest) "lexiconNamespaces")]
       (let [lid (get ns "id")
             last* (last (str/split lid #"\."))
             f (io/file lex-dir (str last* ".edn"))]
         (is (.isFile f) (str "missing lexicon file for " lid))
         (is (= lid (get (edn/reconstitute (load-edn f) (str "lex." last*)) ":id")))))))

#?(:clj
   (deftest test-every-lex-file-is-declared-in-manifest
     (let [declared (set (map #(get % "id") (get (manifest) "lexiconNamespaces")))
           on-disk (set (for [f (.listFiles (io/file lex-dir))
                              :when (str/ends-with? (.getName f) ".edn")]
                          (let [name (str/replace (.getName f) #"\.edn$" "")]
                            (get (edn/reconstitute (load-edn f) (str "lex." name)) ":id"))))]
       (is (= on-disk declared)))))

;; ── manifest gate/non-goal counts (the stated 9 + 7) ────────────────────────
#?(:clj
   (deftest test-manifest-declares-nine-gates-and-seven-nongoals
     (let [m (manifest)]
       (is (= 9 (count (get-in m ["constitutionalGates" "gates"]))))
       (is (= 7 (count (get-in m ["nonGoals" "goals"])))))))

;; ── ontology ≡ code route vocab ─────────────────────────────────────────────
#?(:clj
   (deftest test-ontology-routes-equal-code-routes
     (let [onto (set (map #(str/replace % #"^:+" "")
                          (get (load-edn ontology) ":ontology/triage-routes")))]
       (is (= (set triage/ROUTES) onto)))))

;; ── seed ↔ ontology (no seed edit uses an out-of-vocab target-kind/op) ───────
#?(:clj
   (deftest test-seed-edits-use-only-ontology-vocab
     (let [onto (load-edn ontology)
           kinds (set (get onto ":ontology/target-kinds"))
           ops (set (get onto ":ontology/edit-ops"))]
       (doseq [e (get (edn/reconstitute (load-edn (str root "/data/seed-edit-graph.kotoba.edn"))
                                        "data.seed-edit-graph")
                      ":edit/batch")]
         (is (contains? kinds (get e ":edit/target-kind")) (get e ":edit/target-kind"))
         (is (contains? ops (get e ":edit/op")) (get e ":edit/op"))))))

;; ── actor-profile seed registration ─────────────────────────────────────────
#?(:clj
   (deftest test-committed-fixture-registers-ake-with-its-lexicon
     ;; hermetic: ake's own committed fixture carries its DID + lexicon
     (let [fblob (slurp (str root "/data/sample-profile-seed.kotoba.edn"))]
       (is (str/includes? fblob "did:web:etzhayyim.com:actor:ake"))
       (is (str/includes? fblob "com.etzhayyim.ake")))))

#?(:clj
   (deftest test-real-profile-seed-matches-manifest-when-ake-registered
     ;; soft: the SHARED repo seed registers ake by coordination, committed separately from
     ;; ake's own commits — its absence is not an ake-suite failure (never hard-fails on it).
     (let [blob (slurp profile-seed)]
       (when (str/includes? blob "did:web:etzhayyim.com:actor:ake")
         (let [m (manifest)
               schema (-> (get-in m ["references" "schema"])
                          (str/replace #"^/+" "")
                          (str/replace "\"" ""))]
           (is (str/includes? (str/replace blob "\"" "") schema))
           (is (str/includes? blob "com.etzhayyim.ake")))))))

;; ── ADR file referenced by the manifest exists ──────────────────────────────
#?(:clj
   (deftest test-adr-file-exists
     (let [adr (io/file repo (str/replace (get-in (manifest) ["references" "adr" "master"])
                                          #"^/+" ""))]
       (is (.isFile adr) (str "ADR not found: " adr)))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-consistency)))
