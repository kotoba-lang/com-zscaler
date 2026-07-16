(ns kazaori.methods.test-emergency
  "Conformance tests for the kazaori emergency-response engine."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]
            [kazaori.methods.emergency :as E]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/kazaori"))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── drift guard: the engine's hardcoded enums must match the Lexicons exactly ──
(deftest test-engine-enums-match-lexicons-exactly
  (is (= (known (lex "emergencyDeclarationAttestation") "declarationCategory")
         #{"earthquake" "typhoon-hurricane" "flood" "wildfire" "pandemic-biological"
           "power-outage-extended" "water-shortage" "food-shortage" "building-damage-mass"
           "civil-unrest-non-military" "other"}))
  (is (= (known (lex "evacuationCheckIn") "checkInMethod")
         #{"self-mobile-app" "self-paper-form-attested" "self-phone-call-attested"
           "family-member-on-behalf-with-prior-consent" "guardian-on-behalf-for-minor-or-incapacitated"})
      "G6: surveillance-derived check-in methods must never appear here")
  (is (= (known (lex "emergencySupplyDispatch") "dispatchCategory")
         #{"potable-water-closed-loop-container" "potable-water-single-use-carve-out"
           "food-reserve-stock" "food-mutual-aid" "shelter-material" "medical-supply"
           "power-battery-redirection" "other"}))
  (is (= (known (lex "emergencySupplyDispatch") "supplySource")
         #{"mizuho-water-source" "mitsuho-reserve-stock" "mitsuho-mutual-aid" "iyashi-stockpile"
           "yakushi-stockpile" "hikari-grid-edge-battery" "tatekata-shelter-material"
           "external-donation-attested"}))
  (is (= (known (lex "emergencySupplyDispatch") "unitCode")
         #{"liters" "kg" "meal-units" "kwh" "shelter-spaces" "medical-doses"})))

;; ── declare-emergency: G5/G8/G10 ──
(deftest test-declare-emergency-computes-auto-lift-and-pins-civilian-only
  (let [d (E/declare-emergency
           {:created-at "2026-07-06T00:00:00Z" :declaration-category "flood"
            :declared-scope [{"scopeKind" "community-site" "communityCellSiteId" "site-1"}]
            :declared-start-utc "2026-07-06T00:00:00Z" :initial-duration-days 60
            :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test" "did:web:cl4.test"]
            :attesting-did "did:web:kazaori.etzhayyim.com"})]
    (is (= (get d "autoLiftAtUtc") "2026-09-04T00:00:00Z"))
    (is (= (get d "civilianOnlyAttested") true))))

(deftest test-declare-emergency-rejects-fewer-than-4-council-attestations
  (is (thrown? Exception
               (E/declare-emergency
                {:created-at "2026-07-06T00:00:00Z" :declaration-category "flood"
                 :declared-scope [{"scopeKind" "community-site"}]
                 :declared-start-utc "2026-07-06T00:00:00Z" :initial-duration-days 60
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]
                 :attesting-did "did:web:kazaori.etzhayyim.com"}))))

(deftest test-declare-emergency-rejects-duration-over-60-days
  (is (thrown? Exception
               (E/declare-emergency
                {:created-at "2026-07-06T00:00:00Z" :declaration-category "flood"
                 :declared-scope [{"scopeKind" "community-site"}]
                 :declared-start-utc "2026-07-06T00:00:00Z" :initial-duration-days 61
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test" "did:web:cl4.test"]
                 :attesting-did "did:web:kazaori.etzhayyim.com"}))))

;; ── activate-carve-out: G8 — cannot outlive the parent emergency ──
(deftest test-carve-out-cannot-outlive-emergency
  (is (thrown? Exception
               (E/activate-carve-out
                {:created-at "2026-07-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
                 :emergency-auto-lift-at-utc "2026-09-04T00:00:00Z"
                 :gate-carved "mizuho.G5-single-use-container"
                 :carve-out-justification-cid "bafy...justification"
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test" "did:web:cl4.test"]
                 :activated-at-utc "2026-07-10T00:00:00Z"
                 :auto-revoke-at-utc "2026-09-10T00:00:00Z"      ; AFTER the emergency's own auto-lift
                 :attesting-did "did:web:kazaori.etzhayyim.com"}))))

(deftest test-carve-out-happy-path
  (let [c (E/activate-carve-out
           {:created-at "2026-07-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
            :emergency-auto-lift-at-utc "2026-09-04T00:00:00Z"
            :gate-carved "mizuho.G5-single-use-container"
            :carve-out-justification-cid "bafy...justification"
            :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test" "did:web:cl4.test"]
            :activated-at-utc "2026-07-10T00:00:00Z"
            :auto-revoke-at-utc "2026-08-01T00:00:00Z"
            :attesting-did "did:web:kazaori.etzhayyim.com"})]
    (is (= (get c "gateCarved") "mizuho.G5-single-use-container"))))

;; ── dispatch-supply ──
(deftest test-dispatch-supply-rejects-unknown-category
  (is (thrown? Exception
               (E/dispatch-supply
                {:created-at "2026-07-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
                 :dispatch-category "not-a-real-category" :dispatch-destination-cid "bafy...site"
                 :supply-source "mizuho-water-source" :quantity-integer 500 :unit-code "liters"
                 :attesting-cell-did "did:web:kazaori.etzhayyim.com"}))))

;; ── record-evacuation-checkin: G6 ──
(deftest test-checkin-rejects-missing-encryption-or-signature
  (is (thrown? Exception
               (E/record-evacuation-checkin
                {:created-at "2026-07-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
                 :check-in-pseudonym-did "did:web:pseudo.test" :safe-site-cid "bafy...site"
                 :encrypted-payload-cid "" :member-signature "sig..." :check-in-method "self-mobile-app"}))))

(deftest test-checkin-rejects-surveillance-derived-method
  (is (thrown? Exception
               (E/record-evacuation-checkin
                {:created-at "2026-07-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
                 :check-in-pseudonym-did "did:web:pseudo.test" :safe-site-cid "bafy...site"
                 :encrypted-payload-cid "bafy...payload" :member-signature "sig..."
                 :check-in-method "facial-recognition-match"}))))

;; ── silen-kazaori-review: G9 + timeliness ──
(def ^:private sphere-ok
  {"shelterAttested" true "waterAttested" true "foodAttested" true
   "healthAttested" true "protectionAttested" true})

(deftest test-review-rejects-late-completion
  (is (thrown? Exception
               (E/silen-kazaori-review
                {:created-at "2026-12-10T00:00:00Z" :emergency-declaration-cid "bafy...decl"
                 :lifted-at-utc "2026-09-04T00:00:00Z" :completed-at-utc "2026-12-10T00:00:00Z"  ; 97 days later
                 :sphere-compliance sphere-ok :carve-out-audit-entries []
                 :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]}))))

(deftest test-review-happy-path
  (let [r (E/silen-kazaori-review
           {:created-at "2026-10-01T00:00:00Z" :emergency-declaration-cid "bafy...decl"
            :lifted-at-utc "2026-09-04T00:00:00Z" :completed-at-utc "2026-10-01T00:00:00Z"
            :sphere-compliance sphere-ok :carve-out-audit-entries []
            :council-attestations ["did:web:cl1.test" "did:web:cl2.test" "did:web:cl3.test"]})]
    (is (= (get r "surveillancePenetrationPct") 0))
    (is (= (get r "civilianOnlyCompliance") true))
    (is (= (get r "commercialDisasterMgmtSoftwarePenetrationPct") 0))
    (is (= (get r "reviewCompletedWithin90DaysOfLifting") true))))

(deftest test-solve-is-gated-at-r0
  (is (thrown? Exception (E/solve))))

(defn -main [& _] (run-tests 'kazaori.methods.test-emergency))
