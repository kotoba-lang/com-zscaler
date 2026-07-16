(ns kabuto.methods.bpmn
  "bpmn.py — kabuto 兜 per-company BPMN 2.0 process-model emitter. ADR-2606022000.
  1:1 Clojure port of `methods/bpmn.py`.

  Emits GENERIC, well-formed BPMN 2.0 XML workflow templates for each seeded company (a procurement
  template + a disclosure template), anchors each as a content hash (kotoba-CID stand-in for R0), and
  writes the queryable :company.process/* datoms (with the computed bpmn-cid) back into the Datom log.

  HONEST (G5): these are :synthesized GENERIC templates — a plausible public procurement / disclosure
  workflow, NOT a company's actual internal process. The BPMN XML validates against the OMG BPMN 2.0
  namespace; render it in bpmn-js.

  House style: pure XML/CID builders; the Python ':…' keyword strings stay strings; file I/O only
  behind #?(:clj …). SELF-CONTAINED: requires the actor's existing kabuto-edn sibling; sha-256 via
  the kotoba sibling's host seam. (The Python `__main__` CLI is preserved as -main.)"
  (:require [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (def ^:private here (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)))

(def bpmn-ns "http://www.omg.org/spec/BPMN/20100524/MODEL")

;; (kind, process-name, [task labels]) generic templates
(def templates
  {":procurement" ["Supplier procurement"
                   ["Identify supply need" "Issue RFQ to qualified suppliers"
                    "Evaluate bids (cost / capacity / ESG)" "Award & onboard supplier"
                    "Place purchase order" "Receive & inspect goods"]]
   ":disclosure" ["Supply-chain disclosure"
                  ["Collect supplier list" "Run human-rights / ESG due diligence"
                   "Aggregate findings (aggregate-first)" "Publish public disclosure report"]]})

(defn- xml-escape [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- lstrip-colon [^String s]
  (loop [i 0] (if (and (< i (count s)) (= \: (nth s i))) (recur (inc i)) (subs s i))))

(defn build-bpmn
  "Return well-formed BPMN 2.0 XML: start → tasks (sequence) → end, with a full BPMNDI section and a
  deterministic left-to-right layout (bpmn-js needs DI coordinates). Mirrors build_bpmn()."
  [process-id name company-name tasks]
  (let [start (str process-id "_start")
        end (str process-id "_end")
        task-ids (vec (map #(str process-id "_t" %) (range (count tasks))))
        seq* (vec (concat [start] task-ids [end]))
        kinds (vec (concat ["event"] (repeat (count tasks) "task") ["event"]))
        cy 120
        gap 60
        ;; geometry: lay the chain out left → right on one horizontal lane
        geom (loop [items (map vector seq* kinds), x 160, g {}]
               (if (empty? items)
                 g
                 (let [[nid kind] (first items)
                       [w h] (if (= kind "task") [100 80] [36 36])]
                   (recur (rest items) (+ x w gap)
                          (assoc g nid [x (- cy (quot h 2)) w h])))))
        ;; semantic model
        nodes (concat
               [(str "      <startEvent id=\"" start "\" name=\"Start\"/>")]
               (map (fn [tid label] (str "      <task id=\"" tid "\" name=\"" (xml-escape label) "\"/>"))
                    task-ids tasks)
               [(str "      <endEvent id=\"" end "\" name=\"Done\"/>")])
        idxs (range (dec (count seq*)))
        flows (map (fn [i]
                     (let [fid (str process-id "_f" i)]
                       (str "      <sequenceFlow id=\"" fid "\" sourceRef=\"" (nth seq* i)
                            "\" targetRef=\"" (nth seq* (inc i)) "\"/>")))
                   idxs)
        flow-pairs (map (fn [i] [(str process-id "_f" i) (nth seq* i) (nth seq* (inc i))]) idxs)
        ;; diagram interchange (BPMNDI)
        di (concat
            [(str "    <bpmndi:BPMNPlane id=\"plane_" process-id "\" bpmnElement=\"" process-id "\">")]
            (map (fn [nid]
                   (let [[gx gy gw gh] (get geom nid)]
                     (str "      <bpmndi:BPMNShape id=\"" nid "_di\" bpmnElement=\"" nid "\">"
                          "<omgdc:Bounds x=\"" gx "\" y=\"" gy "\" width=\"" gw "\" height=\"" gh "\"/>"
                          "</bpmndi:BPMNShape>")))
                 seq*)
            (map (fn [[fid s t]]
                   (let [[sx sy _sw sh] (get geom s)
                         [tx ty _tw th] (get geom t)
                         [_ _ sw _] (get geom s)]
                     (str "      <bpmndi:BPMNEdge id=\"" fid "_di\" bpmnElement=\"" fid "\">"
                          "<omgdi:waypoint x=\"" (+ sx sw) "\" y=\"" (+ sy (quot sh 2)) "\"/>"
                          "<omgdi:waypoint x=\"" tx "\" y=\"" (+ ty (quot th 2)) "\"/>"
                          "</bpmndi:BPMNEdge>")))
                 flow-pairs)
            ["    </bpmndi:BPMNPlane>"])]
    (str
     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
     "<definitions xmlns=\"" bpmn-ns "\" "
     "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" "
     "xmlns:omgdc=\"http://www.omg.org/spec/DD/20100524/DC\" "
     "xmlns:omgdi=\"http://www.omg.org/spec/DD/20100524/DI\" "
     "targetNamespace=\"https://etzhayyim.com/ns/kabuto/bpmn\" "
     "id=\"def_" process-id "\">\n"
     "  <!-- kabuto 兜 generic :synthesized template for " (xml-escape company-name)
     " (ADR-2606022000). NOT the company's actual internal process. -->\n"
     "  <process id=\"" process-id "\" name=\"" (xml-escape name) " — " (xml-escape company-name) "\" "
     "isExecutable=\"false\">\n"
     (str/join "\n" nodes) "\n"
     (str/join "\n" flows) "\n"
     "  </process>\n"
     "  <bpmndi:BPMNDiagram id=\"di_" process-id "\">\n"
     (str/join "\n" di) "\n"
     "  </bpmndi:BPMNDiagram>\n"
     "</definitions>\n")))

(defn content-cid
  "Deterministic content hash standing in for a kotoba-CID at R0 (sha256[:32])."
  [data]
  (str "cid.sha256:" (subs (kotoba/*sha256-hex* (str data)) 0 32)))

#?(:clj
   (defn -main
     "CLI entry — write a procurement BPMN per company + a per-:company.process template, plus the
     :company.process/* datoms with computed bpmn-cid. File I/O at the edge."
     [& argv]
     (let [argv (vec argv)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-public-companies.kotoba.edn"))
           outdir (let [i (.indexOf argv "--out")]
                    (if (>= i 0) (io/file (nth argv (inc i))) (io/file here "out")))
           bpmndir (io/file outdir "bpmn")
           rows (kedn/load-edn seed)
           {:keys [companies processes]} (kedn/classify rows)
           want (into (sorted-set)
                      (concat
                       (map (fn [p] [(get p ":company.process/company")
                                     (get p ":company.process/kind" ":procurement")])
                            processes)
                       (map (fn [cid] [cid ":procurement"]) (kedn/co-keys companies))))]
       (.mkdirs bpmndir)
       (let [emitted
             (vec (for [[cid kind] want]
                    (let [[name tasks] (get templates kind (get templates ":procurement"))
                          cname (get-in companies [cid ":company/name"] cid)
                          slug (-> cid (str/replace "org.corp." "") (str/replace "." "_"))
                          proc-id (str "proc_" slug "_" (lstrip-colon kind))
                          xml (build-bpmn proc-id name cname tasks)
                          fname (io/file bpmndir (str slug "." (lstrip-colon kind) ".bpmn"))]
                      (spit fname xml)
                      [(str "proc." cid "." (lstrip-colon kind)) cid name kind (content-cid xml)])))
             es kedn/edn-str
             lines (concat
                    [";; kabuto — BPMN process datoms with computed content CIDs (ADR-2606022000)."
                     ";; :synthesized generic templates (G5); bpmn XML under out/bpmn/. NOT actual processes."
                     "["]
                    (map (fn [[pid cid name kind cid-hash]]
                           (str " {:company.process/id " (es pid) " "
                                ":company.process/company " (es cid) " "
                                ":company.process/name " (es name) " "
                                ":company.process/kind " kind " "
                                ":company.process/bpmn-cid " (es cid-hash) " "
                                ":company.process/sourcing :synthesized}"))
                         emitted)
                    ["]"])]
         (spit (io/file outdir "processes.kotoba.edn") (str (str/join "\n" lines) "\n"))
         (println (str "kabuto.bpmn: wrote " (count emitted) " BPMN files → " bpmndir))
         (println (str "kabuto.bpmn: wrote " (io/file outdir "processes.kotoba.edn")
                       " (" (count emitted) " process datoms)"))))))
