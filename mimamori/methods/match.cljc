(ns mimamori.methods.match
  "mimamori offer-matching cell (ADR-2606112300 §D4) — Clojure port of `methods/match.py`.

  誰の保持者でもない人間を作らない — the matching cell reaches each unkept roster
  member DIRECTLY with one covenant offer at a time, never via a public list:

    G5  no global person view leaves this namespace — the returned summary is
        aggregate-only (counts); the offers themselves exist as bond datoms,
        visible to their two parties (G4) and no one else.
    G3  cooldown respected (a declined offer rests); an offer is an OFFER —
        the matched member may decline or ignore it, penalty-free.
    cap each keeper carries at most `max-kept` active+offered bonds — keeping is
        covenant, not a queue (Wellbecoming §1.13); the relay exists so that no
        keeper becomes a sleepless center (D3).

  Deterministic: candidates are sorted; assignment is round-robin over the
  least-loaded willing keepers. No wall clock, no randomness."
  (:require [clojure.string :as str]
            [mimamori.methods.bond :as bond]))

(def max-kept
  "Bonds (active + standing offers) a keeper may carry."
  2)

(defn keeper-load [m did]
  (count (filter #(and (= did (:keeper %))
                       (#{":active" ":offered"} (:state %)))
                 (bond/bonds-of m did))))

(defn- place-offer
  "Try the least-loaded willing keepers in order; a cooldown pair rests and the
  next keeper is tried. Returns [m' outcome]."
  [m member members]
  (let [keepers (->> members
                     (remove #{member})
                     (filter #(< (keeper-load m %) max-kept))
                     (sort-by (fn [k] [(keeper-load m k) k])))]
    (loop [m m
           ks keepers]
      (if-let [k (first ks)]
        (let [r (try
                  [(bond/offer m k member) :offered]
                  (catch #?(:clj Exception :cljs :default) e
                    (if (and (bond/gate-violation? e)
                             (str/includes? (ex-message e) "cooldown"))
                      nil
                      (throw e))))]
          (if r
            r
            (recur m (rest ks))))
        [m (if (seq keepers) :skipped-cooldown :skipped-capacity)]))))

(defn match-cycle
  "One matching pass: offer a keeper to every unkept member, capacity permitting.
  Returns [engine' summary] — the summary is AGGREGATE-ONLY (G5)."
  [m roster]
  (let [members (vec (sort (set roster)))
        kept-or-offered (into #{}
                              (keep (fn [[bid st]]
                                      (when (#{":active" ":offered"} st)
                                        (get-in m [:kept bid]))))
                              (:state m))
        unkept (vec (remove kept-or-offered members))
        [m counts]
        (reduce (fn [[m counts] member]
                  (let [[m outcome] (place-offer m member members)]
                    [m (update counts outcome inc)]))
                [m {:offered 0 :skipped-cooldown 0 :skipped-capacity 0}]
                unkept)]
    [m {:unkept-before (count unkept)
        :offers-emitted (:offered counts)
        :skipped-cooldown (:skipped-cooldown counts)
        :skipped-capacity (:skipped-capacity counts)}]))
