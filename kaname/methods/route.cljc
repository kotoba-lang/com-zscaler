(ns kaname.methods.route
  "kaname 要 — route the leverage point to OPENING (ADR-2606172100; G2).

  Given the leverage result + nodes, recommends how to DISSOLVE the 要's concentration. The route
  enum contains ONLY concentration-reducing moves (open / route-around / add-redundancy /
  decentralize / insufficient-evidence). capture/seize/control are unrepresentable (gates/G2):
  the whole purpose of finding the 要 is to dissolve the chokepoint, never to grab it (abaki's
  route-around stance). The recommendation is keyed on the node's structure:

    - already-open / low-evidence            → :insufficient-evidence  (nothing to route)
    - high bridge (mediates many layers)      → :route-around           (build a parallel path)
    - high concentration, single supplier-ish → :add-redundancy         (second source)
    - high versatility interface/instrument   → :decentralize / :open   (federate the gate)

  Pure fns; reuses kaname.methods.sos + kaname.methods.gates. Portable .cljc."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            [kaname.methods.gates :as gates]
            #?(:clj [clojure.java.io :as io])))

(defn- recommend
  "Pick the opening-only route for node `nid` from its leverage structure. Returns a map
  {:id :label :route :rationale :leverage :C :V :bridge}. assert-route! re-checks the enum (G2)."
  [nid nodes res]
  (let [node  (get nodes nid {})
        _     (gates/assert-not-person! node)        ; G1
        _     (gates/assert-no-belief-score! node)   ; G5
        kind  (get node ":organism/kind")
        lv    (double (get-in res [:leverage nid] 0.0))
        c     (double (get-in res [:C nid] 0.0))
        v     (long   (get-in res [:V nid] 0))
        b     (double (get-in res [:bridge nid] 0.0))
        route (cond
                (<= lv 0.0)               ":insufficient-evidence"
                (>= b 3.0)               ":route-around"
                (and (>= v 3)
                     (#{":sos/interface" ":sos/instrument"} kind)) ":decentralize"
                (>= v 2)                 ":open"
                :else                    ":add-redundancy")
        rationale
        (case route
          ":insufficient-evidence" "already open / no concentration to dissolve"
          ":route-around"          (str "mediates " v " layers with bridge-load " (format "%.3f" b)
                                         " — build a parallel path so the field does not depend on this single gate")
          ":decentralize"          (str "a cross-domain " (subs (str kind) 5) " spanning " v
                                         " domains — federate the gate so no one position holds it")
          ":open"                  (str "concentration " (format "%.3f" c) " across " v
                                         " domains — open the position (open-standard / commons)")
          ":add-redundancy"        (str "concentration " (format "%.3f" c)
                                         " in a narrow span — add a second source to remove the single point"))]
    (gates/assert-route! route)
    {:id nid :label (get node ":organism/label" nid)
     :route route :rationale rationale
     :leverage lv :C c :V v :bridge b}))

(defn route-all
  "Recommend an opening-only route for the top-`limit` leverage nodes (deterministic order)."
  ([nodes res] (route-all nodes res 8))
  ([nodes res limit]
   (->> (sos/rank (:leverage res) nodes limit)
        (mapv (fn [[nid _ _]] (recommend nid nodes res))))))

(defn route-point
  "Route the single 要 (argmax leverage). Returns the recommendation map, or nil if none."
  [nodes res]
  (when-let [kp (sos/kaname-point res nodes)]
    (recommend (first kp) nodes res)))

(defn report-md
  "Render the OPENING-route report markdown."
  [nodes res]
  (let [recs (route-all nodes res)
        L (transient [])]
    (conj! L "# kaname 要 — opening-route plan (G2: dissolve, never capture)\n")
    (conj! L (str "> Every route DISSOLVES the 要's concentration (open / route-around / "
                  "add-redundancy / decentralize). capture / seize / control are "
                  "**unrepresentable** (structurally refused). kaname PROPOSES; ossekai "
                  "CARRIES (consent-bound, on-chain-logged, structural-first).\n"))
    (conj! L "| rank | structural position | route | leverage L | rationale |")
    (conj! L "|---:|---|---|---:|---|")
    (doseq [[i r] (map-indexed vector recs)]
      (conj! L (str "| " (inc i) " | " (:label r) " | `"
                    (if (str/starts-with? (:route r) ":") (subs (:route r) 1) (:route r))
                    "` | " (format "%.3f" (:leverage r)) " | " (:rationale r) " |")))
    (conj! L (str "\n---\n_kaname 要 · ADR-2606172100 · opening-only · the route enum cannot "
                  "express acquiring the 要. Live ingest / carried intervention G7/Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-sos.kotoba.edn"))
           outdir (io/file here "out")
           {:keys [nodes edges]} (sos/load-file* seed)
           res (sos/leverage nodes edges)]
       (.mkdirs outdir)
       (spit (io/file outdir "opening-route.md") (report-md nodes res))
       (when-let [r (route-point nodes res)]
         (println (str "kaname route 要: " (:label r) " → " (:route r))))
       0)))
