(ns kaname.methods.osekkai
  "kaname 要 — おせっかい proposal emitter (ADR-2606172100; G1/G3).

  Turns the 要 + its opening-route into a DRAFTED-UNSENT proposal handed off to ossekai
  (ADR-2605264000), which carries it consent-bound + on-chain-logged + structural-first (§1.4).
  kaname itself NEVER actuates (no-server-key): the proposal is advisory, `:sent false`,
  `:carrier :ossekai`.

  G1 — every proposal addresses a STRUCTURAL POSITION. assert-not-person! refuses a natural
       person or a coordinate (a public ROLE is allowed; a private profile is not). There is no
       way to emit a per-person target.
  G3 — the proposal is transparent + consent-bound: it states the structural change FIRST, names
       the carrier (ossekai), and is unsent. Coercion / manipulation / campaign verbs are not
       representable in the proposal shape.

  Pure fns; reuses sos + route + gates. Portable .cljc."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            [kaname.methods.route :as route]
            [kaname.methods.gates :as gates]
            #?(:clj [clojure.java.io :as io])))

(defn proposal
  "Build the おせっかい proposal for a single recommendation map (from route/recommend).
  Re-asserts G1 on the target node. Returns the proposal map (advisory, unsent)."
  [rec nodes]
  (let [node (get nodes (:id rec) {})]
    (gates/assert-not-person! node)            ; G1 — structural position only
    (gates/assert-no-belief-score! node)       ; G5
    {:id            (str "kaname.proposal." (str/replace (str (:id rec)) #"[^a-zA-Z0-9.]" "-"))
     :target        (:id rec)
     :target-label  (:label rec)
     :route         (:route rec)
     :leverage      (:leverage rec)
     :rationale     (:rationale rec)
     :structural-first true
     :carrier       ":ossekai"
     :advisory      true
     :sent          false
     :server-key    false}))

(defn proposals
  "Build proposals for the top-`limit` leverage positions (deterministic order)."
  ([nodes res] (proposals nodes res 5))
  ([nodes res limit]
   (->> (route/route-all nodes res limit)
        (remove #(= ":insufficient-evidence" (:route %)))
        (mapv #(proposal % nodes)))))

(defn point-proposal
  "The おせっかい proposal for the single 要, or nil if none / insufficient evidence."
  [nodes res]
  (when-let [r (route/route-point nodes res)]
    (when-not (= ":insufficient-evidence" (:route r))
      (proposal r nodes))))

(defn report-md
  "Render the おせっかい handoff report markdown (advisory; ossekai carries)."
  [nodes res]
  (let [ps (proposals nodes res 5)
        L (transient [])]
    (conj! L "# kaname 要 — おせっかい handoff (advisory · unsent · ossekai carries)\n")
    (conj! L (str "> kaname PROPOSES; **ossekai CARRIES** (consent-bound, on-chain-logged, "
                  "structural-first §1.4). Every proposal targets a STRUCTURAL POSITION — no "
                  "natural person, no coordinate (G1). All proposals are `advisory:true`, "
                  "`sent:false`, `server-key:false` (kaname never actuates).\n"))
    (conj! L "| # | structural position | route | leverage | proposal |")
    (conj! L "|---:|---|---|---:|---|")
    (doseq [[i p] (map-indexed vector ps)]
      (conj! L (str "| " (inc i) " | " (:target-label p) " | `"
                    (if (str/starts-with? (:route p) ":") (subs (:route p) 1) (:route p))
                    "` | " (format "%.3f" (:leverage p)) " | " (:rationale p) " |")))
    (conj! L (str "\n---\n_kaname 要 · ADR-2606172100 · おせっかい → ossekai (ADR-2605264000) · "
                  "advisory/unsent · no-server-key. Carrying a proposal that touches a real "
                  "institution is G7/Council-gated (1 SBT = 1 vote)._\n"))
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
       (spit (io/file outdir "osekkai-handoff.md") (report-md nodes res))
       (when-let [p (point-proposal nodes res)]
         (println (str "kaname おせっかい: " (:target-label p) " → " (:route p)
                       " (advisory, unsent, carrier=ossekai)")))
       0)))
