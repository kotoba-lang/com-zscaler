(ns kabuto.viz.build-viz-data
  "1:1 port of viz/build_viz_data.py build_payload + build_datoms (ADR-2606022000). Reads the company
  graph (via the already-ported kabuto-edn classify) and builds the supply-chain viz payload
  {actor, companies, edges, jurisdictions} + flattens it into the [{e,a,v_edn,added}] kotoba Datom
  array the in-browser kotoba-wasm node hydrates via loadDatoms (ADR-2606013600).

  A redundancy / accountability surface, NEVER a target-list (kabuto G2).

  Ported: build_payload, _edn_scalar, build_datoms (pure-stdlib, float-light). OMITTED (IO leg, not
  ported): main() seed-read + file-writing + the _template.htm viewer render. Like sukashi, the
  Python is already a dead import — its dependency methods/kabuto_edn.py was pruned in a prior wave."
  (:require [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]))

(defn- pyround
  "Python round(x, n) — half-to-even on a double. Mirrors analyze.cljc/pyround."
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      .doubleValue))

(defn build-payload
  "Mirrors build_payload(companies, addresses, contacts, edges)."
  [companies addresses contacts edges]
  (let [addr-by (reduce (fn [m a] (assoc m (get a ":company.address/company") a)) {} addresses)
        contact-by (reduce (fn [m c] (assoc m (get c ":company.contact/company") c)) {} contacts)
        ;; out-degree per supplier + jurisdiction criticality load (insertion-ordered for stable sort)
        out-deg (reduce (fn [m e] (update m (get e ":supply.edge/from") (fnil inc 0))) {} edges)
        {jur-order :order jur :m}
        (reduce (fn [{:keys [order m]} e]
                  (let [s (get e ":supply.edge/from")
                        country (get-in companies [s ":company/country"] "??")
                        crit (double (or (get e ":supply.edge/criticality" 0) 0))]
                    {:order (if (contains? m country) order (conj order country))
                     :m (update m country (fnil + 0.0) crit)}))
                {:order [] :m {}} edges)
        addr-obj (fn [cid]
                   (when-let [a (get addr-by cid)]
                     {"street" (get a ":company.address/street")
                      "city" (get a ":company.address/city")
                      "country" (get a ":company.address/country")
                      "lat" (get a ":company.address/lat")
                      "lon" (get a ":company.address/lon")}))
        contact-obj (fn [cid]
                      (when-let [c (get contact-by cid)]
                        {"website" (get c ":company.contact/website")
                         "ir" (get c ":company.contact/ir-url")}))
        node-list (mapv (fn [cid]
                          (let [c (get companies cid)]
                            {"id" cid
                             "name" (get c ":company/name" cid)
                             "ticker" (get c ":company/ticker")
                             "sector" (get c ":company/sector")
                             "country" (get c ":company/country")
                             "out" (get out-deg cid 0)
                             "address" (addr-obj cid)
                             "contact" (contact-obj cid)}))
                        (kedn/co-keys companies))
        edge-list (mapv (fn [e]
                          {"from" (get e ":supply.edge/from")
                           "to" (get e ":supply.edge/to")
                           "commodity" (str/replace (str (get e ":supply.edge/commodity" ":unknown")) #"^:+" "")
                           "criticality" (get e ":supply.edge/criticality" 0)})
                        edges)
        jurisdictions (->> jur-order
                           (map (fn [k] [k (get jur k)]))
                           (sort-by (fn [[_ v]] (- (double v))))   ; stable: ties keep insertion order
                           (mapv (fn [[k v]] {"country" k "load" (pyround v 2)})))]
    {"actor" "kabuto"
     "glyph" "兜"
     "adr" "2606022000"
     "note" "aggregate-first supply-chain resilience + transparency map; NOT a target-list (G2). sourcing :representative."
     "companies" node-list
     "edges" edge-list
     "jurisdictions" jurisdictions}))

(defn edn-scalar
  "Encode a value as an EDN scalar string for a kotoba Datom v_edn (mirrors _edn_scalar):
  nil/\"\" → nil; bool → true/false; int/float → its literal; \":kw\" passthrough; else quoted string."
  [v]
  (cond
    (nil? v) nil
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (pr-str v)
    :else (let [s (str v)]
            (cond
              (= s "") nil
              (str/starts-with? s ":") s
              :else (kedn/edn-str s)))))

(defn build-datoms
  "Mirrors build_datoms(payload) → [{e,a,v_edn,added}] (skips nil scalars)."
  [payload]
  (let [add (fn [acc e a v]
              (let [s (edn-scalar v)]
                (if (nil? s) acc (conj acc {"e" e "a" a "v_edn" s "added" true}))))
        company-datoms
        (reduce (fn [acc c]
                  (let [e (get c "id")
                        a (or (get c "address") {})
                        ct (or (get c "contact") {})]
                    (-> acc
                        (add e ":company/id" (get c "id"))
                        (add e ":company/name" (get c "name"))
                        (add e ":company/ticker" (get c "ticker"))
                        (add e ":company/sector" (get c "sector"))
                        (add e ":company/country" (get c "country"))
                        (add e ":company/out" (long (or (get c "out") 0)))
                        (add e ":company.address/street" (get a "street"))
                        (add e ":company.address/city" (get a "city"))
                        (add e ":company.address/country" (get a "country"))
                        (add e ":company.address/lat" (get a "lat"))
                        (add e ":company.address/lon" (get a "lon"))
                        (add e ":company.contact/website" (get ct "website"))
                        (add e ":company.contact/ir" (get ct "ir")))))
                [] (get payload "companies"))]
    (reduce (fn [acc ed]
              (if (or (not (get ed "from")) (not (get ed "to")))
                acc
                (let [e (str "supply.edge:" (get ed "from") ">>>" (get ed "to"))]
                  (-> acc
                      (add e ":supply.edge/from" (get ed "from"))
                      (add e ":supply.edge/to" (get ed "to"))
                      (add e ":supply.edge/commodity" (str ":" (get ed "commodity" "unknown")))
                      (add e ":supply.edge/criticality" (double (or (get ed "criticality") 0)))))))
            company-datoms (get payload "edges"))))
