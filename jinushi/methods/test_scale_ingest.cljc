(ns jinushi.methods.test-scale-ingest
  "jinushi 地主 — production-scale streaming ingest tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [jinushi.methods.scale-ingest :as si]
            [jinushi.methods.dvf-values :as dvf]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def dvf-file (io/file repo-root "80-data" "jinushi-land" "fr-dvf-75105.raw.csv"))

(deftest test-streaming-dvf-matches-in-memory
  ;; the bounded-memory stream must produce the SAME aggregates as the sample-scale in-memory path
  (let [s (si/dvf-stream-file dvf-file)
        m (dvf/analyze* (dvf/parse-csv (slurp dvf-file)))]
    (is (= (:mutations s) (:mutations m)) "mutation count identical")
    (is (= (:total-value-eur s) (:total-value-eur m)) "total value identical")
    (is (= (:appt-median-eur-m2 (get (:by-commune s) "75105"))
           (:appt-median-eur-m2 (get (:by-commune m) "75105"))) "apartment €/m² identical")))

(deftest test-pluto-streaming-anonymizes-persons
  ;; at 860k scale, person names must be anonymized ON THE FLY — never accumulated/returned
  (let [rows [{:ownername "RODRIGUEZ, JOSE R" :numfloors "2"}
              {:ownername "3217 HOLDING CORP" :numfloors "3"}
              {:ownername "BROPHY ANNA" :numfloors "1"}]
        acc (reduce si/pluto-aggregate-step {} rows)
        fin (si/pluto-finalize acc)]
    (is (= 2 (get-in fin [:owner-types :natural-person])) "two natural persons counted")
    (is (= 1 (get-in fin [:owner-types :org])) "one org counted")
    (is (= 3 (:parcels fin)))
    ;; org named, persons NOT named anywhere in the accumulator
    (is (= "3217 HOLDING CORP" (:name (first (:top-org-owners fin)))) "org named")
    (is (not-any? #(and (= :natural-person (:type (val %))) (:name (val %))) (:owners acc))
        "no natural-person name retained in the streaming accumulator")))

(deftest test-pluto-row->owner-no-plaintext-person
  (let [p (si/pluto-row->owner {:ownername "SMITH, JOHN" :numfloors "2"})]
    (is (= :natural-person (:type p)))
    (is (nil? (:name p)) "person name dropped (only sha256 key)")
    (is (clojure.string/starts-with? (:key p) "np."))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-scale-ingest)]
    (System/exit (+ (or fail 0) (or error 0)))))
