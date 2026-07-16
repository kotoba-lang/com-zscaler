(ns noroshi.methods.test-consistency
  "SSoT-consistency tests for noroshi (烽) — ADR-2606051600 (items 1+3+4).
  1:1 Clojure port of methods/test_consistency.py (pytest → clojure.test).

  Locks manifest↔files, ontology↔schema, seeds↔schema, manifest↔ontology force-classes.
  Reads EDN via the inlined `_edn` reader behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            [noroshi.methods._edn :as edn]))

#?(:clj (def ^:private actor-root (-> (java.io.File. *file*) .getParentFile .getParentFile)))
#?(:clj
   (def ^:private ontology-file
     (-> actor-root .getParentFile .getParentFile
         (java.io.File. "00-contracts") (java.io.File. "schemas")
         (java.io.File. "photonic-convergence-ontology.kotoba.edn"))))

#?(:clj (defn- f [& parts] (reduce #(java.io.File. %1 %2) actor-root parts)))
#?(:clj (defn- manifest [] (edn/load-edn (f "manifest.edn"))))
#?(:clj
   (defn- schema-idents []
     (set (keep #(get % ":db/ident") (edn/load-edn (f "kotoba" "schema.edn"))))))
#?(:clj
   (defn- seed-attr-keys [file]
     (reduce (fn [keys r]
               (if (map? r)
                 (into keys (filter #(and (string? %) (str/starts-with? % ":") (str/includes? % "/")) (clojure.core/keys r)))
                 keys))
             #{} (edn/load-edn file))))

;; ── manifest ↔ files ─────────────────────────────────────────────────────────
(deftest test-every-manifest-lex-has-a-matching-lexicon-file
  (doseq [lex (get (manifest) ":actor/lex")]
    (let [lid (get lex ":lex/id")
          file (f "lex" (str lid ".edn"))]
      (is (.exists file) (str "missing lexicon file for " lid))
      (is (= (get (edn/load-edn file) ":id") (str "com.etzhayyim.noroshi." lid))))))

(deftest test-every-manifest-cell-exists-as-descriptor-or-coded-dir
  (doseq [cell (get (manifest) ":actor/cells")]
    (let [cid (get cell ":cell/id")
          flat (f "cells" (str cid ".edn"))
          coded-py (f "cells" cid "cell.py")
          coded-cljc (f "cells" cid "cell.cljc")]
      (is (or (.exists flat) (.exists coded-py) (.exists coded-cljc))
          (str "no cell descriptor/dir for " cid))
      (when (get cell ":cell/coded")
        (is (or (.exists coded-py) (.exists coded-cljc))
            (str "cell " cid " marked coded but has no cell.py/cell.cljc"))))))

(deftest test-coded-cells-are-exactly-active-alignment-device-design-reliability-qual
  ;; device_design + reliability_qual joined active_alignment as "coded" cells in the
  ;; maturity pass that added methods/device-design + methods/reliability-qual (real
  ;; PASS/FAIL GR-468-SHAPE engine) — see this actor's follow-up ADR.
  (let [coded (set (mapv #(get % ":cell/id") (filter #(get % ":cell/coded") (get (manifest) ":actor/cells"))))]
    (is (= coded #{"active_alignment" "device_design" "reliability_qual"}))))

;; ── ontology ↔ deployable schema ─────────────────────────────────────────────
(deftest test-ontology-attributes-equal-schema-idents
  ;; Python: set(load_edn(...)[":ontology/attributes"]) — :ontology/attributes is a MAP,
  ;; and set(dict) iterates its KEYS. Mirror that exactly.
  (let [onto-attrs (set (clojure.core/keys (get (edn/load-edn ontology-file) ":ontology/attributes")))]
    (is (= onto-attrs (schema-idents)))))

;; ── seeds ↔ schema ───────────────────────────────────────────────────────────
(deftest test-seed-uses-only-declared-attributes
  (doseq [seed ["kotoba/seed.edn" "data/seed-photonic-fleet.kotoba.edn"]]
    (let [parts (str/split seed #"/")
          file (apply f parts)
          undeclared (set/difference (seed-attr-keys file) (schema-idents))]
      (is (empty? undeclared) (str seed " uses undeclared attributes: " (sort undeclared))))))

;; ── manifest ↔ ontology force classes (and N1) ───────────────────────────────
(deftest test-manifest-force-classes-match-ontology
  (let [m-fc (set (get (manifest) ":actor/force-classes"))
        o-fc (set (get (edn/load-edn ontology-file) ":ontology/force-classes"))]
    (is (= m-fc o-fc))
    (is (not (contains? m-fc ":weaponizable")))))

;; ── seed VALUES obey the ontology enums ──────────────────────────────────────
#?(:clj
   (defn- seed-rows []
     (mapcat (fn [seed] (filter map? (edn/load-edn (apply f (str/split seed #"/")))))
             ["kotoba/seed.edn" "data/seed-photonic-fleet.kotoba.edn"])))

(deftest test-seed-keyword-values-obey-ontology-enums
  (doseq [[attr onto-key forbidden]
          [[":pdev/force-class" ":ontology/force-classes" ":weaponizable"]
           [":pdev/kind" ":ontology/device-kinds" nil]
           [":probot/force-class" ":ontology/force-classes" ":weaponizable"]
           [":sense/target-class" ":ontology/target-classes" ":person"]]]
    (let [allowed (set (get (edn/load-edn ontology-file) onto-key))]
      (doseq [r (seed-rows)]
        (when (contains? r attr)
          (is (contains? allowed (get r attr)) (str attr "=" (get r attr) " not in " onto-key))
          (when forbidden
            (is (not= (get r attr) forbidden))))))))

(deftest test-seed-const-invariants-hold-on-the-data
  (doseq [r (seed-rows)]
    (when (contains? r ":wave/civilian")     (is (= (get r ":wave/civilian") true)))
    (when (contains? r ":pkg/server-held-key") (is (= (get r ":pkg/server-held-key") false)))
    (when (contains? r ":pkg/dry-run")       (is (= (get r ":pkg/dry-run") true)))
    (when (contains? r ":pdev/process")      (is (= (get r ":pdev/process") ":open-pdk")))
    (when (contains? r ":pdev/eda")          (is (contains? #{":gdsfactory" ":meep" ":klayout" ":openlane"} (get r ":pdev/eda"))))
    (when (contains? r ":qual/dry-run")      (is (= (get r ":qual/dry-run") true)))
    (when (contains? r ":qual/representative") (is (= (get r ":qual/representative") true)))
    (when (contains? r ":qual/acceptance")   (is (contains? #{":pass" ":fail"} (get r ":qual/acceptance"))))))

#?(:clj (defn -main [& _] (run-tests 'noroshi.methods.test-consistency)))
