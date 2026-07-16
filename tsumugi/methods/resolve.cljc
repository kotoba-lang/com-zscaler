(ns tsumugi.methods.resolve
  "tsumugi 紡ぎ — §D7.1 latent-entity frontier resolver (on-read).
  Clojure port of methods/resolve.py (1:1, the pure core).

  Computes latent-entity existence via noisy-OR over incident :en/evidence edges
  (existence = aggregate-first, method-versioned, computed FROM the edges — N1/G2, never a
  stored truth-score). Emits :latent/* datoms classified into a frontier
  (:observed / :candidate / :fission-ready).

  stdlib only, deterministic. The Python `read_edn` import is used only by main (file read);
  the core takes already-loaded orgs + edges. `math` is imported by the module but the core
  uses only arithmetic (noisy-OR product + round)."
  (:require [clojure.string :as str]))

(defn v
  "Format a value for EDN output (ingest.py style)."
  [x]
  (cond
    (true? x) "true"
    (false? x) "false"
    (string? x) (if (str/starts-with? x ":") x (str "\"" x "\""))
    :else (str x)))

(defn- pyround [x n] (let [f (Math/pow 10.0 n)] (/ (Math/rint (* (double x) f)) f)))

(defn resolve-latent-entities
  "Compute latent-entity existence via noisy-OR over incident :en/evidence edges. Returns
  :latent/* datom maps, sorted by :latent/organism (deterministic)."
  [orgs edges]
  (let [latent-org-ids (for [[oid org] orgs
                             :when (or (= false (get org ":organism/claimed?"))
                                       (= ":latent" (get org ":organism/standing")))]
                         oid)]
    (vec
     (for [org-id (sort latent-org-ids)]
       (let [evidence-edges (filter #(and (= ":evidence" (get % ":en/kind"))
                                          (= org-id (get % ":en/to")))
                                    edges)
             k (count evidence-edges)]
         (if (zero? k)
           {":latent/organism" org-id ":latent/existence" 0.0 ":latent/evidence-count" 0
            ":latent/viewpoint-consensus" 0 ":latent/method-version" "latent-resolve/v1-noisy-or"
            ":latent/frontier" ":observed"}
           (let [weights (map (fn [e]
                                (let [w (double (or (get e ":en/evidence-weight") 0.0))]
                                  (max 0.0 (min 1.0 w))))
                              evidence-edges)
                 prod (reduce (fn [acc w] (* acc (- 1.0 w))) 1.0 weights)
                 existence (pyround (- 1.0 prod) 4)
                 consensus (count (set (keep #(get % ":en/evidence-kind") evidence-edges)))
                 frontier (cond
                            (and (>= existence 0.7) (>= consensus 2) (>= k 2)) ":fission-ready"
                            (and (>= existence 0.4) (>= k 1)) ":candidate"
                            :else ":observed")]
             {":latent/organism" org-id ":latent/existence" existence ":latent/evidence-count" k
              ":latent/viewpoint-consensus" consensus
              ":latent/method-version" "latent-resolve/v1-noisy-or"
              ":latent/frontier" frontier})))))))

(defn to-edn
  "Serialize latent datoms to EDN (ingest.py style)."
  [datoms]
  (let [head [";; tsumugi 紡ぎ — GENERATED latent-entity frontier (§D7.1 on-read resolver). DO NOT hand-edit."
              ";; existence = aggregate-first, method-versioned, computed FROM incident :en/evidence edges (N1/G2)."
              "["]
        ks [":latent/organism" ":latent/existence" ":latent/evidence-count"
            ":latent/viewpoint-consensus" ":latent/method-version" ":latent/frontier"]
        rows (map (fn [d]
                    (str "{" (str/join " " (for [k ks :when (contains? d k)]
                                             (str k " " (v (get d k))))) "}"))
                  datoms)]
    (str (str/join "\n" (concat head rows ["]"])) "\n")))
