(ns tsumugi.methods.analyze
  "tsumugi 紡ぎ — spirit-in-physics intel analyzer over the Engi Knowledge Graph.
  Clojure port of methods/analyze.py (1:1). ADR-2606011800 (§D7.1 of ADR-2606011000)
  + spirit-ontology (ADR-2606011500).

  Reads a kotoba-EDN power-graph (:organism/* nodes + :en/* 縁), applies the
  Spirit-in-Physics pipeline (10-dim sector/role emotion vector → RBF kernel W →
  Laplacian L = D − W → spectral 3D embed → tensegrity unilateral-spring relax), and
  emits (1) an aggregate-first 取-concentration intel report and (2) the connected
  spirit-graph as :spirit.bond/* + :spirit/* + :grasp/* datoms.

  CONSTITUTIONAL (N1, G2): karma/取 lives ONLY on edges. An organism's 取-concentration is
  the INTEGRAL of its incident edges (held = inbound = power-over-others; bound = outbound
  = しがらみ) — never a stored per-soul score. Power-holding entities only (G1).
  Non-adjudicating mirror (N2): the report is an accountability map, NEVER a target-list.

  numpy → pure Clojure. The single numpy dependency (np.linalg.eigh of the symmetric
  Laplacian for the spectral 3D embedding) is reimplemented as a cyclic Jacobi
  eigensolver with hand-rolled matrix ops; stdlib only, no Java numeric libs. The
  embedding feeds only the tensegrity layout (not any emitted aggregate / readout), so
  eigenvector sign/order ambiguity does not perturb the intel artifacts; eigenVALUES are
  order/sign-stable and matched against numpy within tolerance in the oracle test.

  Keywords stay as ':ns/name' STRINGS (EDN reader convention), 1:1 with the Python.
  main/CLI + file I/O legs are omitted (consistent with the py→clj port wave); the live
  / outward atproto-follow legs are G7 + Council-gated and not ported."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal EDN reader (subset; mirrors analyze._tokens/_atom/_parse/read_edn) ───────────
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
    :else (or (try #?(:clj (Long/parseLong t)
                      :cljs (when (re-matches #"[-+]?\d+" t) (js/parseInt t 10)))
                   (catch #?(:clj Exception :cljs :default) _ nil))
              (try #?(:clj (Double/parseDouble t)
                      :cljs (let [n (js/parseFloat t)] (when-not (js/isNaN n) n)))
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

;; ── representative 10-dim sector/role vector (deterministic; NOT a personality score) ────
(def EMO-LABELS [":joy" ":sadness" ":anger" ":fear" ":disgust"
                 ":calm" ":focus" ":surprise" ":confusion" ":interest"])
(def SECTOR-VEC
  {":automaker"        [0.5 0.2 0.2 0.3 0.1 0.6 0.9 0.2 0.1 0.7]
   ":auto-supplier"    [0.4 0.2 0.2 0.4 0.1 0.7 0.9 0.2 0.1 0.6]
   ":holding"          [0.3 0.2 0.3 0.5 0.2 0.4 0.6 0.3 0.3 0.8]
   ":semiconductor-ip" [0.4 0.1 0.2 0.3 0.1 0.5 0.9 0.3 0.1 0.8]
   ":foundry"          [0.3 0.1 0.2 0.6 0.1 0.5 0.95 0.2 0.1 0.7]
   ":semiconductor"    [0.5 0.1 0.2 0.4 0.1 0.5 0.9 0.4 0.1 0.85]
   ":platform"         [0.6 0.2 0.3 0.4 0.2 0.5 0.8 0.5 0.2 0.9]
   ":ai-lab"           [0.6 0.2 0.3 0.5 0.2 0.4 0.85 0.7 0.3 0.95]
   ":bank"             [0.3 0.3 0.3 0.6 0.2 0.6 0.7 0.2 0.2 0.6]
   ":regulator"        [0.3 0.3 0.4 0.5 0.3 0.7 0.6 0.2 0.3 0.5]
   ":ecological"       [0.4 0.4 0.2 0.3 0.1 0.9 0.3 0.3 0.2 0.4]
   ":exec-role"        [0.5 0.2 0.4 0.5 0.2 0.5 0.8 0.3 0.2 0.8]
   ":media"            [0.5 0.3 0.3 0.4 0.2 0.4 0.7 0.6 0.3 0.9]
   ":gov"              [0.4 0.3 0.3 0.5 0.2 0.6 0.7 0.3 0.3 0.6]})
(def DEFAULT-VEC (vec (repeat 10 0.5)))

(defn load-records
  "Split parsed EDN records into [orgs edges] (mirror of analyze.load minus the file read).
  orgs is an insertion-ordered seq of [id node]; edges is a vector of edge maps."
  [records]
  (reduce (fn [[orgs edges] m]
            (cond
              (not (map? m)) [orgs edges]
              (contains? m ":organism/id") [(conj orgs [(get m ":organism/id") m]) edges]
              (contains? m ":en/id") [orgs (conj edges m)]
              :else [orgs edges]))
          [[] []] records))

#?(:clj
   (defn load
     "Read + split the power-graph file. Returns [orgs edges] (orgs = ordered [[id node] …])."
     [seed-path]
     (load-records (read-edn (slurp (io/file (str seed-path)))))))

;; ── pure-Clojure numeric helpers (replace numpy) ─────────────────────────────────────────
(defn- dvec [coll] (mapv double coll))

(defn- vec-norm [v] (Math/sqrt (reduce + 0.0 (map #(* % %) v))))

(defn- normalize-rows
  "F / (||row|| + 1e-9), row-wise — mirror of F / (np.linalg.norm(F,axis=1,keepdims=True)+1e-9)."
  [rows]
  (mapv (fn [row] (let [d (+ (vec-norm row) 1e-9)] (mapv #(/ % d) row))) rows))

(defn- sq-dist [a b] (reduce + 0.0 (map (fn [x y] (let [d (- x y)] (* d d))) a b)))

(defn- median
  "numpy.median over a non-empty seq of numbers."
  [xs]
  (let [s (vec (sort xs)) m (count s) h (quot m 2)]
    (if (odd? m)
      (double (nth s h))
      (/ (+ (double (nth s (dec h))) (double (nth s h))) 2.0))))

(defn- pyround
  "Python round (banker's rounding, ties-to-even) at n decimals — matches np/Python round()."
  [x n]
  (let [f (Math/pow 10.0 n)]
    (/ (Math/rint (* (double x) f)) f)))

;; ── cyclic Jacobi eigensolver for a real SYMMETRIC matrix (replaces np.linalg.eigh) ──────
;; eigenvalues ascending (like eigh); eigenvectors as columns (evecs[k] = k-th eigenvector).
;; Sign/order within degenerate spaces is solver-defined (eigh-equivalent up to sign/order,
;; to which the layout is invariant). One cyclic sweep over all off-diagonal (p,q), p<q:
(defn- jacobi-sweep
  "One cyclic Jacobi sweep over all off-diagonal (p,q). Returns [a v]."
  [n a v]
  (loop [a a, v v, p 0, q 1]
    (cond
      (>= p n) [a v]
      (>= q n) (recur a v (inc p) (+ p 2))
      :else
      (let [apq (get-in a [p q])]
        (if (< (Math/abs apq) 1.0e-300)
          (recur a v p (inc q))
          (let [app (get-in a [p p])
                aqq (get-in a [q q])
                theta (/ (- aqq app) (* 2.0 apq))
                t (let [s (if (>= theta 0.0) 1.0 -1.0)]
                    (/ s (+ (Math/abs theta) (Math/sqrt (+ (* theta theta) 1.0)))))
                c (/ 1.0 (Math/sqrt (+ (* t t) 1.0)))
                s (* t c)
                ;; rotate A: update rows/cols p and q
                a (reduce
                   (fn [acc k]
                     (if (or (= k p) (= k q))
                       acc
                       (let [akp (get-in acc [k p])
                             akq (get-in acc [k q])
                             nkp (- (* c akp) (* s akq))
                             nkq (+ (* s akp) (* c akq))]
                         (-> acc
                             (assoc-in [k p] nkp) (assoc-in [p k] nkp)
                             (assoc-in [k q] nkq) (assoc-in [q k] nkq)))))
                   a (range n))
                napp (- (* c c app) (* 2.0 s c apq) (- (* s s aqq)))
                naqq (+ (* s s app) (* 2.0 s c apq) (* c c aqq))
                a (-> a
                      (assoc-in [p p] napp)
                      (assoc-in [q q] naqq)
                      (assoc-in [p q] 0.0)
                      (assoc-in [q p] 0.0))
                ;; rotate V (accumulate eigenvectors)
                v (reduce
                   (fn [acc k]
                     (let [vkp (get-in acc [k p])
                           vkq (get-in acc [k q])]
                       (-> acc
                           (assoc-in [k p] (- (* c vkp) (* s vkq)))
                           (assoc-in [k q] (+ (* s vkp) (* c vkq))))))
                   v (range n))]
            (recur a v p (inc q))))))))

(defn- eigh
  "Symmetric eigendecomposition via cyclic Jacobi. Returns [evals evecs-cols] with evals
  ascending (np.linalg.eigh convention); evecs-cols[k] = k-th eigenvector (a length-n seq)."
  [mat]
  (let [n (count mat)
        eps 1.0e-12
        max-sweeps 100]
    (loop [a (mapv dvec mat)
           v (mapv (fn [i] (mapv (fn [j] (if (= i j) 1.0 0.0)) (range n))) (range n))
           sweep 0]
      (let [off (reduce + 0.0
                        (for [p (range n) q (range (inc p) n)]
                          (let [x (get-in a [p q])] (* x x))))]
        (if (or (>= sweep max-sweeps) (<= off eps))
          (let [pairs (mapv (fn [k] [(get-in a [k k]) (mapv #(get-in v [% k]) (range n))])
                            (range n))
                sorted-pairs (vec (sort-by first pairs))]
            [(mapv first sorted-pairs) (mapv second sorted-pairs)])
          (let [[a v] (jacobi-sweep n a v)]
            (recur a v (inc sweep))))))))

;; ── full analysis pipeline (mirror of analyze.main, sans file I/O / CLI / prints) ────────
(defn analyze
  "Run the spirit-in-physics pipeline over [orgs edges] (orgs = ordered [[id node] …]).
  Returns a deterministic map of every value the Python main computes — counts, σ, the
  RBF kernel W, the Laplacian eigen-spectrum, the spectral+tensegrity embedding coords,
  held/bound/deg integrals, connectivity/separation readouts, the 取-concentration rank,
  and the connected components."
  [orgs edges]
  (let [ids (mapv first orgs)
        org-map (into {} orgs)
        idx (into {} (map-indexed (fn [i k] [k i]) ids))
        n (count ids)
        present? (fn [k] (contains? idx k))
        ;; keep only edges whose endpoints exist
        edges (vec (filter #(and (present? (get % ":en/from")) (present? (get % ":en/to"))) edges))
        ;; feature matrix F (n×10), normalized rows
        f (normalize-rows
           (mapv (fn [k] (dvec (get SECTOR-VEC (get (org-map k) ":tsumugi/sector") DEFAULT-VEC)))
                 ids))
        ;; pairwise squared distances D2
        d2 (mapv (fn [i] (mapv (fn [j] (sq-dist (nth f i) (nth f j))) (range n))) (range n))
        ;; sig = median of sqrt of the positive D2 entries (or 1.0 if none)
        pos (for [i (range n) j (range n) :let [d (get-in d2 [i j])] :when (> d 0.0)]
              (Math/sqrt d))
        sig (let [m (when (seq pos) (median pos))] (if (and m (not (zero? m))) m 1.0))
        sig2 (* sig sig)
        ;; RBF kernel W (dense), diagonal zeroed
        w (mapv (fn [i] (mapv (fn [j] (if (= i j) 0.0 (Math/exp (/ (- (get-in d2 [i j])) sig2))))
                              (range n)))
                (range n))
        ;; Laplacian L = D − W
        wsum (mapv (fn [i] (reduce + 0.0 (nth w i))) (range n))
        l (mapv (fn [i] (mapv (fn [j] (if (= i j) (- (nth wsum i) (get-in w [i j]))
                                          (- (get-in w [i j])))) (range n))) (range n))
        ;; spectral embedding: eigenvectors 2..4 of L (columns 1,2,3 — smallest non-trivial)
        [evals evecs] (eigh l)
        ;; coords = evecs[:, 1:4]  → row i = [evecs[1][i] evecs[2][i] evecs[3][i]]
        ncols (min 3 (max 0 (- n 1)))
        coords0 (mapv (fn [i] (mapv (fn [c] (nth (nth evecs (inc c)) i)) (range ncols))) (range n))
        ;; shell-normalize: coords /= (|coords|.max + 1e-9)
        amax (+ (reduce max 0.0 (for [row coords0 x row] (Math/abs x))) 1e-9)
        coords-norm (mapv (fn [row] (mapv #(/ % amax) row)) coords0)
        ;; tensegrity relax: 縁 edges as unilateral (tension-only) springs
        spring (mapv (fn [e]
                       (let [i (idx (get e ":en/from")) j (idx (get e ":en/to"))
                             g (double (get e ":en/grasping-load" 0.3))]
                         [i j (max 0.15 (- 1.0 g)) (+ 0.2 (* 0.8 g))]))
                     edges)
        coords (loop [coords coords-norm, iter 0]
                 (if (>= iter 150)
                   coords
                   (let [disp (reduce
                               (fn [disp [i j l0 k]]
                                 (let [d (mapv - (nth coords i) (nth coords j))
                                       dist (+ (vec-norm d) 1e-9)]
                                   (if (> dist l0)
                                     (let [scale (/ (* k (- dist l0)) dist)
                                           fv (mapv #(* scale %) d)]
                                       (-> disp
                                           (update i (fn [r] (mapv - r fv)))
                                           (update j (fn [r] (mapv + r fv)))))
                                     disp)))
                               (vec (repeat n (vec (repeat ncols 0.0))))
                               spring)]
                     (recur (mapv (fn [row dr] (mapv (fn [x dx] (+ x (* 0.05 dx))) row dr))
                                  coords disp)
                            (inc iter)))))
        ;; 取-concentration: integral of INCIDENT edges (N1)
        {:keys [held bound deg]}
        (reduce (fn [acc e]
                  (let [g (double (get e ":en/grasping-load" 0.3))
                        frm (get e ":en/from") to (get e ":en/to")]
                    (-> acc
                        (update-in [:held to] (fnil + 0.0) g)
                        (update-in [:bound frm] (fnil + 0.0) g)
                        (update-in [:deg frm] (fnil + 0) 1)
                        (update-in [:deg to] (fnil + 0) 1))))
                {:held (zipmap ids (repeat 0.0))
                 :bound (zipmap ids (repeat 0.0))
                 :deg (zipmap ids (repeat 0))}
                edges)
        ;; spirit connectivity (kernel row-sum) + separation
        conn wsum
        conn-min (reduce min conn)
        conn-max (reduce max conn)
        sep (mapv (fn [c] (- 1.0 (/ (- c conn-min) (+ (- conn-max conn-min) 1e-9)))) conn)
        ;; connected components (union-find over 縁)
        parent (let [p (transient (vec (range n)))]
                 (letfn [(find* [pv x] (loop [x x]
                                         (if (= (nth pv x) x) x
                                             (recur (nth pv x)))))]
                   (loop [p (vec (range n)) es edges]
                     (if (empty? es)
                       p
                       (let [e (first es)
                             a (find* p (idx (get e ":en/from")))
                             b (find* p (idx (get e ":en/to")))]
                         (recur (assoc p a b) (rest es)))))))
        find-root (fn [x] (loop [x x] (if (= (nth parent x) x) x (recur (nth parent x)))))
        comps (reduce (fn [acc k] (update acc (find-root (idx k)) (fnil conj []) k))
                      (sorted-map) ids)
        ;; Python sorted(ids, key=held, reverse=True) is STABLE → ties keep insertion order.
        ;; sort-by is stable in Clojure; keying on -held alone preserves the original id order.
        rank (vec (sort-by (fn [k] (- (get held k))) ids))]
    {"ids" ids
     "n" n
     "edges" edges
     "edge_count" (count edges)
     "sig" sig
     "W" w
     "evals" evals
     "evecs" evecs
     "coords" coords
     "held" held
     "bound" bound
     "deg" deg
     "conn" conn
     "sep" (zipmap ids sep)
     "sep_vec" sep
     "components" (vals comps)
     "rank" rank
     "org_map" org-map
     "idx" idx}))

;; ── emit: connected spirit-graph datoms (mirror of the EDN writer in main) ───────────────
(defn render-graph-edn
  "The connected spirit-graph as :spirit.bond/* + :spirit/* + :grasp/* datoms — edge-primary
  (N1): karma = :spirit.bond/signed-weight on edges only."
  [result]
  (let [{:strs [ids edges held bound sep org_map idx W]} result
        sep-of (into {} (map-indexed (fn [i k] [k (nth (get result "sep_vec") i)]) ids))]
    (str
     (str/join
      "\n"
      (concat
       [";; tsumugi 紡ぎ — GENERATED spirit-graph (ADR-2606011800). DO NOT hand-edit."
        ";; edge-primary (N1): karma=:spirit.bond/signed-weight on edges only." "["]
       (map (fn [e]
              (let [i (idx (get e ":en/from")) j (idx (get e ":en/to"))
                    g (double (get e ":en/grasping-load" 0.3))
                    l0 (max 0.15 (- 1.0 g))
                    k (+ 0.2 (* 0.8 g))
                    cond* (pyround (get-in W [i j]) 4)
                    eid (get e ":en/id")]
                (str "{:spirit.bond/id \"sb." (subs eid 3) "\" :spirit.bond/en \"" eid "\" "
                     ":spirit.bond/from \"" (get e ":en/from") "\" :spirit.bond/to \"" (get e ":en/to") "\" "
                     ":spirit.bond/mode :tension :spirit.bond/rest-length " (pyround l0 3) " "
                     ":spirit.bond/stiffness " (pyround k 3) " :spirit.bond/signed-weight " (pyround g 3) " "
                     ":spirit.bond/emotion-weight " cond* " :spirit.bond/conductance " cond* " "
                     ":spirit.bond/source " (get e ":en/source" ":declared") " :spirit.bond/sourcing :representative}")))
            edges)
       (mapcat (fn [k]
                 (let [i (idx k)]
                   [(str "{:spirit/id \"spirit.world." k "\" :spirit/organism \"" k "\" :spirit/scale :world "
                         ":spirit/separation " (pyround (sep-of k) 3) " :spirit/connectivity " (pyround (nth (get result "conn") i) 3) " "
                         ":spirit/sourcing :representative}")
                    (str "{:grasp/organism \"" k "\" :grasp/concentration " (pyround (get held k) 3) " "
                         ":grasp/bound-load " (pyround (get bound k) 3) " :grasp/release-target " (pyround (max 0.0 (- (get held k) 1.0)) 3) "}")]))
               ids)
       ["]"]))
     "\n")))

;; ── emit: aggregate-first intel report (mirror of the markdown writer in main) ───────────
(defn- fmt
  "Python format helper: f'{x:.Nf}' equivalent."
  [x n]
  #?(:clj (format (str "%." n "f") (double x)) :cljs (.toFixed (double x) n)))

(defn render-report
  "Aggregate-first 取-concentration intel report (markdown) — map-not-target (N2),
  edge-primary (N1)."
  [result]
  (let [{:strs [ids edges sig held bound deg org_map components rank sep_vec n]} result
        argmin-sep (apply min-key #(nth sep_vec %) (range n))
        argmax-sep (apply max-key #(nth sep_vec %) (range n))
        head ["# tsumugi 紡ぎ — Spirit-in-Physics Intel Report (取-concentration)"
              ""
              "> Aggregate-first accountability map of **取 (custody-debt) concentration** over"
              "> PUBLIC power-holding entities, routed toward release. **NOT** a target-list."
              "> Edge-primary (N1): karma lives on 縁, not on nodes. Sourcing: `:representative`"
              "> (publicly-documented sample, not exhaustive). Live planet-scale ingest is G11-gated."
              ""
              (str "- organisms (nodes): **" n "**   ·   縁 (edges): **" (count edges)
                   "**   ·   components: **" (count components) "**")
              (str "- emotion-kernel σ_eff = " (fmt sig 4)
                   "   ·   pipeline = sip/kernel-rbf/embed-spectral3/tensegrity-unilateral")
              ""
              "## Top 取-concentration holders (power held OVER others = Σ incident inbound 縁)"
              ""
              "| rank | entity | sector | 取 held | しがらみ bound | degree | release-target |"
              "|---|---|---|---|---|---|---|"]
        rank-rows (map-indexed
                   (fn [r k]
                     (let [o (org_map k)
                           rt (max 0.0 (- (get held k) 1.0))]
                       (str "| " (inc r) " | " (get o ":organism/label" k) " | "
                            (subs (get o ":tsumugi/sector" "—") 1) " | "
                            "**" (fmt (get held k) 2) "** | " (fmt (get bound k) 2) " | "
                            (get deg k) " | " (fmt rt 2) " |")))
                   (take 10 rank))
        spirit ["" "## Spirit readout (separation = blocked-channel entropy; ADR-2605170000)" ""
                (str "- most-connected (lowest separation): **"
                     (get (org_map (nth ids argmin-sep)) ":organism/label") "**")
                (str "- most-separated (highest separation): **"
                     (get (org_map (nth ids argmax-sep)) ":organism/label") "**")
                "" "## Connected components (繋がった 縁 clusters)" ""]
        comp-rows (map-indexed
                   (fn [c members]
                     (str (inc c) ". (" (count members) ") "
                          (str/join ", " (map #(get (org_map %) ":organism/label" %) members))))
                   (sort-by (comp - count) (map vec components)))
        tail ["" "## Honest limits"
              "- `:representative` seed (publicly-documented relationships, bounded sample)."
              "- Emotion vectors are sector-derived representatives, not measured assays."
              "- 取-concentration is an accountability lens on power, never a per-person score (N1/§D9)."
              "- Outward / live atproto-follow ingest over real persons is G11 + Council-gated."]]
    (str (str/join "\n" (concat head rank-rows spirit comp-rows tail)) "\n")))

#?(:clj
   (defn -main
     "CLI entry: mirrors analyze.main — optional [seed.edn] [--out OUTDIR]. ADR-2606261200
     cljc-native operator leg (was `python3 methods/analyze.py`)."
     [& argv]
     (let [argv (vec argv)
           args (vec (remove #(str/starts-with? % "--") argv))
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp
                      (io/file "20-actors" "tsumugi")))
           seed (if (seq args) (io/file (first args)) (io/file here "data" "seed-power-graph.kotoba.edn"))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           [orgs edges] (load seed)
           result (analyze orgs edges)]
       (.mkdirs out)
       (spit (io/file out "spirit-graph.kotoba.edn") (render-graph-edn result))
       (spit (io/file out "intel-report.md") (render-report result))
       (println (str "[tsumugi/analyze] " (count orgs) " organisms · " (count edges)
                     " 縁 → " (.getPath (io/file out "intel-report.md")))))))
