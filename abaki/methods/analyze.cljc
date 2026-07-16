(ns abaki.methods.analyze
  "abaki 暴 — anti-monopoly & chokepoint intelligence membrane.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606073100).

  OSINT-based monitor that tracks, maps (piercing the corporate veil), and structurally
  routes AROUND entities attempting monopolies across compute/data/biology/logistics/
  knowledge domains. CRITICAL framing: route-AROUND, NOT punishment (map-not-target).

  Reads a seed of public entities, computes a Chokepoint Index (CI) per entity from its
  disclosed traits, splits them into blocked (non-aligned, CI > 60) vs safe, renders a
  visualization report + a routing policy (the Route-Around payload other actors consume),
  and — under the R2 autonomous live gate — emits kotoba Datoms for the blocked entities.

  Gates (constitutional, ported 1:1 + test-enforced):
    route-AROUND-not-punishment — abaki emits a routing policy + status Datoms only; it
      models no active attack (DDoS/hacking) on a target. The only action is structural
      severance: no money/data/labour flows to a non-aligned entity.
    map-not-target — the output is a visualization map + a dependency-block list, never a
      hit-list. CI is computed on read from DISCLOSED traits; there is no secret blacklist.
    OSINT-public-only — every designation rests on public evidence (the seed traits +
      intel_findings); no private surveillance feed.

  House style: Python ':…' keyword strings stay strings (incl. :db/id / :abaki/* attrs);
  pure fns; file I/O only at edges. Portable .cljc."
  (:require [clojure.string :as str]
            [abaki.methods.live-gate :as lg]
            #?(:clj [clojure.java.io :as io])))

;; ── Chokepoint Index trait → weight (mirrors analyze.py's `weights` dict; sum order-
;; independent, so iteration order is immaterial — capped at 100 by `min`).
(def weights
  {"closed_source_models" 30
   "proprietary_hardware_lockin" 40
   "pricing_power_abuse" 30
   "f1_hybrid_lockin" 40
   "gene_patents" 30
   "lawsuits_against_farmers" 30
   "warehouse_labor_exploitation" 50
   "market_share_dominance" 20
   "anti_union_tactics" 30})

(defn calculate-ci
  "Calculate the Chokepoint Index (CI). Σ weight of each active trait present in `weights`,
  capped at 100. (`active` is truthy per Python `if active and trait in weights`.)"
  [traits]
  (let [score (reduce-kv
               (fn [acc trait active]
                 (if (and active (contains? weights trait))
                   (+ acc (get weights trait))
                   acc))
               0
               traits)]
    (min 100 score)))

(defn publish-live
  "R1(live): Attempt to publish the routing policy to the kotoba Datom log. Refuses
  structurally unless the live gate admits — i.e. unless a MEMBER has presented the full
  member-signed capability (operator flag + attestation + Council Lv6+ + a real member
  signature; FINDING 260617 + ADR-2606111400/2605231525). Refused → emits nothing (no
  unsigned route-around broadcast). Admitted → returns a vector of Datom maps (one per
  blocked entity), each attributed to the presenting member/operator DID.

  (`require` is clojure.core; the gate fn is live-gate/require-gate, which RAISES
  LiveGateRefused when the capability is absent/server-held/synthetic.)"
  ([routing-policy gate] (publish-live routing-policy gate nil))
  ([routing-policy gate env]
   (let [refused (try (lg/require-gate gate env) nil
                      (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                        (if (= (:abaki.methods.live-gate/kind (ex-data e)) lg/live-gate-refused)
                          e
                          (throw e))))]
     (if refused
       (do
         #?(:clj (println (str "⚠️ [abaki R1] Live publish skipped: " (ex-message refused))))
         [])
       (do
         #?(:clj (println (str "🚀 [abaki] Live gate admitted via member-signed capability ("
                               (:operator-did gate) "). Emitting member-attributed kotoba Datoms...")))
         (mapv (fn [blocked]
                 {":db/id" (get blocked "id")
                  ":abaki/status" ":non-aligned"
                  ":abaki/ci_score" (get blocked "reason_ci" 0)
                  ":abaki/attested_by" (:operator-did gate)})
               (get routing-policy "blocked_entities" [])))))))

;; ── pure analysis: entities → {report-lines, routing-policy} ──────────────────

(defn analyze
  "Pure core of `main`'s loop. Given the parsed entities seq, returns
   {:report-lines [..] :routing-policy {\"blocked_entities\" [..] \"safe_entities\" [..]}}.
   Order of entities is preserved (mirrors Python list iteration / dict order)."
  [entities]
  (let [header ["# abaki: Chokepoint & Monopoly Visualization Report\n"
                "> **Objective**: Visualize monopolies and generate structural reactions (Route Around) to prevent dependency.\n\n"
                "## Identified Entities & Chokepoint Index (CI)\n"
                "| Entity | Domain | CI Score | Status | Primary Owners |"
                "|---|---|---|---|---|"]]
    (loop [es (seq entities)
           lines (vec header)
           blocked []
           safe []]
      (if (empty? es)
        {:report-lines lines
         :routing-policy {"blocked_entities" blocked "safe_entities" safe}}
        (let [entity (first es)
              ci (calculate-ci (get entity "traits"))
              is-blocked (> ci 60)
              status (if is-blocked "🚫 BLOCKED (Non-Aligned)" "✅ SAFE")
              owners (str/join ", " (get entity "beneficial_owners" []))
              line (str "| " (get entity "name") " | " (get entity "domain")
                        " | " ci " | " status " | " owners " |")]
          (recur (rest es)
                 (conj lines line)
                 (if is-blocked
                   (conj blocked {"id" (get entity "id")
                                  "name" (get entity "name")
                                  "domain" (get entity "domain")
                                  "reason_ci" ci})
                   blocked)
                 (if is-blocked
                   safe
                   (conj safe {"id" (get entity "id")
                               "name" (get entity "name")
                               "domain" (get entity "domain")}))))))))

(defn report-md
  "Join report lines into the markdown body (mirrors '\\n'.join(report_lines))."
  [report-lines]
  (str/join "\n" report-lines))

;; ── JSON serialization matching Python json.dump(..., indent=2) ───────────────
;; Faithful enough for the seed shapes here: strings, ints, vectors of ordered maps.

(defn- json-escape [s]
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")))

(defn- name* [k] (if (keyword? k) (name k) (str k)))

(defn- json-str [v indent]
  (let [pad (apply str (repeat indent "  "))
        pad+ (apply str (repeat (inc indent) "  "))]
    (cond
      (nil? v) "null"
      (true? v) "true"
      (false? v) "false"
      (string? v) (str "\"" (json-escape v) "\"")
      (number? v) (str v)
      (map? v)
      (if (empty? v)
        "{}"
        (str "{\n"
             (str/join ",\n"
                       (map (fn [[k val]]
                              (str pad+ "\"" (json-escape (name* k)) "\": " (json-str val (inc indent))))
                            v))
             "\n" pad "}"))
      (sequential? v)
      (if (empty? v)
        "[]"
        (str "[\n"
             (str/join ",\n" (map (fn [x] (str pad+ (json-str x (inc indent)))) v))
             "\n" pad "]"))
      :else (str "\"" (json-escape (str v)) "\""))))

(defn to-json
  "Serialize to JSON text matching Python json.dump(obj, f, indent=2) (no trailing newline)."
  [v]
  (json-str v 0))

;; ── minimal JSON reader (subset: objects {}, arrays [], strings, numbers, bool, null) ──
;; The seed is JSON (not EDN); the Clojure host reads it at the #?(:clj) edge. Keys are
;; kept as strings to mirror Python dicts string-for-string.

#?(:clj
   (defn read-json
     "Parse JSON text into Clojure data (string keys, vectors for arrays, ordered via
     array-map for ≤8 keys — entity maps here are small). Uses a hand-rolled scanner to
     avoid a dependency; sufficient for the abaki seed shape."
     [text]
     (let [n (count text)
           pos (atom 0)]
       (letfn [(peek-ch [] (when (< @pos n) (.charAt ^String text @pos)))
               (next-ch [] (let [c (peek-ch)] (swap! pos inc) c))
               (skip-ws [] (while (and (< @pos n)
                                       (let [c (.charAt ^String text @pos)]
                                         (or (= c \space) (= c \tab) (= c \newline) (= c \return))))
                             (swap! pos inc)))
               (parse-string []
                 (next-ch) ;; opening quote
                 (let [sb (StringBuilder.)]
                   (loop []
                     (let [c (next-ch)]
                       (cond
                         (= c \") (.toString sb)
                         (= c \\) (let [e (next-ch)]
                                    (.append sb (case e
                                                  \" \" \\ \\ \/ \/
                                                  \n \newline \t \tab \r \return
                                                  \b \backspace \f \formfeed
                                                  \u (let [hex (subs text @pos (+ @pos 4))]
                                                       (swap! pos + 4)
                                                       (char (Integer/parseInt hex 16)))
                                                  e))
                                    (recur))
                         :else (do (.append sb c) (recur)))))))
               (parse-number []
                 (let [start @pos]
                   (while (and (< @pos n)
                               (let [c (.charAt ^String text @pos)]
                                 (or (Character/isDigit c) (#{\- \+ \. \e \E} c))))
                     (swap! pos inc))
                   (let [s (subs text start @pos)]
                     (if (re-find #"[.eE]" s) (Double/parseDouble s) (Long/parseLong s)))))
               (parse-value []
                 (skip-ws)
                 (let [c (peek-ch)]
                   (cond
                     (= c \{) (parse-object)
                     (= c \[) (parse-array)
                     (= c \") (parse-string)
                     (= c \t) (do (swap! pos + 4) true)
                     (= c \f) (do (swap! pos + 5) false)
                     (= c \n) (do (swap! pos + 4) nil)
                     :else (parse-number))))
               (parse-array []
                 (next-ch) ;; [
                 (skip-ws)
                 (if (= (peek-ch) \])
                   (do (next-ch) [])
                   (loop [out []]
                     (let [v (parse-value)]
                       (skip-ws)
                       (let [c (next-ch)]
                         (cond
                           (= c \,) (recur (conj out v))
                           (= c \]) (conj out v)
                           :else (throw (ex-info "bad array" {:pos @pos}))))))))
               (parse-object []
                 (next-ch) ;; {
                 (skip-ws)
                 (if (= (peek-ch) \})
                   (do (next-ch) {})
                   (loop [out (array-map)]
                     (skip-ws)
                     (let [k (parse-string)]
                       (skip-ws)
                       (next-ch) ;; :
                       (let [v (parse-value)]
                         (skip-ws)
                         (let [c (next-ch)]
                           (cond
                             (= c \,) (recur (assoc out k v))
                             (= c \}) (assoc out k v)
                             :else (throw (ex-info "bad object" {:pos @pos})))))))))]
         (parse-value)))))

#?(:clj
   (defn -main
     "CLI entry: analyze the seed → out/abaki-report.md + out/routing-policy.json, then run
     the R2 autonomous live publish. File I/O only at this edge."
     [& _argv]
     (let [base (-> *file* io/file .getParentFile .getParentFile)
           data-file (io/file base "data" "seed.json")
           out-dir (io/file base "out")
           data (read-json (slurp data-file))
           {:keys [report-lines routing-policy]} (analyze (get data "entities"))]
       (.mkdirs out-dir)
       (spit (io/file out-dir "abaki-report.md") (report-md report-lines))
       (spit (io/file out-dir "routing-policy.json") (to-json routing-policy))
       (println (str "✅ abaki processing complete. Generated report and routing policy in " out-dir))
       (let [gate (lg/make-live-gate)]
         (publish-live routing-policy gate {})))))
