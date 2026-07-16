(ns iryo.methods.karte
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def phi-fields
  #{"name" "kana" "dob" "birthdate" "address" "phone" "email"
    "soap_s" "soap_o" "soap_a" "soap_p" "free_text" "note" "mrn"})

(defn phi-leak! [msg]
  (throw (ex-info msg {:error :phi-leak})))

(defn make-patient [pseudonym-did sex birth-year encrypted-payload-cid]
  {:pseudonym-did pseudonym-did :sex (or sex "U") :birth-year birth-year
   :encrypted-payload-cid encrypted-payload-cid})

(defn age-on [patient on-date]
  (when (:birth-year patient)
    (- (.getYear on-date) (:birth-year patient))))

(defn make-insurance [hokensha-bango futan-wari honnin-kazoku kogaku-kubun kohi]
  {:hokensha-bango hokensha-bango :futan-wari (or futan-wari 0.3)
   :honnin-kazoku (or honnin-kazoku "honnin") :kogaku-kubun kogaku-kubun :kohi (or kohi [])})

(defn make-diagnosis [shobyo-code icd10 name onset outcome is-main]
  {:shobyo-code shobyo-code :icd10 icd10 :name name :onset onset
   :outcome (or outcome "継続") :is-main (boolean is-main)})

(defn make-soap-note [encounter-date author-did encrypted-cid]
  (when (str/blank? encrypted-cid)
    (phi-leak! "SoapNote requires encrypted_cid (SOAP free-text is PHI)"))
  {:encounter-date encounter-date :author-did author-did :encrypted-cid encrypted-cid})

(defn make-karte [patient insurance diagnoses notes]
  {:patient patient :insurance insurance :diagnoses (or diagnoses []) :notes (or notes [])})

(defn public-meta [karte]
  {:patient-did (get-in karte [:patient :pseudonym-did])
   :sex (get-in karte [:patient :sex])
   :hokensha-bango (get-in karte [:insurance :hokensha-bango])
   :futan-wari (get-in karte [:insurance :futan-wari])
   :diagnoses (mapv (fn [d] {:shobyo-code (:shobyo-code d) :icd10 (:icd10 d)
                              :is-main (:is-main d) :outcome (:outcome d) :onset (:onset d)})
                    (:diagnoses karte))
   :note-count (count (:notes karte))
   :encrypted-payload-cid (get-in karte [:patient :encrypted-payload-cid])})

(defn assert-no-phi! [meta]
  (doseq [[k _] meta]
    (when (contains? phi-fields (str/lower-case (name k)))
      (phi-leak! (str "plaintext PHI field in public meta: " k)))
    (when (= k :diagnoses)
      (doseq [d (get meta k)]
        (doseq [[dk _] d]
          (when (contains? phi-fields (str/lower-case (name dk)))
            (phi-leak! (str "plaintext PHI field in diagnosis: " dk))))))))

(defn- sha256-hex [s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn rotating-pseudonym-did [stable-secret period]
  (let [h (sha256-hex (str stable-secret "|" period))]
    (str "did:web:patient.iryo.etzhayyim.com:" (subs h 0 32))))
