#!/usr/bin/env bb
;; Charter Rider v2.0 §2 content scanner (clj-native port of charter_rider.py).
;; Port of src/etzhayyim_organism/sensors/charter_rider.py
(ns etzhayyim-organism.sensors.charter-rider
  "Charter Rider v2.0 §2 content scanner.
   Heuristic classifier for the 8 prohibited categories enumerated in
   /CHARTER-RIDER.md §2(a)..(h), per ADR-2605192200. Faithful port of the
   Python scanner — same rules, same pattern text, same API semantics.

   API:
     (require 'etzhayyim-organism.sensors.charter-rider :as cr)
     (def r (cr/scan text))
     (:ok r)             ; => boolean
     (:hits r)           ; => [{:section :label :term :snippet} ...]
     (cr/reason r)       ; => \"ok\" | \"§2(a) 'weapon'…\"")

;; ---------------------------------------------------------------------------
;; Hit and ScanResult (mirror the Python @dataclass Hit + ScanResult)
;; ---------------------------------------------------------------------------

(defn ->Hit [section label term snippet]
  {:section section :label label :term term :snippet snippet})

(defn ->ScanResult [ok hits]
  {:ok ok :hits hits})

(defn reason
  "Return a human-readable summary of the scan result."
  [{:keys [ok hits]}]
  (if ok
    "ok"
    (->> (take 3 hits)
         (map #(str (:section %) " " (pr-str (:term %))))
         (clojure.string/join "; "))))

;; ---------------------------------------------------------------------------
;; §2 Rules — same patterns as the Python version (case-insensitive)
;; ---------------------------------------------------------------------------

(def ^:private rules
  "Each entry: [section label [compiled-Pattern...]]
   Mirrors _RULES in charter_rider.py — same regex text, same section labels."
  [["§2(a)" "WEAPONS AND MILITARY"
    [(re-pattern "(?i)\\b(weapon|munition|grenade|warhead|ballistic missile|battle tank|combat drone|combat aircraft|biological weapon|chemical weapon|nerve agent|cluster munition|landmine)\\b")]]

   ["§2(b)" "SPECULATIVE FINANCE"
    [(re-pattern "(?i)\\b(prediction market|leverage(d)?\\s+(derivative|trade)|perpetual swap|naked option|crypto casino|degen yield farm|memecoin pump)\\b")]]

   ["§2(c)" "SURVEILLANCE CAPITALISM"
    [(re-pattern "(?i)\\b(ad tracking|cross-site tracking|behavioral ad target|third[- ]party (analytics|tracker|cookie)|ad ?sense|ad ?words|meta pixel|google analytics 4 ad|affiliate link|sponsored post|buy now)\\b")
     (re-pattern "(?i)\\b(limited (time )?offer|discount code|promo code|click here to (buy|purchase|sign up))\\b")]]

   ["§2(d)" "FOSSIL FUEL EXTRACTION (NEW)"
    [(re-pattern "(?i)\\b(new (oil|gas) (well|field|drilling|extraction)|greenfield (oil|coal|gas)|coal mine expansion|tar sands|oil sands|fracking expansion)\\b")]]

   ["§2(e)" "SPECIALIST GATEKEEPING"
    [(re-pattern "(?i)\\b(paywall|certification fee monopoly|licensure rent|guild gatekeep)\\b")]]

   ["§2(f)" "MULTI-GENERATIONAL HARM"
    [(re-pattern "(?i)\\b(addictive (design|loop)|dark pattern|infinite scroll engagement|exploit (children|minor)|groom(ing)? (children|minor)|CSAM|non-consensual|deepfake (porn|sexual))\\b")]]

   ["§2(g)" "STRICT INDIVIDUALIST ONTOLOGY"
    [(re-pattern "(?i)\\b(strict individualism is (the )?(only )?valid|reject (all )?collective ontology|only the individual exists|no such thing as society)\\b")]]

   ["§2(h)" "WELLBECOMING SUBORDINATION VIOLATION"
    [(re-pattern "(?i)\\b(maximi[sz]e (user )?(engagement|retention|screen time) (at|above) (well[- ]being|wellbecoming)|dopamine[- ]loop optimi[sz]ation|engagement[- ]ranked feed override)\\b")]]])

;; ---------------------------------------------------------------------------
;; scan
;; ---------------------------------------------------------------------------

(defn scan
  "Return a ScanResult marking §2 violations found in `text`.
   Equivalent to `scan(text: str) -> ScanResult` in the Python module."
  [text]
  (if (or (nil? text) (= text ""))
    (->ScanResult true [])
    (let [hits (for [[section label patterns] rules
                     pat patterns
                     :let [matcher (re-matcher pat text)]
                     :when true
                     :let [found (loop [m (re-find matcher) acc []]
                                   (if (nil? m)
                                     acc
                                     (let [term    (if (string? m) m (first m))
                                           start   (.start matcher)
                                           end     (.end matcher)
                                           s-start (max 0 (- start 40))
                                           s-end   (min (count text) (+ end 40))
                                           snippet (-> (subs text s-start s-end)
                                                       (clojure.string/replace #"\n" " "))]
                                       (recur (re-find matcher)
                                              (conj acc (->Hit section label term snippet))))))]
                     hit found]
               hit)
          hits (vec hits)]
      (->ScanResult (empty? hits) hits))))

;; ---------------------------------------------------------------------------
;; explain
;; ---------------------------------------------------------------------------

(defn explain
  "Return a human-readable summary of what rules are active.
   Mirrors `explain()` in the Python module."
  []
  (let [lines (atom ["Charter Rider §2 scanner — active rules:"])]
    (doseq [[section label patterns] rules]
      (swap! lines conj (str "  " section " " label))
      (doseq [pat patterns]
        (let [s (.pattern pat)
              t (if (> (count s) 80) (str (subs s 0 80) "…") s)]
          (swap! lines conj (str "      " t)))))
    (swap! lines conj "")
    (swap! lines conj "Source of truth: /CHARTER-RIDER.md §2 (per ADR-2605192200 v2.0).")
    (clojure.string/join "\n" @lines)))
