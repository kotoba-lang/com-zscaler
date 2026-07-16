;; ported from 20-actors/hakoniwa/methods/ingest.py — real port replacing the unit_refactor
;; stage-0 "TODO: port-failed" stubs. NS fixed root.hakoniwa.* → hakoniwa.* (20-actors source root).
(ns hakoniwa.methods.ingest
  "ingest.py — hakoniwa 箱庭 REAL PUBLIC-entity ingest → enriched box → kotoba EDN + content-address.
  1:1 Clojure port of `methods/ingest.py` (ADR-2606111500 R1).

  Pulls a BOUNDED slice of REAL PUBLIC entities (organizations / topics) from Wikidata and folds
  them into a box as :entity nodes that the SYNTHETIC personas deliberate over.

  G1 — REAL ENTITIES ARE ORGS / TOPICS ONLY; the AGENTS stay SYNTHETIC. A fetched QID whose P31
       instance-of set hits a natural-person class (Q5 …) is DROPPED. Personas are generated
       fictional archetypes (:persona/synthetic true), never ingested.
  G6 — sourcing honesty (:authoritative real entities; :synthetic generated personas).

  Self-contained sha-256 + JSON reader (for the fixture / live response). Network fetch + the
  file-writing main are behind #?(:clj ...). The Python `__main__` printer is omitted."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [hakoniwa.methods.world :as w]
            [hakoniwa.methods.cid :as cidlib]))

(def ^:private ua "etzhayyim-hakoniwa/0.1 (+https://etzhayyim.com; public-entity ingest)")
(def ^:private timeout-ms 20000)
(def cohorts [":prepared-anchor" ":cautious-commuter" ":skeptic" ":connector" ":newcomer"])

;; ── minimal JSON reader (string-keyed maps; for the Wikidata fixture / response) ──────────────
(declare json-value)
(defn- jskip-ws [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))
(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\) (let [e (nth s (inc i))]
                   (case e
                     \" (do (.append sb \") (recur (+ i 2) sb))
                     \\ (do (.append sb \\) (recur (+ i 2) sb))
                     \/ (do (.append sb \/) (recur (+ i 2) sb))
                     \b (do (.append sb \backspace) (recur (+ i 2) sb))
                     \f (do (.append sb \formfeed) (recur (+ i 2) sb))
                     \n (do (.append sb \newline) (recur (+ i 2) sb))
                     \r (do (.append sb \return) (recur (+ i 2) sb))
                     \t (do (.append sb \tab) (recur (+ i 2) sb))
                     \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                          (.append sb (char cp)) (recur (+ i 6) sb))
                     (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))
