(ns tsumugi.methods.analyze-influence
  "tsumugi 紡ぎ — DIACHRONIC influence-history analyzer (ADR-2606061500).
  Clojure port of methods/analyze_influence.py (1:1).

  Extends the present-tense power-graph analyzer backward in time. Reads a
  kotoba-EDN influence-history graph (:organism/* + :hist/* + :mirror/* nodes and :flow/*
  directed influence 縁), then:

    1. validates the TEMPORAL DAG (N5): every :flow points forward in time; cycles-in-time
       are reported as data errors.
    2. runs the Spirit-in-Physics pipeline over the influence kernel: affect-class → 10-dim
       emotion vector → RBF kernel W → Laplacian L=D−W → spectral 3D embed → tensegrity
       unilateral-spring relax.
    3. computes EDGE-INTEGRAL influence readouts (N1 — never a stored per-soul score):
         - outbound-reach  : Katz-decayed forward reach a node SEEDS  (source-strength)
         - inbound-debt    : Katz-decayed influence a node RECEIVES   (synthesizer-strength)
         - broker          : inbound × outbound proxy
    4. emits an aggregate-first influence report + the connected influence-graph as
       :spirit.bond/* + :influence/* datoms (edge-primary).

  CONSTITUTIONAL (read influence-history-ontology.kotoba.edn):
    N1 edge-primary  — influence lives on :flow/signed-weight; node readouts are integrals.
    N2 mirror only   — nodes are observed, never spoken-as.
    N3 non-eschat.   — influence OF a tradition, never its truth; no verdict/salvation datom.
    N4 public+settled — public influence-bearing figures only; no living PII.
    N5 temporal DAG  — enforced + reported here.

  numpy → pure Clojure. The matrix inverse (I − βA)^−1 is computed via Gaussian elimination
  (LU decomposition); the spectral embedding uses a cyclic Jacobi eigensolver (same pattern as
  tsumugi.methods.analyze, duplicated here since analyze/eigh is private to that ns). stdlib
  only; no Java numeric libs.

  Keywords stay as ':ns/name' STRINGS (EDN reader convention), 1:1 with the Python.
  main/CLI + file I/O legs are omitted (consistent with the py→clj port wave); the live
  / outward legs are G7 + Council-gated and not ported."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze :as an]
            #?(:clj [clojure.java.io :as io])))

;; ── re-export shared EDN reader from analyze (same family) ───────────────────────────────
(def read-edn an/read-edn)

;; ── representative 10-dim affect/role vector (deterministic; NOT a soul score) ──────────
(def EMO-LABELS [":joy" ":sadness" ":anger" ":fear" ":disgust"
                 ":calm" ":focus" ":surprise" ":confusion" ":interest"])

(def AFFECT-VEC
  {":covenantal" [0.5 0.4 0.3 0.5 0.2 0.6 0.7 0.3 0.2 0.8]
   ":reforming"  [0.5 0.3 0.6 0.4 0.3 0.4 0.8 0.4 0.2 0.9]
   ":liberative" [0.5 0.3 0.1 0.2 0.1 0.95 0.7 0.3 0.2 0.7]
   ":inquiring"  [0.4 0.2 0.2 0.3 0.2 0.6 0.9 0.6 0.4 0.95]
   ":animist"    [0.6 0.3 0.2 0.3 0.1 0.8 0.5 0.5 0.3 0.7]
   ":ordering"   [0.4 0.3 0.3 0.4 0.2 0.7 0.8 0.2 0.2 0.6]})

(def DEFAULT-VEC (vec (repeat 10 0.5)))

;; ── pure-Clojure numeric helpers ─────────────────────────────────────────────────────────
(defn- dvec [coll] (mapv double coll))

(defn- vec-norm [v] (Math/sqrt (reduce + 0.0 (map #(* % %) v))))

(defn- sq-dist [a b]
  (reduce + 0.0 (map (fn [x y] (let [d (- x y)] (* d d))) a b)))

(defn- normalize-rows [rows]
  (mapv (fn [row] (let [d (+ (vec-norm row) 1e-9)] (mapv #(/ % d) row))) rows))

(defn- median [xs]
  (let [s (vec (sort xs)) m (count s) h (quot m 2)]
    (if (odd? m)
      (double (nth s h))
      (/ (+ (double (nth s (dec h))) (double (nth s h))) 2.0))))

(defn- pyround
  "Python round (banker's rounding, ties-to-even) at n decimals."
  [x n]
  (let [f (Math/pow 10.0 n)]
    (/ (Math/rint (* (double x) f)) f)))

;; ── LU-based matrix inverse for small n (replaces np.linalg.inv) ─────────────────────────
;; Used for Katz centrality: M = (I − βA)^−1 − I.
(defn- mat-inv
  "LU decomposition with partial pivoting → inverse of n×n matrix A (as vec-of-vecs)."
  [A]
  (let [n (count A)
        ;; augment [A | I]
        aug (mapv (fn [i]
                    (let [row (dvec (nth A i))
                          id-row (mapv (fn [j] (if (= i j) 1.0 0.0)) (range n))]
                      (vec (concat row id-row))))
                  (range n))]
    (loop [aug aug, col 0]
      (if (>= col n)
        ;; extract right half
        (mapv (fn [row] (subvec row n (* 2 n))) aug)
        ;; partial pivot: find max |aug[row][col]| for row >= col
        (let [prow (apply max-key (fn [r] (Math/abs (nth (nth aug r) col))) (range col n))
              aug (if (= prow col) aug
                      (assoc aug col (nth aug prow) prow (nth aug col)))
              pivot (nth (nth aug col) col)]
          (if (< (Math/abs pivot) 1e-300)
            (throw (ex-info "Singular matrix in mat-inv" {:col col}))
            ;; eliminate below
            (let [aug (reduce (fn [a r]
                                (let [factor (/ (nth (nth a r) col) pivot)
                                      new-row (mapv (fn [j] (- (nth (nth a r) j)
                                                               (* factor (nth (nth a col) j))))
                                                    (range (* 2 n)))]
                                  (assoc a r new-row)))
                              aug (range (inc col) n))
                  ;; normalise pivot row
                  aug (assoc aug col (mapv #(/ % pivot) (nth aug col)))]
              ;; back-substitute
              (let [aug (reduce (fn [a r]
                                  (let [factor (nth (nth a r) col)
                                        new-row (mapv (fn [j] (- (nth (nth a r) j)
                                                                 (* factor (nth (nth a col) j))))
                                                      (range (* 2 n)))]
                                    (assoc a r new-row)))
                                aug (range 0 col))]
                (recur aug (inc col))))))))))

;; ── load influence-history EDN ────────────────────────────────────────────────────────────
(defn load-records
  "Split parsed EDN records into [nodes flows].
  nodes: map of id → node-map. flows: vector of flow maps."
  [records]
  (reduce (fn [[nodes flows] m]
            (cond
              (not (map? m)) [nodes flows]
              (contains? m ":organism/id") [(assoc nodes (get m ":organism/id") m) flows]
              (contains? m ":flow/id") [nodes (conj flows m)]
              :else [nodes flows]))
          [{} []] records))

#?(:clj
   (defn load
     "Read + split the influence-history file. Returns [nodes flows]."
     [path]
     (load-records (read-edn (slurp (io/file (str path)))))))

;; ── temporal year accessor ────────────────────────────────────────────────────────────────
(defn node-year
  "Get the year value keyed by `which` (or :hist/year-from fallback), as int."
  [node which]
  (int (or (get node which)
           (get node ":hist/year-from")
           0)))

;; ── N5: temporal-DAG validation ──────────────────────────────────────────────────────────
(defn check-temporal-dag
  "Returns a vector of violation tuples [flow-id from ya to yb] for any flow where
  source.year-from > receiver.year-to (source begins after receiver has ended)."
  [nodes flows]
  (reduce (fn [acc f]
            (let [a (get nodes (get f ":flow/from"))
                  b (get nodes (get f ":flow/to"))
                  ya (node-year a ":hist/year-from")
                  yb (node-year b ":hist/year-to")]
              (if (> ya yb)
                (conj acc [(get f ":flow/id") (get f ":flow/from") ya (get f ":flow/to") yb])
                acc)))
          [] flows))

;; ── cyclic Jacobi eigensolver (private copy; analyze/eigh is defn- so not re-exported) ────
;; Same algorithm as tsumugi.methods.analyze/eigh — eigenvalues ascending (np.linalg.eigh
;; convention); evecs[k] = k-th eigenvector (length-n seq).
(defn- jacobi-sweep*
  "One cyclic Jacobi sweep over all off-diagonal (p,q) pairs."
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
                a (reduce (fn [acc k]
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
                a (-> a (assoc-in [p p] napp) (assoc-in [q q] naqq)
                       (assoc-in [p q] 0.0)  (assoc-in [q p] 0.0))
                v (reduce (fn [acc k]
                            (let [vkp (get-in acc [k p])
                                  vkq (get-in acc [k q])]
                              (-> acc
                                  (assoc-in [k p] (- (* c vkp) (* s vkq)))
                                  (assoc-in [k q] (+ (* s vkp) (* c vkq))))))
                           v (range n))]
            (recur a v p (inc q))))))))

(defn- eigh*
  "Cyclic Jacobi eigendecomposition of real symmetric matrix. Returns [evals evecs-cols],
  evals ascending (np.linalg.eigh convention); evecs-cols[k] = k-th eigenvector."
  [mat]
  (let [n (count mat)
        eps 1.0e-12 max-sweeps 100]
    (loop [a (mapv dvec mat)
           v (mapv (fn [i] (mapv (fn [j] (if (= i j) 1.0 0.0)) (range n))) (range n))
           sweep 0]
      (let [off (reduce + 0.0
                        (for [p (range n) q (range (inc p) n)
                              :let [x (get-in a [p q])]] (* x x)))]
        (if (or (>= sweep max-sweeps) (<= off eps))
          (let [pairs (mapv (fn [k] [(get-in a [k k])
                                     (mapv #(get-in v [% k]) (range n))]) (range n))
                sorted-pairs (vec (sort-by first pairs))]
            [(mapv first sorted-pairs) (mapv second sorted-pairs)])
          (let [[a v] (jacobi-sweep* n a v)]
            (recur a v (inc sweep))))))))

;; ── Spirit-in-Physics pipeline (affect-class → RBF kernel → Laplacian → spectral + tensegrity)
(defn- build-physics
  "Run the spectral+tensegrity layout. Returns {:w :sig :coords :sep :conn}."
  [ids nodes flows idx]
  (let [n (count ids)
        ;; feature matrix F (n×10), normalized rows
        f (normalize-rows
           (mapv (fn [k] (dvec (get AFFECT-VEC (get (get nodes k) ":influence/affect-class") DEFAULT-VEC)))
                 ids))
        ;; pairwise squared distances D2
        d2 (mapv (fn [i] (mapv (fn [j] (sq-dist (nth f i) (nth f j))) (range n))) (range n))
        ;; sig = median(sqrt(positive D2)) or 1.0
        pos (for [i (range n) j (range n)
                  :let [d (get-in d2 [i j])]
                  :when (> d 0.0)]
              (Math/sqrt d))
        sig (let [m (when (seq pos) (median pos))] (if (and m (not (zero? m))) m 1.0))
        sig2 (* sig sig)
        ;; RBF kernel W
        w (mapv (fn [i]
                  (mapv (fn [j] (if (= i j) 0.0 (Math/exp (/ (- (get-in d2 [i j])) sig2))))
                        (range n)))
                (range n))
        ;; Laplacian L = D − W
        wsum (mapv (fn [i] (reduce + 0.0 (nth w i))) (range n))
        l (mapv (fn [i]
                  (mapv (fn [j] (if (= i j) (- (nth wsum i) (get-in w [i j]))
                                    (- (get-in w [i j])))) (range n)))
                (range n))
        ;; spectral embedding: eigenvectors 2..4 of L
        [_evals evecs] (eigh* l)
        ncols (min 3 (max 0 (- n 1)))
        coords0 (mapv (fn [i]
                        (mapv (fn [c] (nth (nth evecs (inc c)) i)) (range ncols)))
                      (range n))
        amax (+ (reduce max 0.0 (for [row coords0 x row] (Math/abs x))) 1e-9)
        coords-norm (mapv (fn [row] (mapv #(/ % amax) row)) coords0)
        ;; tensegrity relax: :flow/* edges as unilateral springs
        spring (mapv (fn [f]
                       (let [i (idx (get f ":flow/from"))
                             j (idx (get f ":flow/to"))
                             s (double (Math/abs
                                        (double (or (get f ":flow/strain")
                                                    (Math/abs (double (get f ":flow/signed-weight" 0.3)))))))]
                         [i j (max 0.15 (- 1.0 s)) (+ 0.2 (* 0.8 s))]))
                     flows)
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
        ;; spirit connectivity + separation
        conn wsum
        conn-min (reduce min conn)
        conn-max (reduce max conn)
        sep (mapv (fn [c] (- 1.0 (/ (- c conn-min) (+ (- conn-max conn-min) 1e-9)))) conn)]
    {:w w :sig sig :coords coords :conn conn :sep sep}))

;; ── Katz centrality via matrix inverse ───────────────────────────────────────────────────
(defn- katz-centrality
  "Compute Katz outbound-reach and inbound-debt via M=(I−βA)^−1 − I.
  A[i][j] = |signed-weight| of flows from i to j.
  outbound = M.sum(axis=1), inbound = M.sum(axis=0)."
  [n ids idx flows beta]
  (let [;; build adjacency A (n×n)
        A (reduce (fn [acc f]
                    (let [i (idx (get f ":flow/from"))
                          j (idx (get f ":flow/to"))
                          w (Math/abs (double (get f ":flow/signed-weight" 0.3)))]
                      (update-in acc [i j] (fnil + 0.0) w)))
                  (vec (repeat n (vec (repeat n 0.0)))) flows)
        ;; I − βA
        IbA (mapv (fn [i]
                    (mapv (fn [j] (- (if (= i j) 1.0 0.0) (* beta (get-in A [i j]))))
                          (range n)))
                  (range n))
        ;; M = (I − βA)^−1 − I
        M-full (try (mat-inv IbA)
                    (catch #?(:clj Exception :cljs :default) e
                      ;; fallback: identity (should not happen on a DAG)
                      (vec (repeat n (vec (repeat n 0.0))))))
        M (mapv (fn [i] (mapv (fn [j] (- (get-in M-full [i j]) (if (= i j) 1.0 0.0)))
                               (range n)))
                (range n))
        outbound (mapv (fn [i] (reduce + 0.0 (nth M i))) (range n))
        inbound  (mapv (fn [j] (reduce + 0.0 (map #(get-in M [% j]) (range n)))) (range n))
        omax (let [mx (reduce max outbound)] (if (zero? mx) 1.0 mx))
        imax (let [mx (reduce max inbound)]  (if (zero? mx) 1.0 mx))
        broker (mapv (fn [i] (* (/ (nth outbound i) omax) (/ (nth inbound i) imax)))
                     (range n))]
    {:outbound outbound :inbound inbound :broker broker}))

;; ── connected components (union-find) ────────────────────────────────────────────────────
(defn- connected-components
  "Returns a map of root-idx → [member-ids] over the undirected projection of flows."
  [ids idx flows]
  (let [n (count ids)
        find-root (fn [p x]
                    (loop [x x]
                      (if (= (nth p x) x) x (recur (nth p x)))))
        p (reduce (fn [p f]
                    (let [a (find-root p (idx (get f ":flow/from")))
                          b (find-root p (idx (get f ":flow/to")))]
                      (assoc p a b)))
                  (vec (range n)) flows)
        find-r (fn [x] (loop [x x] (if (= (nth p x) x) x (recur (nth p x)))))]
    (reduce (fn [acc k]
              (update acc (find-r (idx k)) (fnil conj []) k))
            (sorted-map) ids)))

;; ── main analysis pipeline ────────────────────────────────────────────────────────────────
(defn analyze
  "Run the full diachronic influence analysis over [nodes flows].
  nodes = map of id→node; flows = vector of flow maps.
  Returns a deterministic result map mirroring the Python main's computed values."
  [nodes flows]
  (let [ids (vec (keys nodes))
        idx (into {} (map-indexed (fn [i k] [k i]) ids))
        n (count ids)
        ;; keep only flows whose endpoints are in the graph
        flows (vec (filter #(and (contains? idx (get % ":flow/from"))
                                 (contains? idx (get % ":flow/to")))
                           flows))
        violations (check-temporal-dag nodes flows)
        ;; physics
        {:keys [w sig coords conn sep]} (build-physics ids nodes flows idx)
        ;; Katz centrality
        beta 0.5
        {:keys [outbound inbound broker]} (katz-centrality n ids idx flows beta)
        ;; connected components
        comps (connected-components ids idx flows)]
    {"ids" ids
     "n" n
     "flows" flows
     "flow_count" (count flows)
     "violations" violations
     "sig" sig
     "W" w
     "coords" coords
     "conn" conn
     "sep" sep
     "outbound" (zipmap ids outbound)
     "outbound_vec" outbound
     "inbound" (zipmap ids inbound)
     "inbound_vec" inbound
     "broker" (zipmap ids broker)
     "components" (vals comps)
     "nodes" nodes
     "idx" idx
     "beta" beta}))

;; ── label + conf helpers (mirror of lbl / conf in analyze_influence.py) ──────────────────
(defn lbl [nodes k]
  (get (get nodes k) ":organism/label" k))

(defn conf [nodes k]
  (let [v (get (get nodes k) ":hist/dating-confidence" ":representative")]
    (if (str/starts-with? v ":") (subs v 1) v)))

;; ── EDN emitter for the influence-graph (mirror of the EDN writer in main) ───────────────
(defn render-influence-graph-edn
  "Emit :spirit.bond/* + :influence/* datoms from an analyze result map."
  [result]
  (let [{:strs [ids flows W outbound_vec inbound_vec broker nodes idx sig]} result
        outbound (get result "outbound")
        inbound  (get result "inbound")
        broker-m (get result "broker")
        conn     (get result "conn")
        sep      (get result "sep")]
    (str/join
     "\n"
     (concat
      [";; tsumugi 紡ぎ — GENERATED diachronic influence-graph (ADR-2606061500). DO NOT hand-edit."
       ";; edge-primary (N1): influence=:spirit.bond/signed-weight on :flow/* edges only."
       ";; readouts are integrals of incident edges, materialized for viz — never a soul-score."
       "["]
      ;; :spirit.bond/* per flow
      (map (fn [f]
             (let [i    (idx (get f ":flow/from"))
                   j    (idx (get f ":flow/to"))
                   w    (double (get f ":flow/signed-weight" 0.3))
                   s    (double (Math/abs
                                 (double (or (get f ":flow/strain")
                                             (Math/abs w)))))
                   l0   (max 0.15 (- 1.0 s))
                   k    (+ 0.2 (* 0.8 s))
                   lag  (- (node-year (get nodes (get f ":flow/to")) ":hist/year-from")
                           (node-year (get nodes (get f ":flow/from")) ":hist/year-to"))
                   cond* (pyround (get-in W [i j]) 4)
                   fid  (get f ":flow/id")]
               (str "{:spirit.bond/id \"sb." (subs fid 3)
                    "\" :spirit.bond/en \"" fid
                    "\" :spirit.bond/from \"" (get f ":flow/from")
                    "\" :spirit.bond/to \"" (get f ":flow/to")
                    "\" :spirit.bond/mode :tension :spirit.bond/rest-length " (pyround l0 3)
                    " :spirit.bond/stiffness " (pyround k 3)
                    " :spirit.bond/signed-weight " (pyround w 3)
                    " :flow/kind " (get f ":flow/kind" ":influences")
                    " :flow/lag-years " lag
                    " :spirit.bond/emotion-weight " cond*
                    " :spirit.bond/conductance " cond*
                    " :spirit.bond/source " (get f ":flow/source" ":declared")
                    " :spirit.bond/sourcing :representative}")))
           flows)
      ;; :spirit/* + :influence/* per node
      (mapcat (fn [k]
                (let [i (idx k)
                      s (get sep i (get (get result "sep") k 0.0))
                      c (get conn i (get (get result "conn") k 0.0))]
                  [(str "{:spirit/id \"spirit.hist." k
                        "\" :spirit/organism \"" k
                        "\" :spirit/scale :historical"
                        " :spirit/separation " (pyround (double s) 3)
                        " :spirit/connectivity " (pyround (double c) 3)
                        " :spirit/sourcing :representative}")
                   (str "{:influence/node \"" k
                        "\" :influence/outbound-reach " (pyround (double (get outbound k 0.0)) 3)
                        " :influence/inbound-debt " (pyround (double (get inbound k 0.0)) 3)
                        " :influence/betweenness " (pyround (double (get broker-m k 0.0)) 3)
                        " :influence/method \"infl-1.0.0/katz-beta0.5/kernel-rbf/embed-spectral3/tensegrity-unilateral\"}")]))
             ids)
      ["]"]))))

;; ── aggregate-first influence report (mirror of the R[] builder in main) ─────────────────
(defn render-influence-report
  "Build the aggregate-first influence report markdown string."
  [result]
  (let [{:strs [ids flows n violations nodes outbound inbound broker components sig beta]} result
        by-out (vec (sort-by (fn [k] (- (get outbound k 0.0))) ids))
        by-in  (vec (sort-by (fn [k] (- (get inbound k 0.0))) ids))
        by-brk (vec (sort-by (fn [k] (- (get broker k 0.0))) ids))
        eras-order [":bronze-age" ":iron-age" ":axial" ":2nd-temple" ":late-antiquity"
                    ":early-medieval" ":medieval" ":reformation" ":enlightenment"
                    ":modern" ":contemporary"]
        by-era (reduce (fn [acc k]
                         (update acc (get (get nodes k) ":hist/era" ":?") (fnil conj []) k))
                       {} ids)
        nv (count violations)
        self-in (vec (filter #(= (get % ":flow/to") "self.etzhayyim") flows))
        comps-sorted (vec (sort-by (comp - count) components))]
    (str/join
     "\n"
     (concat
      ["# tsumugi 紡ぎ — Diachronic Influence-History Report"
       ""
       "> Aggregate-first map of **influence-as-information-flow** across PUBLIC historical"
       "> figures, documents, events and traditions, routed to understanding — **NOT** a"
       "> ranking of worth, a target-list, or a hagiography. Edge-primary (N1): influence"
       "> lives on 縁, never on a soul. Sourcing `:representative` (documented intellectual-"
       "> /religious-history, bounded sample). **We datafy the INFLUENCE OF a tradition,"
       "> NEVER its theological truth** (N3, 非終末論). Mirror-only: nodes are observed,"
       "> never spoken-as (N2). Live planet-scale ingest is G7/Council-gated."
       ""
       (str "- nodes: **" n "**  ·  influence 縁: **" (count flows)
            "**  ·  components: **" (count components) "**")
       (str "- emotion-kernel σ_eff = " (format "%.4f" (double sig))
            "  ·  Katz β = " beta "  ·  method = infl-1.0.0")
       (str "- temporal-DAG (N5): **"
            (if (zero? nv) "OK — all edges forward in time"
                (str nv " VIOLATION(S)")) "**")
       ""]
      (when (pos? nv)
        (concat
         ["### ⚠ N5 temporal violations (information cannot precede its source)" ""]
         (map (fn [[fid fr ya to yb]]
                (str "- `" fid "`: " (lbl nodes fr) " (ends " ya ") → " (lbl nodes to) " (begins " yb ")"))
              violations)
         [""]))
      ["## Top influence SOURCES (outbound-reach = forward Katz reach this node SEEDS)"
       ""
       "> Who/what propagated furthest downstream through history. Edge-integral, never a soul-score."
       ""
       "| rank | node | kind | tradition | outbound-reach | dating |"
       "|---|---|---|---|---|---|"]
      (map-indexed (fn [i k]
                     (let [nd (get nodes k)
                           trad (str/join "," (map (fn [t]
                                                     (let [ts (str t)]
                                                       (if (str/starts-with? ts ":") (subs ts 1) ts)))
                                                   (get nd ":hist/tradition" [])))
                           knd (let [s (str (get nd ":hist/subkind" ":—"))]
                                 (if (str/starts-with? s ":") (subs s 1) s))]
                       (str "| " (inc i) " | " (lbl nodes k) " | " knd " | " trad
                            " | **" (format "%.2f" (double (get outbound k 0.0)))
                            "** | " (conf nodes k) " |")))
                   (take 12 by-out))
      [""
       "## Top SYNTHESIZERS (inbound-debt = influence this node RECEIVED — the 産霊 side)"
       ""
       "| rank | node | kind | inbound-debt | outbound-reach |"
       "|---|---|---|---|---|"]
      (map-indexed (fn [i k]
                     (let [knd (let [s (str (get (get nodes k) ":hist/subkind" ":—"))]
                                 (if (str/starts-with? s ":") (subs s 1) s))]
                       (str "| " (inc i) " | " (lbl nodes k) " | " knd
                            " | **" (format "%.2f" (double (get inbound k 0.0)))
                            "** | " (format "%.2f" (double (get outbound k 0.0))) " |")))
                   (take 10 by-in))
      [""
       "## Top TRANSMISSION BROKERS (inbound × outbound — bridges through which history flowed)"
       ""]
      (map-indexed (fn [i k]
                     (str (inc i) ". **" (lbl nodes k) "** (broker "
                          (format "%.3f" (double (get broker k 0.0))) ")"))
                   (take 8 by-brk))
      (when (seq self-in)
        (concat
         [""
          "## etzhayyim doctrinal genealogy (own inbound influence — 産霊 receiving side)"
          ""
          "> Not a claim of equivalence with the sources; an honest map of documented inflows."
          ""]
         (map (fn [f]
                (str "- " (lbl nodes (get f ":flow/from"))
                     " —" (let [s (str (get f ":flow/kind" ""))]
                             (if (str/starts-with? s ":") (subs s 1) s))
                     "→ etzhayyim (w="
                     (format "%+.2f" (double (get f ":flow/signed-weight" 0.0)))
                     ", strain="
                     (format "%.2f" (double (get f ":flow/strain" 0.0))) ")"))
              (sort-by (fn [f] (- (Math/abs (double (get f ":flow/signed-weight" 0.0))))) self-in))))
      [""
       "## Era layering (diachronic 軌跡 — Wellbecoming as history, not a final state; N2/N3)"
       ""]
      (keep (fn [e]
              (when (contains? by-era e)
                (str "- **" (subs e 1) "**: "
                     (str/join ", " (map (partial lbl nodes) (get by-era e))))))
            eras-order)
      [""
       "## Connected components (woven 縁 clusters)"
       ""]
      (map-indexed (fn [c members]
                     (str (inc c) ". (" (count members) ") "
                          (str/join ", " (map (partial lbl nodes) members))))
                   comps-sorted)
      [""
       "## Honest limits"
       "- `:representative` seed — documented influence, a bounded sample, NOT exhaustive."
       "- Dating is approximate; per-node `:hist/dating-confidence` flags legendary/traditional"
       "  attributions (e.g. Moses, Bodhidharma) — never asserted as fact (N3)."
       "- Affect vectors are class-derived representatives, not measured assays."
       "- Influence readouts are edge-integrals (N1), an analytic lens — never a per-soul score,"
       "  a verdict on a tradition's truth (N3), or a ranking of human worth (N4)."
       "- Outward / live ingest (archives, citation graphs) + any published post is G7+Council-gated."]))))

#?(:clj
   (defn -main
     "CLI entry: mirrors analyze_influence.main — [seed.edn] [--out OUTDIR]. ADR-2606261200
     cljc-native operator leg (was `python3 methods/analyze_influence.py`)."
     [& argv]
     (let [argv (vec argv)
           args (vec (remove #(str/starts-with? % "--") argv))
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           seed (if (seq args) (io/file (first args)) (io/file here "data" "seed-influence-history.kotoba.edn"))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           [nodes flows] (load seed)
           result (analyze nodes flows)]
       (.mkdirs out)
       (spit (io/file out "influence-graph.kotoba.edn") (render-influence-graph-edn result))
       (spit (io/file out "influence-report.md") (render-influence-report result))
       (println (str "[tsumugi/influence] " (count nodes) " nodes · " (count flows)
                     " flows → " (.getPath (io/file out "influence-report.md")))))))
