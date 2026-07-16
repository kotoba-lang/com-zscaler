(ns aburi.methods.analyze
  "aburi 炙り — edge-primary personal-tracking-exposure analyzer.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606161630).

  Reads a kotoba-EDN tracker-exposure graph (:organism/* nodes + :en/* 縁 over the
  tracker-exposure-ontology) and surfaces — for the MEMBER'S OWN surfaces — the answer to:
  \"when I accept the ToS / grant a permission on Google / Facebook / X / Apple, WHICH ad networks &
  data brokers collect my data, and HOW MUCH does each one track me.\" It computes, on read:

    exposure[collector]   = Σ incident inbound :flows-to load × disclosed permission-sensitivity
                            weight — \"how much this company tracks you\" (THE headline; the 取-holder)
    relief[collector]     = Σ incident inbound :relieves load — exposure removed once a route is used
    net_exposure[col]     = exposure − relief  (what still tracks you after the opt-outs you have)
    surface_leak[surface] = Σ over :grants(surface→perm) load × permission_leak[perm] — \"which
                            platform exposes you most\" (Google / Facebook / X / Apple …)
    spread[datatype]      = Σ incident inbound :collects load — \"what kinds of your data are most
                            widely harvested\"
    route_cov[perm]       = Σ outbound :routes-to load (0 = no opt-out/DSAR route = reciprocity gap)
    unrouted_permissions  = permissions that LEAK (outbound :flows-to > 0) but have NO :routes-to —
                            the asymmetric-surveillance gap, routed to himotoki/kaiyaku/kurashimori/tedai

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. Exposure lives ONLY on edges (:en/load). A collector's tracking-load is
      the INTEGRAL of its incident inbound :flows-to 縁 — computed on READ, never a stored per-
      collector score. There is no :aburi/score-of-collector.
    G1 — OWN-DATA-ONLY. The lens maps the MEMBER'S OWN exposure (this public seed is REPRESENTATIVE
      / aggregate, no real person). NO record of any OTHER person; NO third-party PII; NO biometric;
      NO raw identifier values (the analyzer reads exposure STRUCTURE, never personal values).
    G3 / N3 — non-adjudicating. Collector catalogue membership + collector→data mappings + sensitivity
      bands are DISCLOSED facts (Exodus Privacy / IAB sellers.json / Apple App-Privacy / Play Data-
      safety), never aburi verdicts. Naming an SDK as an ad collector is a public catalogue fact.
    G4 — RECIPROCITY-RESTORING (§2(c) v3.1, ADR-2606082400). aburi makes the asymmetric ad-watcher
      VISIBLE TO THE WATCHED. It itself never tracks, never sells, builds no asymmetric dossier.

  House style: Python ':…' keyword strings stay strings (incl. all :organism/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, \"string\", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; \":ns/name\" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Significant tokens (group 1 of each tok-re match that captured). Portable: clj uses
  a JVM Matcher; cljs (squint/ComponentizeJS wasm, ADR-2606261200) uses a native global
  RegExp .exec loop over the same pattern (tok-re is JVM/JS-compatible regex syntax)."
  [s]
  #?(:clj
     (let [m (re-matcher tok-re s)]
       ((fn step []
          (lazy-seq
           (when (.find m)
             (let [t (.group m 1)]
               (if (nil? t) (step) (cons t (step)))))))))
     :cljs
     (let [re  (js/RegExp. (.-source tok-re) "g")
           out #js []]
       (loop [mm (.exec re s)]
         (when mm
           (let [t (aget mm 1)] (when (some? t) (.push out t)))
           (recur (.exec re s))))
       out)))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    ;; int → long, else float, else raw. Portable: clj parses via JVM, cljs (squint/
    ;; ComponentizeJS wasm, ADR-2606261200) via strict regex + native parse so a
    ;; non-numeric token stays a string (parseFloat's leniency would mis-type "1abc").
    #?(:clj
       (let [as-long (try (Long/parseLong t) (catch Exception _ ::nan))]
         (if (not= as-long ::nan)
           as-long
           (let [as-dbl (try (Double/parseDouble t) (catch Exception _ ::nan))]
             (if (not= as-dbl ::nan) as-dbl t))))
       :cljs
       (cond
         (re-matches #"-?\d+" t)                                   (js/parseInt t 10)
         (re-matches #"-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?" t) (js/parseFloat t)
         :else t))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed permission/data sensitivity → representative weight (NOT a verdict; mirrors schema)
(def sensitivity-weight
  {":critical" 1.0 ":sensitive" 0.8 ":moderate" 0.5 ":low" 0.25})

(def grant-kinds #{":grants"})
(def flow-kinds #{":flows-to"})
(def collect-kinds #{":collects"})
(def route-kinds #{":routes-to"})
(def relieve-kinds #{":relieves"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved (ordered map) to match Python dict order."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id") (assoc-in acc [:nodes (get f ":organism/id")] f)
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (array-map) :edges []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse a tracker-exposure EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- ordered-map
  "Wrap a plain map with the first-touch insertion order of its keys (mirroring a Python
  defaultdict). ::order is a vector of keys in first-touch order."
  []
  ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f to the value at k (default 0.0 via fnil), recording k's
  first-touch position in ::order metadata."
  [m k f]
  (let [had? (contains? m k)
        m' (update m k (fnil f 0.0))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-order [d] (::order (meta d)))

(defn- omap-items
  "Items of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [d]
  (let [order (omap-order d)]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Returns
   {\"exposure\" {col v} \"relief\" {col v} \"net_exposure\" {col v}
    \"permission_leak\" {perm v} \"surface_leak\" {surf v} \"spread\" {dt v}
    \"route_coverage\" {perm v} \"unrouted_permissions\" [nid …]}.

   exposure[col]    = Σ incident inbound :flows-to load × disclosed sensitivity weight
   permission_leak  = Σ outbound :flows-to load (raw)
   spread[dt]       = Σ incident inbound :collects load
   route_cov[perm]  = Σ outbound :routes-to load (0 = reciprocity gap)
   relief[col]      = Σ incident inbound :relieves load
   surface_leak     = two-hop integral: Σ over :grants(surface→perm) load × permission_leak[perm]
   net_exposure     = exposure − relief

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable sort
   in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (let [[exposure permission-leak spread route-cov relief grants]
        (loop [es edges
               exposure (ordered-map) permission-leak (ordered-map)
               spread (ordered-map) route-cov (ordered-map)
               relief (ordered-map) grants []]
          (if (empty? es)
            [exposure permission-leak spread route-cov relief grants]
            (let [e (first es)
                  kind (get e ":en/kind")
                  load- (->load e)
                  src (get e ":en/from")
                  dst (get e ":en/to")]
              (cond
                (contains? flow-kinds kind)
                (let [sens (get-in nodes [src ":permission/sensitivity"])
                      w (get sensitivity-weight sens 0.5)] ; unknown sensitivity → neutral 0.5
                  (recur (rest es)
                         (omap-update exposure dst #(+ % (* load- w)))
                         (omap-update permission-leak src #(+ % load-))
                         spread route-cov relief grants))

                (contains? collect-kinds kind)
                (recur (rest es)
                       exposure permission-leak
                       (omap-update spread dst #(+ % load-))
                       route-cov relief grants)

                (contains? route-kinds kind)
                (recur (rest es)
                       exposure permission-leak spread
                       (omap-update route-cov src #(+ % load-))
                       relief grants)

                (contains? relieve-kinds kind)
                (recur (rest es)
                       exposure permission-leak spread route-cov
                       (omap-update relief dst #(+ % load-))
                       grants)

                (contains? grant-kinds kind)
                (recur (rest es)
                       exposure permission-leak spread route-cov relief
                       (conj grants [src dst load-]))

                :else
                (recur (rest es) exposure permission-leak spread route-cov relief grants)))))
        ;; surface-leak = two-hop integral: how much each platform's granted permissions feed collectors
        surface-leak (reduce (fn [sl [src perm load-]]
                               (omap-update sl src #(+ % (* load- (get permission-leak perm 0.0)))))
                             (ordered-map)
                             grants)
        ;; net exposure after the relief routes already exercised (exposure's first-touch order)
        net-exposure (reduce (fn [ne cid]
                               (omap-update ne cid (fn [_] (- (get exposure cid)
                                                              (get relief cid 0.0)))))
                             (ordered-map)
                             (omap-order exposure))
        ;; permissions that leak but have no opt-out / DSAR route = the reciprocity gap (sorted)
        unrouted (sort
                  (for [nid (keys nodes)
                        :let [n (get nodes nid)]
                        :when (and (= ":permission" (get n ":organism/kind"))
                                   (> (get permission-leak nid 0.0) 0)
                                   (= 0.0 (get route-cov nid 0.0)))]
                    nid))]
    {"exposure" exposure
     "relief" relief
     "net_exposure" net-exposure
     "permission_leak" permission-leak
     "surface_leak" surface-leak
     "spread" spread
     "route_coverage" route-cov
     "unrouted_permissions" (vec unrouted)}))

(defn rank
  "Top-`limit` (id, label, value) rows of d, sorted by -value only (STABLE — ties keep
  first-touch insertion order, mirroring Python's `sorted(d.items(), key=lambda kv: -kv[1])`
  on an insertion-ordered dict)."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> (sort-by (fn [[_ v]] (- v)) (omap-items d))
        (take limit)
        (mapv (fn [[nid v]]
                [nid (get-in nodes [nid ":organism/label"] nid) v])))))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the tracking-exposure report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-surf (count-kind nodes ":surface")
        n-perm (count-kind nodes ":permission")
        n-col (count-kind nodes ":collector")
        n-dt (count-kind nodes ":datatype")
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# aburi 炙り — personal-tracking-exposure report (own-data, reciprocity-restoring)\n")
    (conj! L (str "> **G1 — OWN-DATA-ONLY / G4 — RECIPROCITY-RESTORING.** This maps the member's OWN "
                  "exposure (public seed is REPRESENTATIVE — no real person). No record of any other "
                  "person, no third-party PII, no biometric, no raw identifier values. Collector "
                  "catalogue membership + data mappings are DISCLOSED facts (Exodus Privacy / IAB "
                  "sellers.json / Apple App-Privacy / Play Data-safety), NEVER an accusation (N3). "
                  "aburi makes the asymmetric ad-watcher visible to the watched (§2(c) v3.1) — it never "
                  "tracks, sells, or builds a dossier. Exposure lives only on edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-surf " surfaces · " n-perm " permissions · "
                  n-col " collectors · " n-dt " data-types) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Who tracks you most — ad networks & data brokers collecting your data\n")
    (conj! L (str "_Σ inbound :flows-to load × DISCLOSED permission-sensitivity, minus relief already "
                  "exercised. The 取-holders — routed to sukashi / kabuto / tsumugi for supply-chain "
                  "transparency, never a target-list._\n"))
    (conj! L "| rank | collector | kind | catalogue | net-exposure |")
    (conj! L "|---:|---|---|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "net_exposure") nodes 14))]
      (let [n (get nodes nid {})
            kind0 (or (get n ":collector/kind") "—")
            kind (lstrip-colon (str (if (or (nil? kind0) (false? kind0)) "—" kind0)))
            cat0 (or (get n ":collector/catalog") "—")
            cat (lstrip-colon (str (if (or (nil? cat0) (false? cat0)) "—" cat0)))]
        (conj! L (str "| " (inc i) " | " label " | " kind " | " cat " | " (fmt3 v) " |"))))

    (conj! L "\n## Which platform exposes you most — surface leak (Google / Facebook / X / Apple …)\n")
    (conj! L (str "_Σ over granted permissions of grant-load × the permission's downstream flow to "
                  "collectors — which consent venue leaks the most of you._\n"))
    (conj! L "| rank | surface | operator | leak |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "surface_leak") nodes 10))]
      (let [op0 (or (get-in nodes [nid ":surface/operator"]) "—")
            op (str (if (or (nil? op0) (false? op0)) "—" op0))]
        (conj! L (str "| " (inc i) " | " label " | " op " | " (fmt3 v) " |"))))

    (conj! L "\n## What kinds of your data are most widely harvested\n")
    (conj! L "| rank | data-type | spread |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "spread") nodes 10))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L "\n## Reciprocity gap — exposures with NO opt-out / DSAR route\n")
    (conj! L (str "_a permission whose data leaks to collectors but has no :routes-to relief — the next "
                  "route to wire, routed to himotoki (DSAR) / kaiyaku (sever) / kurashimori (opt-out) / "
                  "tedai (on-device revoke). Never a reason to do nothing._\n"))
    (let [unrouted (get res "unrouted_permissions")]
      (if (seq unrouted)
        (doseq [nid unrouted]
          (conj! L (str "- **" (get-in nodes [nid ":organism/label"] nid) "** — leaks, no relief route")))
        (conj! L "- _(every leaking permission in the seed has at least one relief route)_")))

    (conj! L (str "\n---\n_aburi 炙り · ADR-2606161630 · own-data · reciprocity-restoring · "
                  "non-adjudicating · no-other-person · edge-primary. Live ingest of the member's own "
                  "exports + relief routing are G7/Council + member-sig-gated; aburi proposes, "
                  "himotoki/kaiyaku/kurashimori/tedai carry.\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/tracking-exposure-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-tracker-exposure.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "tracking-exposure-report.md") (report-md nodes edges res))
       (println (str "aburi: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "tracking-exposure-report.md")))
       (when-let [top (first (rank (get res "net_exposure") nodes 1))]
         (println (str "  top tracker: " (nth top 1)
                       " (" (fmt3 (nth top 2)) ")")))
       0)))
