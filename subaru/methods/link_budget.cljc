(ns subaru.methods.link-budget
  "subaru 昴 — connectivity link-budget analyzer. 1:1 Clojure port of methods/link_budget.py
  (ADR-2606162355). The engineering core the other methods import.

    link margin (dB) = EIRP − path-loss + G/T − required-C/N  (per :link)

  CONSTITUTIONAL:
    G1 — connectivity COMMONS, NEVER a surveillance / targeting / military-C2 platform. No DPI,
      user-geolocation-as-product, or targeting-relay attribute — check-g1 throws on any. Service
      keyed to :service-area (aggregate region), never a tracked person.
    G2 — no person-tracking; content E2E-encrypted ciphertext routed but unread.
    G8 — sourcing honesty. Representative engineering estimates.

  House style: ':…' keyword strings stay strings; pure fns; file I/O only at edges. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset) — mirrors link_budget.py faithfully.
(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t))) (str/replace "\\\"" "\"") (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan) as-long
          (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
            (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i) i (inc i)]
    (cond
      (= t "[") (loop [i i out []]
                  (let [[x i] (parse-step toks i)]
                    (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{") (loop [i i out {}]
                  (let [[k i] (parse-step toks i)]
                    (if (= k end-marker) [out i]
                        (let [[v i] (parse-step toks i)] (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn [text] (first (parse-step (vec (tokens text)) 0)))

;; G1/G3 backstops
(def banned-attrs [":link/inspect" ":link/dpi" ":user/location" ":subscriber/geo"
                   ":relay/targeting" ":traffic/retain" ":content/plaintext"])
(def commons-entitlement #{":social-security-level-0" ":sbt-internal"})

(defn load-graph [forms]
  (reduce
   (fn [{:keys [nodes] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id")
       (let [nid (get f ":organism/id") had? (contains? nodes nid) nodes' (assoc nodes nid f)]
         (assoc acc :nodes (if had? (with-meta nodes' (meta nodes))
                               (vary-meta nodes' update ::node-order (fnil conj []) nid))))
       (and (contains? f ":en/from") (contains? f ":en/to")) (update acc :edges conj f)
       :else acc))
   {:nodes (with-meta {} {::node-order []}) :edges []}
   forms))

(defn node-ids [nodes] (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj (defn load-file* [path] (load-graph (read-edn (slurp (str path))))))

(defn check-g1
  "G1/G3: connectivity commons. Throws ex-info on any banned attribute, non-commons
  entitlement kind, or a service-area lacking an aggregate :area/region."
  [nodes]
  (doseq [[nid n] nodes]
    (doseq [b banned-attrs]
      (when (contains? n b)
        (throw (ex-info (str "G1 violation: surveillance/targeting attr " b " on " nid) {:gate :g1}))))
    (when (= ":entitlement" (get n ":organism/kind"))
      (let [k (get n ":entitlement/kind")]
        (when-not (contains? commons-entitlement k)
          (throw (ex-info (str "G3 violation: non-commons entitlement " k " on " nid) {:gate :g3})))))
    (when (and (= ":service-area" (get n ":organism/kind")) (not (get n ":area/region")))
      (throw (ex-info (str "G1 violation: service-area " nid " lacks an aggregate :area/region")
                      {:gate :g1}))))
  true)

(defn link-budget [nodes _edges]
  (check-g1 nodes)
  (let [rows (->> (vals nodes)
                  (filter #(= ":link" (get % ":organism/kind")))
                  (mapv (fn [n]
                          (let [eirp (double (get n ":link/eirp-dbw" 0.0))
                                ploss (double (get n ":link/path-loss-db" 0.0))
                                gt (double (get n ":link/gt-dbk" 0.0))
                                req (double (get n ":link/required-cn-db" 0.0))]
                            {:link (get n ":organism/id") :label (get n ":organism/label")
                             :band (get n ":link/band") :kind (get n ":link/kind")
                             :margin_db (- (+ eirp gt) ploss req)})))
                  (sort-by :margin_db)
                  vec)]
    {:links rows :min_margin_db (if (seq rows) (:margin_db (first rows)) 0.0)
     :all_closed (every? #(> (:margin_db %) 0) rows)}))

(defn- fmt1 [v] (format "%.1f" (double v)))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md [_nodes _edges res]
  (let [L (transient [])]
    (conj! L "# subaru 昴 — connectivity link-budget report\n")
    (conj! L (str "> **G1 — connectivity COMMONS, NEVER a surveillance / targeting / military-C2 "
                  "platform.** No DPI, no user-geolocation-as-product, no targeting relay; service "
                  "keyed to aggregate :service-area, never a person. **G2** content E2E-encrypted "
                  "(routed, unread). **G8** representative engineering estimates.\n"))
    (conj! L "\n## Per-link budget (margin = EIRP − path-loss + G/T − required-C/N, on read)\n")
    (conj! L "| link | band | margin (dB) | closed? |")
    (conj! L "|---|:--:|---:|:--:|")
    (doseq [r (:links res)]
      (conj! L (str "| " (:label r) " | " (lstrip-colon (str (:band r))) " | " (fmt1 (:margin_db r))
                    " | " (if (> (:margin_db r) 0) "✅" "❌") " |")))
    (conj! L (str "\n**Minimum margin: " (fmt1 (:min_margin_db res)) " dB** — "
                  (if (:all_closed res) "✅ all links close" "❌ a link does not close") "\n"))
    (conj! L (str "\n---\n_subaru 昴 · ADR-2606162355 · connectivity-commons · no-surveillance · "
                  "cash≡0 §1.16 in-kind · representative estimates. Live ops Council-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (clojure.java.io/file (or (System/getenv "SUBARU_ACTOR_DIR") "20-actors/subaru"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-constellation.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (link-budget nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "link-budget-report.md") (report-md nodes edges res))
       (println (str "subaru link-budget: " (count (:links res)) " links, min margin "
                     (fmt1 (:min_margin_db res)) " dB → "
                     (clojure.java.io/file outdir "link-budget-report.md")))
       0)))
