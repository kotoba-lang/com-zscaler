#!/usr/bin/env bb
;; matsurigoto wasm build — bb-native (Clojure / babashka; no shell). ADR-2606072802.
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. Shelling out to system binaries via babashka.process
;; (componentize-py / wasm-tools / npx / ipfs) is allowed — only the orchestration
;; logic itself moves out of bash.
;;
;; Build all 5 matsurigoto egov service modules as WASI Component-Model components with
;; componentize-py, transpile with jco, and report each one's IPFS CID (ADR-2606062300 R1.A;
;; same componentize-py path as the watatsuna precedent, ADR-2606014600).
;; Requires: python3 (for componentize-py via venv), node/npx (jco), ipfs, wasm-tools.
;;
;;   bb 20-actors/matsurigoto/wasm/build.clj
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def wit-dir (str (fs/path here "../../../00-contracts/wit/matsurigoto")))

;; world:module pairs — each WIT world exports exactly one service interface (egov.wit).
(def modules
  {"tax-assess"       "tax_assess_app"
   "civil-registry"   "civil_registry_app"
   "corp-registry"    "corp_registry_app"
   "credential-issue" "credential_issue_app"
   "benefit-disburse" "benefit_disburse_app"})

(defn kebab->camel [s]
  (str/replace s #"-([a-z])" (fn [[_ c]] (str/upper-case c))))

;; componentize-py in an isolated venv (PEP-668 environments block global pip).
(def venv (or (System/getenv "CPY_VENV") "/tmp/cpy-venv"))
(def cpy (str venv "/bin/componentize-py"))

(when-not (fs/exists? cpy)
  (p/shell "python3" "-m" "venv" venv)
  (p/shell (str venv "/bin/pip") "install" "--quiet" "componentize-py"))

(doseq [[world mod] (sort modules)]
  (println (str "=== " world " (" mod ".py) ==="))
  (let [wasm-file (str world ".wasm")]
    (p/shell {:dir (str here)} cpy "-d" wit-dir "-w" world "componentize" mod "-o" wasm-file)
    (p/shell {:dir (str here)} "wasm-tools" "validate" wasm-file)
    (p/shell {:dir (str here)} "npx" "-y" "@bytecodealliance/jco@latest" "transpile" wasm-file
             "-o" (str "transpiled-" world) "--name" (kebab->camel world))
    (let [cid (-> (p/sh {:dir (str here)} "ipfs" "add" "-Q" "--only-hash" "--cid-version=1" wasm-file)
                  :out str/trim)
          size (fs/size (fs/path here wasm-file))]
      (println (format "%s  %d bytes  CID=%s" wasm-file size cid)))))

(println "Run 'node verify.mjs' to check all 5 against their reference specs.")
(println "If a CID changed, update the matching <world>.meta.json + :egov.module/cid in the standard EDN.")
