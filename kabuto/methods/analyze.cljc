(ns kabuto.methods.analyze
  "kabuto 兜 — global public-company supply-chain concentration analyzer.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606022000).

  Reads a kotoba-EDN company graph (:company/* listed companies, :company.address/*
  HQ, :company.contact/* public contact, :supply.edge/* supplier→customer edges,
  :company.process/* BPMN refs) and emits an AGGREGATE-FIRST supply-chain intel
  report + derived concentration datoms (flagged :derived — never re-ingested).

  CONSTITUTIONAL framing (kabuto G2/G3/G4): a supply-chain RESILIENCE +
  corporate-power TRANSPARENCY map, NEVER a target-list. Concentration is ranked so
  buyers can DIVERSIFY and the public can hold concentration accountable — it does
  NOT identify \"who to hit\". kabuto does not adjudicate; it states public facts.

  House style: Python ':…' keyword strings stay strings (incl. all :company/* /
  :supply.edge/* attrs); pure fns; file I/O only at #?(:clj) edges.

  Float/round parity: Python `round()` is banker's-rounding (HALF_EVEN) over the
  true binary value of the double, then `str()` gives the shortest repr. We mirror
  that with BigDecimal(double).setScale(n, HALF_EVEN) → str. Insertion-ordered
  accumulators carry ::order metadata so stable sort-by ties the Python defaultdict
  iteration order byte-for-byte."
  (:require [clojure.string :as str]
            [clojure.set]
            [kabuto.methods.kabuto-edn :as kedn]))

;; ── float formatting (Python round + str parity) ─────────────────────────────

(defn pyround
  "Python round(x, n): HALF_EVEN over the exact binary value of the double."
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.doubleValue)))

(defn- num-str
  "str() of a number — Long prints without decimal, Double via Double/toString
  (== Python repr for the rounded magnitudes in play). Used inside report cells."
  [v]
  (if (integer? v) (str v) (str (double v))))

(defn- rstr
  "str(round(x, n)) — the f-string form `{round(x, n)}` used throughout."
  [x n]
  (num-str (pyround x n)))

(defn- comma0
  "Python `{x:,.0f}` — comma-grouped, zero-decimal."
  [x]
  (format "%,.0f" (double x)))

;; ── CPython set-iteration-order emulation (for tier_depth / intermediaries) ───
;; Python builds `set(in_deg) | set(out_deg)` and `set(in_deg) & set(out_deg)`,
;; then stable-sorts. The tie order within equal sort keys is therefore CPython
;; set-iteration order — reproducible only under PYTHONHASHSEED=0 (fixed siphash13
;; key = 0). We replicate it exactly: siphash13 string hash + the setobject.c
;; table (PySet_MINSIZE=8, LINEAR_PROBES=9, perturb probing, set_merge presize).
;; This is the sole reason the report is byte-identical to the Python -main.

(defn- ^long rotl [^long x ^long b]
  (bit-or (bit-shift-left x b) (unsigned-bit-shift-right x (- 64 b))))

(defn- ^long siphash13 [^bytes data]
  ;; CPython pysiphash, c-rounds=1 d-rounds=3, k0=k1=0 (PYTHONHASHSEED=0).
  (let [v (long-array [(unchecked-long 0x736f6d6570736575)
                       (unchecked-long 0x646f72616e646f6d)
                       (unchecked-long 0x6c7967656e657261)
                       (unchecked-long 0x7465646279746573)])
        n (alength data)
        sround (fn [vv]
                 (let [v0 (aget vv 0) v1 (aget vv 1) v2 (aget vv 2) v3 (aget vv 3)
                       v0 (unchecked-add v0 v1) v1 (rotl v1 13) v1 (bit-xor v1 v0) v0 (rotl v0 32)
                       v2 (unchecked-add v2 v3) v3 (rotl v3 16) v3 (bit-xor v3 v2)
                       v0 (unchecked-add v0 v3) v3 (rotl v3 21) v3 (bit-xor v3 v0)
                       v2 (unchecked-add v2 v1) v1 (rotl v1 17) v1 (bit-xor v1 v2) v2 (rotl v2 32)]
                   (doto vv (aset 0 v0) (aset 1 v1) (aset 2 v2) (aset 3 v3))))
        base (* 8 (quot n 8))]
    (loop [i 0]
      (when (< i base)
        (let [mi (loop [j 0 m 0]
                   (if (< j 8)
                     (recur (inc j) (bit-or m (bit-shift-left (long (bit-and (aget data (+ i j)) 0xff)) (* 8 j))))
                     m))]
          (aset v 3 (bit-xor (aget v 3) mi))
          (sround v)
          (aset v 0 (bit-xor (aget v 0) mi)))
        (recur (+ i 8))))
    (let [b (loop [j base b (bit-shift-left (long n) 56)]
              (if (< j n)
                (recur (inc j) (bit-or b (bit-shift-left (long (bit-and (aget data j) 0xff)) (* 8 (- j base)))))
                b))]
      (aset v 3 (bit-xor (aget v 3) b)) (sround v) (aset v 0 (bit-xor (aget v 0) b))
      (aset v 2 (bit-xor (aget v 2) (unchecked-long 0xff)))
      (sround v) (sround v) (sround v)
      (bit-xor (aget v 0) (aget v 1) (aget v 2) (aget v 3)))))

(defn- ^long pyhash [s]
  (let [bs (.getBytes (str s) "UTF-8")]
    (if (zero? (alength bs)) 0
        (let [h (siphash13 bs)] (if (= h -1) -2 h)))))

(def ^:private LINEAR-PROBES 9)
(def ^:private MINSIZE 8)
(defn- new-table [size] {:mask (dec size) :table (object-array size) :used 0 :fill 0})

(defn- insert-clean [st h key]
  (let [tbl (:table st) mask (:mask st)]
    (loop [i (bit-and h mask) perturb h]
      (if (nil? (aget tbl i))
        (do (aset tbl i [h key]) (-> st (update :used inc) (update :fill inc)))
        (let [found (when (<= (+ i LINEAR-PROBES) mask)
                      (loop [j 1]
                        (when (<= j LINEAR-PROBES)
                          (if (nil? (aget tbl (+ i j))) (+ i j) (recur (inc j))))))]
          (if found
            (do (aset tbl found [h key]) (-> st (update :used inc) (update :fill inc)))
            (let [perturb' (unsigned-bit-shift-right perturb 5)]
              (recur (bit-and (+ (* i 5) 1 perturb') mask) perturb'))))))))

(defn- table-resize [st minused]
  (let [newsize (loop [s MINSIZE] (if (> s minused) s (recur (bit-shift-left s 1))))
        old (:table st)]
    (loop [k 0 cur (new-table newsize)]
      (if (< k (alength old))
        (let [e (aget old k)]
          (recur (inc k) (if e (insert-clean cur (e 0) (e 1)) cur)))
        cur))))

(defn- lookup-slot [st h key]
  (let [tbl (:table st) mask (:mask st)]
    (loop [i (bit-and h mask) perturb h]
      (let [e (aget tbl i)]
        (if (nil? e)
          [:empty i]
          (if (and (= (e 0) h) (= (e 1) key))
            [:found]
            (let [res (when (<= (+ i LINEAR-PROBES) mask)
                        (loop [j 1]
                          (when (<= j LINEAR-PROBES)
                            (let [e2 (aget tbl (+ i j))]
                              (cond (nil? e2) [:empty (+ i j)]
                                    (and (= (e2 0) h) (= (e2 1) key)) [:found]
                                    :else (recur (inc j)))))))]
              (if res res
                  (let [perturb' (unsigned-bit-shift-right perturb 5)]
                    (recur (bit-and (+ (* i 5) 1 perturb') mask) perturb'))))))))))

(defn- set-add [st key]
  (let [h (pyhash key) r (lookup-slot st h key)]
    (if (= (first r) :found)
      st
      (let [idx (second r)]
        (aset (:table st) idx [h key])
        (let [st (-> st (update :used inc) (update :fill inc))]
          (if (>= (* (:fill st) 5) (* (:mask st) 3))
            (table-resize st (if (> (:used st) 50000) (* (:used st) 2) (* (:used st) 4)))
            st))))))

(defn- contains-key? [st h key] (= :found (first (lookup-slot st h key))))
(defn- new-set [keys] (reduce set-add (new-table MINSIZE) keys))
(defn- set-order [st]
  (let [tbl (:table st)] (keep (fn [i] (when-let [e (aget tbl i)] (e 1))) (range (alength tbl)))))

(defn- set-merge-into-empty [keys-in-order src-used]
  (let [minused (* src-used 2)
        size0 (loop [s MINSIZE] (if (> s minused) s (recur (bit-shift-left s 1))))]
    (reduce (fn [st k] (insert-clean st (pyhash k) k)) (new-table size0) keys-in-order)))

(defn- set-merge [st other-order other-used]
  (let [st (if (>= (* (+ (:fill st) other-used) 5) (* (:mask st) 3))
             (table-resize st (* (+ (:used st) other-used) 2))
             st)]
    (reduce set-add st other-order)))

(defn- py-set-union
  "set(a-keys) | set(b-keys) — CPython iteration order (a, b each in first-touch order)."
  [a-keys b-keys]
  (let [sa (new-set a-keys) sb (new-set b-keys)
        res (set-merge-into-empty (set-order sa) (:used sa))
        res (set-merge res (set-order sb) (:used sb))]
    (set-order res)))

(defn- py-set-inter
  "set(a-keys) & set(b-keys) — CPython iterates the smaller, checks the larger."
  [a-keys b-keys]
  (let [sa (new-set a-keys) sb (new-set b-keys)
        [small large] (if (<= (:used sa) (:used sb)) [(set-order sa) sb] [(set-order sb) sa])]
    (set-order (reduce (fn [r k] (if (contains-key? large (pyhash k) k) (set-add r k) r))
                       (new-table MINSIZE) small))))

;; ── ordered (insertion-tracking) accumulators (mirror Python defaultdict) ─────
;; ::order metadata = vector of keys in first-touch order so a stable sort ties
;; exactly the Python defaultdict iteration order.

(defn- omap [] ^{::order []} {})

(defn- omap-update [m k f init]
  (let [had? (contains? m k)
        m' (update m k (fnil f init))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-items
  "Items in first-touch insertion order (falls back to seq order)."
  [m]
  (if-let [order (::order (meta m))]
    (map (fn [k] [k (get m k)]) order)
    (seq m)))

(defn- omap-keys [m]
  (if-let [order (::order (meta m))] order (keys m)))

;; ── ISO-3166 alpha-2 → macro region bloc ─────────────────────────────────────

(def ^:private bloc-table
  {"JP" "East Asia" "KR" "East Asia" "CN" "East Asia" "TW" "East Asia" "HK" "East Asia"
   "MO" "East Asia"
   "US" "North America" "CA" "North America" "MX" "North America"
   "NL" "Europe" "DE" "Europe" "FR" "Europe" "CH" "Europe" "GB" "Europe" "IE" "Europe"
   "ES" "Europe" "IT" "Europe" "SE" "Europe" "FI" "Europe" "DK" "Europe" "NO" "Europe"
   "LU" "Europe" "PL" "Europe" "AT" "Europe" "PT" "Europe" "SI" "Europe" "HR" "Europe"
   "LT" "Europe" "IS" "Europe" "GR" "Europe" "CZ" "Europe" "HU" "Europe" "RO" "Europe"
   "IN" "South Asia" "BD" "South Asia" "PK" "South Asia" "LK" "South Asia"
   "SG" "SE Asia" "ID" "SE Asia" "TH" "SE Asia" "MY" "SE Asia" "VN" "SE Asia" "PH" "SE Asia"
   "SA" "Middle East" "AE" "Middle East" "QA" "Middle East" "KW" "Middle East"
   "BH" "Middle East" "OM" "Middle East" "JO" "Middle East" "IL" "Middle East" "TR" "Middle East"
   "BR" "Latin America" "CL" "Latin America" "CO" "Latin America" "AR" "Latin America"
   "PE" "Latin America" "PA" "Latin America"
   "ZA" "Africa" "NG" "Africa" "EG" "Africa" "KE" "Africa" "MA" "Africa"
   "CI" "Africa" "GH" "Africa"
   "AU" "Oceania" "NZ" "Oceania"
   "GE" "Eurasia" "KZ" "Eurasia"})

(defn- bloc [country] (get bloc-table country "Other"))

;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- ->crit
  "float(e.get(':supply.edge/criticality', 0.0) or 0.0) — coerce, 0.0 on nil/false."
  [e]
  (let [v (get e ":supply.edge/criticality")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- co-sector [companies s] (get-in companies [s ":company/sector"] ":unknown"))
(defn- co-country [companies s] (get-in companies [s ":company/country"] "??"))

(defn- neg [x] (- x))

;; ── analyze ──────────────────────────────────────────────────────────────────

(defn analyze
  "Port of analyze(companies, edges). Returns a map keyed exactly like the Python
  dict (string keys); insertion-ordered accumulators carry ::order metadata."
  [companies edges]
  ;; first pass: degree sets + per-(customer,commodity) suppliers + concentrations
  (let [{:keys [in-deg out-deg in-order out-order cust-comm sector-conc jur-load bloc-load systemic]}
        (reduce
         (fn [acc e]
           (let [s (get e ":supply.edge/from")
                 c (get e ":supply.edge/to")
                 crit (->crit e)
                 commodity (get e ":supply.edge/commodity" ":unknown")]
             (if (or (not s) (not c))
               acc
               (let [sec (co-sector companies s)
                     country (co-country companies s)
                     acc (if (contains? (:in-deg acc) c) acc (update acc :in-order conj c))
                     acc (if (contains? (:out-deg acc) s) acc (update acc :out-order conj s))]
                 (-> acc
                     (update-in [:in-deg c] (fnil conj #{}) s)
                     (update-in [:out-deg s] (fnil conj #{}) c)
                     (update :cust-comm omap-update [c commodity]
                             #(conj % [s crit]) [])
                     (update :systemic omap-update s #(+ % crit) 0.0)
                     (update :sector-conc omap-update [sec commodity] #(+ % crit) 0.0)
                     (update :jur-load omap-update country #(+ % crit) 0.0)
                     (update :bloc-load omap-update (bloc country) #(+ % crit) 0.0))))))
         {:in-deg {} :out-deg {} :in-order [] :out-order [] :cust-comm (omap)
          :sector-conc (omap) :jur-load (omap) :bloc-load (omap) :systemic (omap)}
         edges)

        ;; single-source: (customer,commodity) met by exactly one supplier ≥0.7
        single-source-unsorted
        (reduce
         (fn [out [[c commodity] sups]]
           (if (= 1 (count sups))
             (let [[sup crit] (first sups)]
               (if (>= crit 0.7)
                 (conj out [c commodity sup crit])
                 out))
             out))
         []
         (omap-items cust-comm))
        single-source (vec (sort-by (fn [r] (neg (nth r 3))) single-source-unsorted))

        ;; diversification — customer supplier-sector count & inbound crit
        {:keys [cust-sup-secs cust-in-crit]}
        (reduce
         (fn [acc e]
           (let [s (get e ":supply.edge/from")
                 c (get e ":supply.edge/to")]
             (if (or (not s) (not c))
               acc
               (-> acc
                   (update :cust-sup-secs omap-update c
                           #(conj % (co-sector companies s)) #{})
                   (update :cust-in-crit omap-update c #(+ % (->crit e)) 0.0)))))
         {:cust-sup-secs (omap) :cust-in-crit (omap)}
         edges)
        diversification-unsorted
        (reduce
         (fn [out c]
           (let [secs (count (get cust-sup-secs c))
                 load (pyround (get cust-in-crit c) 2)
                 idx (if (> load 0) (pyround (/ secs load) 3) 0.0)]
             (if (>= load 1.0)
               (conj out [c secs load idx])
               out)))
         []
         (omap-keys cust-in-crit))
        diversification (vec (sort-by (fn [r] (nth r 3)) diversification-unsorted))

        ;; intermediaries — nodes both customer and supplier (in×out).
        ;; Python iterates `set(in_deg) & set(out_deg)` (CPython order), then stable-sorts.
        inter-nodes (py-set-inter in-order out-order)
        intermediaries-unsorted
        (mapv (fn [n]
                (let [ind (count (get in-deg n))
                      outd (count (get out-deg n))]
                  [n ind outd (* ind outd)]))
              inter-nodes)
        intermediaries (vec (sort-by (fn [r] (neg (nth r 3))) intermediaries-unsorted))

        ;; tier depth — longest disclosed upstream chain (cycle-guarded memo)
        depth-memo (atom {})
        depth-fn (fn depth-fn [node stack]
                   (if-let [m (get @depth-memo node)]
                     m
                     (if (contains? stack node)
                       0
                       (let [sups (get in-deg node)]
                         (if (empty? sups)
                           (do (swap! depth-memo assoc node 0) 0)
                           (let [stack' (conj stack node)
                                 d (inc (apply max (map #(depth-fn % stack') sups)))]
                             (swap! depth-memo assoc node d)
                             d))))))
        ;; set(in_deg) | set(out_deg) — CPython iteration order, then stable-sort.
        all-nodes-union (py-set-union in-order out-order)
        tier-depth-sorted
        (sort-by (fn [r] (neg (nth r 1)))
                 (mapv (fn [n] [n (depth-fn n #{})]) all-nodes-union))
        tier-depth (vec (filter (fn [[_ d]] (> d 0)) tier-depth-sorted))

        ;; per-commodity HHI
        comm-sup-crit
        (reduce
         (fn [acc e]
           (let [s (get e ":supply.edge/from")
                 commodity (get e ":supply.edge/commodity" ":unknown")
                 crit (->crit e)]
             (if (and s (> crit 0))
               (omap-update acc commodity
                            (fn [inner] (omap-update inner s #(+ % crit) 0.0))
                            (omap))
               acc)))
         (omap)
         edges)
        commodity-hhi-unsorted
        (reduce
         (fn [out [commodity shares]]
           (let [tot (reduce + 0.0 (map second (omap-items shares)))]
             (if (<= tot 0)
               out
               (let [hhi (reduce + 0.0 (map (fn [[_ v]] (let [r (/ v tot)] (* r r)))
                                            (omap-items shares)))]
                 (conj out [commodity (count (omap-keys shares)) (pyround hhi 3)])))))
         []
         (omap-items comm-sup-crit))
        commodity-hhi (vec (sort-by (fn [r] (neg (nth r 2))) commodity-hhi-unsorted))

        ;; cross-bloc dependency exposure
        {:keys [cross-bloc cross-total all-total]}
        (reduce
         (fn [acc e]
           (let [s (get e ":supply.edge/from")
                 c (get e ":supply.edge/to")
                 crit (->crit e)]
             (if (or (not s) (not c))
               acc
               (let [acc (update acc :all-total + crit)
                     bs (bloc (co-country companies s))
                     bc (bloc (co-country companies c))]
                 (if (not= bs bc)
                   (-> acc
                       (update :cross-bloc omap-update [bs bc] #(+ % crit) 0.0)
                       (update :cross-total + crit))
                   acc)))))
         {:cross-bloc (omap) :cross-total 0.0 :all-total 0.0}
         edges)
        cross-corridors
        (vec (sort-by (fn [r] (neg (second r)))
                      (map (fn [[[a b] v]] [(str a "→" b) v]) (omap-items cross-bloc))))
        cross-share (if (not= all-total 0.0)
                      (pyround (/ (* 100 cross-total) all-total) 1)
                      0.0)

        ;; composite resilience
        cust-single-cnt
        (reduce (fn [m [c _ _ _]] (omap-update m c inc 0))
                (omap) single-source)
        cust-cross-in
        (reduce
         (fn [acc e]
           (let [s (get e ":supply.edge/from")
                 c (get e ":supply.edge/to")
                 crit (->crit e)]
             (if (and s c (not= (bloc (co-country companies s))
                                (bloc (co-country companies c))))
               (update acc :m omap-update c #(+ % crit) 0.0)
               acc)))
         {:m (omap)} edges)
        cust-cross-in (:m cust-cross-in)
        resilience-unsorted
        (reduce
         (fn [out [c load]]
           (if (< load 1.0)
             out
             (let [secs (max 1 (count (get cust-sup-secs c)))
                   cross-frac (if (not= load 0.0) (/ (get cust-cross-in c 0.0) load) 0.0)
                   frag (+ (* (get cust-single-cnt c 0) 2) (/ load secs) cross-frac)
                   score (pyround (/ 100.0 (+ 1.0 frag)) 1)]
               (conj out [c score (get cust-single-cnt c 0) (count (get cust-sup-secs c))
                          (pyround load 2) (pyround cross-frac 2)]))))
         []
         (omap-items cust-in-crit))
        resilience (vec (sort-by (fn [r] (nth r 1)) resilience-unsorted))

        ;; market-cap concentration
        {:keys [sector-cap capped]}
        (reduce
         (fn [acc cid]
           (let [co (get companies cid)
                 mc-raw (get co ":company/market-cap-busd")
                 mc (try (when (number? mc-raw) (double mc-raw))
                         (catch #?(:clj Exception :cljs :default) _ nil))]
             (if (or (nil? mc) (<= mc 0))
               acc
               (-> acc
                   (update :capped conj [cid mc])
                   (update :sector-cap omap-update (get co ":company/sector" ":unknown")
                           #(+ % mc) 0.0)))))
         {:sector-cap (omap) :capped []}
         (kedn/co-keys companies))
        total-cap (reduce + 0.0 (map second capped))
        sector-cap-rank (vec (sort-by (fn [[_ v]] (neg v)) (omap-items sector-cap)))
        cap-hhi (if (not= total-cap 0.0)
                  (pyround (reduce + 0.0 (map (fn [[_ v]] (let [r (/ v total-cap)] (* r r)))
                                              (omap-items sector-cap))) 3)
                  0.0)
        top-caps (vec (take 12 (sort-by (fn [r] (neg (second r))) capped)))

        ;; degree maps -> counts (preserve insertion order)
        in-deg-cnt (with-meta
                     (into {} (map (fn [k] [k (count (get in-deg k))]) (omap-keys in-deg)))
                     (meta in-deg))
        out-deg-cnt (with-meta
                      (into {} (map (fn [k] [k (count (get out-deg k))]) (omap-keys out-deg)))
                      (meta out-deg))]
    {"in_deg" in-deg-cnt
     "out_deg" out-deg-cnt
     "single_source" single-source
     "sector_concentration" sector-conc
     "jurisdiction_load" jur-load
     "systemic" systemic
     "diversification" diversification
     "intermediaries" intermediaries
     "tier_depth" tier-depth
     "bloc_load" bloc-load
     "commodity_hhi" commodity-hhi
     "cross_corridors" cross-corridors
     "cross_share" cross-share
     "resilience" resilience
     "sector_cap_rank" sector-cap-rank
     "cap_hhi" cap-hhi
     "total_cap" (pyround total-cap 1)
     "cap_count" (count capped)
     "cap_coverage" (if (seq companies)
                      (pyround (/ (* 100 (count capped)) (count companies)) 1)
                      0.0)
     "top_caps" top-caps}))

(defn cname [companies cid]
  (get-in companies [cid ":company/name"] cid))

;; ── sorting helpers for report (insertion-ordered items, stable -value) ──────

(defn- rank-desc
  "sorted(items, key=lambda kv: -kv[1]) over insertion-ordered items (stable)."
  [m]
  (sort-by (fn [[_ v]] (neg v)) (omap-items m)))

(defn- lstrip-colon [s]
  (let [s (str s)]
    (if (str/starts-with? s ":") (subs s 1) s)))

;; ── render report ────────────────────────────────────────────────────────────

(defn render-report
  [companies addresses contacts edges processes a]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P "# kabuto 兜 — global public-company supply-chain concentration report")
    (P "")
    (P (str "> ADR-2606022000 · **aggregate-first** · supply-chain RESILIENCE + corporate-power "
            "TRANSPARENCY map (NOT a target-list; kabuto G2). kabuto does not adjudicate (G4). "
            "All sourcing `:representative` — bounded illustrative seed of public companies + "
            "disclosed supplier relationships, NOT an exhaustive bill of materials."))
    (P "")
    (let [status-counts (reduce (fn [m c] (omap-update m (get c ":company/status" ":listed") inc 0))
                                (omap) (kedn/co-vals companies))
          n-listed (get status-counts ":listed" 0)
          n-hist (- (count companies) n-listed)
          hist-note (if (not= n-hist 0)
                      (str " (incl. **" n-hist "** delisted/acquired retained as history — Datomic as-of, "
                           "非終末論: facts are superseded, never deleted)")
                      "")]
      (P (str "- companies: **" (count companies) "**" hist-note "  ·  HQ addresses: **"
              (count addresses) "**  ·  public contacts: **" (count contacts)
              "**  ·  supply edges: **" (count edges) "**  ·  BPMN process templates: **"
              (count processes) "**")))
    (let [sectors (reduce (fn [m c] (omap-update m (get c ":company/sector" ":unknown") inc 0))
                          (omap) (kedn/co-vals companies))
          sorted-sectors (sort-by (fn [[_ v]] (neg v)) (omap-items sectors))]
      (P (str "- sectors covered: "
              (str/join ", " (map (fn [[s n]] (str "`" s "` " n)) sorted-sectors))))
      (P "")

      ;; ── data-coverage self-audit ──
      (let [n (max 1 (count companies))
            companies-set (set (kedn/co-keys companies))
            with-addr (count (clojure.set/intersection
                              (set (map #(get % ":company.address/company") addresses)) companies-set))
            with-contact (count (clojure.set/intersection
                                 (set (map #(get % ":company.contact/company") contacts)) companies-set))
            in-edges (reduce (fn [s e]
                               (conj s (get e ":supply.edge/from") (get e ":supply.edge/to")))
                             #{} edges)
            with-edge (count (clojure.set/intersection in-edges companies-set))
            with-cap (count (filter #(get % ":company/market-cap-busd") (kedn/co-vals companies)))
            countries (count (set (map #(get % ":company/country") (kedn/co-vals companies))))
            exch-count (reduce (fn [m c] (omap-update m (get c ":company/exchange" ":?") inc 0))
                               (omap) (kedn/co-vals companies))
            n-exch (count (omap-keys exch-count))
            top-exch (vec (take 8 (sort-by (fn [[_ v]] (neg v)) (omap-items exch-count))))
            sector-vals (map second (omap-items sectors))]
        (P "## Data coverage — G5 honesty self-audit")
        (P "")
        (P (str "What the seed ACTUALLY carries (absence = \"not yet ingested\", never \"does not exist\"). "
                "A bounded `:representative` slice, growing each iteration — not exhaustive coverage."))
        (P "")
        (P "| field | companies | coverage |")
        (P "|---|---:|---:|")
        (P (str "| registered HQ address | " with-addr " | " (rstr (/ (* 100 with-addr) n) 1) "% |"))
        (P (str "| public IR contact | " with-contact " | " (rstr (/ (* 100 with-contact) n) 1) "% |"))
        (P (str "| ≥1 disclosed supply edge | " with-edge " | " (rstr (/ (* 100 with-edge) n) 1) "% |"))
        (P (str "| market-cap snapshot | " with-cap " | " (rstr (/ (* 100 with-cap) n) 1) "% |"))
        (P (str "| distinct countries | " countries " | — |"))
        (P (str "| distinct listing exchanges | " n-exch " | — |"))
        (P (str "| sector balance (min..max per sector) | " (apply min sector-vals) ".."
                (apply max sector-vals) " | — |"))
        (P "")
        (P (str "Exchange coverage (top listing venues by company count) — where the seed is "
                "thick vs. where the R1 GLEIF/EDGAR/exchange ingest should still reach:"))
        (P "")
        (P "| exchange | companies |  | exchange | companies |")
        (P "|---|---:|---|---|---:|")
        (let [half (quot (+ (count top-exch) 1) 2)]
          (doseq [i (range half)]
            (let [[le lc] (nth top-exch i)]
              (if (< (+ i half) (count top-exch))
                (let [[re rc] (nth top-exch (+ i half))]
                  (P (str "| `" (lstrip-colon le) "` | " lc " |  | `" (lstrip-colon re) "` | " rc " |")))
                (P (str "| `" (lstrip-colon le) "` | " lc " |  | | |"))))))
        (P "")))

    ;; ── single-source dependencies ──
    (P "## Single-source dependencies — supply-chain redundancy gaps")
    (P "")
    (P (str "A (customer, commodity) met by exactly ONE disclosed supplier at high criticality "
            "(≥0.7). These are where a customer's production shares one supplier's fate — "
            "**routed to diversification, never to interdiction.**"))
    (P "")
    (P "| customer | commodity | sole disclosed supplier | criticality |")
    (P "|---|---|---|---:|")
    (doseq [[c commodity sup crit] (get a "single_source")]
      (P (str "| " (cname companies c) " | `" commodity "` | " (cname companies sup) " | "
              (num-str crit) " |")))
    (when (empty? (get a "single_source"))
      (P "| (none in seed) | | | |"))
    (P "")

    ;; ── jurisdiction concentration ──
    (P "## Jurisdiction concentration — geographic supply load")
    (P "")
    (P (str "Σ disclosed-dependency criticality whose SUPPLIER domiciles in each country. "
            "Higher = more of the seeded supply chain shares one geographic/regulatory fate."))
    (P "")
    (P "| supplier domicile | Σ criticality |")
    (P "|---|---:|")
    (doseq [[country load] (rank-desc (get a "jurisdiction_load"))]
      (P (str "| `" country "` | " (rstr load 2) " |")))
    (P "")

    ;; ── regional-bloc concentration ──
    (P "## Regional-bloc concentration — supply load by macro-region")
    (P "")
    (P (str "Σ disclosed-dependency criticality grouped by the SUPPLIER's macro-region "
            "bloc. Surfaces which world region the seeded supply chain leans on most — "
            "routed to cross-region diversification, never to interdiction (G2)."))
    (P "")
    (let [total-bloc (let [t (reduce + 0.0 (map second (omap-items (get a "bloc_load"))))]
                       (if (zero? t) 1.0 t))]
      (P "| region bloc | Σ criticality | share |")
      (P "|---|---:|---:|")
      (doseq [[b load] (rank-desc (get a "bloc_load"))]
        (P (str "| " b " | " (rstr load 2) " | " (rstr (/ (* 100 load) total-bloc) 1) "% |"))))
    (P "")

    ;; ── per-commodity HHI ──
    (P "## Commodity supplier concentration — Herfindahl index (HHI)")
    (P "")
    (P (str "Supplier-share concentration WITHIN each commodity, by Σ criticality "
            "(HHI = Σ share²; 1.0 = a single disclosed supplier, low = fragmented). "
            "High HHI = the world depends on few suppliers for that input — the headline "
            "redundancy priority, routed to diversification (G2)."))
    (P "")
    (P "| commodity | disclosed suppliers | HHI |")
    (P "|---|---:|---:|")
    (doseq [[commodity nsup hhi] (get a "commodity_hhi")]
      (P (str "| `" (lstrip-colon commodity) "` | " nsup " | " (num-str hhi) " |")))
    (P "")

    ;; ── sector × commodity concentration ──
    (P "## Sector × commodity concentration")
    (P "")
    (P "| supplier sector | commodity | Σ criticality |")
    (P "|---|---|---:|")
    (doseq [[[sec commodity] load] (take 15 (rank-desc (get a "sector_concentration")))]
      (P (str "| `" sec "` | `" commodity "` | " (rstr load 2) " |")))
    (P "")

    ;; ── systemic suppliers ──
    (P "## Most systemic suppliers — outward dependency weight")
    (P "")
    (P (str "Suppliers carrying the most criticality-weighted outward dependency across the "
            "seeded graph. High = many customers concentrate on them (a transparency signal: "
            "where corporate supply power concentrates)."))
    (P "")
    (P "| supplier | sector | customers | Σ outward criticality |")
    (P "|---|---|---:|---:|")
    (doseq [[sup load] (take 15 (rank-desc (get a "systemic")))]
      (let [sec (get-in companies [sup ":company/sector"] "?")]
        (P (str "| " (cname companies sup) " | `" sec "` | " (get (get a "out_deg") sup 0)
                " | " (rstr load 2) " |"))))
    (P "")

    ;; ── customer diversification ──
    (P "## Least-diversified customers — supplier-sector concentration")
    (P "")
    (P (str "Customers whose inbound dependency draws on the FEWEST distinct supplier "
            "sectors per unit of criticality (index = distinct-sectors ÷ Σ inbound "
            "criticality). Lower = more brittle; candidates for cross-sector diversification."))
    (P "")
    (P "| customer | supplier sectors | Σ inbound criticality | diversification index |")
    (P "|---|---:|---:|---:|")
    (doseq [[c secs load idx] (take 12 (get a "diversification"))]
      (P (str "| " (cname companies c) " | " secs " | " (num-str load) " | " (num-str idx) " |")))
    (when (empty? (get a "diversification"))
      (P "| (none in seed) | | | |"))
    (P "")

    ;; ── intermediaries / supply-betweenness ──
    (P "## Supply-chain intermediaries — betweenness (in × out)")
    (P "")
    (P (str "Nodes that are BOTH a customer and a supplier sit in the chain's middle. "
            "Ranked by in×out: the more paths route through them, the more systemic a "
            "single disruption — a transparency signal, never a target (G2)."))
    (P "")
    (P "| node | suppliers (in) | customers (out) | betweenness |")
    (P "|---|---:|---:|---:|")
    (doseq [[n ind outd score] (take 12 (get a "intermediaries"))]
      (P (str "| " (cname companies n) " | " ind " | " outd " | " score " |")))
    (when (empty? (get a "intermediaries"))
      (P "| (none in seed) | | | |"))
    (P "")

    ;; ── tier depth ──
    (P "## Deepest disclosed supply chains — tier depth")
    (P "")
    (P (str "Longest DISCLOSED upstream chain of suppliers-of-suppliers reaching each node "
            "(depth = hops back toward raw inputs). Deeper = more upstream stages where a "
            "disruption can originate; routed to multi-tier visibility, never to a target."))
    (P "")
    (P "| node | tier depth |")
    (P "|---|---:|")
    (doseq [[n d] (take 12 (get a "tier_depth"))]
      (P (str "| " (cname companies n) " | " d " |")))
    (when (empty? (get a "tier_depth"))
      (P "| (none in seed) | |"))
    (P "")

    ;; ── cross-bloc dependency exposure ──
    (P "## Cross-bloc dependency exposure — geopolitical supply corridors")
    (P "")
    (P (str "**" (num-str (get a "cross_share")) "%** of disclosed-dependency criticality flows across a "
            "macro-region bloc boundary (supplier and customer in different blocs) — the "
            "geopolitical exposure / reshoring surface. Top corridors below; routed to "
            "supply diversification + resilience, never to interdiction (G2)."))
    (P "")
    (P "| corridor (supplier bloc → customer bloc) | Σ criticality |")
    (P "|---|---:|")
    (doseq [[corridor load] (take 12 (get a "cross_corridors"))]
      (P (str "| " corridor " | " (rstr load 2) " |")))
    (when (empty? (get a "cross_corridors"))
      (P "| (none in seed) | |"))
    (P "")

    ;; ── market-cap concentration ──
    (when (and (get a "cap_count") (not (zero? (get a "cap_count"))))
      (P "## Market-cap concentration — listed economic weight by sector")
      (P "")
      (P (str "Across the **" (get a "cap_count") "** seed companies carrying a publicly-known market cap "
              "(**" (num-str (get a "cap_coverage")) "%** of the seed; Σ ≈ **$" (comma0 (get a "total_cap"))
              "B** `:representative`), "
              "the sector **cap-HHI = " (num-str (get a "cap_hhi")) "** (Σ sector-share²; higher = listed value piled into "
              "fewer sectors). An economic-weight concentration signal complementing the criticality view — "
              "which sectors hold the market value, aggregate-first, never a target-list (G2). Honesty (G5): "
              "covers only companies with a public cap; absence ≠ zero value."))
      (P "")
      (P "| sector | Σ market cap (USD B) | share of covered cap |")
      (P "|---|---:|---:|")
      (doseq [[sec cap] (take 12 (get a "sector_cap_rank"))]
        (let [sh (if (not= (get a "total_cap") 0.0)
                   (pyround (/ (* 100 cap) (get a "total_cap")) 1)
                   0.0)]
          (P (str "| " (lstrip-colon sec) " | " (comma0 cap) " | " (num-str sh) "% |"))))
      (P "")
      (P "Largest covered caps (orientation only, not a ranking of importance):")
      (P "")
      (P "| company | market cap (USD B) |")
      (P "|---|---:|")
      (doseq [[cid cap] (get a "top_caps")]
        (P (str "| " (cname companies cid) " | " (comma0 cap) " |")))
      (P ""))

    ;; ── composite resilience score ──
    (P "## Composite supply-resilience score — most-fragile customers")
    (P "")
    (P (str "Capstone synthesis: score = 100/(1+fragility), where fragility rises with "
            "single-source inputs (×2), inbound criticality concentrated in few supplier "
            "sectors, and cross-bloc inbound share. **Lower = more fragile.** Aggregate-first "
            "accountability/redundancy ranking — never a target-list (G2)."))
    (P "")
    (P "| customer | resilience score | single-source | supplier sectors | inbound crit | cross-bloc share |")
    (P "|---|---:|---:|---:|---:|---:|")
    (doseq [[c score ss secs load cross] (take 12 (get a "resilience"))]
      (P (str "| " (cname companies c) " | " (num-str score) " | " ss " | " secs " | "
              (num-str load) " | " (num-str cross) " |")))
    (when (empty? (get a "resilience"))
      (P "| (none in seed) | | | | | |"))
    (P "")

    (P "---")
    (P (str "*Generated by `kabuto/methods/analyze.py`. HONEST: R0 bounded seed of public "
            "companies; supplier edges are disclosed/public `:representative` estimates, NOT an "
            "exhaustive bill of materials; criticality is a bounded estimate, never a contract "
            "figure. Full GLEIF/EDGAR/exchange-universe ingest is G7 Council + operator gated.*"))
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── render datoms ─────────────────────────────────────────────────────────────

(defn render-datoms
  [companies a]
  (let [L (transient [])
        P (fn [s] (conj! L s))
        es kedn/edn-str]
    (P ";; kabuto — DERIVED supply-chain concentration datoms (ADR-2606022000). :derived — NOT fact.")
    (P ";; Recomputed from the seed graph; do not re-ingest as :authoritative.")
    (P "[")
    (doseq [[c commodity sup crit] (get a "single_source")]
      (P (str " {:supply/single-source-customer " (es c) " :supply/commodity \"" commodity "\" "
              ":supply/sole-supplier " (es sup) " :supply/criticality " (num-str crit) " :supply/derived true}")))
    (doseq [[country load] (rank-desc (get a "jurisdiction_load"))]
      (P (str " {:supply/jurisdiction \"" country "\" :supply/jurisdiction-load " (rstr load 2) " "
              ":supply/derived true}")))
    (doseq [[sup load] (rank-desc (get a "systemic"))]
      (P (str " {:supply/systemic-supplier " (es sup) " "
              ":supply/out-degree " (get (get a "out_deg") sup 0) " "
              ":supply/outward-criticality " (rstr load 2) " :supply/derived true}")))
    (doseq [[c secs load idx] (get a "diversification")]
      (P (str " {:supply/diversification-customer " (es c) " "
              ":supply/supplier-sectors " secs " :supply/inbound-criticality " (num-str load) " "
              ":supply/diversification-index " (num-str idx) " :supply/derived true}")))
    (doseq [[n ind outd score] (get a "intermediaries")]
      (P (str " {:supply/intermediary " (es n) " :supply/in-degree " ind " "
              ":supply/out-degree " outd " :supply/betweenness " score " :supply/derived true}")))
    (doseq [[n d] (get a "tier_depth")]
      (P (str " {:supply/tier-depth-node " (es n) " :supply/tier-depth " d " :supply/derived true}")))
    (doseq [[b load] (rank-desc (get a "bloc_load"))]
      (P (str " {:supply/region-bloc \"" b "\" :supply/bloc-load " (rstr load 2) " :supply/derived true}")))
    (doseq [[commodity nsup hhi] (get a "commodity_hhi")]
      (P (str " {:supply/commodity \"" (lstrip-colon commodity) "\" "
              ":supply/commodity-suppliers " nsup " :supply/commodity-hhi " (num-str hhi) " :supply/derived true}")))
    (doseq [[corridor load] (get a "cross_corridors")]
      (P (str " {:supply/cross-bloc-corridor \"" corridor "\" :supply/cross-bloc-load " (rstr load 2) " "
              ":supply/derived true}")))
    (doseq [[c score ss secs load cross] (get a "resilience")]
      (P (str " {:supply/resilience-customer " (es c) " :supply/resilience-score " (num-str score) " "
              ":supply/single-source-count " ss " :supply/derived true}")))
    (doseq [[sec cap] (get a "sector_cap_rank")]
      (P (str " {:supply/cap-sector \"" (lstrip-colon sec) "\" :supply/cap-sector-busd " (rstr cap 1) " "
              ":supply/derived true}")))
    (when (and (get a "cap_count") (not (zero? (get a "cap_count"))))
      (P (str " {:supply/cap-hhi " (num-str (get a "cap_hhi")) " :supply/cap-total-busd " (num-str (get a "total_cap")) " "
              ":supply/cap-covered " (get a "cap_count") " :supply/cap-coverage-pct " (num-str (get a "cap_coverage")) " "
              ":supply/derived true}")))
    (P "]")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── main (file I/O at the #?(:clj) edge) ──────────────────────────────────────

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-public-companies.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           rows (kedn/load-edn seed)
           {:keys [companies addresses contacts edges processes]} (kedn/classify rows)
           a (analyze companies edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md")
             (render-report companies addresses contacts edges processes a))
       (spit (clojure.java.io/file outdir "supply-criticality.kotoba.edn")
             (render-datoms companies a))
       (println (str "kabuto: " (count companies) " companies, " (count edges) " supply edges, "
                     (count addresses) " HQ addresses, " (count processes) " processes"))
       (println (str "single-source dependencies (≥0.7): " (count (get a "single_source"))))
       (let [top (take 3 (rank-desc (get a "jurisdiction_load")))]
         (println (str "top supplier jurisdictions: "
                       (str/join ", " (map (fn [[c v]] (str c " " (rstr v 2))) top)))))
       (println (str "wrote " (clojure.java.io/file outdir "intel-report.md") " + "
                     (clojure.java.io/file outdir "supply-criticality.kotoba.edn")))
       0)))
