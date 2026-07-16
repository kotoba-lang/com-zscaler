(ns shomei.methods.analyze
  "analyze.cljc — 証明 (shomei) end-to-end identity-binding membrane (dry-run). ADR-2606072100.
  1:1 Clojure port of `methods/analyze.py`.

  Load the representative seed → for each member: issue challenge → mint a subject-signed
  identityClaim per possession (ReferenceVerifier-simulated) → verify (policy + crypto) →
  aggregate into a personhoodCredential + W3C VC. gov-* possessions hit the Council gate
  (the :shomei/gated ex-info) and are reported as gated, never silently verified.

  Self-sovereign (G1), own-identity-only (G2), no-PII (G3), proof-mandatory (G4), no-server-key
  (G7), identity-assurance-not-social-credit (G8).

  House style: the core (build/verify/aggregate) is pure; file I/O (seed read + report/JSON
  write) only at the #?(:clj) -main edge. Byte-parity target = out/identity-report.md +
  out/personhood-credentials.json (matches analyze.py)."
  (:require [clojure.string :as str]
            [shomei.methods.blake2b :as b2]
            [shomei.methods.claims :as claims]
            [shomei.methods.factors :as f]
            [shomei.methods.revoke :as revoke]
            [shomei.methods.aggregate :as agg]
            [shomei.methods.verify :as v]
            #?(:clj [shomei.methods.edn :as edn])))

;; ── HMAC-SHA256 reference token (analyze._ref_token) ────────────────────────────
(defn- ref-token [secret ^bytes msg]
  #?(:clj (#'v/hmac-sha256-hex secret msg)
     :cljs (#'v/hmac-sha256-hex secret msg)))

(defn- str-bytes [^String s]
  #?(:clj (.getBytes s "UTF-8") :cljs (vec (map #(.charCodeAt % 0) s))))

(defn mint
  "DRY-RUN mint of a subject-signed claim + its challenge + proof material (ReferenceVerifier).
  Returns {:challenge :claim :proof-material :secret}. In production the client signs this."
  [member p base-ts]
  (let [subject (get member "subjectDid")
        salt (get member "salt")
        nonce (str "nonce-"
                   (b2/hexdigest
                     (str-bytes (str subject (get p "factorKind") (get p "identifier"))) 12))
        challenge {"subjectDid" subject
                   "factorKind" (get p "factorKind")
                   "nonce" nonce
                   "issuedAt" base-ts
                   "expiresAt" (+ base-ts 600)}
        esh (claims/external-subject-hash salt (get p "identifier"))
        skeleton {"subjectDid" subject
                  "factorKind" (get p "factorKind")
                  "factorClass" (get f/FACTOR_CLASS (get p "factorKind"))
                  "proofKind" (get p "proofKind")
                  "challengeNonce" nonce
                  "externalSubjectHash" esh
                  "verified" true
                  "issuedAt" base-ts}
        subject-sig (ref-token (get p "secret") (claims/canonical-claim-bytes skeleton))
        claim (claims/build-claim
                {:subject-did subject
                 :factor-kind (get p "factorKind")
                 :proof-kind (get p "proofKind")
                 :challenge-nonce nonce
                 :external-subject-hash esh
                 :issued-at base-ts
                 :subject-sig subject-sig
                 :verified true
                 :external-handle (get p "handle")
                 :encrypted-payload-cid (get p "encryptedPayloadCid")})
        proof-material {"proof" (ref-token (get p "secret")
                                           (str-bytes (str nonce "|" esh)))}]
    {:challenge challenge :claim claim :proof-material proof-material :secret (get p "secret")}))

(defn run
  "Pure end-to-end over a parsed seed map. Returns {\"results\" [...]}."
  [seed]
  (let [base-ts (long (get seed "baseTs" 1781000000))
        results
        (mapv
          (fn [member]
            (let [subject (get member "subjectDid")
                  secrets (into {} (map (fn [p] [[subject (get p "factorKind")] (get p "secret")])
                                        (get member "possessions")))
                  verifier (v/reference-verifier secrets false)
                  seen (atom #{})
                  step (reduce
                         (fn [{:keys [verified gated failed] :as acc} p]
                           (let [{:keys [challenge claim proof-material]} (mint member p base-ts)
                                 res (try
                                       {:ok (v/verify-claim claim challenge verifier
                                                            :proof-material proof-material
                                                            :now (+ base-ts 10)
                                                            :seen-nonces seen)}
                                       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                                         (if (v/gated-error? e) {:gated e} (throw e))))]
                             (cond
                               (:gated res)
                               (let [msg (#?(:clj #(.getMessage ^Throwable %) :cljs ex-message) (:gated res))
                                     reason (first (str/split msg #";"))]
                                 (update acc :gated conj {"factorKind" (get p "factorKind") "reason" reason}))
                               (get (:ok res) "verified")
                               (let [claim* (assoc claim "cid" (str "claim:" (get claim "challengeNonce")))]
                                 (update acc :verified conj claim*))
                               :else
                               (update acc :failed conj {"factorKind" (get p "factorKind")
                                                         "reason" (get (:ok res) "reason")}))))
                         {:verified [] :gated [] :failed []}
                         (get member "possessions"))
                  active (revoke/active-verified-factors (:verified step) [])
                  cred (agg/aggregate subject active :issued-at base-ts)
                  vc (agg/to-w3c-vc subject cred)]
              {"subjectDid" subject
               "credential" cred
               "vc" vc
               "verifiedCount" (count (:verified step))
               "gated" (:gated step)
               "failed" (:failed step)}))
          (get seed "members"))]
    {"results" results}))

;; ── report (analyze._write_report) ─────────────────────────────────────────────
(defn report-md [results]
  (let [head ["# 証明 (shomei) — believer identity-binding report (dry-run)\n"
              (str "_Self-sovereign · own-identity-only · no-PII · proof-mandatory · no-server-key. "
                   "Identity ASSURANCE, never a social-credit score (G8)._\n")]
        body (reduce
               (fn [L r]
                 (let [c (get r "credential")
                       lvl (get c "assuranceLevel")
                       sect (str "\n## `" (get r "subjectDid") "`\n"
                                 "- **IAL " lvl " (" (agg/assurance-label lvl) ")** · "
                                 "proof-of-personhood=" (if (get c "proofOfPersonhood") "True" "False") " · "
                                 "factors=" (get c "factorCount") " across " (get c "distinctClasses") " class(es)\n"
                                 "- verified factors: "
                                 (let [vf (str/join ", " (get c "verifiedFactors"))]
                                   (if (seq vf) vf "(none)")) "\n"
                                 "- issuer: `" (get c "issuer") "` · subjectDidHash: `"
                                 (subs (get c "subjectDidHash") 0 16) "…`")
                       L (conj L sect)
                       L (if (seq (get r "gated"))
                           (conj L (str "- gated (Council R0, ADR-2605260000): "
                                        (str/join ", " (map #(get % "factorKind") (get r "gated")))))
                           L)
                       L (if (seq (get r "failed"))
                           (conj L (str "- failed: "
                                        (str/join ", " (map #(str (get % "factorKind") "(" (get % "reason") ")")
                                                            (get r "failed")))))
                           L)]
                   L))
               head results)]
    (str (str/join "\n" body) "\n")))

;; ── personhood-credentials.json (json.dumps([...vc...], indent=2)) ───────────────
(defn- json-escape [s]
  (-> (str s)
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\t" "\\t")
      (str/replace "\r" "\\r")))

(defn- omap-items
  "Ordered key/val pairs honouring ::order metadata (else map order)."
  [m]
  (if-let [order (::order (meta m))]
    (concat (keep (fn [k] (when (contains? m k) [k (get m k)])) order)
            (remove (fn [[k _]] (some #{k} order)) (seq m)))
    (seq m)))

(defn- json-indent
  "json.dumps(v, indent=2, ensure_ascii=False) — newline + 2-space-per-level, ', '/': ' → ',\\n'/': '."
  [v level]
  (let [pad (apply str (repeat (* 2 (inc level)) " "))
        pad- (apply str (repeat (* 2 level) " "))]
    (cond
      (nil? v) "null"
      (true? v) "true"
      (false? v) "false"
      (string? v) (str "\"" (json-escape v) "\"")
      (integer? v) (str v)
      (map? v) (let [items (omap-items v)]
                 (if (empty? items)
                   "{}"
                   (str "{\n"
                        (str/join ",\n"
                                  (map (fn [[k val]]
                                         (str pad "\"" (json-escape k) "\": " (json-indent val (inc level))))
                                       items))
                        "\n" pad- "}")))
      (sequential? v) (if (empty? v)
                        "[]"
                        (str "[\n"
                             (str/join ",\n" (map (fn [x] (str pad (json-indent x (inc level)))) v))
                             "\n" pad- "]"))
      :else (str "\"" (json-escape (str v)) "\""))))

(defn credentials-json [results]
  (json-indent (mapv #(get % "vc") results) 0))

#?(:clj
   (defn -main
     "Run the dry-run membrane over the seed + write out/identity-report.md +
     out/personhood-credentials.json. Byte-parity target = analyze.py."
     [& args]
     (let [seed-path (or (first args) "20-actors/shomei/data/seed-claims.json")
           out-dir (or (second args) "20-actors/shomei/methods/out")
           res (run (edn/load-json seed-path))
           results (get res "results")]
       (.mkdirs (clojure.java.io/file out-dir))
       (spit (clojure.java.io/file out-dir "identity-report.md") (report-md results))
       (spit (clojure.java.io/file out-dir "personhood-credentials.json") (credentials-json results))
       (doseq [r results]
         (let [c (get r "credential")]
           (println (str (get r "subjectDid") ": IAL " (get c "assuranceLevel")
                         " (" (agg/assurance-label (get c "assuranceLevel")) ") "
                         "pop=" (if (get c "proofOfPersonhood") "True" "False")
                         " factors=" (get c "factorCount") "/" (get c "distinctClasses") "cls "
                         "gated=" (count (get r "gated")) " failed=" (count (get r "failed"))))))
       (println (str "→ " out-dir "/identity-report.md  +  " out-dir "/personhood-credentials.json")))))