(defn- json-number [^String s i]
  (let [end (loop [j i] (if (and (< j (count s))
                                 (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                          (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))
(defn- json-array [^String s i]
  (loop [i (jskip-ws s (inc i)), out []]
    (if (= (nth s i) \]) [out (inc i)]
        (let [[v i] (json-value s i) i (jskip-ws s i)]
          (if (= (nth s i) \,) (recur (jskip-ws s (inc i)) (conj out v)) [(conj out v) (inc i)])))))
(defn- json-object [^String s i]
  (loop [i (jskip-ws s (inc i)), out {}]
    (if (= (nth s i) \}) [out (inc i)]
        (let [[k i] (json-string s i) i (jskip-ws s i)
              [v i] (json-value s (jskip-ws s (inc i))) out (assoc out k v) i (jskip-ws s i)]
          (if (= (nth s i) \,) (recur (jskip-ws s (inc i)) out) [out (inc i)])))))
(defn- json-value [^String s i]
  (let [i (jskip-ws s i) c (nth s i)]
    (cond (= c \{) (json-object s i) (= c \[) (json-array s i) (= c \") (json-string s i)
          (= c \t) [true (+ i 4)] (= c \f) [false (+ i 5)] (= c \n) [nil (+ i 4)]
          :else (json-number s i))))
(defn parse-json [text] (first (json-value text 0)))

;; ── entity accessors (mirror _label / _p31 / _prop_targets) ──────────────────────────────────
(defn- ent-label [ent]
  (let [labels (get ent "labels" {})]
    (cond
      (contains? labels "en") (get-in labels ["en" "value"])
      (contains? labels "ja") (get-in labels ["ja" "value"])
      :else (get ent "id" "?"))))

(defn- p31 [ent]
  (vec (keep (fn [claim] (get-in claim ["mainsnak" "datavalue" "value" "id"]))
             (get-in ent ["claims" "P31"] []))))

(defn- prop-targets [ent pid]
  (vec (keep (fn [claim] (get-in claim ["mainsnak" "datavalue" "value" "id"]))
             (get-in ent ["claims" pid] []))))

;; ── deterministic hash helpers (mirror _h / _u: sha256 first 6 bytes big-endian) ─────────────
(defn- h-int ^long [& parts]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (.getBytes ^String (str/join ":" (map str parts)) "UTF-8"))]
    (reduce (fn [acc i] (bit-or (bit-shift-left acc 8) (bit-and (long (aget d i)) 0xff)))
            0 (range 6))))

(defn- u-float ^double [& parts]
  (/ (double (mod (apply h-int parts) 100000)) 100000.0))

(defn- round3 ^double [^double v] (/ (Math/rint (* v 1000.0)) 1000.0))

(defn generate-personas
  "Generate n SYNTHETIC fictional-archetype personas (G1) deliberating over the topic.
  Deterministic (hash-seeded). Returns [persona-nodes edges]. Mirrors generate_personas."
  ([n topic-id] (generate-personas n topic-id 7))
  ([n topic-id seed]
   (let [base-sus {":prepared-anchor" 0.18 ":cautious-commuter" 0.47 ":skeptic" 0.30
                   ":connector" 0.62 ":newcomer" 0.78}
         base-stance {":prepared-anchor" 0.82 ":cautious-commuter" 0.53 ":skeptic" 0.20
                      ":connector" 0.50 ":newcomer" 0.44}
         personas (mapv
                   (fn [i]
                     (let [cohort (nth cohorts (mod i (count cohorts)))
                           sus (round3 (min 0.95 (max 0.05 (+ (base-sus cohort) (* (- (u-float seed "sus" i) 0.5) 0.1)))))
                           stance (round3 (min 0.95 (max 0.05 (+ (base-stance cohort) (* (- (u-float seed "st" i) 0.5) 0.1)))))
                           pid (format "persona.g%02d" i)
                           node (cond-> {":sim/id" pid ":sim/kind" ":persona"
                                         ":sim/label" (str "synthetic archetype " (str/replace cohort #"^:+" "") " #" i)
                                         ":sim/sourcing" ":synthetic" ":persona/synthetic" true
                                         ":persona/cohort" cohort ":persona/susceptibility" sus
                                         ":persona/initial-stance" stance}
                                  (= cohort ":connector") (assoc ":persona/weight" 1.5))]
                       {:node node :pid pid :stance stance}))
                   (range n))
         stance-edges (mapv (fn [{:keys [pid stance]}]
                              {":en/from" pid ":en/to" topic-id ":en/kind" ":holds-stance"
                               ":en/weight" stance ":en/sourcing" ":synthetic"})
                            personas)
         influence-edges (vec (for [i (range n)
                                    kk (range 2)
                                    :let [j (mod (h-int seed "inf" i kk) n)]
                                    :when (not= j i)
                                    :let [wv (round3 (+ 0.3 (* (u-float seed "w" i kk) 0.5)))]]
                                {":en/from" (format "persona.g%02d" j)
                                 ":en/to" (format "persona.g%02d" i)
                                 ":en/kind" ":influences" ":en/weight" wv ":en/sourcing" ":synthetic"}))]
     [(mapv :node personas) (vec (concat stance-edges influence-edges))])))

(defn build-box
  "Fold fetched REAL entities + generated SYNTHETIC personas into one box.
  Returns [nodes edges provenance]. G1: real entities are orgs/topics; persons dropped.
  Mirrors build_box (nodes is insertion-ordered via an array-map where it matters for emit)."
  [sources fetched]
  (let [person-classes (set (get sources ":ingest/person-classes" []))
        props (get sources ":ingest/properties" {})
        topic (get sources ":ingest/topic" {})
        n-personas (long (get sources ":ingest/synthetic-personas" 16))
        topic-id "entity.topic"
        nodes0 (array-map
                topic-id {":sim/id" topic-id ":sim/kind" ":entity"
                          ":sim/label" (get topic ":topic/label" "public topic")
                          ":sim/sourcing" ":representative"
                          ":entity/public-ref" "topic.resilience-commons"}
                "outcome.adoption" {":sim/id" "outcome.adoption" ":sim/kind" ":outcome"
                                    ":sim/label" "synthetic-cohort mean adoption stance"
                                    ":sim/sourcing" ":representative" ":outcome/measures" ":all"
                                    ":outcome/statistic" ":mean-stance"
                                    ":outcome/use" (get topic ":topic/use" ":preparedness")})
        ;; REAL public entities (G1: orgs/topics only — refuse natural persons)
        [nodes kept dropped]
        (reduce (fn [[nodes kept dropped] spec]
                  (let [qid (get spec ":qid")
                        ent (get fetched qid)]
                    (if-not ent
                      [nodes kept dropped]
                      (let [p31s (set (p31 ent))]
                        (if (seq (set/intersection p31s person-classes))
                          [nodes kept (conj dropped {"qid" qid "reason" "natural-person (P31∈person-classes)"})]
                          (let [eid (str "entity.wd-" (str/lower-case qid))]
                            [(assoc nodes eid {":sim/id" eid ":sim/kind" ":entity" ":sim/label" (ent-label ent)
                                               ":sim/sourcing" ":authoritative" ":entity/public-ref" (str "wd:" qid)})
                             (conj kept {"qid" qid "label" (ent-label ent) "eid" eid})
                             dropped]))))))
                [nodes0 [] []]
                (get sources ":ingest/entities" []))
        kept-qids (into {} (map (fn [k] [(get k "qid") (get k "eid")]) kept))
        ;; structural :relates-to edges among kept entities
        rel-edges (vec (for [spec (get sources ":ingest/entities" [])
                             :let [qid (get spec ":qid")]
                             :when (and (contains? kept-qids qid) (contains? fetched qid))
                             [pname pid] props
                             :when (not= pname ":instance-of")
                             tgt (prop-targets (get fetched qid) pid)
                             :when (contains? kept-qids tgt)]
                         {":en/from" (kept-qids qid) ":en/to" (kept-qids tgt)
                          ":en/kind" ":relates-to" ":en/weight" 1.0 ":en/sourcing" ":authoritative"}))
        [personas pedges] (generate-personas n-personas topic-id)
        nodes (reduce (fn [acc p] (assoc acc (get p ":sim/id") p)) nodes personas)
        ;; sonae-style authoritative relay signal
        nodes (assoc nodes "signal.relay"
                     {":sim/id" "signal.relay" ":sim/kind" ":signal"
                      ":sim/label" "official preparedness advisory RELAY (sonae-style)"
                      ":sim/sourcing" ":representative" ":signal/push" 0.15 ":signal/at-step" 3})
        n-exposed (max 1 (quot (* (count personas) 2) 3))
        exposure-edges (mapv (fn [p] {":en/from" (get p ":sim/id") ":en/to" "signal.relay"
                                      ":en/kind" ":exposed-to" ":en/weight" 1.0 ":en/sourcing" ":synthetic"})
                             (take n-exposed personas))
        edges (vec (concat rel-edges pedges exposure-edges))
        provenance {"source" "wikidata" "kept" kept "dropped" dropped
                    "n_entities_kept" (count kept) "n_entities_dropped" (count dropped)
                    "n_personas" (count personas) "n_nodes" (count nodes) "n_edges" (count edges)}]
    [nodes edges provenance]))

;; ── EDN writer (mirrors to_edn) ──────────────────────────────────────────────────────────────
(def ^:private node-order
  [":sim/kind" ":sim/label" ":sim/sourcing" ":entity/public-ref"
   ":persona/synthetic" ":persona/cohort" ":persona/susceptibility"
   ":persona/initial-stance" ":persona/weight" ":signal/push" ":signal/at-step"
   ":outcome/measures" ":outcome/statistic" ":outcome/use"])
(def ^:private edge-order [":en/from" ":en/to" ":en/kind" ":en/weight" ":en/sourcing"])

(defn- fmt-g [^double v]
  (if (and (== v (Math/rint v)) (<= (Math/abs v) 1.0e15))
    (str (long v))
    (let [s (format "%.6g" v)]
      (if (str/includes? s "e")
        (let [[m e] (str/split s #"e")
              m (if (str/includes? m ".") (str/replace (str/replace m #"0+$" "") #"\.$" "") m)]
          (str m "e" e))
        (if (str/includes? s ".") (str/replace (str/replace s #"0+$" "") #"\.$" "") s)))))

(defn- fmt [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (and (string? v) (str/starts-with? v ":")) v
    (string? v) (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")
    (and (number? v) (not (integer? v))) (fmt-g (double v))
    :else (str v)))

(defn to-edn [nodes edges]
  (let [L (transient
           [";; hakoniwa 箱庭 — GENERATED ingested box (ADR-2606111500). DO NOT hand-edit."
            ";; REAL PUBLIC entities (:authoritative, Wikidata CC0) + SYNTHETIC personas (:synthetic, G1)."
            ";; No natural persons (P31=Q5 dropped at ingest). No PII. Agents are fictional archetypes."
            "["])]
    (doseq [[nid n] nodes]
      (let [parts (concat [(str "{:sim/id " (fmt nid))]
                          (for [a node-order :when (and (contains? n a) (some? (get n a)))]
                            (str a " " (fmt (get n a)))))]
        (conj! L (str (str/join " " parts) "}"))))
    (doseq [e edges]
      (let [parts (for [a edge-order :when (contains? e a)] (str a " " (fmt (get e a))))]
        (conj! L (str "{" (str/join " " parts) "}"))))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── network fetch + main (behind #?(:clj)) ───────────────────────────────────────────────────
#?(:clj
   (defn- entitydata-url [base qid]
     (str (str/replace base #"/+$" "") "/" qid ".json")))

#?(:clj
   (defn fetch-entity
     "Fetch one Wikidata entity record (PUBLIC, no auth). Returns the entity sub-map."
     [base qid]
     (let [^java.net.HttpURLConnection conn
           (doto (.openConnection (java.net.URL. (entitydata-url base qid)))
             (.setConnectTimeout timeout-ms) (.setReadTimeout timeout-ms)
             (.setRequestProperty "User-Agent" ua)
             (.setRequestProperty "Accept" "application/json"))
           doc (parse-json (slurp (.getInputStream conn)))]
       (get-in doc ["entities" qid]))))
