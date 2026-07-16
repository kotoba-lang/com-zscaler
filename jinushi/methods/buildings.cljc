(ns jinushi.methods.buildings
  "jinushi 地主 — BUILDING-level ownership KG + company linkage.

  Extends jinushi from land-AREA coverage to per-BUILDING ownership: who owns which building,
  how many floors, and — via the owner's LEI / Wikidata QID — the bridge to the corporate KGs
  (kabuto 兜 supply-chain · uchiwake 内訳 BOM · kanjō 勘定 disclosure · keizu 系図 power · tsumugi
  紡ぎ 取-weave). This is the 不動産 (real-estate) ownership graph the registry/atlas needs.

  GATE (reframed per operator directive 2026-06-16 — 「土地建物は public data, 自然人であっても」):
    The constitution does NOT ban personal data; it bans ASYMMETRIC or MONETIZED surveillance
    (Rider v3.1 §2(c) reciprocity axis, ADR-2606082400) while AFFIRMING reciprocal/symmetric
    相互監視 (Tier-0 神の監視, the 村社会 transparency). Land/building ownership IS public record.
    So the gate is NOT 'exclude natural persons' — it is:
      P1 PUBLIC-RECORD provenance only (already-disclosed registry / open KG; never covert or
         inferred), marked on every source (:provenance/public-record true).
      P2 RECIPROCAL / SYMMETRIC — the registry is open to all equally; a parcel/building's owner
         is as visible as anyone's. This is mirrored transparency, not a one-way watch-feed.
      P3 MAP-NOT-TARGET, NON-MONETIZED — routed to commons-return / transparency, never emitted
         as a seizure / eviction / targeting list, never sold.
    Natural-person ownership is therefore representable from a public registry under P1–P3 (this
    Wikidata slice happens to be all legal entities). What stays unrepresentable is covert/
    inferred ownership, asymmetric watch-lists, and monetized resale of the records."
  (:require [clojure.string :as str]
            [jinushi.methods.datom-emit :as de]
            #?(:clj [clojure.java.io :as io])))

(defn analyze
  "Pure: building-ownership snapshot → ownership-KG view.
    :by-owner   owner-qid → {:label :lei :type :count :floors :buildings}
    :concentration {:owner-count :building-count :top-by-buildings [...] :hhi}
    :company-links  [{:owner :label :lei :wikidata}]  (the join keys to the corporate KGs)"
  [{:keys [owners records]}]
  (let [by-owner
        (reduce (fn [m r]
                  (let [o (:owner r) info (get owners o)]
                    (-> m
                        (update-in [o :count] (fnil inc 0))
                        (update-in [o :floors] (fnil + 0) (or (:floors r) 0))
                        (update-in [o :buildings] (fnil conj []) (:building r))
                        (assoc-in [o :label] (:label info))
                        (assoc-in [o :lei] (:lei info))
                        (assoc-in [o :type] (or (:type info) :org)))))
                {} records)
        ;; vertical-scale (ビルのフロア) 取-concentration: total floors controlled per owner
        top-floors (->> by-owner
                        (filter (fn [[_ v]] (pos? (or (:floors v) 0))))
                        (map (fn [[k v]] {:owner k :label (:label v) :floors (:floors v) :buildings (:count v)}))
                        (sort-by :floors >) (take 10) vec)
        n (count records)
        shares (into {} (map (fn [[k v]] [k (/ (double (:count v)) (max 1 n))])) by-owner)
        hhi (reduce + 0.0 (map (fn [s] (* 10000.0 s s)) (vals shares)))
        top (->> by-owner
                 (map (fn [[k v]] {:owner k :label (:label v) :buildings (:count v) :floors (:floors v)
                                   :share (get shares k)}))
                 (sort-by :buildings >) (take 10) vec)
        links (->> by-owner
                   (filter (fn [[_ v]] (:lei v)))
                   (map (fn [[k v]] {:owner k :label (:label v) :lei (:lei v) :wikidata k}))
                   (sort-by :label) vec)]
    {:by-owner by-owner
     :concentration {:owner-count (count by-owner) :building-count n
                     :top-by-buildings top :top-by-floors top-floors :hhi hhi
                     :buildings-with-floors (count (filter :floors records))}
     :company-links links}))

(defn datoms
  "Canonical EAVT Datom log for the building-ownership KG. Ground :building/* + :owner.org/*
  nodes + :owns edges; derived :jinushi/building-* concentration (transient)."
  ([snap analysis] (datoms snap analysis 1))
  ([{:keys [owners records]} analysis tx]
   (let [L (transient [])]
     (conj! L ";; jinushi 地主 — building-ownership KG (kotoba EAVT, ADR-2605312345). [e a v tx op].")
     (conj! L ";; PUBLIC-RECORD + SYMMETRIC (相互監視 affirmed); map-not-target; non-monetized.")
     (conj! L "[")
     ;; owner (legal-entity / public-record) nodes — carry LEI + Wikidata QID for corp-KG join
     (doseq [[qid info] (sort-by key owners)]
       (conj! L (str "[:owner." qid " :owner.org/wikidata " (de/fmt qid) " " tx " :add]"))
       (when (:label info) (conj! L (str "[:owner." qid " :owner.org/label " (de/fmt (:label info)) " " tx " :add]")))
       (when (:lei info)   (conj! L (str "[:owner." qid " :owner.org/lei " (de/fmt (:lei info)) " " tx " :add]")))
       (conj! L (str "[:owner." qid " :owner.org/type " (de/fmt (name (or (:type info) :org))) " " tx " :add]")))
     ;; building nodes + ownership edge
     (doseq [r (sort-by :building records)]
       (let [b (str "building." (:building r))]
         (conj! L (str "[:" b " :building/country " (de/fmt (:cc r)) " " tx " :add]"))
         (when (:floors r) (conj! L (str "[:" b " :building/floors " (de/fmt (:floors r)) " " tx " :add]")))
         (conj! L (str "[:" b " :building/owner :owner." (:owner r) " " tx " :add]"))))
     ;; derived building-取-concentration (transient)
     (conj! L ";; ── derived building-取-concentration (transient; aggregate, G2) ──")
     (let [c (:concentration analysis)]
       (conj! L (str "[:building.world :jinushi/building-hhi " (de/fmt (:hhi c)) " " tx " :derived] ;; :bond/is-transient true"))
       (doseq [t (:top-by-buildings analysis)]
         (conj! L (str "[:building.world :jinushi/top-owner :owner." (:owner t)
                       " " tx " :derived] ;; :bond/is-transient true buildings=" (:buildings t))))
       ;; vertical-scale: top owners by total FLOORS controlled (ビルのフロア 取-concentration)
       (doseq [t (:top-by-floors c)]
         (conj! L (str "[:building.world :jinushi/top-floors-owner :owner." (:owner t)
                       " " tx " :derived] ;; :bond/is-transient true floors=" (:floors t)))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn load-snapshot [dir]
     (let [f (io/file dir "wikidata-buildings.kotoba.edn")]
       (when (.exists f) (clojure.edn/read-string (slurp f))))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           snap (load-snapshot dir)]
       (if-not snap
         (println "no wikidata-buildings.kotoba.edn — operator fetch first")
         (let [a (analyze snap)
               c (:concentration a)
               out (io/file dir "building-ownership-datoms.kotoba.edn")]
           (spit out (datoms snap a 1))
           (println (format "buildings: %d / owners: %d / company-links (LEI): %d / building-HHI %.0f"
                            (:building-count c) (:owner-count c) (count (:company-links a)) (:hhi c)))
           (println (format "buildings-with-floors: %d" (:buildings-with-floors c)))
           (println "top owners by #buildings:")
           (doseq [t (take 5 (:top-by-buildings c))]
             (println (format "  %-32s %3d buildings, %d floors" (:label t) (:buildings t) (:floors t))))
           (println "top owners by TOTAL FLOORS controlled (vertical 取-concentration):")
           (doseq [t (take 5 (:top-by-floors c))]
             (println (format "  %-32s %5d floors across %d buildings" (:label t) (:floors t) (:buildings t))))
           (println (str "→ " out))))
       0)))
