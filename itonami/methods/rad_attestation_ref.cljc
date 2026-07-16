(ns itonami.methods.rad-attestation-ref
  "itonami 営み — RAD-attestation reference recorder (SCAFFOLD, verification cell).

  Operationalizes the etzhayyim BMC gate :hyp/etzhayyim-registry-value
  (\"itonami 契約の RAD attestation 参照フック\"): when itonami OBSERVES that a
  contract/engagement it operates depends on a counterparty organism's RAD
  identity attestation (80-data/kotoba-rad/<name>.identity.journal.edn), it emits
  an append-only kotoba Datom recording *that a reference occurred* — the raw
  metric behind etzhayyim.rad-metrics' funding/attestation gate.

  Same house style as itonami.methods.datom-emit (ADR-2606082300): projects an
  observation into append-only kotoba Datoms [e a v tx op]; keyword-strings stay
  strings in the emitted text; pure fn, file I/O only at the #?(:clj) edge.

  CONSTITUTIONAL (G1 — OBSERVE → RECORD only): recording a reference is a
  DISCLOSED fact ('this contract cited that attestation'), never a verdict on the
  attestation's validity and NEVER a write-back that mints/signs an attestation.
  Signing a RAD attestation is a Council/no-server-key leg, out of scope here.

  STATUS: SCAFFOLD. The pure emitter + shape are real and tested; the live
  contract-integration hook (where the reference OBSERVATIONS come from — the
  itonami contract/engagement store) is a TODO, wired once the contract lane
  exists. Until then `-main` emits from an explicit seed EDN of reference maps."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

;; Attribute vocabulary for a reference observation. A reference entity records
;; WHICH contract cited WHICH organism's attestation, and WHICH RID/sigref it
;; pointed at (so the citation is verifiable against the RAD ledger).
(def ref-attrs
  [":rad-ref/contract"    ; itonami contract/engagement id that cited the attestation
   ":rad-ref/organism"    ; :rad/name of the counterparty organism
   ":rad-ref/rid"         ; the counterparty RAD identity RID it pointed at
   ":rad-ref/did-web"     ; :rad/did-web of the organism (redundant, for audit)
   ":rad-ref/at"          ; ISO-8601 timestamp of the reference
   ":rad-ref/requirement"]) ; :contract-requirement | :advisory (why it was cited)

(defn- fmt
  "Emit a value into the Datom text: keyword-strings kept literal, other strings
   quoted+escaped (mirrors itonami.methods.datom-emit/fmt for the subset used)."
  [v]
  (cond
    (nil? v) "nil"
    (keyword? v) (str v)
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    :else (str v)))

(defn ref-eid
  "Deterministic entity id for a reference observation: rad-ref.<contract>.<organism>."
  [{:strs [contract organism] :as _r}]
  (str "rad-ref." contract "." organism))

(defn emit
  "Pure 1:1-of-datom-emit style: seq of reference maps (string-keyed:
   \"contract\" \"organism\" \"rid\" \"did-web\" \"at\" \"requirement\") →
   the kotoba Datom-log EDN text (trailing newline). GROUND op :add — each
   reference is a durable disclosed fact."
  ([refs] (emit refs 1))
  ([refs tx]
   (let [L (transient [])
         attr-key {":rad-ref/contract" "contract" ":rad-ref/organism" "organism"
                   ":rad-ref/rid" "rid" ":rad-ref/did-web" "did-web"
                   ":rad-ref/at" "at" ":rad-ref/requirement" "requirement"}]
     (conj! L ";; itonami 営み — GENERATED kotoba Datom log: RAD-attestation references (SCAFFOLD).")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op]. GROUND op :add.")
     (conj! L ";; G1: a reference is a DISCLOSED citation fact, never an attestation mint/verdict.")
     (conj! L "[")
     (doseq [r refs]
       (let [eid (ref-eid r)]
         (doseq [a ref-attrs]
           (let [v (get r (attr-key a))]
             (when (some? v)
               (conj! L (str "[" (fmt eid) " " a " " (fmt v) " " tx " :add]")))))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI: read a seed EDN of reference maps → stdout Datom log. SCAFFOLD — the
      live hook (reference observations sourced from the itonami contract store)
      is a TODO. Args: [seed.edn] [--tx N]."
     [& argv]
     (let [argv (vec argv)
           seed (when (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (first argv))
           tx   (if (some #{"--tx"} argv)
                  (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                  1)
           refs (if seed (edn/read-string (slurp seed)) [])]
       (print (emit refs tx))
       0)))
