(ns tsukuroi.methods.test-charter-gates
  "tsukuroi 繕い — constitutional-gate conformance tests (manifest + central lexicons).

  Substrate-native Clojure (clj + datomic first tier). tsukuroi is the authorized
  vulnerability-REMEDIATION patch-proposer (the defensive counterpart of akuma's probing): it
  PROPOSES defensive patches via fork-and-PR under a dual-signed mandate, and a HUMAN owner
  merges — it never probes, never exploits, never auto-merges, never holds a platform key. Its
  13 gates are declared in the manifest `constitutionalGates` and encoded as const fields across
  the 5 central AT-Proto lexicons at 00-contracts/lexicons/com/etzhayyim/tsukuroi/. This suite
  pins them so a future R-phase cell wave cannot silently drift them:

    G3  NO PROBING — vulnerability input ONLY via an akuma findingCid
    G4  PROPOSE-ONLY / NO AUTONOMOUS MERGE — patchProposal.autonomousMerge const false,
        remediationMandate.mergeAuthorityHeld const false, review autonomousMergeCount const 0
    G5  DEFENSIVE-ONLY / NO EXPLOIT — patchProposal.defensiveOnly const true,
        review exploitArtifactCount const 0
    G6  SCOPED WRITE — review outOfScopeWriteCount const 0; mandate fixes allowedPaths
    G7  dual-signature mandate (owner + authority DIDs + signatures)
    G8  NO PLATFORM-HELD KEY (ADR-2605231525) — review platformHeldKeyCount const 0;
        submission via an owner-issued delegation credential
    G9  sandbox validation — never the live target (ranAgainstLiveTarget const false,
        sandboxNamespace const tsukuroi-validate)
    G11 closure requires owner human merge AND akuma re-probe pass

  Reads central lexicons via cheshire (string keys). It weakens no gate; it asserts them.
  G8 IS the substrate-wide no-server-key invariant for this actor; G10 pins Murakumo-only."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; tsukuroi/
     (def ^:private root (.getParentFile (.getParentFile actor-dir)))          ;; repo root
     (def ^:private lexdir
       (java.io.File. root "00-contracts/lexicons/com/etzhayyim/tsukuroi"))
     (defn- lex [name]
       (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc]
  (let [main (get-in doc ["defs" "main"])]
    (or (get main "record") main)))
(defn- required-of [doc] (set (get (record-node doc) "required")))
(defn- const-of [doc field] (get-in (record-node doc) ["properties" field "const"]))

;; ── 13 gates declared (manifest dict, keys G1…G13) ──
(deftest all-13-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])
        gates (or gates (get (manifest) "constitutionalGates"))
        nums  (->> (keys gates)
                   (keep #(second (re-matches #"G(\d+).*" %)))
                   (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 14)) nums) "manifest must declare G1–G13")))

;; ── G4 — PROPOSE-ONLY / NO AUTONOMOUS MERGE ──
(deftest g4-propose-only-no-autonomous-merge
  (is (= false (const-of (lex "patchProposal") "autonomousMerge"))
      "G4: patchProposal.autonomousMerge const false")
  (is (= false (const-of (lex "remediationMandate") "mergeAuthorityHeld"))
      "G4: remediationMandate.mergeAuthorityHeld const false")
  (is (= 0 (const-of (lex "silenTsukuroiReview") "autonomousMergeCount"))
      "G4: review autonomousMergeCount const 0"))

;; ── G5 — DEFENSIVE-ONLY / NO EXPLOIT ──
(deftest g5-defensive-only-no-exploit
  (is (= true (const-of (lex "patchProposal") "defensiveOnly"))
      "G5: patchProposal.defensiveOnly const true")
  (is (= 0 (const-of (lex "silenTsukuroiReview") "exploitArtifactCount"))
      "G5: review exploitArtifactCount const 0"))

;; ── G6 — SCOPED WRITE: out-of-scope writes structurally zero; mandate fixes allowedPaths ──
(deftest g6-scoped-write
  (is (= 0 (const-of (lex "silenTsukuroiReview") "outOfScopeWriteCount"))
      "G6: review outOfScopeWriteCount const 0")
  (is (contains? (required-of (lex "remediationMandate")) "allowedPaths")
      "G6: mandate must fix allowedPaths")
  (is (contains? (required-of (lex "patchProposal")) "pathsTouched")
      "G6: a proposal must declare pathsTouched (⊆ allowedPaths)"))

;; ── G7 dual-signature mandate + G8 no platform-held key ──
(deftest g7-dual-sig-g8-no-platform-key
  (let [m (required-of (lex "remediationMandate"))]
    (doseq [f ["ownerDid" "ownerSignature" "authorityDid" "authoritySignature"]]
      (is (contains? m f) (str "G7: mandate must require " f " (dual-signature)"))))
  (is (= 0 (const-of (lex "silenTsukuroiReview") "platformHeldKeyCount"))
      "G8: review platformHeldKeyCount const 0 (no platform-held key, ADR-2605231525)"))

;; ── G9 — sandbox validation, NEVER the live target ──
(deftest g9-sandbox-not-live-target
  (is (= false (const-of (lex "patchValidationResult") "ranAgainstLiveTarget"))
      "G9: patchValidationResult.ranAgainstLiveTarget const false")
  (is (= "tsukuroi-validate" (const-of (lex "patchValidationResult") "sandboxNamespace"))
      "G9: validation runs in the egress-restricted tsukuroi-validate namespace"))

;; ── G3 — NO PROBING: vulnerability input only via an akuma findingCid ──
(deftest g3-no-probing-finding-input
  (doseq [n ["patchProposal" "remediationMandate"]]
    (is (contains? (required-of (lex n)) "findingCid")
        (str "G3: " n " must require a findingCid (input only via akuma, no self-probing)"))))

;; ── G11 — closure requires owner human merge AND akuma re-probe pass ──
(deftest g11-closure-owner-merge-and-reprobe
  (let [c (required-of (lex "closureAttestation"))]
    (is (contains? c "ownerMerged") "G11: closure requires ownerMerged (human merge)")
    (is (contains? c "akumaReprobePass") "G11: closure requires akumaReprobePass")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'tsukuroi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
