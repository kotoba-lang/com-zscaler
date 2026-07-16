(ns iryo.methods.fhir)

(def icd10-jp-system "urn:oid:1.2.392.200119.4.504.4")
(def shinryo-system "urn:oid:1.2.392.200119.4.403.1")
(def iyaku-system "urn:oid:1.2.392.100495.20.2.74")
(def hoken-system "urn:oid:1.2.392.200119.4.204")

(defn- tail [did]
  (last (clojure.string/split did #":")))

(defn- condition-status [outcome]
  (get {"治癒" "resolved" "軽快" "remission" "中止" "inactive" "死亡" "inactive"} outcome "active"))

(defn to-fhir-bundle [karte rez & {:keys [claim-id] :or {claim-id "rezept-1"}}]
  (let [patient-ref {"reference" (str "Patient/" (tail (get-in karte [:patient :pseudonym-did])))}
        entries (atom [])]

    ;; Coverage
    (swap! entries conj {"resource" {"resourceType" "Coverage" "id" "coverage-1" "status" "active"
                                      "beneficiary" patient-ref
                                      "payor" [{"identifier" {"system" hoken-system "value" (get-in karte [:insurance :hokensha-bango])}}]
                                      "extension" [{"url" "https://iryo.etzhayyim.com/fhir/futanWari"
                                                    "valueDecimal" (get-in karte [:insurance :futan-wari])}]}})

    ;; Conditions
    (doseq [[i d] (map-indexed vector (:diagnoses karte))]
      (swap! entries conj {"resource" {"resourceType" "Condition" "id" (str "condition-" (inc i))
                                        "subject" patient-ref
                                        "clinicalStatus" {"coding" [{"code" (condition-status (:outcome d))}]}
                                        "code" {"coding" [{"system" icd10-jp-system "code" (:icd10 d) "display" (:name d)}]}
                                        "onsetString" (or (:onset d) "")}}))

    ;; Claim
    (let [items (mapv (fn [[n line]]
                        (let [sys (if (= (:kind line) "drug") iyaku-system shinryo-system)]
                          {"sequence" n
                           "productOrService" {"coding" [{"system" sys "code" (:code line) "display" (:name line)}]}
                           "quantity" {"value" (:count line)}
                           "unitPrice" {"value" (:unit-ten line) "unit" "点"}
                           "net" {"value" (:ten line) "unit" "点"}
                           "category" {"text" (:kubun line)}}))
                      (map-indexed #(vector (inc %1) %2) (:lines rez)))]
      (swap! entries conj {"resource" {"resourceType" "Claim" "id" claim-id "status" "active"
                                        "type" {"coding" [{"code" "institutional"}]}
                                        "use" "claim"
                                        "patient" patient-ref
                                        "insurance" [{"sequence" 1 "focal" true "coverage" {"reference" "Coverage/coverage-1"}}]
                                        "item" items
                                        "total" {"value" (:total-ten rez) "unit" "点"}
                                        "extension" [{"url" "https://iryo.etzhayyim.com/fhir/totalIryohiYen"
                                                      "valueInteger" (:total-iryohi-yen rez)}
                                                     {"url" "https://iryo.etzhayyim.com/fhir/patientPayYen"
                                                      "valueInteger" (:patient-pay-yen rez)}
                                                     {"url" "https://iryo.etzhayyim.com/fhir/kogakuApplied"
                                                      "valueBoolean" (:kogaku-applied rez)}]}}))

    {"resourceType" "Bundle" "type" "collection" "entry" @entries}))
