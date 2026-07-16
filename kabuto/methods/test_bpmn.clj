#!/usr/bin/env bb
;; Working Clojure test for methods/bpmn.clj (no Python test existed; new coverage).
(ns kabuto.methods.test-bpmn
  "Tests for the kabuto 兜 BPMN 2.0 process-model emitter (methods/bpmn.clj).

  Guards well-formed BPMN XML (start→tasks→end + BPMNDI layout), deterministic content-CID,
  XML escaping, and byte-parity with bpmn.py (a fixed example pins the exact sha256).

  Run:  bb --classpath 20-actors 20-actors/kabuto/methods/test_bpmn.clj"
  (:require [kabuto.methods.bpmn :as b]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- proc-xml []
  (let [[name tasks] (get b/templates ":procurement")]
    (b/build-bpmn "proc_jp_toyota_procurement" name "Toyota & Co <Ltd>" tasks)))

(deftest bpmn-is-well-formed
  (let [xml (proc-xml)]
    (is (str/starts-with? xml "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
    (is (str/includes? xml "<definitions"))
    (is (str/includes? xml "<startEvent"))
    (is (str/includes? xml "<endEvent"))
    (is (str/includes? xml "<bpmndi:BPMNDiagram"))
    (is (str/includes? xml "<bpmndi:BPMNPlane"))
    (is (str/ends-with? xml "</definitions>\n"))))

(deftest task-and-flow-counts
  (let [[_ tasks] (get b/templates ":procurement")
        xml (proc-xml)
        n-tasks (count (re-seq #"<task " xml))
        n-flows (count (re-seq #"<sequenceFlow " xml))]
    (is (= n-tasks (count tasks)))                 ; 6 procurement tasks
    (is (= n-flows (inc (count tasks))))))         ; start→t0…→end = tasks+1 flows

(deftest xml-escaping-applied
  (let [xml (proc-xml)]
    (is (str/includes? xml "Toyota &amp; Co &lt;Ltd&gt;"))   ; & < > escaped in the company name
    (is (not (str/includes? xml "Toyota & Co <Ltd>")))))

(deftest content-cid-format-and-determinism
  (let [xml (proc-xml)
        cid (b/content-cid xml)]
    (is (str/starts-with? cid "cid.sha256:"))
    (is (= (count (subs cid (count "cid.sha256:"))) 32))
    (is (= cid (b/content-cid xml)))))             ; deterministic

(deftest byte-parity-with-py
  ;; pins the exact sha256 bpmn.py computes for this fixed example → XML is byte-identical
  (is (= (b/content-cid (proc-xml)) "cid.sha256:5b6040d61705f89d5d2bb6e72daf3b19")))

(deftest templates-present
  (is (contains? b/templates ":procurement"))
  (is (contains? b/templates ":disclosure"))
  (is (= 4 (count (second (get b/templates ":disclosure"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kabuto.methods.test-bpmn)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
