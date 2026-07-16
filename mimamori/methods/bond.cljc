(ns mimamori.methods.bond
  "mimamori 見守り — covenant bond lifecycle + schema validator + EAVT emit.
  Clojure port of `methods/bond.py` (mishmeret ha-adam, ADR-2606112300).

  The degeneration path 五人組→隣組→Stasi→social-credit is made UNREPRESENTABLE
  here, not merely prohibited:

    G1  care-route targets outside {kokoro, wakai, iyashi} throw (no denunciation rail)
    G2  any :mishmeret.person/* attr or score/risk token throws (no score-of-soul)
    G3  no active bond without the kept's explicit consent; decline/exit unconditional
    G4  bonds are visible to BOTH parties by construction (no hidden keeping)
    G5  queries are own-DID-only; no all-persons view (NEVER-a-throne, ADR-2606112200 D3)
    G6  heartbeats are content-free (the act only)
    G7  R0 accepts SYNTHETIC fictional DIDs only

  The engine is a pure value (plain map); every lifecycle op returns the next
  engine. Deterministic: tx = event cycle (no wall clock). Portable .cljc —
  runnable on JVM/babashka/SCI and the kotoba-clj WASM trajectory."
  (:require [clojure.string :as str]))

;; ── schema (the whole representable surface) ─────────────────────

(def attr-whitelist
  #{":mishmeret.bond/keeper" ":mishmeret.bond/kept" ":mishmeret.bond/state"
    ":mishmeret.bond/cycle" ":mishmeret.bond/ceremony"
    ":mishmeret.keep/bond" ":mishmeret.keep/act" ":mishmeret.keep/cycle"
    ":mishmeret.route/bond" ":mishmeret.route/to" ":mishmeret.route/consent"
    ":mishmeret.route/cycle"
    ":mishmeret.relay/bond" ":mishmeret.relay/from" ":mishmeret.relay/to"
    ":mishmeret.relay/cycle"})

(def forbidden-attr-prefixes [":mishmeret.person/"])          ;; G2: no person nodes at all
(def forbidden-tokens ["score" "risk" "isolation" "danger" "rating"]) ;; G2
(def care-whitelist #{":kokoro" ":wakai" ":iyashi"})          ;; G1: the ONLY route targets
(def states #{":offered" ":active" ":declined" ":exited" ":handed-off"})
(def synthetic-mark ":fictional:")                            ;; G7 (R0)
(def reoffer-cooldown 3)                                      ;; cycles (anti-pestering, G3)

(defn gate-violation
  "A mishmeret gate would be violated — the operation is unrepresentable."
  [msg]
  (ex-info msg {:mishmeret/gate-violation true}))

(defn gate-violation? [e]
  (boolean (:mishmeret/gate-violation (ex-data e))))

(defn validate-attr [attr]
  (doseq [p forbidden-attr-prefixes]
    (when (str/starts-with? attr p)
      (throw (gate-violation (str "G2: person-node attribute unrepresentable: " attr)))))
  (let [low (str/lower-case attr)]
    (doseq [t forbidden-tokens]
      (when (str/includes? low t)
        (throw (gate-violation (str "G2: score-of-soul token unrepresentable: " attr))))))
  (when-not (attr-whitelist attr)
    (throw (gate-violation (str "schema: unknown attr " attr " (whitelist-only)"))))
  attr)

(defn validate-did [did]
  (when-not (str/includes? did synthetic-mark)
    (throw (gate-violation (str "G7: R0 accepts SYNTHETIC fictional DIDs only: " did))))
  did)

;; ── engine (one value = one replayed log) ────────────────────────

(defn new-engine []
  {:datoms []          ;; [e a v tx op] — op is always :add
   :cycle 0
   :state {}           ;; bond-id → state
   :keeper {}          ;; bond-id → current keeper
   :kept {}            ;; bond-id → kept
   :declined-at {}})   ;; bond-id → cycle (cooldown)

(defn- add* [m e a v tx]
  (validate-attr a)
  (update m :datoms conj [e a v tx ":add"]))

(defn bond-id [keeper kept]
  (str "bond." keeper "." kept))

(defn- tick [m]
  (let [m (update m :cycle inc)]
    [m (:cycle m)]))

;; ── lifecycle ────────────────────────────────────────────────────

(defn offer [m keeper kept]
  (validate-did keeper)
  (validate-did kept)
  (let [bid (bond-id keeper kept)
        declined (get-in m [:declined-at bid])]
    (when (and declined (< (- (:cycle m) declined) reoffer-cooldown))
      (throw (gate-violation "G3: re-offer cooldown — a declined offer rests")))
    (let [[m tx] (tick m)]
      (-> m
          (assoc-in [:state bid] ":offered")
          (assoc-in [:keeper bid] keeper)
          (assoc-in [:kept bid] kept)
          (add* bid ":mishmeret.bond/keeper" keeper tx)
          (add* bid ":mishmeret.bond/kept" kept tx)
          (add* bid ":mishmeret.bond/state" ":offered" tx)
          (add* bid ":mishmeret.bond/cycle" tx tx)))))

(defn consent [m keeper kept]
  (let [bid (bond-id keeper kept)]
    (when (not= ":offered" (get-in m [:state bid]))
      (throw (gate-violation "G3: consent requires a standing offer")))
    (let [[m tx] (tick m)]
      (-> m
          (assoc-in [:state bid] ":active")
          (add* bid ":mishmeret.bond/state" ":active" tx)))))

(defn decline [m keeper kept]
  (let [bid (bond-id keeper kept)]
    (when (not= ":offered" (get-in m [:state bid]))
      (throw (gate-violation "G3: decline targets a standing offer")))
    (let [[m tx] (tick m)]
      (-> m
          (assoc-in [:state bid] ":declined")
          (assoc-in [:declined-at bid] tx)
          (add* bid ":mishmeret.bond/state" ":declined" tx)))))

(defn exit-bond
  "Unilateral, unconditional, penalty-free (G3). Appends — never erases."
  [m keeper kept]
  (let [bid (bond-id keeper kept)]
    (when (not= ":active" (get-in m [:state bid]))
      (throw (gate-violation "G3: exit targets an active bond")))
    (let [[m tx] (tick m)]
      (-> m
          (assoc-in [:state bid] ":exited")
          (add* bid ":mishmeret.bond/state" ":exited" tx)))))

(defn heartbeat
  "Content-free (G6): the ACT only. No message, no observation, no note."
  [m keeper kept]
  (let [bid (bond-id keeper kept)]
    (when (not= ":active" (get-in m [:state bid]))
      (throw (gate-violation "G3: keeping requires an active, consented bond")))
    (let [[m tx] (tick m)
          kid (str "keep." bid "." tx)]
      (-> m
          (add* kid ":mishmeret.keep/bond" bid tx)
          (add* kid ":mishmeret.keep/act" ":reached-out" tx)
          (add* kid ":mishmeret.keep/cycle" tx tx)))))

(defn route-care
  "G1: care actors ONLY. G3: the kept consents to THIS routing, each time."
  [m keeper kept to kept-consents?]
  (when-not (care-whitelist to)
    (throw (gate-violation (str "G1: route target unrepresentable: " to
                                " (care whitelist = " (vec (sort care-whitelist)) ")"))))
  (when-not kept-consents?
    (throw (gate-violation "G3: care routing requires the kept's consent, each time")))
  (let [bid (bond-id keeper kept)]
    (when (not= ":active" (get-in m [:state bid]))
      (throw (gate-violation "G3: routing requires an active bond")))
    (let [[m tx] (tick m)
          rid (str "route." bid "." tx)]
      (-> m
          (add* rid ":mishmeret.route/bond" bid tx)
          (add* rid ":mishmeret.route/to" to tx)
          (add* rid ":mishmeret.route/consent" true tx)
          (add* rid ":mishmeret.route/cycle" tx tx)))))

(defn handoff
  "Relay 継ぎ (G5): finite keepers in succession — no sleepless center.

  G3: the relay does NOT carry consent. The kept consented to THIS keeper,
  not to the next one — the incoming keeper's bond is left at :offered and
  the kept must consent explicitly (a keeping gap is acceptable; consent is
  not). Matches com.etzhayyim.mimamori.relayHandoff (fresh offer + consent)."
  [m keeper kept to-keeper]
  (validate-did to-keeper)
  (let [bid (bond-id keeper kept)]
    (when (not= ":active" (get-in m [:state bid]))
      (throw (gate-violation "G3: handoff targets an active bond")))
    (let [[m tx] (tick m)
          rid (str "relay." bid "." tx)
          m (-> m
                (assoc-in [:state bid] ":handed-off")
                (add* bid ":mishmeret.bond/state" ":handed-off" tx)
                (add* rid ":mishmeret.relay/bond" bid tx)
                (add* rid ":mishmeret.relay/from" keeper tx)
                (add* rid ":mishmeret.relay/to" to-keeper tx)
                (add* rid ":mishmeret.relay/cycle" tx tx))]
      (offer m to-keeper kept))))

;; ── queries (G4 + G5: own-DID-only; both parties see) ────────────

(defn bonds-of
  "Every bond in which `did` is a PARTY (keeper or kept) — and no other.
  G4: the kept always sees who keeps them. G5: there is no all-persons view."
  [m did]
  (->> (:state m)
       (keep (fn [[bid st]]
               (when (or (= did (get-in m [:keeper bid]))
                         (= did (get-in m [:kept bid])))
                 {:bond bid
                  :keeper (get-in m [:keeper bid])
                  :kept (get-in m [:kept bid])
                  :state st})))
       (sort-by :bond)
       vec))

;; ── EAVT emit ────────────────────────────────────────────────────

(defn emit [m]
  (let [render-v (fn [v]
                   (cond
                     (true? v) "true"
                     (false? v) "false"
                     (and (string? v) (str/starts-with? v ":")) v
                     (integer? v) (str v)
                     :else (str "\"" v "\"")))
        lines (concat
               [";; mimamori 見守り — GENERATED kotoba Datom log (ADR-2606112300). DO NOT hand-edit."
                ";; Canonical EAVT (ADR-2605312345). [e a v tx op] — append-only, op :add only."
                ";; bond-edge-only: there are NO :mishmeret.person/* datoms (G2, by construction)."
                "["]
               (for [[e a v tx op] (:datoms m)]
                 (str "[" (if (str/starts-with? e ":") e (str "\"" e "\""))
                      " " a " " (render-v v) " " tx " " op "]"))
               ["]"])]
    (str (str/join "\n" lines) "\n")))

;; ── seed replay ──────────────────────────────────────────────────

(defn load-seed
  "Validate a parsed seed map (G7). JSON parsing happens at the host edge."
  [seed]
  (when-not (:synthetic seed)
    (throw (gate-violation "G7: R0 seed must declare synthetic:true")))
  (doseq [did (:roster seed)]
    (validate-did did))
  seed)

(defn replay [seed]
  (reduce
   (fn [m {:keys [op keeper kept to to_keeper] :as ev}]
     (case op
       "offer"     (offer m keeper kept)
       "consent"   (consent m keeper kept)
       "decline"   (decline m keeper kept)
       "exit"      (exit-bond m keeper kept)
       "heartbeat" (heartbeat m keeper kept)
       "route"     (route-care m keeper kept to (boolean (:consent ev)))
       "handoff"   (handoff m keeper kept to_keeper)
       (throw (gate-violation (str "unknown op " op)))))
   (new-engine)
   (:events seed)))

#?(:clj
   (defn load-seed-file
     "Read + parse + validate a seed JSON file (babashka/JVM edge)."
     [path]
     (let [parse (requiring-resolve 'cheshire.core/parse-string)]
       (load-seed (parse (slurp (str path)) true)))))
