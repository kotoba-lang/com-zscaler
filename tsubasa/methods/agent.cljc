(ns tsubasa.methods.agent
  "tsubasa 翼 — flight discovery commons (the Skyscanner inversion). 1:1 port of py/agent.py. Honest
  fare/route meta-search; every onward link is affiliate-stripped and the member self-books on the
  airline's own site (no inflow). Structural invariants: no-affiliate-no-inflow (G1 — strip-affiliate
  + commission/tithe ≡ 0, member principal), emissions-honest (G4 — co2Kg on every result, greenest
  first-class), anti-dark (G3 — no urgency/scarcity field), no-person-tracking (G5 — stateless w.r.t.
  searcher). The optional `from kotoba import datalog, llm` host binding is unused and is the omitted
  leg."
  (:require [clojure.string :as str]))

;; Affiliate / tracking params stripped from an onward airline link (G1). Mirrors okaimono.
(def ^:private AFFILIATE-PARAMS
  #{"aff" "affid" "affiliate" "partner" "partner_id" "clickid" "click_id" "subid"
    "tag" "ref" "referrer" "gclid" "fbclid" "msclkid" "irclickid" "ranmid" "siteid"})
(def ^:private AFFILIATE-PREFIXES ["utm_" "aff_" "pk_"])

(defn total-cost-minor
  "True total cost a traveller pays: base fare + checked-bag fee (G4 honesty — never just the
  headline fare)."
  [fare]
  (+ (long (get fare "fareMinor" 0)) (long (get fare "baggageMinor" 0))))

(defn strip-affiliate
  "Remove affiliate + tracking parameters from an airline URL (G1) — tsubasa earns no referral.
  Functional params (flight, date, cabin) are preserved; order is kept stable."
  [url]
  (let [[_ scheme netloc path query]
        (re-matches #"(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#.*)?" (or url ""))
        pairs (if (and query (not= query ""))
                (map (fn [p] (let [i (str/index-of p "=")]
                               (if i [(subs p 0 i) (subs p (inc i))] [p ""])))
                     (str/split query #"&"))
                [])
        kept (filter (fn [[k _]]
                       (let [kl (str/lower-case k)]
                         (and (not (AFFILIATE-PARAMS kl))
                              (not (some #(str/starts-with? kl %) AFFILIATE-PREFIXES)))))
                     pairs)
        q (str/join "&" (map (fn [[k v]] (str k "=" v)) kept))
        base (cond (and (seq scheme) (seq netloc)) (str scheme "://" netloc path)
                   (seq scheme) (str scheme ":" path)
                   :else (str netloc path))]
    (if (= q "") base (str base "?" q))))

;; ── search (G4 emissions surfaced, G3 honest, G5 stateless-w.r.t.-searcher) ──
(def ^:private SORTS
  {"total" total-cost-minor
   "emissions" (fn [f] (double (get f "co2Kg" 0.0)))
   "duration" (fn [f] (long (get f "durationMin" 0)))})

(defn search-fares
  "Return matching fares, each annotated with totalMinor + co2Kg (G4 — emissions on every option),
  ranked by `sort` (total cost default; or emissions / duration). Honest availability: no scarcity,
  no per-searcher state (G3/G5). Unknown sort falls back to total."
  ([origin destination depart-date fares] (search-fares origin destination depart-date fares "total"))
  ([origin destination depart-date fares sort]
   (let [key (get SORTS sort total-cost-minor)
         matches (->> fares
                      (filter #(and (= (get % "origin") origin)
                                    (= (get % "destination") destination)
                                    (= (get % "departDate") depart-date)))
                      (mapv (fn [f] (assoc f "totalMinor" (total-cost-minor f)
                                           "co2Kg" (double (get f "co2Kg" 0.0))))))]
     (vec (sort-by key matches)))))

;; ── compare — cheapest / greenest / fastest as first-class results (G4) ──────
(defn compare-fares
  "Expose the cheapest, greenest, and fastest options together so emissions is a first-class axis
  (G4) — a low-fare/high-CO₂ option cannot be presented while hiding a greener one."
  [fares]
  (if (empty? fares)
    {"cheapest" nil "greenest" nil "fastest" nil}
    {"cheapest" (apply min-key total-cost-minor fares)
     "greenest" (apply min-key (fn [f] (double (get f "co2Kg" 0.0))) fares)
     "fastest" (apply min-key (fn [f] (long (get f "durationMin" 0))) fares)}))

;; ── self-book handoff (G1 — member books on the airline's own site, no inflow) ──
(defn self-book-handoff
  "Hand the member to the airline's OWN booking page, affiliate-stripped (G1). tsubasa is not the
  merchant-of-record: no commission, no tithe, principal is the member."
  [fare]
  {"mode" "self-book-handoff"
   "principal" "member"                              ; tsubasa never books (G1)
   "carrier" (get fare "carrier")
   "bookUrl" (strip-affiliate (get fare "bookUrl" ""))
   "commissionMinor" 0                               ; structural zero (G1)
   "titheMinor" 0})                                  ; external: no internal value flow
