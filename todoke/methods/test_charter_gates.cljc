(ns todoke.methods.test-charter-gates
  "todoke 届け — constitutional-gate conformance tests (manifest + local lexicons).

  Substrate-native Clojure (clj + datomic first tier). todoke is last-mile (one-mile) autonomous
  delivery, curb-to-door ≤25kg on a SAE-L4 sidewalk envelope. It reads the first-tier `lex/*.edn`
  via clojure.edn and the manifest via cheshire. Its 15 gates are declared in the manifest and
  encoded as enum/const across the 5 lexicons. This suite pins them so a future R-phase cell wave
  cannot silently drift them.

  NOTE (Phase 4 edn-datomize, 2026-07-10): `lex/*.edn` files are now Datomic/Datascript
  tx-data (`[{:db/id -1 :<ns>/lexicon 1 :<ns>/id \"...\" :<ns>/defs \"<pr-str blob>\"}]`)
  so they stay directly queryable. `lex` below reconstitutes the original bare
  `{:lexicon .. :id .. :defs ..}` map shape (unblobbing the pr-str'd nested `:defs`) so every
  downstream helper (`record-node` / `required-of` / `enum-of` / `const-of`) is unchanged.
  Still tolerates the pre-transform bare-map shape for safety/idempotency.

    G14 no armed platform / no contraband — deliveryJob.armed const false; payloadClass is a
        bounded benign set (the headline Charter-Rider §2(a) gate)
    G5  no-gig — deliveryJob.gig const false
    G7  SAE-L4 envelope — lastMileRoute requires saeLevel + envelopeOk; saeWithinCeiling const
        true; safetyAlert can refuse sae-level-too-high / zone-outside-odd / speed-exceeds-cap
    G8/G12 privacy-by-construction + no-server-key — handoffProof.onDeviceOnly const true,
        serverSigned const false, proofKind ∈ on-device kinds only (no cloud imagery/biometric)
    G13 consent-bound — every delivery + hand-off requires a consentRef
    G2  displacement-dividend coupling — mission record carries a displacementCohortRef

  Reads manifest via cheshire + local lexicons via clojure.edn. It weakens no gate; it asserts
  them. G12 IS the substrate no-server-key invariant for this actor; Murakumo-only is manifest G4."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; todoke/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- unblob [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))
     (defn- reconstitute-entity [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))
     (defn- lex [name]
       (let [content (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))]
         (if (and (vector? content) (map? (first content)) (contains? (first content) :db/id))
           (reconstitute-entity content)
           content)))
     (defn- manifest []
  (let [e (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))
        gm (into {} (map (fn [g] [(:gate/id g) g]) (:actor/gates e)))]
    {"constitutionalGates" {"gates" gm}
     "gates" gm
     "nonGoals" (:actor/non-goals e)
     "cells" (:actor/cells e)
     "name" (:actor/id e)
     "purpose" (:actor/purpose e)
     "tier" "Tier-B"
     "status" (some-> (:actor/status e) name)}))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- prop-names [doc] (set (map name (keys (:properties (record-node doc))))))
(defn- enum-of [doc field] (set (get-in (record-node doc) [:properties field :enum])))
(defn- const-of [doc field] (get-in (record-node doc) [:properties field :const]))

;; ── 15 gates + non-goals declared ──
(deftest gates-and-nongoals-declared
  (let [cg (get (manifest) "constitutionalGates")
        gm (or (get cg "gates") cg)
        gn (->> (keys gm) (keep #(second (re-matches #"G(\d+).*" %)))
                (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 16)) gn) "manifest must declare G1–G15")
    (is (contains? (manifest) "nonGoals") "manifest must declare nonGoals")))

;; ── G14 no armed/contraband + G5 no-gig ──
(deftest g14-no-armed-g5-no-gig
  (let [d (lex "deliveryJob")]
    (is (= false (const-of d :armed)) "G14: deliveryJob.armed const false")
    (is (= false (const-of d :gig)) "G5: deliveryJob.gig const false")
    (is (= #{"parcel" "groceries" "documents" "meal" "returns"} (enum-of d :payloadClass))
        "G14: payloadClass is a bounded benign set (no weapon/contraband class)")))

;; ── G7 — SAE-L4 envelope, refusal-by-construction ──
(deftest g7-sae-l4-envelope
  (let [r (lex "lastMileRoute")]
    (is (every? (required-of r) ["saeLevel" "envelopeOk"]) "G7: route must record saeLevel + envelopeOk")
    (is (= true (const-of r :saeWithinCeiling)) "G7: saeWithinCeiling const true"))
  (is (set/subset? #{"sae-level-too-high" "zone-outside-odd" "speed-exceeds-zone-cap"}
                   (enum-of (lex "safetyAlert") :kind))
      "G7: safetyAlert can refuse SAE-level / zone / speed-cap violations"))

;; ── G8 privacy-by-construction + G12 no-server-key ──
(deftest g8-g12-privacy-no-server-key
  (let [h (lex "handoffProof")]
    (is (= true (const-of h :onDeviceOnly)) "G8: handoffProof.onDeviceOnly const true")
    (is (= false (const-of h :serverSigned)) "G12: serverSigned const false (recipient signs)")
    (is (= #{"recipient-signature" "locker-code" "on-device-photo-hash"} (enum-of h :proofKind))
        "G8: proofKind is on-device kinds only (no cloud imagery/biometric)")))

;; ── G13 — consent-bound ──
(deftest g13-consent-bound
  (doseq [n ["deliveryJob" "handoffProof"]]
    (is (contains? (required-of (lex n)) "consentRef")
        (str "G13: " n " must require a consentRef"))))

;; ── G2 — displacement-dividend coupling ──
(deftest g2-displacement-coupling
  (is (contains? (prop-names (lex "missionCompleteRecord")) "displacementCohortRef")
      "G2: mission record carries a displacementCohortRef (funded-cohort coupling)"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'todoke.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
