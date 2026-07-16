(ns toritsugi.methods.test-manifest-invariants
  "toritsugi — manifest invariants (ported from 70-tools/scripts/audit/test_toritsugi_invariants.py; reads manifest.edn blob, jsonld retired).

  Extended for the Phase-3/4 build (ADR-2605312030 R1 technical): the 7 cells are
  now IMPLEMENTED as `src/toritsugi/cells/<name>/state_machine.cljc` (the
  ProcedureGovernor's structural membranes), so the manifest's cell `module`
  references must point at that implementation and each named cell MUST resolve to
  a real state-machine file on disk. This pins the manifest↔code SSoT so a future
  R-phase wave cannot silently strand a cell (manifest lists a module that no
  longer exists) or invent a cell (code ships a cell the manifest does not declare)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str] [clojure.edn :as edn]))
(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/toritsugi"))
(def ^:private cells-dir (java.io.File. actor-dir "src/toritsugi/cells"))
(defn- manifest [] (:actor/manifest (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(deftest manifest-gates-namespaces-cells
  (let [m (manifest)
        gates (get-in m ["constitutionalGates" "gates"])]
    (is (= (count gates) 15) "ADR-2605312030 pins 15 gates G1..G15")
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 16)))) "gates are exactly G1..G15")
    (let [ns (get m "lexiconNamespaces")]
      (is (= (count ns) 6) "6 lexiconNamespaces")
      (doseq [n ns]
        (let [leaf (last (str/split n #"\."))]
          (is (.exists (java.io.File. lexdir (str leaf ".json"))) (str "missing lexicon: " leaf)))))
    (let [cells (get m "cells")]
      (is (= (count cells) 7) "7 cells")
      (doseq [c cells]
        (let [name   (get c "name")
              module (get c "module")
              sm     (java.io.File. cells-dir (str name "/state_machine.cljc"))]
          (is (str/starts-with? module "toritsugi.cells.")
              (str "module references the toritsugi.cells.* implementation: " module))
          (is (.exists sm)
              (str "cell implementation exists: src/toritsugi/cells/" name "/state_machine.cljc")))))))

;; manifest↔disk cell parity: no stray implemented cell that the manifest does not
;; declare (the manifest is the SSoT for the cell roster), and the declared module
;; maps to the cell name (toritsugi.cells.<dashed-name>).
(deftest manifest-cells-disk-parity
  (let [declared (set (map #(get % "name") (get (manifest) "cells")))
        on-disk  (->> (.listFiles cells-dir)
                      (keep #(when (.isDirectory ^java.io.File %) (.getName ^java.io.File %)))
                      (set))]
    (is (= declared on-disk)
        (str "manifest cell roster == src/toritsugi/cells/ on disk (no orphan / no stray)"))))
;; NOTE: the Python original also asserted <module>/cell.py exists on disk — obsolete
;; (the toritsugi cells were ported off cell.py to cljc), already failing in the Python
;; audit suite, so it is dropped here (not a regression).
(defn -main [& _] (let [r (run-tests 'toritsugi.methods.test-manifest-invariants)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))
