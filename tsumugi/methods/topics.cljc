(ns tsumugi.methods.topics
  "tsumugi 紡ぎ — §D7.1 topic derivation (viewpoint-cluster, R0).
  Clojure port of methods/topics.py (1:1, the pure core).

  Derives :topic/* nodes + :en/kind :topic-binding edges from evidence-edge viewpoints
  (G2 edge-primary, N1). Each distinct :en/evidence-kind is a candidate topic; coherence =
  the share of evidence-bearing entities that carry that viewpoint; a binding's confidence is
  the clamped sum of its evidence weights.

  stdlib only, deterministic. The Python `read_edn` import is used only by main (file read),
  omitted from this port; the core takes already-loaded edges."
  (:require [clojure.string :as str]))

(defn v
  "Format a value for EDN output (ingest.py style)."
  [x]
  (cond
    (true? x) "true"
    (false? x) "false"
    (string? x) (if (str/starts-with? x ":") x (str "\"" x "\""))
    :else (str x)))

(defn- strip1 [s] (if (str/starts-with? s ":") (subs s 1) s))   ; Python viewpoint[1:]
(defn- pyround [x n] (let [f (Math/pow 10.0 n)] (/ (Math/rint (* (double x) f)) f)))

(defn humanize-viewpoint
  "Convert :semantic → 'semantic-viewpoint topic'."
  [viewpoint-str]
  (str (strip1 viewpoint-str) "-viewpoint topic"))

(defn derive-topics
  "Derive [topics bindings] from evidence edges, both sorted deterministically."
  [edges]
  (let [{:keys [viewpoints evw]}
        (reduce (fn [acc e]
                  (if (= ":evidence" (get e ":en/kind"))
                    (let [vp (get e ":en/evidence-kind") ent (get e ":en/to")
                          w (double (get e ":en/evidence-weight" 0.0))]
                      (if (and (some? vp) (some? ent))
                        (-> acc (update :viewpoints conj vp)
                            (update-in [:evw [ent vp]] (fnil + 0.0) w))
                        acc))
                    acc))
                {:viewpoints #{} :evw {}} edges)
        entities-with-evidence (set (map first (keys evw)))
        n-ents (count entities-with-evidence)
        topics (mapv (fn [vp]
                       (let [topic-id (str "topic." (strip1 vp))
                             distinct-ents (count (set (for [[ent v*] (keys evw) :when (= v* vp)] ent)))
                             coherence (if (pos? n-ents) (pyround (/ (double distinct-ents) n-ents) 4) 0.0)]
                         {":topic/id" topic-id
                          ":topic/label" (humanize-viewpoint vp)
                          ":topic/coherence" coherence
                          ":topic/viewpoint" vp}))
                     (sort viewpoints))
        bindings (->> evw
                      (map (fn [[[ent vp] w]]
                             (let [topic-id (str "topic." (strip1 vp))]
                               {":en/id" (str "topicbind." topic-id "." ent)
                                ":en/kind" ":topic-binding"
                                ":en/from" topic-id
                                ":en/to" ent
                                ":en/binding-confidence" (pyround (min 1.0 (max 0.0 w)) 4)
                                ":en/stability" 1.0
                                ":en/source" ":derived"})))
                      (sort-by #(get % ":en/id"))
                      vec)]
    [topics bindings]))

(defn to-edn
  "Serialize topic datoms + bindings to EDN (ingest.py style)."
  [topics bindings]
  (let [head [";; tsumugi 紡ぎ — GENERATED topic graph (§D7.1 topic derivation, R0 viewpoint-cluster). DO NOT hand-edit."
              ";; :topic/* nodes + :en/kind :topic-binding edges from evidence viewpoints (G2 edge-primary, N1)."
              "["]
        topic-ks [":topic/id" ":topic/label" ":topic/coherence" ":topic/viewpoint"]
        bind-ks [":en/id" ":en/kind" ":en/from" ":en/to"
                 ":en/binding-confidence" ":en/stability" ":en/source"]
        row (fn [m ks] (str "{" (str/join " " (for [k ks :when (contains? m k)]
                                                (str k " " (v (get m k))))) "}"))
        lines (concat head
                      (map #(row % topic-ks) topics)
                      (map #(row % bind-ks) bindings)
                      ["]"])]
    (str (str/join "\n" lines) "\n")))
