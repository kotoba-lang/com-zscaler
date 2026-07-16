(ns kanae.methods.project-yoro
  "kanae 鼎 — fundFlowEdge → yoro projection (1:1 Clojure port of
  methods/project_yoro.py, ADR-2605302300 + ADR-2606042330). Projects assembled
  fundFlowEdge records into `:yoro.fiscal/*` (+ minimal `:yoro.profile/*`) datoms
  so the gov mirror-nodes resolve as actors on /search + /profile.

  Aggregate gov endpoints only (G10). Maps use string keys (mirroring the Python
  dicts / assemble output). JSON seed merge lives at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; Endpoint label → (yoro mirror-actor handle, stage tier). Aggregate gov nodes only (G10).
(def ^:private label-did
  [[#"一般会計|National Treasury|General Account" "kokko" "L7"]
   [#"文部科学省|Ministry of Education" "gov-jp-mext" "L5"]
   [#"国立大学法人" "gov-jp-mext-univ-grants" "L1"]
   [#"科学技術振興" "gov-jp-mext-sci-tech" "L1"]])

(def ^:private actor-prefix "did:web:etzhayyim.com:actor:")

;; Minimal profile records so projected gov nodes resolve as actors.
(def ^:private profiles
  {"gov-jp-mext"
   {"handle" "gov-jp-mext.etzhayyim.com"
    "displayName" "文部科学省 (MEXT) — gov mirror"
    "description" (str "Government mirror-actor (observational, ADR-2606042330). 文教及び科学振興費 "
                       "fiscal flow assembled by kanae from danjo budget ledger (:representative).")}})

(defn- slug-did [label]
  (or (some (fn [[pat handle _]] (when (re-find pat label) (str actor-prefix handle))) label-did)
      (let [base (-> (str/lower-case label)
                     (str/replace #"[^a-z0-9]+" "-")
                     (str/replace #"^-+|-+$" ""))
            base (subs base 0 (min 32 (count base)))
            base (if (seq base) base "gov-unknown")]
        (str actor-prefix "gov-jp-" base))))

(defn- stage-for [label]
  (or (some (fn [[pat _ stage]] (when (re-find pat label) stage)) label-did) "L5"))

(defn- to-int [s] #?(:clj (Long/parseLong (str s)) :cljs (js/parseInt (str s) 10)))

(defn- json-val
  "json.dumps(v, ensure_ascii=False) for the scalar value types a datom holds (string/long)."
  [v]
  (cond
    (nil? v) "null"
    (string? v) (str \" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \")
    :else (str v)))

(defn- datom [e a value]
  {"e" e "a" a "v_edn" (json-val value) "added" true})

(defn project
  "fundFlowEdge list → :yoro.fiscal/* (+ minimal :yoro.profile/*) datoms."
  [edges]
  (let [seen (atom #{})]
    (reduce
     (fn [datoms edge]
       (let [from-label (get-in edge ["fromEndpoint" "label"])
             to-label   (get-in edge ["toEndpoint" "label"])
             from-did (slug-did from-label)
             to-did   (slug-did to-label)
             period-num (str/replace (get edge "period") "FY" "")
             fy (if (seq period-num) (to-int period-num) 0)
             stage (stage-for from-label)
             e (str "fiscal:" from-did ":" to-did ":" fy ":" (get edge "flowClass"))
             fiscal [(datom e ":yoro.fiscal/from" from-did)
                     (datom e ":yoro.fiscal/to" to-did)
                     (datom e ":yoro.fiscal/stage" stage)
                     (datom e ":yoro.fiscal/fiscalYear" fy)
                     (datom e ":yoro.fiscal/amountJpy" (to-int (get edge "amount")))
                     (datom e ":yoro.fiscal/basis"
                            (str (get edge "flowClass") " · " (get edge "_programCode" "")
                                 " (kanae assembled, :representative)"))
                     (datom e ":yoro.fiscal/programCode" (get edge "_programCode" ""))
                     (datom e ":yoro.fiscal/sourceUrl" (get edge "_sourceUrl" ""))
                     (datom e ":yoro.fiscal/observedAt" (get edge "observedAt" ""))]
             prof-datoms
             (reduce
              (fn [acc label]
                (let [did (slug-did label)
                      handle (subs did (count actor-prefix))
                      prof (get profiles handle)]
                  (if (and prof (not (contains? @seen did)))
                    (do (swap! seen conj did)
                        (let [pe (str "profile:" did)]
                          (into acc [(datom pe ":yoro.profile/did" did)
                                     (datom pe ":yoro.profile/handle" (get prof "handle"))
                                     (datom pe ":yoro.profile/displayName" (get prof "displayName"))
                                     (datom pe ":yoro.profile/description" (get prof "description"))])))
                    acc)))
              []
              [from-label to-label])]
         (into datoms (concat fiscal prof-datoms))))
     []
     edges)))

#?(:clj
   (defn merge-into-seed
     "Idempotently merge projected datoms into the yoro seed snapshot JSON. Removes any
     prior entities this projector owns before adding (re-runs don't duplicate)."
     [seed-path datoms]
     (let [parse @(requiring-resolve 'cheshire.core/parse-string)
           gen   @(requiring-resolve 'cheshire.core/generate-string)
           seed (parse (slurp seed-path))
           owned (set (map #(get % "e") datoms))
           before (count seed)
           kept (vec (remove #(contains? owned (get % "e")) seed))
           merged (into kept datoms)]
       (spit seed-path (gen merged))
       {"removed" (- before (count kept)) "added" (count datoms) "total" (count merged)})))
