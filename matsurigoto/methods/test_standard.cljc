(ns matsurigoto.methods.test-standard
  "Tests for the COFOG-based e-gov service standard (matsurigoto 政, ADR-2606062300).
  1:1 Clojure port of methods/test_standard.py. Auto-discovered by etzhayyim.tools.discovery.

  Keywords stay ':…' STRINGS (root CLAUDE.md convention), so seed records key on string
  attrs exactly as the Python tests do."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [matsurigoto.methods.standard :as S]))

;; resolve the actor data dir from this test file's location (file I/O at the #?(:clj) edge)
(def ^:private here
  #?(:clj (-> *file* clojure.java.io/file .getParentFile .getParentFile)
     :cljs nil))

(def DOC
  #?(:clj (S/load-standard (clojure.java.io/file here "data" "cofog-standard.kotoba.edn")
                           (clojure.java.io/file here "data" "profiles"))
     :cljs nil))

(deftest test-standard-loads-as-map
  (is (map? DOC))
  (is (= (get-in DOC [":standard" ":standard/id"]) "egov-cofog-standard")))

(deftest test-cofog-backbone-has-10-divisions
  (let [divs (filter #(= (get % ":cofog/level") ":division") (get DOC ":cofog"))]
    (is (= (count divs) 10))
    (let [codes (set (map #(get % ":cofog/code") divs))]
      (is (= codes (set (map #(format "%02d" %) (range 1 11))))))))

(deftest test-cofog-groups-reference-existing-division
  (let [codes (set (map #(get % ":cofog/code") (get DOC ":cofog")))]
    (doseq [r (get DOC ":cofog")]
      (when (= (get r ":cofog/level") ":group")
        (is (contains? codes (get r ":cofog/parent")) (get r ":cofog/code"))))))

(deftest test-validate-passes-clean
  (let [errors (S/validate DOC)]
    (is (= errors []) errors)))

(deftest test-every-service-maps-to-valid-cofog-class
  (let [cofog (S/cofog-index DOC)]
    (doseq [s (get DOC ":services")]
      (is (contains? cofog (get s ":egov.service/cofog")) (get s ":egov.service/id")))))

(deftest test-every-service-has-known-module
  (let [mods (S/module-index DOC)]
    (doseq [s (get DOC ":services")]
      (is (contains? mods (get s ":egov.service/module")) (get s ":egov.service/id")))))

(deftest test-every-service-is-spec-derived-g2
  ;; G2: spec-derived-only — non-empty official spec basis on every service.
  (doseq [s (get DOC ":services")]
    (let [specs (or (get s ":egov.service/spec-basis") [])]
      (is (>= (count specs) 1) (get s ":egov.service/id")))))

(deftest test-every-service-carries-the-universal-invariants
  ;; G1 no-operator-master-key + G2 spec-derived, structurally on every service.
  (doseq [s (get DOC ":services")]
    (let [inv (get s ":egov.service/invariants")]
      (is (= (get inv ":server-held-authority") false) (get s ":egov.service/id"))  ; G1
      (is (= (get inv ":spec-derived") true) (get s ":egov.service/id")))))          ; G2

(deftest test-etzhayyim-is-a-government-polity-profile-present
  ;; The correction: etzhayyim IS a government (Kingdom of God) with a 統治機構.
  ;; The polity profile must exist, be Council-governed in sovereign mode, and bind
  ;; COFOG functions onto etzhayyim's OWN constitutional organs.
  (let [polities (get DOC ":polity-profiles")
        et (first (filter #(= (get % ":polity-profile/id") "etzhayyim") polities))]
    (is (= (get et ":polity-profile/operated-by") ":etzhayyim-council"))
    (is (= (get et ":polity-profile/authority-mode") ":sovereign-governance"))
    (let [service-ids (set (map #(get % ":egov.service/id") (get DOC ":services")))]
      (doseq [b (get et ":polity-profile/bindings")]
        (is (contains? service-ids (get b ":bind/service")) (get b ":bind/service"))
        (is (get b ":bind/organ"))        ; a real etzhayyim constitutional organ
        (is (get b ":bind/legal-basis"))  ; Charter / ADR basis
        (is (get b ":bind/spec"))))))

(deftest test-both-principals-declared
  (let [principals (set (map #(get % ":principal/id")
                             (get-in DOC [":standard" ":standard/principals"])))]
    (is (= principals #{":etzhayyim-sovereign" ":nation-state-adopter"}))))

(deftest test-authority-is-borne-not-disclaimed
  ;; G3: every profile names a legitimate authority — the Kingdom via Council, a state via itself.
  (doseq [p (get DOC ":polity-profiles")]
    (is (contains? S/allowed-operated-by (get p ":polity-profile/operated-by")))
    (is (contains? S/allowed-authority-mode (get p ":polity-profile/authority-mode"))))
  (doseq [p (get DOC ":country-profiles")]
    (is (contains? S/allowed-operated-by (get p ":country-profile/operated-by")))
    (is (contains? S/allowed-authority-mode (get p ":country-profile/authority-mode")))))

(deftest test-named-domains-all-covered
  (let [domains (set (map #(get % ":egov.service/domain") (get DOC ":services")))]
    (doseq [required S/required-domains]
      (is (contains? domains required) (str "missing named domain " required)))))

(def ^:private valid-maturity #{":standard-draft" ":planned" ":reference-impl"})

(deftest test-no-service-is-live-executable-at-r0
  ;; Honest R0: a service is at most :reference-impl (runs in conformance tests, NOT wired to
  ;; a live government record). :executable (live) requires Council+operator gating.
  (doseq [s (get DOC ":services")]
    (is (contains? valid-maturity (get s ":egov.service/maturity")) (get s ":egov.service/id"))
    (is (not= (get s ":egov.service/maturity") ":executable") (get s ":egov.service/id"))))

;; DEFERRED: test_tax_assess_reference_impl_is_wired_and_correct — depends on the Python
;; module methods/modules/tax_assess.py (assess_income_tax + SERVER_HELD_AUTHORITY), which is
;; an ingest/module dependency not in scope for this standard.cljc port. The standard-side
;; half of that assertion (the three tax services are :reference-impl) is checked here.
(deftest test-tax-assess-backed-services-are-reference-impl
  (let [tax-services (into {} (for [s (get DOC ":services")
                                    :when (= (get s ":egov.service/module") "tax-assess")]
                                [(get s ":egov.service/id") s]))]
    (doseq [sid ["tax.income.file" "tax.corporate.file" "tax.vat.file"]]
      (is (= (get-in tax-services [sid ":egov.service/maturity"]) ":reference-impl") sid))))

(deftest test-jp-profile-binds-each-service-to-agency-legal-basis-and-spec
  (let [profiles (get DOC ":country-profiles")
        jp (first (filter #(= (get % ":country-profile/iso3") "JPN") profiles))
        service-ids (set (map #(get % ":egov.service/id") (get DOC ":services")))]
    (doseq [b (get jp ":country-profile/bindings")]
      (is (contains? service-ids (get b ":bind/service")) (get b ":bind/service"))
      (is (get b ":bind/agency"))
      (is (get b ":bind/legal-basis"))
      (is (get b ":bind/national-spec"))
      ;; links back to the ooyake observation atlas (read-side who/where)
      (is (str/starts-with? (get b ":bind/atlas-did") "did:web:etzhayyim.com:gov:")))))

(deftest test-country-profiles-are-sourcing-honest
  ;; No profile may claim :authoritative coverage at R0 (ooyake G5 precedent).
  (doseq [p (get DOC ":country-profiles")]
    (is (= (get p ":country-profile/sourcing") ":representative") (get p ":country-profile/iso3"))))

(deftest test-multiple-countries-loaded-from-profiles-dir
  ;; 各国ように調整 — per-country profiles load from data/profiles/*.edn and merge.
  (let [iso3s (set (map #(get % ":country-profile/iso3") (get DOC ":country-profiles")))]
    (doseq [expect #{"JPN" "USA" "DEU" "GBR" "KOR" "EST" "IND" "EUR"}]
      (is (contains? iso3s expect) (str "missing country profile " expect)))))

(deftest test-every-country-binding-targets-a-known-service-with-full-localization
  (let [service-ids (set (map #(get % ":egov.service/id") (get DOC ":services")))]
    (doseq [p (get DOC ":country-profiles")]
      (is (= (get p ":country-profile/operated-by") ":adopting-government") (get p ":country-profile/iso3"))
      (is (= (get p ":country-profile/authority-mode") ":supplied-to-state") (get p ":country-profile/iso3"))
      (is (seq (get p ":country-profile/bindings")) (str (get p ":country-profile/iso3") " has no bindings"))
      (doseq [b (get p ":country-profile/bindings")]
        (is (contains? service-ids (get b ":bind/service")) (get b ":bind/service"))
        (is (get b ":bind/agency"))
        (is (get b ":bind/legal-basis"))
        (is (get b ":bind/national-spec"))))))

(deftest test-coverage-report-renders
  (let [cov (S/coverage DOC)]
    (is (= (get cov "divisions_total") 10))
    (is (>= (get cov "services_total") 15))
    (let [report (S/render-report DOC cov (S/validate DOC))]
      (is (str/includes? (str/lower-case report) "coverage"))
      (is (str/includes? report "COFOG")))))
