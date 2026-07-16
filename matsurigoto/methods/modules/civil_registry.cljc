(ns matsurigoto.methods.modules.civil-registry
  "civil_registry.py — matsurigoto 政 `civil-registry` module (R0 reference implementation).
  1:1 Clojure port of `methods/modules/civil_registry.py` (ADR-2606062300).

  Pure-function VALIDATION + APPEND-ONLY RECORD CONSTRUCTION for vital events (UN CRVS +
  OpenCRVS). A registration is validated then emitted as an immutable record + an UNSIGNED
  W3C-VC certificate skeleton (the governing organ signs with ITS own key).

    G1 no-operator-master-key : SERVER-HELD-AUTHORITY false; certificates returned UNSIGNED.
    G2 spec-derived-only      : UN CRVS + OpenCRVS + W3C VC 2.0 shapes only.
    G5 append-only (非終末論)  : every helper RETURNS A NEW record list; nothing is overwritten.
    G6 data-minimization      : only the fields the vital event requires.

  House style: result maps stay string-keyed (json.loads shapes); pure fns; stdlib only.
  The Python __main__ demo is omitted."
  (:require [clojure.string :as str]))

;; G1: this module holds NO signing authority and signs no certificate.
(def SERVER-HELD-AUTHORITY false)

(def ^:private vital-kinds #{"birth" "death" "marriage"})

(defn- iso
  "ISO-8601 strings sort lexically; we only need ordering + non-future checks."
  [s]
  (if (or (not (string? s)) (< (count s) 4)
          (not (re-matches #"\d{4}" (subs s 0 4))))
    (throw (ex-info (str "timestamp must be ISO-8601, got " (pr-str s)) {}))
    s))

(defn- capitalize-py
  "Python str.capitalize(): first char upper, rest lower."
  [s]
  (if (empty? s)
    s
    (str (str/upper-case (subs s 0 1)) (str/lower-case (subs s 1)))))

(defn- unsigned-certificate
  "A W3C-VC certificate SKELETON. G1: unsigned — the governing organ signs with ITS key."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" (str (capitalize-py kind) "Certificate")]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil                                      ; G1 — this module signs nothing
   "server_held_authority" SERVER-HELD-AUTHORITY    ; false
   "status" "issued-unsigned"})

(defn- record
  "An immutable CRVS datom (append-only). 非終末論: one event, never a final state."
  [kind record-id fields occurred-at]
  {"record_id" record-id
   "vital_kind" kind
   "occurred_at" occurred-at
   "fields" (into {} fields)   ; data-minimized (G6)
   "immutable" true})          ; G5 — appended, never overwritten

(defn register-birth
  "Validate + construct a birth registration (UN CRVS). Pure function."
  [record-id child parents place occurred-at now]
  (when-not (and child (not= child ""))
    (throw (ex-info "birth: child is required" {})))
  (when-not (seq parents)
    (throw (ex-info "birth: at least one parent is required" {})))
  (when-not (and place (not= place ""))
    (throw (ex-info "birth: place is required" {})))
  (when (> (compare (iso occurred-at) (iso now)) 0)
    (throw (ex-info "birth: occurrence cannot be in the future" {})))
  (let [rec (record "birth" record-id
                    {"child" child "parents" (vec parents) "place" place} occurred-at)]
    {"record" rec "certificate" (unsigned-certificate "birth" child record-id)}))

(defn register-death
  "Validate + construct a death registration (UN CRVS). Pure function."
  ([record-id decedent place occurred-at now] (register-death record-id decedent place occurred-at now nil))
  ([record-id decedent place occurred-at now cause]
   (when-not (and decedent (not= decedent ""))
     (throw (ex-info "death: decedent is required" {})))
   (when-not (and place (not= place ""))
     (throw (ex-info "death: place is required" {})))
   (when (> (compare (iso occurred-at) (iso now)) 0)
     (throw (ex-info "death: occurrence cannot be in the future" {})))
   (let [fields (cond-> {"decedent" decedent "place" place}
                  (and cause (not= cause "")) (assoc "cause" cause))  ; ICD-11 coded where present
         rec (record "death" record-id fields occurred-at)]
     {"record" rec "certificate" (unsigned-certificate "death" decedent record-id)})))

(defn register-marriage
  "Validate + construct a marriage registration (UN CRVS). Pure function.

  Requires two DISTINCT partners, a place, a non-future occurrence, and that neither partner
  is already in an active marriage within `existing-marriages` (a seq of pairs)."
  ([record-id partner-a partner-b place occurred-at now]
   (register-marriage record-id partner-a partner-b place occurred-at now []))
  ([record-id partner-a partner-b place occurred-at now existing-marriages]
   (when-not (and partner-a (not= partner-a "") partner-b (not= partner-b ""))
     (throw (ex-info "marriage: two partners are required" {})))
   (when (= partner-a partner-b)
     (throw (ex-info "marriage: partners must be distinct" {})))
   (when-not (and place (not= place ""))
     (throw (ex-info "marriage: place is required" {})))
   (when (> (compare (iso occurred-at) (iso now)) 0)
     (throw (ex-info "marriage: occurrence cannot be in the future" {})))
   (let [already (set (for [pair existing-marriages, p pair] p))]
     (when (or (contains? already partner-a) (contains? already partner-b))
       (throw (ex-info "marriage: a partner is already in an active marriage" {})))
     (let [rec (record "marriage" record-id
                       {"partners" (vec (sort [partner-a partner-b])) "place" place} occurred-at)]
       {"record" rec "certificate" (unsigned-certificate "marriage" record-id record-id)}))))

(defn register-residency
  "Residence registration (転入届). Append-only — a move-in is a new datom (G5)."
  ([record-id person new-address occurred-at now]
   (register-residency record-id person new-address occurred-at now nil))
  ([record-id person new-address occurred-at now prior-address]
   (when-not (and person (not= person ""))
     (throw (ex-info "residency: person is required" {})))
   (when-not (and new-address (not= new-address ""))
     (throw (ex-info "residency: new_address is required" {})))
   (when (> (compare (iso occurred-at) (iso now)) 0)
     (throw (ex-info "residency: occurrence cannot be in the future" {})))
   (let [fields (cond-> {"person" person "address" new-address}
                  (and prior-address (not= prior-address "")) (assoc "prior_address" prior-address))
         rec (record "residency" record-id fields occurred-at)]
     {"record" rec "certificate" (unsigned-certificate "residency" person record-id)})))

(defn append
  "G5: append a registration to a history, returning a NEW list (never mutate in place)."
  [history result]
  (conj (vec history) (get result "record")))

(defn current-address
  "Latest residency datom for a person = current address (max occurred_at). 非終末論."
  [history person]
  (let [fixes (filter #(and (= (get % "vital_kind") "residency")
                            (= (get-in % ["fields" "person"]) person))
                      history)]
    (when (seq fixes)
      ;; max(fixes, key=lambda r: r["occurred_at"]) — lexical string compare; Python max keeps
      ;; the FIRST maximal on ties (iterating in order), so keep the earlier on a tie.
      (let [latest (reduce (fn [best r]
                             (if (> (compare (get r "occurred_at") (get best "occurred_at")) 0)
                               r best))
                           (first fixes) (rest fixes))]
        (get-in latest ["fields" "address"])))))

(defn solve
  [& _]
  (throw (ex-info (str "civil-registry R0: reference validation + record construction only. "
                       "Live registration against a real civil register is Council+operator "
                       "gated (principal A: Council Lv7+; principal B: adopting state).")
                  {})))
