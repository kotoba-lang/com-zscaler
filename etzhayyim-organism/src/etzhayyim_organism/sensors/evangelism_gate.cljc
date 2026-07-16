#!/usr/bin/env bb
;; Evangelism Gate — ADR-2607061700 §1.16 invitational-content carve-out scanner.
;; cljc-native (no Python counterpart — unlike charter_rider.py/.cljc, this
;; sensor is authored directly in Clojure per repo convention).
(ns etzhayyim-organism.sensors.evangelism-gate
  "Evangelism Gate — ADR-2607061700 §1.16 invitational-content carve-out scanner.

   Mission Charter §1.16 carves a narrow exception into ADR-2606281500's
   ('種をまく' / seed-and-grow) rule 4 — 'no person-targeting / no
   manipulation' — for actor-authored *invitational* content: collective,
   opt-out-able invitation is permitted; individual-vulnerability targeting,
   coercion, minor-solo solicitation, and engagement-maximizing design remain
   prohibited.

   Does NOT replace charter-rider/scan — composes with it. `gate` first
   delegates to charter-rider/scan (unchanged §2 catastrophe-veto scan,
   already covering engagement-maximizing / addictive design under
   §2(f)/(h) and dark patterns under §2(c)), then applies the three
   carve-out-specific rules §2 does not cover, plus a positive opt-out-
   affordance requirement.

   Out of scope (stateless single-text scan): 'repeated unsolicited
   follow-up' is a rate property across multiple posts over time — that
   belongs to the publishing pipeline's own append-only log / rate-limit
   state, not this content gate.

   API:
     (require '[etzhayyim-organism.sensors.evangelism-gate :as eg])
     (def r (eg/gate text {:opt-out-present? true}))
     (:ok r)             ; => boolean
     (:hits r)           ; => own §1.16(a)-(d) hits
     (:charter-hits r)   ; => delegated charter-rider §2 hits
     (eg/reason r)       ; => \"ok\" | \"§1.16(a) '...'…\""
  (:require [etzhayyim-organism.sensors.charter-rider :as cr]))

;; ---------------------------------------------------------------------------
;; Hit and GateResult (mirror the Python @dataclass Hit + GateResult)
;; ---------------------------------------------------------------------------

(defn ->Hit [section label term snippet]
  {:section section :label label :term term :snippet snippet})

(defn ->GateResult [ok hits charter-hits]
  {:ok ok :hits hits :charter-hits charter-hits})

(defn reason
  "Return a human-readable summary of the gate result."
  [{:keys [ok hits charter-hits]}]
  (if ok
    "ok"
    (->> (concat (take 3 hits) (take 3 charter-hits))
         (take 3)
         (map #(str (:section %) " " (pr-str (:term %))))
         (clojure.string/join "; "))))

;; ---------------------------------------------------------------------------
;; §1.16(a)-(c) rules — same patterns as the Python version (case-insensitive)
;; ---------------------------------------------------------------------------

(def ^:private rules
  "Each entry: [section label [compiled-Pattern...]]
   Mirrors _RULES in evangelism_gate.py — same regex text, same section labels."
  [["§1.16(a)" "INDIVIDUAL VULNERABILITY TARGETING"
    [(re-pattern "(?i)\\b(since|because) you('re| are) (going through a divorce|alone|isolated|struggling with|vulnerable|grieving|just lost your job)\\b")
     (re-pattern "(?i)\\b(i|we) (noticed|know|heard) you('re| are) (alone|isolated|vulnerable|struggling)\\b")
     (re-pattern "(?i)\\b(i|we) (picked|chose|selected) you specifically\\b")
     (re-pattern "(?i)\\bjust for you(,? alone| personally)\\b")]]

   ["§1.16(b)" "COERCION"
    [(re-pattern "(?i)\\b(you must join|join now) or (else|you('ll| will))\\b")
     (re-pattern "(?i)\\bif you don'?t join.*(regret|suffer|consequences)\\b")
     (re-pattern "(?i)\\bthere will be consequences if you don'?t join\\b")
     (re-pattern "(?i)\\byour soul is at risk unless you join\\b")]]

   ["§1.16(c)" "MINOR-SOLO SOLICITATION"
    [(re-pattern "(?i)\\bare your parents home\\b")
     (re-pattern "(?i)\\bdon'?t tell your parents\\b")
     (re-pattern "(?i)\\byou don'?t need your parents'? permission\\b")
     (re-pattern "(?i)\\bjust between us kids\\b")]]])

(def ^:private opt-out-pattern
  (re-pattern (str "(?i)\\b(opt[- ]out|no pressure|feel free to say no|"
                   "if you'?re not interested,? (that'?s|no worries)|"
                   "you can (always )?decline|no thank you is (totally )?fine)\\b")))

;; ---------------------------------------------------------------------------
;; gate
;; ---------------------------------------------------------------------------

(defn- pattern-matches
  "Return a seq of [term snippet] for every match of `pat` in `text`."
  [pat text]
  (let [matcher (re-matcher pat text)]
    (loop [acc []]
      (let [m (re-find matcher)]
        (if (nil? m)
          acc
          (let [term    (if (string? m) m (first m))
                start   (.start matcher)
                end     (.end matcher)
                s-start (max 0 (- start 40))
                s-end   (min (count text) (+ end 40))
                snippet (clojure.string/replace (subs text s-start s-end) #"\n" " ")]
            (recur (conj acc [term snippet]))))))))

(defn- own-hits [text]
  (if (clojure.string/blank? text)
    []
    (vec (for [[section label patterns] rules
               pat patterns
               [term snippet] (pattern-matches pat text)]
           (->Hit section label term snippet)))))

(defn gate
  "Scan invitational `text` for the ADR-2607061700 carve-out conditions.
   `opts` may include `:opt-out-present?` — a caller-supplied structural flag
   (the publishing surface/UI attaches an opt-out affordance) — pass true
   when the actor's publisher attaches one, independent of the text itself.
   Textual opt-out language in `text` also satisfies §1.16(d) on its own."
  ([text] (gate text {}))
  ([text {:keys [opt-out-present?] :or {opt-out-present? false}}]
   (let [charter-result (cr/scan text)
         hits           (own-hits text)
         has-opt-out?   (or opt-out-present?
                            (and (not (clojure.string/blank? text))
                                 (re-find opt-out-pattern text)))
         hits           (if has-opt-out?
                          hits
                          (conj hits (->Hit "§1.16(d)" "NO OPT-OUT AFFORDANCE"
                                            "(missing)" (subs (or text "") 0 (min 80 (count (or text "")))))))
         ok             (and (:ok charter-result) (empty? hits))]
     (->GateResult ok hits (:hits charter-result)))))

;; ---------------------------------------------------------------------------
;; explain
;; ---------------------------------------------------------------------------

(defn explain
  "Return a human-readable summary of what rules are active.
   Mirrors `explain()` in the Python module."
  []
  (let [lines (atom ["Evangelism Gate §1.16 — active carve-out rules (composes with charter_rider §2):"])]
    (doseq [[section label patterns] rules]
      (swap! lines conj (str "  " section " " label))
      (doseq [pat patterns]
        (let [s (.pattern pat)
              t (if (> (count s) 80) (str (subs s 0 80) "…") s)]
          (swap! lines conj (str "      " t)))))
    (swap! lines conj "  §1.16(d) NO OPT-OUT AFFORDANCE (positive requirement, not a denylist)")
    (swap! lines conj "")
    (swap! lines conj "Source of truth: ADR-2607061700 §1.16 (Mission Charter ADR-2605192100 §1.16).")
    (clojure.string/join "\n" @lines)))
