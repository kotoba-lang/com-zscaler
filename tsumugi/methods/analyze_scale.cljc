(ns tsumugi.methods.analyze-scale
  "tsumugi 紡ぎ — (A) scale-agnostic 産官学報 power-concentration analyzer.
  Clojure port of methods/analyze_scale.py (1:1). ADR-2606092000.

  Reads a kotoba-EDN scale-power graph (:pwr/* nodes + :tie/* 縁) and computes, EDGE-PRIMARY
  and AGGREGATE-FIRST, how tightly each LOCALITY and each SCALE weaves its 産官学報
  (industry/government/academia/press) sectors together — routed to OPENING. A structural
  map, never a verdict and never a target-list.

  Constitutional gates enforced in load/validate:
    S1 edge-primary — concentration is the integral of incident cross-sector :tie/grasping-load;
       NO per-node score (a :pwr/power-score etc. attr raises).
    S2 person-excluded — :pwr/standing ∈ {:institutional :public-seat}; :private-person raises.
    S5 non-adjudicating — verdict tokens (癒着/談合/capture…) as a :tie/kind raise.

  Pure stdlib (pywasm-ready); file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal EDN reader (manual-replace strings, matching analyze_scale._atom) ──────────────
(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (keep (fn [m] (when (vector? m) (second m))) (re-seq token-re s)))

(defn- atom* [t]
  (cond
    (str/starts-with? t "\"") (-> (subs t 1 (dec (count t)))
                                  (str/replace "\\\"" "\"") (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else (or (try #?(:clj (Long/parseLong t) :cljs (let [n (js/parseInt t 10)]
                                                      (when (re-matches #"[-+]?\d+" t) n)))
                   (catch #?(:clj Exception :cljs :default) _ nil))
              (try #?(:clj (Double/parseDouble t) :cljs (let [n (js/parseFloat t)]
                                                          (when-not (js/isNaN n) n)))
                   (catch #?(:clj Exception :cljs :default) _ nil))
              t)))

(def ^:private END ::end)

(defn- parse-form [state]
  (let [t (first @state)]
    (vswap! state rest)
    (cond
      (= t "[") (loop [out []] (let [x (parse-form state)] (if (= x END) out (recur (conj out x)))))
      (= t "{") (loop [out {}] (let [k (parse-form state)]
                                 (if (= k END) out (recur (assoc out k (parse-form state))))))
      (or (= t "]") (= t "}")) END
      :else (atom* t))))

(defn read-edn [text] (parse-form (volatile! (seq (tokens text)))))

;; ── closed vocabs (mirror the ontology :db/allowed) ───────────────────────────────────────
(def SECTORS [":san" ":kan" ":gaku" ":hou" ":min" ":kin"])
(def SECTOR-JA {":san" "産" ":kan" "官" ":gaku" "学" ":hou" "報" ":min" "民" ":kin" "金"})
(def SCALES [":global" ":supranational" ":national" ":regional" ":municipal" ":local" ":intra-org"])
(def COLLECTIVE-KINDS [":org" ":region" ":municipality" ":community" ":intra-org-faction"
                       ":academic-clique" ":keiretsu" ":advisory-body"])
(def COLLECTIVE-JA {":org" "組織/企業単位" ":region" "地域(県)" ":municipality" "市区町村"
                    ":community" "コミュニティ" ":intra-org-faction" "社内派閥"
                    ":academic-clique" "学閥" ":keiretsu" "系列" ":advisory-body" "審議会"})
(def STANDINGS [":institutional" ":public-seat"])
(def TIE-KINDS [":custodies" ":depends-on" ":funds" ":awards" ":seats-on"
                ":co-member" ":supplies" ":covers" ":employs" ":follows"])
(def VERDICT-TOKENS #{":corruption" ":collusion" ":capture" ":癒着" ":汚職" ":談合" ":guilt" ":不正"})
(def FORBIDDEN-NODE-ATTRS #{":pwr/power-score" ":pwr/influence" ":pwr/rank" ":pwr/score"})

(defn- in? [coll x] (boolean (some #(= % x) coll)))

(defn validate-node [n]
  (doseq [a (keys n)]
    (when (contains? FORBIDDEN-NODE-ATTRS a)
      (throw (ex-info (str "S1 breach: per-node score attr " a " on " (get n ":pwr/id")
                           " (concentration is edge-primary; integral computed on read)") {}))))
  (let [st (get n ":pwr/standing")]
    (when-not (in? STANDINGS st)
      (throw (ex-info (str "S2 breach: :pwr/standing " (pr-str st) " on " (get n ":pwr/id")
                           " not in " STANDINGS " (private persons unrepresentable; seats only)") {}))))
  (when-not (in? SCALES (get n ":pwr/scale"))
    (throw (ex-info (str "closed-vocab breach: :pwr/scale " (pr-str (get n ":pwr/scale"))) {})))
  (when-not (in? SECTORS (get n ":pwr/sector"))
    (throw (ex-info (str "closed-vocab breach: :pwr/sector " (pr-str (get n ":pwr/sector"))) {})))
  (let [ck (get n ":pwr/collective-kind")]
    (when (and (some? ck) (not (in? COLLECTIVE-KINDS ck)))
      (throw (ex-info (str "closed-vocab breach: :pwr/collective-kind " (pr-str ck)) {})))))

(defn validate-tie [t]
  (let [k (get t ":tie/kind")]
    (when (contains? VERDICT-TOKENS k)
      (throw (ex-info (str "S5 breach: verdict token " k " as :tie/kind on " (get t ":tie/id")
                           " (concentration is structural, never a verdict)") {})))
    (when-not (in? TIE-KINDS k)
      (throw (ex-info (str "closed-vocab breach: :tie/kind " (pr-str k) " on " (get t ":tie/id")) {})))
    (let [srcs (get t ":tie/sources")]
      (when-not (and (sequential? srcs) (>= (count srcs) 2))
        (throw (ex-info (str "S4 breach: :tie/sources on " (get t ":tie/id") " has <2 public citations") {}))))
    (let [gl (get t ":tie/grasping-load")]
      (when-not (and (number? gl) (<= 0.0 gl 1.0))
        (throw (ex-info (str "range breach: :tie/grasping-load " (pr-str gl) " on " (get t ":tie/id") " ∉ [0,1]") {}))))))

(defn validate-records
  "Split + validate parsed records into [nodes ties] (raises on a gate breach)."
  [records]
  (reduce (fn [[nodes ties] r]
            (cond
              (not (map? r)) [nodes ties]
              (contains? r ":pwr/id") (do (validate-node r) [(assoc nodes (get r ":pwr/id") r) ties])
              (contains? r ":tie/id") (do (validate-tie r) [nodes (conj ties r)])
              :else [nodes ties]))
          [{} []] records))

#?(:clj
   (defn load-graph
     "Read + validate the scale-power graph file. Returns [nodes ties]."
     [seed-path]
     (validate-records (read-edn (slurp (io/file (str seed-path)))))))

(defn- pyround [x n] (let [f (Math/pow 10.0 n)] (/ (Math/rint (* (double x) f)) f)))
(defn- ja [m k] (get m k k))

(defn analyze
  "Aggregate-first, edge-primary concentration. Returns a deterministic map."
  [nodes ties]
  (let [sector-of   (into {} (map (fn [[nid n]] [nid (get n ":pwr/sector")]) nodes))
        locality-of (into {} (map (fn [[nid n]] [nid (get n ":pwr/locality")]) nodes))
        scale-of    (into {} (map (fn [[nid n]] [nid (get n ":pwr/scale")]) nodes))
        present?    (fn [x] (contains? nodes x))
        gl-of       (fn [t] (double (get t ":tie/grasping-load" 0.0)))
        ;; per-locality cross-sector readout
        loc (reduce (fn [acc t]
                      (let [f (get t ":tie/from") to (get t ":tie/to")]
                        (if-not (and (present? f) (present? to))
                          acc
                          (let [lf (locality-of f) lt (locality-of to)
                                sf (sector-of f) st (sector-of to) gl (gl-of t)]
                            (if (= lf lt)
                              (let [d (get acc lf {:sectors #{} :cross_load 0.0 :cross_ties 0})
                                    d (-> d (update :sectors conj sf st))
                                    d (if (not= sf st)
                                        (-> d (update :cross_load + gl) (update :cross_ties inc))
                                        d)]
                                (assoc acc lf d))
                              acc)))))
                    {} ties)
        localities (->> loc
                        (map (fn [[lname d]]
                               (let [diversity (count (:sectors d))]
                                 {"locality" lname
                                  "sector_diversity" diversity
                                  "sectors" (vec (sort (map #(ja SECTOR-JA %) (:sectors d))))
                                  "cross_sector_load" (pyround (:cross_load d) 4)
                                  "cross_sector_ties" (:cross_ties d)
                                  "concentration" (pyround (* (:cross_load d) diversity) 4)})))
                        (sort-by (juxt #(- (get % "concentration")) #(get % "locality")))
                        vec)
        ;; per-scale aggregate
        scale-agg (reduce (fn [acc t]
                            (let [f (get t ":tie/from") to (get t ":tie/to")]
                              (if-not (and (present? f) (present? to))
                                acc
                                (let [gl (gl-of t)]
                                  (reduce (fn [a s] (let [d (get a s {:load 0.0 :ties 0})]
                                                      (assoc a s (-> d (update :load + gl) (update :ties inc)))))
                                          acc (set [(scale-of f) (scale-of to)]))))))
                          {} ties)
        scales (->> scale-agg
                    (map (fn [[s d]] {"scale" s "load" (pyround (:load d) 4) "ties" (:ties d)}))
                    (sort-by (juxt #(- (get % "load")) #(get % "scale")))
                    vec)
        ;; cross-sector brokers
        span (reduce (fn [acc t]
                       (let [f (get t ":tie/from") to (get t ":tie/to")]
                         (if-not (and (present? f) (present? to))
                           acc
                           (let [gl (gl-of t)]
                             (reduce (fn [a [x y]]
                                       (if (not= (sector-of x) (sector-of y))
                                         (let [d (get a x {:sectors #{} :load 0.0})]
                                           (assoc a x (-> d (update :sectors conj (sector-of y)) (update :load + gl))))
                                         a))
                                     acc [[f to] [to f]])))))
                     {} ties)
        brokers (->> span
                     (map (fn [[nid d]]
                            {"id" nid "label" (get-in nodes [nid ":pwr/label"])
                             "sector" (ja SECTOR-JA (sector-of nid))
                             "bridges_to" (vec (sort (map #(ja SECTOR-JA %) (:sectors d))))
                             "span" (count (:sectors d)) "cross_load" (pyround (:load d) 4)}))
                     (sort-by (juxt #(- (get % "span")) #(- (get % "cross_load")) #(get % "id")))
                     vec)
        ;; per-collective-kind aggregate
        ck-of (into {} (map (fn [[nid n]] [nid (get n ":pwr/collective-kind")]) nodes))
        ck-agg (reduce (fn [acc t]
                         (let [f (get t ":tie/from") to (get t ":tie/to")]
                           (if-not (and (present? f) (present? to))
                             acc
                             (let [gl (gl-of t)]
                               (reduce (fn [a nid]
                                         (if-let [ck (ck-of nid)]
                                           (let [d (get a ck {:load 0.0 :nodes #{}})]
                                             (assoc a ck (-> d (update :load + gl) (update :nodes conj nid))))
                                           a))
                                       acc (set [f to]))))))
                       {} ties)
        collective-kinds (->> ck-agg
                              (map (fn [[ck d]] {"kind" ck "ja" (ja COLLECTIVE-JA ck)
                                                 "node_count" (count (:nodes d)) "load" (pyround (:load d) 4)}))
                              (sort-by (juxt #(- (get % "load")) #(get % "kind")))
                              vec)
        ;; cross-scale vertical integration
        parent-of (into {} (map (fn [[nid n]] [nid (get n ":pwr/parent")]) nodes))
        root-of (fn root-of [nid]
                  (loop [cur nid seen #{}]
                    (let [p (parent-of cur)]
                      (cond
                        (or (nil? p) (contains? seen p)) cur
                        (not (present? p)) p
                        :else (recur p (conj seen cur))))))
        nload (reduce (fn [acc t]
                        (let [gl (gl-of t)]
                          (reduce (fn [a x] (if (present? x) (update a x (fnil + 0.0) gl) a))
                                  acc [(get t ":tie/from") (get t ":tie/to")])))
                      {} ties)
        fam (reduce (fn [acc [nid _]]
                      (let [r (root-of nid)
                            d (get acc r {:scales #{} :localities #{} :members 0 :load 0.0})]
                        (assoc acc r (-> d (update :scales conj (scale-of nid))
                                         (update :localities conj (locality-of nid))
                                         (update :members inc)
                                         (update :load + (get nload nid 0.0))))))
                    {} nodes)
        vertical (->> fam
                      (filter (fn [[_ d]] (>= (count (:scales d)) 2)))
                      (map (fn [[r d]]
                             {"root" r "label" (if (present? r) (get-in nodes [r ":pwr/label"]) r)
                              "scale_span" (count (:scales d))
                              "scales" (vec (sort (filter some? (:scales d))))
                              "locality_span" (count (:localities d)) "members" (:members d)
                              "load" (pyround (:load d) 4)}))
                      (sort-by (juxt #(- (get % "scale_span")) #(- (get % "locality_span"))
                                     #(- (get % "load")) #(get % "root")))
                      vec)]
    {"localities" localities "scales" scales "brokers" brokers
     "collective_kinds" collective-kinds "vertical" vertical
     "node_count" (count nodes) "tie_count" (count ties)}))

(defn render-report
  "Markdown report (mirror of render_report) — map-not-target, aggregate-first."
  [result]
  (let [L (atom ["# tsumugi (A) — scale-agnostic 産官学報 concentration" ""
                 "> **Map, not target. Aggregate-first. Person-excluded (seats only). Non-adjudicating.**"
                 "> Concentration is the EDGE-PRIMARY integral of cross-sector co-location, routed to OPENING."
                 "> Co-location density is a structural fact — never a verdict (癒着/談合 unrepresentable)." ""
                 (str "nodes: " (get result "node_count") " · ties: " (get result "tie_count")) ""
                 "## Localities by 産官学報 cross-sector concentration" ""
                 "| locality | sectors woven | diversity | cross-load | concentration |"
                 "|---|---|---|---|---|"])]
    (doseq [x (get result "localities")]
      (swap! L conj (str "| " (get x "locality") " | " (str/join " " (get x "sectors")) " | "
                         (get x "sector_diversity") " | " (get x "cross_sector_load")
                         " | **" (get x "concentration") "** |")))
    (swap! L into ["" "## Scales (same lens, every scale)" "" "| scale | incident load | ties |" "|---|---|---|"])
    (doseq [x (get result "scales")]
      (swap! L conj (str "| " (get x "scale") " | " (get x "load") " | " (get x "ties") " |")))
    (when (seq (get result "collective_kinds"))
      (swap! L into ["" "## Granularity (粒度: 組織/地域/コミュニティ/社内派閥/学閥)" ""
                     "| collective-kind | nodes | incident load |" "|---|---|---|"])
      (doseq [x (get result "collective_kinds")]
        (swap! L conj (str "| " (get x "ja") " (`" (get x "kind") "`) | " (get x "node_count") " | " (get x "load") " |"))))
    (when (seq (get result "vertical"))
      (swap! L into ["" "## Vertically-integrated organizations (跨-scale 縦の集中 — :pwr/parent chains)" ""
                     "| organization (root) | scale span | scales | localities | incident load |"
                     "|---|---|---|---|---|"])
      (doseq [x (take 10 (get result "vertical"))]
        (swap! L conj (str "| " (get x "label") " | **" (get x "scale_span") "** | "
                           (str/join " " (get x "scales")) " | " (get x "locality_span") " | " (get x "load") " |"))))
    (swap! L into ["" "## Cross-sector brokers (seat/org, never a person)" ""
                   "| id | sector | bridges to | span | cross-load |" "|---|---|---|---|---|"])
    (doseq [x (take 10 (get result "brokers"))]
      (swap! L conj (str "| `" (get x "id") "` | " (get x "sector") " | " (str/join " " (get x "bridges_to"))
                         " | " (get x "span") " | " (get x "cross_load") " |")))
    (str (str/join "\n" @L) "\n")))

(defn render-graph-edn
  "Per-locality computed readouts as datoms (S1 — readout, not a stored node score)."
  [result]
  (str (str/join "\n"
                 (concat
                  [";; tsumugi (A) scale-graph — GENERATED; computed-on-read readouts (S1)."
                   ";; concentration is a per-LOCALITY aggregate, NOT a per-node score. Do not hand-edit."
                   "["]
                  (map (fn [x] (str " {:scale.cluster/locality \"" (get x "locality") "\" "
                                    ":scale.cluster/sector-diversity " (get x "sector_diversity") " "
                                    ":scale.cluster/cross-sector-load " (get x "cross_sector_load") " "
                                    ":scale.cluster/concentration " (get x "concentration") "}"))
                       (get result "localities"))
                  ["]"]))
       "\n"))

#?(:clj
   (defn -main
     "CLI entry: mirrors analyze_scale.main — [seed.edn] [--out OUTDIR]. ADR-2606261200
     cljc-native operator leg (was `python3 methods/analyze_scale.py`)."
     [& argv]
     (let [argv (vec argv)
           args (vec (remove #(str/starts-with? % "--") argv))
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           seed (if (seq args) (io/file (first args)) (io/file here "data" "seed-scale-power.kotoba.edn"))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           [nodes ties] (load-graph seed)
           result (analyze nodes ties)]
       (.mkdirs out)
       (spit (io/file out "scale-report.md") (render-report result))
       (spit (io/file out "scale-graph.kotoba.edn") (render-graph-edn result))
       (println (str "[tsumugi/scale] " (count nodes) " nodes · " (count ties)
                     " ties → " (.getPath (io/file out "scale-report.md")))))))
