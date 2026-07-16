(ns shomei.methods.revoke
  "revoke.cljc — 証明 (shomei) append-only binding revocation. ADR-2606072100.
  1:1 Clojure port of `methods/revoke.py`.

  G5 consent-bound + revocable: only the subject unlinks their own factor. G10 + Tier-0
  永久記憶: a revocation is an APPEND-ONLY retraction, NEVER a deletion — the original claim's
  history is permanently retained. Aggregation recomputes assurance EXCLUDING revoked claims;
  the as-of record that the binding once existed never disappears."
  (:require [clojure.string :as str]
            [shomei.methods.factors :as f]))

(defn validate-revocation
  "Structural gate for a bindingRevocation. Raises ex-info on violation."
  ([rev] (validate-revocation rev nil))
  ([rev claim]
   (let [required ["subjectDid" "claimRef" "factorKind" "reason" "revokedAt" "subjectSig"]
         missing (vec (remove #(contains? rev %) required))]
     (when (seq missing)
       (throw (ex-info (str "bindingRevocation missing required field(s): " missing) {}))))
   (when-not (and (string? (get rev "subjectDid")) (str/starts-with? (get rev "subjectDid") "did:"))
     (throw (ex-info "revocation subjectDid must be a DID" {})))
   (when-not (contains? f/FACTOR_KINDS (get rev "factorKind"))
     (throw (ex-info (str "unknown factorKind: " (pr-str (get rev "factorKind"))) {})))
   (when-not (contains? f/REVOCATION_REASONS (get rev "reason"))
     (throw (ex-info (str "unknown revocation reason: " (pr-str (get rev "reason"))) {})))
   (when-not (integer? (get rev "revokedAt"))
     (throw (ex-info "revokedAt must be an integer unix timestamp" {})))
   (when-not (and (string? (get rev "subjectSig")) (seq (get rev "subjectSig")))
     (throw (ex-info "G7: subjectSig (subject-signed) is mandatory on a revocation" {})))
   ;; G5 — only the binding's owner may revoke it.
   (when (and (some? claim) (not= (get claim "subjectDid") (get rev "subjectDid")))
     (throw (ex-info "G5: revocation subjectDid must equal the claim's subjectDid (only the owner revokes)" {})))
   rev))

(defn active-verified-factors
  "The set of factorKinds with ≥1 verified, NON-revoked claim (append-only as-of semantics).

  A claim is inactive iff a revocation references it by claimRef (or, lacking a claimRef link,
  matches subjectDid+factorKind with revokedAt ≥ the claim's issuedAt)."
  [claims revocations]
  (let [revoked-refs (set (keep #(get % "claimRef") revocations))
        revoked-kinds (set (map (fn [r] [(get r "subjectDid") (get r "factorKind") (long (get r "revokedAt"))])
                                revocations))]
    (reduce
      (fn [out c]
        (if-not (get c "verified")
          out
          (let [ref (or (get c "cid") (get c "claimRef"))]
            (cond
              (and (some? ref) (contains? revoked-refs ref)) out
              (some (fn [[sd fk ra]]
                      (and (= sd (get c "subjectDid"))
                           (= fk (get c "factorKind"))
                           (>= ra (long (get c "issuedAt")))))
                    revoked-kinds) out
              :else (conj out (get c "factorKind"))))))
      #{} claims)))
