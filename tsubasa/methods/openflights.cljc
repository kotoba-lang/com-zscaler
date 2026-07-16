#!/usr/bin/env bb
;; tsubasa 翼 — OpenFlights real public-domain coverage source (R3+). ADR-2606072802 §R3.
(ns tsubasa.methods.openflights
  "openflights.cljc — fold the OpenFlights PUBLIC-DOMAIN datasets (Open Database License) into
  real `:airport` / `:carrier` coverage rows, raising tsubasa's coverage off a real source
  instead of the bounded seed.

  OpenFlights (https://github.com/jpatokal/openflights, ODbL) publishes `airports.dat` and
  `airlines.dat` as CSV. This namespace PARSES that CSV (pure) into `:authoritative` coverage
  rows; the read-only autonomous fetch is `fetch.cljc`'s job (no-server-key: read-only). It
  emits NO fares — OpenFlights has no prices, and a fare needs `:fare/co2-kg` (G4); fares stay
  the `:public`/`:member-principal` fare-source path. Coverage (airports/carriers) is exactly
  what the original 'raise coverage' ask wanted, from a real public source.

  Region is best-effort from a country→region map (OpenFlights uses country NAMES); an unknown
  country maps to `:unknown` (honestly excluded from a region target, never guessed)."
  (:require [clojure.string :as str]))

;; ── country (OpenFlights name) → world region (best-effort, honest :unknown fallback) ──
(def ^:private country->region
  {"Japan" :east-asia "South Korea" :east-asia "China" :east-asia "Hong Kong" :east-asia
   "Taiwan" :east-asia "Macau" :east-asia "Mongolia" :east-asia
   "India" :south-asia "Pakistan" :south-asia "Bangladesh" :south-asia "Sri Lanka" :south-asia
   "Nepal" :south-asia
   "Singapore" :southeast-asia "Thailand" :southeast-asia "Malaysia" :southeast-asia
   "Indonesia" :southeast-asia "Vietnam" :southeast-asia "Philippines" :southeast-asia
   "Cambodia" :southeast-asia "Myanmar" :southeast-asia "Laos" :southeast-asia
   "United Kingdom" :europe "France" :europe "Germany" :europe "Spain" :europe "Italy" :europe
   "Netherlands" :europe "Switzerland" :europe "Ireland" :europe "Portugal" :europe
   "Belgium" :europe "Austria" :europe "Sweden" :europe "Norway" :europe "Denmark" :europe
   "Finland" :europe "Poland" :europe "Greece" :europe "Russia" :europe "Turkey" :europe
   "United Arab Emirates" :middle-east "Qatar" :middle-east "Saudi Arabia" :middle-east
   "Israel" :middle-east "Kuwait" :middle-east "Bahrain" :middle-east "Oman" :middle-east
   "Jordan" :middle-east "Lebanon" :middle-east
   "United States" :north-america "Canada" :north-america "Mexico" :north-america
   "Brazil" :south-america "Argentina" :south-america "Chile" :south-america "Peru" :south-america
   "Colombia" :south-america "Ecuador" :south-america
   "Australia" :oceania "New Zealand" :oceania "Fiji" :oceania
   "South Africa" :africa "Egypt" :africa "Kenya" :africa "Nigeria" :africa "Ethiopia" :africa
   "Morocco" :africa "Tanzania" :africa})

(defn region-of [country] (get country->region country :unknown))

;; ── CSV (OpenFlights .dat: quoted fields, \N = null) ──────────────────────────
(defn parse-csv-line
  "Parse one OpenFlights CSV line into a vector of fields (handles double-quoted fields with
  embedded commas). \\N stays as the literal string \"\\N\" (caller treats it as null)."
  [line]
  (loop [chars (seq line) field (StringBuilder.) out [] in-q false]
    (if-let [c (first chars)]
      (cond
        (= c \") (recur (rest chars) field out (not in-q))
        (and (= c \,) (not in-q)) (recur (rest chars) (StringBuilder.) (conj out (str field)) false)
        :else (recur (rest chars) (.append field c) out in-q))
      (conj out (str field)))))

(defn- nullish? [s] (or (nil? s) (str/blank? s) (= s "\\N")))

(defn parse-airports
  "airports.dat CSV text → `:airport` rows (only rows with a real 3-letter IATA).
  Columns: 0 id,1 name,2 city,3 country,4 IATA,5 ICAO,6 lat,7 lon,…"
  [csv-text]
  (->> (str/split-lines (or csv-text ""))
       (remove str/blank?)
       (keep (fn [line]
               (let [f (parse-csv-line line)
                     iata (nth f 4 nil) country (nth f 3 nil) name (nth f 1 nil)]
                 (when (and (not (nullish? iata)) (= 3 (count iata)) (re-matches #"[A-Z]{3}" iata))
                   {:type :airport :airport/iata iata
                    :airport/name (if (nullish? name) iata name)
                    :airport/country (if (nullish? country) "" country)
                    :airport/region (region-of country)
                    :airport/sourcing :authoritative
                    :airport/source "openflights:airports.dat"}))))
       vec))

(defn parse-airlines
  "airlines.dat CSV text → `:carrier` rows (active airlines with a real 2-char IATA).
  Columns: 0 id,1 name,2 alias,3 IATA,4 ICAO,5 callsign,6 country,7 active(Y/N)"
  [csv-text]
  (->> (str/split-lines (or csv-text ""))
       (remove str/blank?)
       (keep (fn [line]
               (let [f (parse-csv-line line)
                     iata (nth f 3 nil) name (nth f 1 nil) active (nth f 7 nil)]
                 (when (and (not (nullish? iata)) (= 2 (count iata))
                            (re-matches #"[A-Z0-9]{2}" iata) (= active "Y"))
                   {:type :carrier :carrier/iata iata
                    :carrier/name (if (nullish? name) iata name)
                    :carrier/sourcing :authoritative
                    :carrier/source "openflights:airlines.dat"}))))
       ;; dedup by IATA (OpenFlights has duplicate codes); keep first
       (reduce (fn [acc r] (if (some #(= (:carrier/iata %) (:carrier/iata r)) acc) acc (conj acc r))) [])
       vec))

(defn merge-coverage
  "Merge OpenFlights airport/carrier rows into an existing seed, de-duping by IATA so seed rows
  (which may carry richer data) win. Returns the combined row vector."
  [seed-rows of-airports of-carriers]
  (let [seed-ap (set (map :airport/iata (filter #(= :airport (:type %)) seed-rows)))
        seed-ca (set (map :carrier/iata (filter #(= :carrier (:type %)) seed-rows)))]
    (vec (concat seed-rows
                 (remove #(seed-ap (:airport/iata %)) of-airports)
                 (remove #(seed-ca (:carrier/iata %)) of-carriers)))))

#?(:clj
   (defn -main [& args]
     (let [airports-csv (some-> (first args) slurp)
           airlines-csv (some-> (second args) slurp)
           aps (if airports-csv (parse-airports airports-csv) [])
           cas (if airlines-csv (parse-airlines airlines-csv) [])]
       (println (str ";; openflights — airports=" (count aps) " carriers=" (count cas)))
       (println (pr-str (vec (concat aps cas)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
