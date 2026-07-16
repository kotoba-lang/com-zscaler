(ns matsurigoto.methods.modules.corp-registry
  "corp_registry.py — matsurigoto 政 `corp-registry` module (R0 reference implementation).
  1:1 Clojure port of `methods/modules/corp_registry.py` (ADR-2606062300).

  Pure-function VALIDATION + registry-number assignment + ISO 17442 LEI issuance with a real
  ISO 7064 MOD 97-10 check-digit computation, then an APPEND-ONLY record + an UNSIGNED
  incorporation certificate.

  Spec basis (G2): ISO 17442 (LEI) + GLEIF LEI-CDF + EU BRIS + W3C VC 2.0.

    G1 no-operator-master-key : SERVER-HELD-AUTHORITY false; certificate UNSIGNED.
    G2 spec-derived-only      : ISO 17442 LEI structure + ISO 7064 MOD 97-10 checksum.
    G5 append-only (非終末論)  : a change is an appended amendment record.

  House style: result maps stay string-keyed; pure fns; stdlib only. The MOD-97-10 arithmetic
  runs over big integers (BigInteger). The Python __main__ demo is omitted."
  (:require [clojure.string :as str]))

(def SERVER-HELD-AUTHORITY false)  ; G1

;; ── ISO 17442 LEI + ISO 7064 MOD 97-10 (the conformance anchor) ──
(defn- to-digits
  "Convert an alphanumeric string to its ISO 7064 numeric form (0-9 stay; A=10 … Z=35)."
  [s]
  (apply str
         (map (fn [ch]
                (cond
                  (Character/isDigit ^char ch) (str ch)
                  (and (>= (int ch) (int \A)) (<= (int ch) (int \Z))) (str (- (int ch) 55))
                  :else (throw (ex-info (str "LEI char must be [0-9A-Z], got " (pr-str (str ch))) {}))))
              s)))

(defn- mod97 [^String numeric-str]
  ;; int(numeric-str) % 97 over arbitrary-precision
  (.intValue (.mod (java.math.BigInteger. numeric-str) (java.math.BigInteger. "97"))))

(defn compute-lei-check-digits
  "ISO 7064 MOD 97-10 check digits for an 18-char LEI base.
  digits = numeric(base18 + \"00\"); check = 98 − (digits mod 97); zero-padded to 2."
  [base18]
  (when (not= (count base18) 18)
    (throw (ex-info (str "LEI base must be 18 chars, got " (count base18)) {})))
  (let [m (mod97 (to-digits (str base18 "00")))]
    (let [c (- 98 m)]
      (if (< c 10) (str "0" c) (str c)))))

(defn validate-lei
  "A 20-char LEI is valid iff numeric(lei) mod 97 == 1 (ISO 7064 MOD 97-10)."
  [lei]
  (if (or (not (string? lei)) (not= (count lei) 20))
    false
    (try
      (= (mod97 (to-digits lei)) 1)
      (catch Exception _ false))))

(defn assign-lei
  "Build a valid LEI: 4-char LOU prefix + reserved '00' + 12-char entity id + 2 check digits."
  ([lou-prefix entity-id12]
   (when (not= (count lou-prefix) 4)
     (throw (ex-info "LOU prefix must be 4 chars" {})))
   (when (not= (count entity-id12) 12)
     (throw (ex-info "entity id must be 12 chars" {})))
   (let [base (str/upper-case (str lou-prefix "00" entity-id12))]
     (str base (compute-lei-check-digits base)))))

;; ── registry records ──
(defn- unsigned-certificate
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil                                      ; G1
   "server_held_authority" SERVER-HELD-AUTHORITY    ; false
   "status" "issued-unsigned"})

(defn- zero-pad
  "Zero-pad an integer to width n (Python f\"{n:0Wd}\")."
  [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-incorporation
  "Validate + construct a company incorporation registration. Pure function."
  ([entity-name officers capital articles address jurisdiction sequence]
   (register-incorporation entity-name officers capital articles address jurisdiction sequence "EZHY" nil))
  ([entity-name officers capital articles address jurisdiction sequence lou-prefix entity-id12]
   (when-not (and entity-name (not= entity-name ""))
     (throw (ex-info "incorporation: entity_name required" {})))
   (when-not (seq officers)
     (throw (ex-info "incorporation: at least one officer required" {})))
   (when (< capital 0)
     (throw (ex-info "incorporation: capital must be >= 0" {})))
   (when-not (and articles (not= articles ""))
     (throw (ex-info "incorporation: articles required" {})))
   (when-not (and address (not= address ""))
     (throw (ex-info "incorporation: address required" {})))
   (when (< sequence 0)
     (throw (ex-info "incorporation: sequence must be >= 0" {})))
   (let [registry-number (str (str/upper-case jurisdiction) "-" (zero-pad sequence 8))
         ;; eid = (entity_id12 or f"{sequence:012d}")[:12].rjust(12,"0").upper()
         base-eid (or entity-id12 (zero-pad sequence 12))
         eid (-> base-eid
                 (#(subs % 0 (min 12 (count %))))
                 (#(str (apply str (repeat (max 0 (- 12 (count %))) "0")) %))
                 (str/upper-case))
         lei (assign-lei lou-prefix eid)
         record {"record_id" registry-number
                 "kind" "incorporation"
                 "entity_name" entity-name
                 "officers" (vec officers)
                 "capital" capital
                 "jurisdiction" jurisdiction
                 "lei" lei
                 "immutable" true}]  ; G5
     {"record" record "lei" lei "registry_number" registry-number
      "certificate" (unsigned-certificate "IncorporationCertificate" registry-number registry-number)})))

(defn register-change
  "Append-only amendment (変更登記). G5: never overwrites the incorporation record."
  [registry-number changed-fields effective-date]
  (when-not (and registry-number (not= registry-number ""))
    (throw (ex-info "change: registry_number required" {})))
  (when-not (seq changed-fields)
    (throw (ex-info "change: changed_fields required" {})))
  (let [record {"record_id" (str registry-number "#chg@" effective-date)
                "kind" "change"
                "registry_number" registry-number
                "changed" (into {} changed-fields)
                "effective_date" effective-date
                "immutable" true}]  ; G5 — appended, not an overwrite
    {"record" record}))

(defn append
  "G5: append a registry record, returning a NEW list."
  [history result]
  (conj (vec history) (get result "record")))

(defn solve
  [& _]
  (throw (ex-info (str "corp-registry R0: reference validation + LEI assignment only. Live "
                       "registration against a real corporate register is Council+operator "
                       "gated (principal A: Council Lv7+; principal B: adopting state).")
                  {})))
