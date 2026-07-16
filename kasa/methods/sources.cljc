(ns kasa.methods.sources
  "kasa 嵩 — source catalogue (the G1 admissibility layer). 1:1 Clojure port of methods/sources.py.

  The single source of truth for WHICH public sources kasa may ingest. Encodes the constitutional
  G1 rule (Charter Rider §2(e) anti-gatekeeping + §2(c) vendor query-tracking):

    ADMISSIBLE : public, redistributable HEADLINE figures + open datasets.
    PROHIBITED : the PAID, copyrighted FULL reports + subscription terminals. The headline figure a
                 vendor puts in a FREE press release is admissible; the paywalled report / terminal
                 compilation is not. Read the press release, never the terminal.

  Used by ingest (admissible? gate) + the invariant tests. ADR-2606072000."
  (:require [clojure.string :as str]))

;; publisher -> [default-license default-access note]. The ADMISSIBLE public sources.
(def admissible-sources
  (array-map
   "wsts"              [":press-release" ":press-release" "WSTS Blue Book headline actuals (free press releases)."]
   "sia"               [":press-release" ":press-release" "SIA Global Semiconductor Sales (free monthly/annual press)."]
   "trendforce"        [":press-release" ":press-release" "TrendForce DRAM/NAND revenue (free press releases)."]
   "jpr"               [":press-release" ":press-release" "Jon Peddie Research GPU/AIB market (free press summaries)."]
   "idc"               [":press-release" ":press-release" "IDC HEADLINE shipment figures in free press releases ONLY — never the paid IDC report/tracker subscription."]
   "top500"            [":public-domain" ":public-list" "TOP500 public semiannual list (aggregate Rmax)."]
   "epoch-ai"          [":cc-by" ":open-dataset" "Epoch AI Notable AI Models database (CC-BY, redistributable)."]
   "our-world-in-data" [":cc-by" ":open-dataset" "Our World in Data (CC-BY, redistributable)."]
   "company-filing"    [":public-domain" ":company-filing" "Issuer primary disclosure (10-K / 有報) — public; cross-links kanjō."]))

(def prohibited
  #{"gartner-report" "idc-report" "idc-tracker" "omdia" "bloomberg-terminal"
    "sp-capital-iq" "refinitiv" "factset" "statista-pro" "yole-report"})

(def prohibited-access #{":paid-terminal" ":subscription" ":paywalled-report"})

(defn admissible?
  "G1 gate: true iff this (publisher, access) is a public/redistributable source kasa may ingest."
  ([publisher] (admissible? publisher nil))
  ([publisher access]
   (let [pub (str/replace publisher #"^:+" "")
         acc (when (some? access) (str/replace access #"^:+" ""))]
     (cond
       (contains? prohibited pub) false
       (and (some? acc) (seq acc) (contains? prohibited-access (str ":" acc))) false
       :else (contains? admissible-sources pub)))))
