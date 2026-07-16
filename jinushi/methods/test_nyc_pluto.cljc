(ns jinushi.methods.test-nyc-pluto
  "jinushi 地主 — NYC PLUTO government-cadastre ingest tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.nyc-pluto :as p]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def snap-file (io/file repo-root "80-data" "jinushi-land" "nyc-pluto-parcels.kotoba.edn"))
(defn snap [] (clojure.edn/read-string (slurp snap-file)))

(deftest test-org-heuristic
  (is (p/org? "3217 HOLDING CORP"))
  (is (p/org? "WILCOX RESTORATION CORP"))
  (is (p/org? "NYC HOUSING AUTHORITY"))
  (is (p/org? "413-415-417 E. 81 REALTY LLC"))
  (is (not (p/org? "RODRIGUEZ, JOSE R")))
  (is (not (p/org? "BROPHY ANNA"))))

(deftest test-owner-key-anonymizes-persons
  (let [pk (p/owner-key "RODRIGUEZ, JOSE R" false)
        ok (p/owner-key "3217 HOLDING CORP" true)]
    (is (str/starts-with? pk "np.") "natural person → anonymized np.<hash> key")
    (is (not (str/includes? pk "RODRIGUEZ")) "person name NOT in the key")
    (is (str/starts-with? ok "org.") "legal entity → named org key")
    (is (= pk (p/owner-key "rodriguez, jose r" false)) "person key stable (case-insensitive)")))

(deftest test-snapshot-publish-prudence
  ;; committed artifact: legal entities named, natural persons carry NO name (publish prudence).
  (let [recs (:records (snap))
        persons (filter #(= :natural-person (:owner/type %)) recs)
        orgs (filter #(= :org (:owner/type %)) recs)]
    (is (pos? (count persons)) "natural-person parcels ingested (US-NY gate-permitted)")
    (is (every? #(nil? (:owner/name %)) persons) "NO natural-person names in the committed artifact")
    (is (every? #(str/starts-with? (:owner/key %) "np.") persons) "persons anonymized by key")
    (is (some :owner/name orgs) "legal entities ARE named (corporate accountability)")))

(deftest test-no-coordinates-g1
  (let [recs (:records (snap))]
    (is (not-any? #(or (:lat %) (:lon %) (:latitude %) (:parcel/centroid %)) recs)
        "G1: no precise dwelling coordinate ingested")
    (is (every? :parcel/id recs) "every parcel keyed by its public BBL")))

(deftest test-datoms
  (let [recs (:records (snap)) o (p/datoms recs 1)]
    (is (re-find #":parcel/owner :owner\.org\." o) "named legal-entity owner edge")
    (is (re-find #":parcel/owner :owner\.np\." o) "anonymized natural-person owner edge")
    (is (not (re-find #":owner\.np\.[0-9a-f]+ :owner/name" o)) "no name datom for anonymized persons")))

(deftest test-analyze
  (let [a (p/analyze* (:records (snap)))]
    (is (>= (:parcels a) 1000) "sample size")
    (is (pos? (get-in a [:owner-types :natural-person])) "natural persons counted")
    (is (pos? (get-in a [:owner-types :org])) "orgs counted")
    (is (seq (:top-org-owners a)) "top org owners ranked + named")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-nyc-pluto)]
    (System/exit (+ (or fail 0) (or error 0)))))
