(ns kabuto.viz.build-bpmn-manifest
  "1:1 port of viz/build_bpmn_manifest.py (ADR-2606022000). Parses the generated process datoms
  (out/processes.kotoba.edn) and assembles the single manifest JSON the yoro AgentProfile
  \"Process\" tab serves at <embed-base>/_app/bpmn.json: a bounded FEATURED set of notable
  companies inlined with BPMN XML + the total process count. Honest (G5): :synthesized generic
  templates, never a company's actual internal process; aggregate-first, never a target-list (G2).

  Ported: the pure-stdlib `_proc_meta` regex parser (→ parse-proc-meta on injected text), the
  `_bpmn_path` slug (→ bpmn-filename), and the FEATURED-assembly (→ build-manifest with the disk
  side — which XML files exist + their content — injected as an xml-by-filename map). OMITTED (IO
  leg, not ported): main() reading out/processes.kotoba.edn + globbing/reading out/bpmn/*.bpmn +
  writing viz/bpmn-manifest.json."
  (:require [clojure.string :as str]))

(def FEATURED
  ["org.corp.tw.tsmc" "org.corp.us.apple" "org.corp.jp.toyota"
   "org.corp.kr.samsung-electronics" "org.corp.nl.asml" "org.corp.us.nvidia"
   "org.corp.tw.foxconn" "org.corp.jp.sony" "org.corp.us.intel"
   "org.corp.sa.aramco" "org.corp.ch.nestle" "org.corp.de.volkswagen"
   "org.corp.us.microsoft" "org.corp.cn.byd" "org.corp.us.boeing"
   "org.corp.fr.airbus" "org.corp.gb.astrazeneca" "org.corp.in.reliance"])

(def ^:private proc-re
  ;; Mirrors the _proc_meta finditer pattern exactly (char classes incl. newlines; no DOTALL needed).
  #"\{:company\.process/id\s+\"([^\"]+)\"[^}]*?:company\.process/company\s+\"([^\"]+)\"[^}]*?:company\.process/name\s+\"([^\"]+)\"[^}]*?:company\.process/kind\s+(:[a-z-]+)[^}]*?:company\.process/bpmn-cid\s+\"([^\"]+)\"")

(defn parse-proc-meta
  "Port of _proc_meta — parse processes.kotoba.edn text → vector of {id, company, name, kind, cid}."
  [text]
  (mapv (fn [[_ pid company name kind cid]]
          {"id" pid "company" company "name" name
           "kind" (str/replace kind #"^:+" "") "cid" cid})
        (re-seq proc-re text)))

(defn bpmn-filename
  "Port of _bpmn_path's filename — '<slug>.<kind>.bpmn' where slug = company sans 'org.corp.', dots→_."
  [company kind]
  (let [slug (-> company (str/replace "org.corp." "") (str/replace "." "_"))]
    (str slug "." kind ".bpmn")))

(defn build-manifest
  "Port of main()'s manifest assembly. `xml-by-filename` injects the disk side (which bpmn files
  exist + their content); a featured proc whose file is absent is skipped (mirrors path.exists)."
  ([procs xml-by-filename] (build-manifest procs xml-by-filename FEATURED))
  ([procs xml-by-filename featured-companies]
   (let [by-company (reduce (fn [m p] (update m (get p "company") (fnil conj []) p)) {} procs)
         featured (vec (for [company featured-companies
                             p (get by-company company [])
                             :let [fname (bpmn-filename company (get p "kind"))]
                             :when (contains? xml-by-filename fname)]
                         {"id" (get p "id") "company" company "name" (get p "name")
                          "kind" (get p "kind") "cid" (get p "cid")
                          "xml" (get xml-by-filename fname)}))]
     {"schema" "com.etzhayyim.kabuto.bpmnManifest/1"
      "actor" "kabuto"
      "did" "did:web:etzhayyim.com:actor:kabuto"
      "note" (str "Generic :synthesized BPMN procurement/disclosure templates per "
                  "ADR-2606022000 — NOT a company's actual internal process. "
                  "Aggregate-first resilience/transparency, never a target-list (G2).")
      "total" (count procs)
      "featuredCount" (count featured)
      "processes" featured})))
