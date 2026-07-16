(ns tsumugi.methods.ingest-scale
  "tsumugi 紡ぎ — §B scale-power ingest membrane (the OFFLINE core).
  Clojure port of methods/ingest_scale.py (1:1, the offline/normalize path). ADR-2606092000.

  Normalizes structural-public parent→child org relations (Wikidata-P749-shaped rows) into
  :pwr/* org nodes + :custodies :tie/* edges, re-validating EVERY candidate through
  analyze-scale's own gate validators (S1/S2/S4/S5) before admitting it; gate breaches are
  dropped + logged, never silently accepted.

  The LIVE Wikidata/GLEIF fetch (urllib) + the forage planner + the G7 --live operator gate
  live only in the Python main, omitted from this port; the offline membrane (normalize-rows /
  ingest-offline / make-* / admit) is pure stdlib, network-free. Depends on the already-ported
  analyze-scale (load-graph + validate-node/-tie). File I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-scale :as asc]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])))

(def COUNTRY-CODE
  {"japan" "jp" "united states" "us" "united states of america" "us"
   "united kingdom" "uk" "germany" "de" "south korea" "kr"
   "republic of korea" "kr" "taiwan" "tw" "france" "fr" "china" "cn"
   "netherlands" "nl" "switzerland" "ch" "canada" "ca" "india" "in"
   "czech republic" "cz" "czechia" "cz" "italy" "it" "spain" "es"
   "sweden" "se" "australia" "au" "brazil" "br" "russia" "ru"})

(def PARENT-ALIASES
  {"toyota" "org.ext.toyota-motor" "toyota motor" "org.ext.toyota-motor"
   "meta" "org.ext.meta-platforms" "meta platforms" "org.ext.meta-platforms"
   "volkswagen group" "org.ext.volkswagen-ag" "volkswagen ag" "org.ext.volkswagen-ag"
   "alphabet inc." "org.ext.alphabet-inc" "general motors" "org.corp.us.gm"
   "tsmc" "org.corp.tw.tsmc-hsinchu" "softbank group" "org.ext.softbank-group"
   "hitachi" "org.corp.jp.hitachi-works" "mitsubishi heavy industries" "org.corp.jp.7011"
   "google" "org.ext.google-llc" "google llc" "org.ext.google-llc"
   "audi ag" "org.ext.audi" "audi" "org.ext.audi"
   "volkswagen" "org.ext.volkswagen-ag" "sony" "org.ext.sony-group"})

(defn slug [label]
  (let [s (-> (str (or label "")) str/lower-case (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"^-+|-+$" ""))]
    (if (str/blank? s) "x" s)))

(defn locality-of [country]
  (if (str/blank? (str country))
    "ext.unknown"
    (let [c (str/lower-case (str/trim (str country)))]
      (if (re-matches #"[a-z]{2}" c)
        (if (= c "gb") "uk" c)
        (get COUNTRY-CODE c (str "ext." (slug country)))))))

(defn make-org-node
  "A structural-public org node. S2: always :institutional; S1: no score attr."
  [label country]
  {":pwr/id" (str "org.ext." (slug label))
   ":pwr/label" label
   ":pwr/standing" ":institutional"
   ":pwr/scale" ":national"
   ":pwr/sector" ":san"
   ":pwr/locality" (locality-of country)
   ":pwr/collective-kind" ":keiretsu"
   ":pwr/sourcing" ":representative"})

(defn- last-seg [id] (last (str/split id #"\.")))

(defn make-custody-tie
  "parent :custodies child. S4: ≥2 public citations; S5: factual kind only."
  ([parent-id child-id child-label parent-label child-ref] (make-custody-tie parent-id child-id child-label parent-label child-ref nil))
  ([parent-id child-id child-label parent-label child-ref cite]
   (let [cite (or cite
                  ["Wikidata WDQS P749 (parent-organization statement)"
                   (if (and child-ref (re-matches #"Q\d+" (str child-ref)))
                     (str "https://www.wikidata.org/wiki/" child-ref)
                     (str "Wikidata item: " child-label " (P749 parent → " parent-label ")"))])]
     {":tie/id" (str "tie.ext." (last-seg parent-id) "." (last-seg child-id))
      ":tie/kind" ":custodies"
      ":tie/from" parent-id ":tie/to" child-id
      ":tie/grasping-load" 0.6
      ":tie/sources" [(first cite) (second cite)]
      ":tie/sourcing" ":representative"})))

(defn edn
  "Serialise one record to a single-line EDN map (mirror of the Python edn())."
  [rec]
  (str "{"
       (str/join " "
                 (map (fn [[k v]]
                        (cond
                          (boolean? v) (str k " " (if v "true" "false"))
                          (sequential? v) (str k " [" (str/join " " (map (fn [x] (if (str/starts-with? (str x) ":") (str x)
                                                                                    (str "\"" (-> (str x) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))) v)) "]")
                          (number? v) (str k " " v)
                          (and (string? v) (str/starts-with? v ":")) (str k " " v)
                          :else (str k " \"" (-> (str v) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")))
                      rec))
       "}"))

(defn- admit
  "Re-validate a candidate node through analyze-scale; on a gate breach drop+log, return false."
  [node dropped]
  (try (asc/validate-node node) [true dropped]
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
         [false (conj dropped [(get node ":pwr/id") (subs (str (ex-message e)) 0 (min 80 (count (str (ex-message e)))))])])))

(defn- admit-tie [tie dropped]
  (try (asc/validate-tie tie) [true dropped]
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
         [false (conj dropped [(get tie ":tie/id") (subs (str (ex-message e)) 0 (min 80 (count (str (ex-message e)))))])])))

(defn normalize-rows
  "rows [{child parent country childRef}] → [nodes ties new-nodes new-ties dropped], each
  candidate crossing the S1/S2/S4/S5 membrane (analyze-scale validators)."
  [rows seed]
  (let [[nodes ties] (asc/load-graph seed)
        init {:seen-nodes (set (keys nodes))
              :seen-ties (set (map #(get % ":tie/id") ties))
              :new-nodes [] :new-ties [] :dropped []}
        final
        (reduce
         (fn [acc r]
           (let [child (str/trim (str (or (get r "child") "")))
                 parent (str/trim (str (or (get r "parent") "")))]
             (cond
               (or (str/blank? child) (str/blank? parent) (= child parent))
               (update acc :dropped conj [(if (str/blank? child) "?" child) "missing/degenerate org pair"])
               (or (re-matches #"Q\d+" child) (re-matches #"Q\d+" parent))
               (update acc :dropped conj [child "no real label (raw QID) — quality drop"])
               :else
               (let [calias (get PARENT-ALIASES (str/lower-case child))
                     [child-id cnode] (if (and calias (contains? (:seen-nodes acc) calias))
                                        [calias nil]
                                        (let [n (make-org-node child (get r "country"))] [(get n ":pwr/id") n]))
                     palias (get PARENT-ALIASES (str/lower-case parent))
                     [parent-id pnode] (if (and palias (contains? (:seen-nodes acc) palias))
                                         [palias nil]
                                         (let [n (make-org-node parent (get r "country"))] [(get n ":pwr/id") n]))
                     ;; candidates: parent first (insert(0)), then child
                     candidates (filterv some? [pnode cnode])
                     acc (reduce (fn [a n]
                                   (if (contains? (:seen-nodes a) (get n ":pwr/id"))
                                     a
                                     (let [[ok? dropped] (admit n (:dropped a))]
                                       (if ok?
                                         (-> a (update :new-nodes conj n)
                                             (update :seen-nodes conj (get n ":pwr/id"))
                                             (assoc :dropped dropped))
                                         (assoc a :dropped dropped)))))
                                 acc candidates)
                     tie (make-custody-tie parent-id child-id child parent (get r "childRef") (get r "cite"))]
                 (if (contains? (:seen-ties acc) (get tie ":tie/id"))
                   acc
                   (let [[ok? dropped] (admit-tie tie (:dropped acc))]
                     (if ok?
                       (-> acc (update :new-ties conj tie)
                           (update :seen-ties conj (get tie ":tie/id"))
                           (assoc :dropped dropped))
                       (assoc acc :dropped dropped))))))))
         init rows)]
    [nodes ties (:new-nodes final) (:new-ties final) (:dropped final)]))

#?(:clj
   (defn ingest-offline
     "Read all *.json fixtures in a dir → rows → normalize-rows (hermetic, no network)."
     [fixtures-dir seed]
     (let [rows (->> (.listFiles (io/file (str fixtures-dir)))
                     (map #(.getPath %))
                     (filter #(str/ends-with? % ".json"))
                     sort
                     (mapcat (fn [f] (get (json/parse-string (slurp f)) "orgs")))
                     (mapv (fn [o] {"child" (get o "child") "parent" (get o "parent")
                                    "country" (get o "country") "childRef" (get o "childRef" "Q")})))]
       (normalize-rows rows seed))))

;; ── 粘菌/菌糸 foraging (offline, derived from the seed itself) ───────────────────────────────
#?(:clj
   (defn derive-seed-qids
     "RING-2 self-expansion: every promoted tie cites its child's Wikidata item URL, so the
     committed seed ALREADY NAMES the QIDs of orgs in the graph. Returns that set of QIDs."
     [seed-path]
     (set (map second (re-seq #"https://www\.wikidata\.org/wiki/(Q\d+)" (slurp (str seed-path)))))))

#?(:clj
   (defn forage-plan
     "Offline foraging plan from the seed: an org already a :tie/from is HARVESTED; an org leaf
     (sector-bearing, no outgoing tie) is a FRONTIER TIP; empty QID frontier ⇒ STARVATION → fruit
     (switch substrate). Pure-offline + deterministic — the cloud loop reads this to grow toward
     food, not on a clock. Mirror of the Python forage_plan."
     [seed-path]
     (let [[nodes ties] (asc/load-graph seed-path)
           parents (set (map #(get % ":tie/from") ties))
           seed-qids (derive-seed-qids seed-path)
           {:keys [harvested frontier]}
           (reduce (fn [acc [nid n]]
                     (cond
                       (contains? parents nid) (update acc :harvested conj nid)
                       (and (get n ":pwr/sector") (str/starts-with? nid "org."))
                       (update acc :frontier conj nid)
                       :else acc))
                   {:harvested [] :frontier []} nodes)
           starving (or (empty? seed-qids) (empty? frontier))]
       {"harvested_anchors" (count harvested)
        "frontier_tips" (count frontier)
        "frontier_sample" (vec (take 15 (sort frontier)))
        "anchor_qids_available" (count seed-qids)
        "starving" starving
        "recommendation" (if starving
                           "FRUIT → switch substrate (Wikidata exhausted): run --gleif, or add a new registry anchor source"
                           (str "GROW → next ring anchors on " (count frontier) " frontier tips (--ring2)"))
        "niche" "植物-producer also publishes (publish.py) — the colony feeds humanity, not only itself"})))

#?(:clj
   (defn -main
     "CLI entry: mirrors ingest_scale.main offline + --forage paths. --forage writes a substrate
     foraging plan; offline ingest-offline(fixtures, seed) → out/scale-ingested.kotoba.edn +
     out/seed-plus-ingest-scale.kotoba.edn (out/ only). --live (Wikidata/GLEIF) is G7-gated network
     and not ported (refused). ADR-2606261200 cljc-native operator leg."
     [& argv]
     (let [argv     (vec argv)
           here     (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                          pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                      (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           seed     (io/file here "data" "seed-scale-power.kotoba.edn")
           fixtures (io/file here "data" "ingest-scale")
           out      (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))]
       (.mkdirs out)
       (cond
         (some #{"--forage"} argv)
         (let [plan (forage-plan (.getPath seed))]
           (spit (io/file out "forage-plan.json") (json/generate-string plan {:pretty true}))
           (println (str "[tsumugi/forage] " (get plan "recommendation") " → out/forage-plan.json")))
         (some #{"--live"} argv)
         (binding [*out* *err*]
           (println (str "REFUSED: live scale ingest (Wikidata/GLEIF) is G7-gated network + an R0 "
                         "scaffold (not ported). Requires TSUMUGI_OPERATOR_GATE=1 + "
                         "TSUMUGI_OPERATOR_DID; offline fixture ingest runs without --live.")))
         :else
         (let [[_n _t new-nodes new-ties _d] (ingest-offline (.getPath fixtures) (.getPath seed))
               enode     (fn [coll] (str/join "\n" (map #(str " " (edn %)) coll)))
               add-lines (concat [";; tsumugi 紡ぎ — GENERATED scale ingest (ADR-2606061500). DO NOT hand-edit."
                                  ";; :representative; out/ only — promotion into the committed seed is a separate human-reviewed PR."
                                  "[" ";; ── ingested org nodes ──"]
                                 (map #(str " " (edn %)) new-nodes)
                                 [";; ── ingested custody 縁 ──"]
                                 (map #(str " " (edn %)) new-ties)
                                 ["]"])
               add-file  (io/file out "scale-ingested.kotoba.edn")
               base      (str/trimr (slurp seed))
               combined  (io/file out "seed-plus-ingest-scale.kotoba.edn")]
           (spit add-file (str (str/join "\n" add-lines) "\n"))
           (spit combined (str (subs base 0 (dec (count base)))
                               "\n ;; ── INGESTED ──\n" (enode new-nodes) "\n" (enode new-ties) "]\n"))
           (println (str "[tsumugi/ingest-scale] +" (count new-nodes) " nodes · +"
                         (count new-ties) " 縁 → " (.getPath add-file))))))))
