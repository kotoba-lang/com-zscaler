#!/usr/bin/env bb
;; tsubasa 翼 — live fare ingest adapter (charter-clean, R3). ADR-2606072802.
(ns tsubasa.methods.ingest
  "ingest.cljc — tsubasa 翼 LIVE fare ingest adapter (R3, G8 founder-attested).

  Folds a fetched fare payload into canonical :authoritative :fare/* rows that the
  analyze/autorun/kotoba pipeline already consumes. The G8 gate is UNLOCKED (founder
  Lv7+ attestation = the PR that lands this), but the unlock is CHARTER-BOUNDED, and
  those bounds are STRUCTURAL here, not policy:

    * SOURCE — only :public (free / disclosed) or :member-principal (the member's OWN
      airline-account credentials, no-server-key) sources are representable. A
      :paid-terminal source (Amadeus/Sabre/Travelport opaque billed terminal) is
      REFUSED by `assert-clean-source` — an opaque, lock-in commercial terminal scores
      negative on the ECL objective function (Rider §2(e)/§2(i)). (G8 bound)
    * NO NETWORK in the loop — this namespace performs NO I/O. The operator/member runs
      the fetch leg in their OWN runtime and hands ingest the parsed payload (a file /
      value). The loop holds no key and makes no call (no-server-key). (G6/G8)
    * G1 no-affiliate-no-inflow — book-url is affiliate-stripped on the way in; a fare
      carrying a commission/affiliate/merchant key is REJECTED (poisoned input dropped).
    * G4 emissions-honest — a fare with no positive co2 is REJECTED (emissions may never
      be silently absent).
    * G5 no-person-tracking — a fare carrying a searcher/person/profile key is REJECTED.

  Every accepted fare is :fare/sourcing :authoritative + :fare/source (cited provenance)
  + :fare/ingested-at (caller-supplied as-of). Per-row fail-open: a bad row is dropped
  and reported, never aborts the batch."
  (:require [tsubasa.methods.agent :as agent]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

(def ^:private forbidden-key-substrings
  ["commission" "affiliate" "merchant" "sponsored"   ; G1
   "urgency" "scarcity" "seatsleft" "pricewillrise"   ; G3
   "searcher" "person" "profile" "patternoflife"])    ; G5

(defn- poisoned?
  "True if a raw fare map carries any charter-forbidden key (G1/G3/G5)."
  [raw]
  (let [ks (map #(-> % name str/lower-case (str/replace #"[-_]" "")) (keys raw))]
    (boolean (some (fn [k] (some #(str/includes? k %) forbidden-key-substrings)) ks))))

(def ^:private allowed-source-kinds #{:public :member-principal})

(defn assert-clean-source
  "Refuse a non-charter-clean ingest source (G8 bound). Returns source-kind on success;
  throws ex-info on a paid/opaque terminal or an unknown kind."
  [source-kind]
  (when-not (contains? allowed-source-kinds source-kind)
    (throw (ex-info (str "tsubasa ingest refused: source-kind " (pr-str source-kind)
                         " is not charter-clean (only :public / :member-principal; a paid GDS "
                         "terminal is unrepresentable — Rider §2(e)/§2(i), G8 bound).")
                    {:source-kind source-kind :allowed allowed-source-kinds})))
  source-kind)

(defn- g [raw kk ks] (or (get raw kk) (get raw ks)))

(defn- ->cabin [v]
  (cond (keyword? v) v
        (string? v) (keyword (str/lower-case v))
        :else :economy))

(defn normalize-fare
  "Raw fetched fare map → canonical :authoritative :fare/* row, or {:reject <reason>}.
  Accepts handler-shaped keys (origin/destination/carrier/fareMinor/co2Kg/…) keyword or
  string. `source` is the cited provenance (URL / account); `as-of` the caller stamp."
  [raw source as-of]
  (let [origin (g raw :origin "origin")
        dest (g raw :destination "destination")
        carrier (g raw :carrier "carrier")
        co2 (g raw :co2Kg "co2Kg")]
    (cond
      (poisoned? raw)              {:reject :forbidden-key}
      (not (and origin dest carrier)) {:reject :missing-od-carrier}
      (not (number? co2))          {:reject :no-co2}          ; G4
      (not (pos? co2))             {:reject :nonpositive-co2} ; G4
      (str/blank? (str source))    {:reject :no-source}       ; G8 provenance
      :else
      (let [cabin (->cabin (g raw :cabin "cabin"))
            id (or (g raw :id "id")
                   (str "fare." (str/lower-case (str origin)) "-" (str/lower-case (str dest))
                        "-" (str/lower-case (str carrier)) "-" (name cabin)))]
        {:type :fare
         :fare/id id
         :fare/origin (str origin)
         :fare/destination (str dest)
         :fare/carrier (str carrier)
         :fare/stops (long (or (g raw :stops "stops") 0))
         :fare/duration-min (long (or (g raw :durationMin "durationMin") 0))
         :fare/fare-minor (long (or (g raw :fareMinor "fareMinor") 0))
         :fare/baggage-minor (long (or (g raw :baggageMinor "baggageMinor") 0))
         :fare/currency (str (or (g raw :currency "currency") "USD"))
         :fare/co2-kg (double co2)
         :fare/cabin cabin
         :fare/book-url (agent/strip-affiliate (str (or (g raw :bookUrl "bookUrl") "")))  ; G1
         :fare/sourcing :authoritative
         :fare/source (str source)
         :fare/ingested-at (str as-of)}))))

(defn ingest
  "Fold a fetched payload into canonical :authoritative :fare rows.
  opts: {:source <str provenance> :as-of <str stamp> :source-kind <:public|:member-principal>}.
  Refuses a non-charter-clean source-kind (G8). Per-row fail-open: bad rows are dropped
  and reported. Returns {:rows [<:fare row>...] :accepted n :rejected [{:raw .. :reason ..}...]}."
  [payload {:keys [source as-of source-kind] :or {source-kind :public}}]
  (assert-clean-source source-kind)
  (reduce
   (fn [acc raw]
     (let [r (normalize-fare raw source as-of)]
       (if (:reject r)
         (update acc :rejected conj {:raw raw :reason (:reject r)})
         (-> acc (update :rows conj r) (update :accepted inc)))))
   {:rows [] :accepted 0 :rejected []}
   payload))

#?(:clj
   (defn -main [& args]
     ;; The operator/member fetch leg writes a payload EDN file in their OWN runtime; this
     ;; loop only NORMALIZES it (no network, no key). Default source-kind :public.
     ;;   bb ingest.cljc <payload.edn> <source> <as-of> [member]
     (let [payload-path (or (first args)
                            (throw (ex-info "usage: ingest.cljc <payload.edn> <source> <as-of> [member]" {})))
           source (or (second args) "")
           as-of (or (nth args 2 nil) "manual")
           kind (if (= (nth args 3 nil) "member") :member-principal :public)
           payload (vec (edn/read-string (slurp payload-path)))
           {:keys [rows accepted rejected]} (ingest payload {:source source :as-of as-of :source-kind kind})]
       (println (str ";; tsubasa ingest — source-kind=" (name kind) " accepted=" accepted
                     " rejected=" (count rejected)))
       (when (seq rejected)
         (println (str ";; rejected reasons: " (frequencies (map :reason rejected)))))
       (println (pr-str (vec rows))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
