(ns kabuto.viz.test-build-bpmn-manifest
  "Tests for the kabuto BPMN manifest builder (ADR-2606022000 port of build_bpmn_manifest). Covers
  the regex proc parser (multi-record, interspersed fields, kind keyword-strip), the bpmn-filename
  slug, and the FEATURED-assembly: only featured companies inlined, procs whose XML file is absent
  skipped (the path.exists guard, injected via xml-by-filename), total counts ALL procs while
  featuredCount counts only inlined, and the manifest envelope fields."
  (:require [clojure.test :refer [deftest is]]
            [kabuto.viz.build-bpmn-manifest :as m]))

(def ^:private sample-text
  (str "{:company.process/id \"p.tsmc.procurement\" :company.process/company \"org.corp.tw.tsmc\" "
       ":company.process/name \"TSMC Procurement\" :company.process/kind :procurement "
       ":company.process/bpmn-cid \"bafkrei111\" :extra \"x\"}\n"
       "{:company.process/id \"p.apple.disclosure\" :company.process/company \"org.corp.us.apple\" "
       ":company.process/name \"Apple Disclosure\" :company.process/kind :disclosure "
       ":company.process/bpmn-cid \"bafkrei222\"}\n"
       ;; a non-featured company — parsed into total, but never inlined
       "{:company.process/id \"p.acme.procurement\" :company.process/company \"org.corp.us.acme\" "
       ":company.process/name \"Acme Procurement\" :company.process/kind :procurement "
       ":company.process/bpmn-cid \"bafkrei333\"}"))

(deftest test-parse-proc-meta
  (let [procs (m/parse-proc-meta sample-text)]
    (is (= 3 (count procs)))
    (is (= {"id" "p.tsmc.procurement" "company" "org.corp.tw.tsmc" "name" "TSMC Procurement"
            "kind" "procurement" "cid" "bafkrei111"} (first procs)))
    (is (= "disclosure" (get (second procs) "kind")))          ; keyword stripped
    (is (= "org.corp.us.acme" (get (nth procs 2) "company")))))

(deftest test-bpmn-filename
  (is (= "tw_tsmc.procurement.bpmn" (m/bpmn-filename "org.corp.tw.tsmc" "procurement")))
  (is (= "kr_samsung-electronics.disclosure.bpmn"
         (m/bpmn-filename "org.corp.kr.samsung-electronics" "disclosure"))))

(deftest test-build-manifest-features-and-skips
  (let [procs (m/parse-proc-meta sample-text)
        ;; tsmc xml present, apple xml ABSENT (skipped), acme not featured at all
        xml {"tw_tsmc.procurement.bpmn" "<bpmn>tsmc</bpmn>"}
        man (m/build-manifest procs xml)]
    (is (= "com.etzhayyim.kabuto.bpmnManifest/1" (get man "schema")))
    (is (= "kabuto" (get man "actor")))
    (is (= "did:web:etzhayyim.com:actor:kabuto" (get man "did")))
    (is (= 3 (get man "total")))                ; ALL parsed procs
    (is (= 1 (get man "featuredCount")))        ; only tsmc (apple xml absent, acme not featured)
    (let [entry (first (get man "processes"))]
      (is (= "p.tsmc.procurement" (get entry "id")))
      (is (= "org.corp.tw.tsmc" (get entry "company")))
      (is (= "<bpmn>tsmc</bpmn>" (get entry "xml")))
      (is (= "bafkrei111" (get entry "cid"))))))

(deftest test-build-manifest-respects-featured-order
  ;; Two featured companies both with xml present → inlined in FEATURED order, not parse order.
  (let [procs (m/parse-proc-meta sample-text)
        xml {"tw_tsmc.procurement.bpmn" "<a/>" "us_apple.disclosure.bpmn" "<b/>"}
        ;; explicit featured list with apple BEFORE tsmc → apple must come first
        man (m/build-manifest procs xml ["org.corp.us.apple" "org.corp.tw.tsmc"])]
    (is (= 2 (get man "featuredCount")))
    (is (= ["org.corp.us.apple" "org.corp.tw.tsmc"]
           (mapv #(get % "company") (get man "processes"))))))

(deftest test-empty-text
  (is (= [] (m/parse-proc-meta "")))
  (is (= 0 (get (m/build-manifest [] {}) "total"))))
