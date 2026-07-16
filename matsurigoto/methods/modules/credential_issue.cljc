(ns matsurigoto.methods.modules.credential-issue
  "credential_issue.py — matsurigoto 政 `credential-issue` module (R0 reference implementation).
  1:1 Clojure port of `methods/modules/credential_issue.py` (ADR-2606062300).

  A pure-function ICAO Doc 9303 TD3 MRZ builder with the real 7-3-1 weighted check-digit
  algorithm. Reproduces the ICAO 9303 worked example exactly. Produces the MRZ + an UNSIGNED
  issuance record (the passport authority signs the chip/SOD with ITS own ICAO-PKD key, G1).

  Spec basis (G2): ICAO Doc 9303 (MRTD) + ISO/IEC 19794 + W3C VC 2.0.

    G1 no-operator-master-key : SERVER-HELD-AUTHORITY false; the document is UNSIGNED here.
    G2 spec-derived-only      : ICAO 9303 MRZ structure + 7-3-1 check digit.
    G6 data-minimization      : only MRZ fields.

  House style: result maps stay string-keyed; pure fns; stdlib only. The Python __main__
  demo is omitted."
  (:require [clojure.string :as str]))

(def SERVER-HELD-AUTHORITY false)  ; G1

(def ^:private weights [7 3 1])

(defn- char-value
  "ICAO 9303 MRZ char value: digits = value, A-Z = 10..35, filler '<' = 0."
  [ch]
  (cond
    (= ch \<) 0
    (Character/isDigit ^char ch) (- (int ch) (int \0))
    (and (>= (int ch) (int \A)) (<= (int ch) (int \Z))) (- (int ch) 55)  ; 'A' → 10
    :else (throw (ex-info (str "MRZ char must be [0-9A-Z<], got " (pr-str (str ch))) {}))))

(defn mrz-check-digit
  "ICAO Doc 9303 check digit: Σ(value × weight[7,3,1 repeating]) mod 10."
  [data]
  (let [total (reduce + 0 (map-indexed
                           (fn [i ch] (* (char-value ch) (nth weights (mod i 3))))
                           data))]
    (str (mod total 10))))

(defn- pad
  "Uppercase, replace spaces with filler '<', pad/truncate to n chars."
  [s n]
  (let [s (-> (str/upper-case s) (str/replace " " "<"))
        padded (str s (apply str (repeat n "<")))]
    (subs padded 0 n)))

(defn build-td3-mrz
  "Build the two 44-char TD3 (passport) MRZ lines with all ICAO check digits."
  ([doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd]
   (build-td3-mrz doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd ""))
  ([doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd personal-number]
   (when (or (not= (count issuing-state) 3) (not= (count nationality) 3))
     (throw (ex-info "issuing_state and nationality must be 3-letter ICAO codes" {})))
   (when (or (not= (count dob-yymmdd) 6) (not= (count expiry-yymmdd) 6))
     (throw (ex-info "dates must be YYMMDD (6 digits)" {})))
   (when-not (contains? #{"M" "F" "<"} sex)
     (throw (ex-info "sex must be M, F, or < (unspecified)" {})))
   (let [name-field (pad (str surname "<<" given-names) 39)
         line1 (str "P<" (str/upper-case issuing-state) name-field)
         doc (pad doc-number 9)
         c-doc (mrz-check-digit doc)
         c-dob (mrz-check-digit dob-yymmdd)
         c-exp (mrz-check-digit expiry-yymmdd)
         pers (pad personal-number 14)
         c-pers (mrz-check-digit pers)
         composite-input (str doc c-doc dob-yymmdd c-dob expiry-yymmdd c-exp pers c-pers)
         c-composite (mrz-check-digit composite-input)
         line2 (str doc c-doc (str/upper-case nationality) dob-yymmdd c-dob sex
                    expiry-yymmdd c-exp pers c-pers c-composite)]
     {"line1" line1 "line2" line2
      "check_digits" {"doc" c-doc "dob" c-dob "expiry" c-exp
                      "personal" c-pers "composite" c-composite}})))

(defn validate-td3-line2
  "Verify the field + composite check digits of a TD3 MRZ line 2."
  [line2]
  (if (not= (count line2) 44)
    false
    (try
      (let [doc (subs line2 0 9) c-doc (subs line2 9 10)
            dob (subs line2 13 19) c-dob (subs line2 19 20)
            exp (subs line2 21 27) c-exp (subs line2 27 28)
            pers (subs line2 28 42) c-pers (subs line2 42 43)
            c-comp (subs line2 43 44)]
        (cond
          (not= (mrz-check-digit doc) c-doc) false
          (not= (mrz-check-digit dob) c-dob) false
          (not= (mrz-check-digit exp) c-exp) false
          (not= (mrz-check-digit pers) c-pers) false
          :else
          (let [composite-input (str doc c-doc dob c-dob exp c-exp pers c-pers)]
            (= (mrz-check-digit composite-input) c-comp))))
      (catch Exception _ false))))

(defn- unsigned-document
  [kind subject mrz]
  {"type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject}
   "mrz" mrz
   "sod" nil                                        ; G1 — issuing state signs the SOD (ICAO PKD)
   "proof" nil
   "server_held_authority" SERVER-HELD-AUTHORITY    ; false
   "status" "issued-unsigned"})

(defn issue-passport
  "Validate + assemble an MRTD passport (ICAO 9303). Returns MRZ + unsigned document (G1)."
  ([doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd subject-did]
   (issue-passport doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd subject-did ""))
  ([doc-number issuing-state nationality surname given-names dob-yymmdd sex expiry-yymmdd subject-did personal-number]
   (when-not (and doc-number (not= doc-number ""))
     (throw (ex-info "passport: doc_number required" {})))
   (when-not (and surname (not= surname ""))
     (throw (ex-info "passport: surname required" {})))
   (let [mrz (build-td3-mrz doc-number issuing-state nationality surname given-names
                            dob-yymmdd sex expiry-yymmdd personal-number)]
     {"mrz" mrz "document" (unsigned-document "Passport" subject-did mrz)})))

(defn solve
  [& _]
  (throw (ex-info (str "credential-issue R0: reference MRZ assembly only. Live passport/ID "
                       "issuance + SOD signing is the issuing state's ICAO-PKD authority "
                       "(principal B) / Council Lv7+ (principal A) + operator gated.")
                  {})))
