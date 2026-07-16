(ns kaname.methods.ingest
  "kaname 要 — WEB → mirror-graph ingest (ADR-2606172100 R1; G7 live leg, founder-approved 2026-06-17).

  Fetch a PUBLIC web source (a company homepage / 公開投稿 / press release / announcement), extract the
  DISCLOSED organizational relationships it STATES, and build kaname mirror-format forms in a domain
  layer — every edge carrying an on-the-record :en/basis (the source URL). The extracted graph is just
  another mirror kaname joins (join.cljc).

  CONSTITUTIONAL:
    Inference  — Murakumo-only (ADR-2605215000). Extraction runs on the local Ollama gemma-4-E4B-it-qat
                 (the per-node Ollama gemma path), via loopback; NEVER an external LLM API.
    G1         — person-excluded. Natural persons are dropped at extraction (prompt) AND filtered
                 (drop-persons) — only organisations / institutions / structural positions survive.
    G4         — DISCLOSED-only + non-adjudicating. We record what a page STATES, as a fact, with its
                 source URL; we never infer hidden ties or judge them.
    G5         — every ingested edge carries :en/basis (the source URL + the stated phrase). An edge
                 with no basis is dropped (no unbasis'd relation).
    sourcing   — ingested loads are REPRESENTATIVE lock-in weights by relationship kind (an announcement
                 states a relation's EXISTENCE, not its magnitude) — flagged, never a measured number.
    no-server-key — fetch is an anonymous public GET (no creds); kaname holds no platform key.

  Pure where possible; the two side-effecting edges (HTTP fetch, Ollama call) are isolated. The
  form-building + guards are pure and deterministic (tested on a fixture extraction, no live Ollama)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [babashka.http-client :as http])
            #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.java.io :as io])))

(def ollama-model "hf.co/unsloth/gemma-4-E4B-it-qat-GGUF:UD-Q4_K_XL")

;; relationship kind (from extraction) → [kaname-edge-kind representative-load direction]
;; direction :fwd = from→to as stated; :rev = swap (the dependant points at what it depends on).
(def rel-map
  {"invests-in"      [":concentrates" 0.6 :fwd]   ; investor → investee (capital onto investee)
   "compute-provider" [":concentrates" 0.7 :fwd]  ; provider → recipient (compute lock-in onto recipient)
   "supplier"        [":concentrates" 0.6 :fwd]   ; supplier → customer (lock-in onto customer)
   "customer"        [":depends-on" 0.5 :fwd]     ; customer → vendor (dependency)
   "partners"        [":couples" 0.3 :fwd]
   "regulates"       [":gates" 0.5 :fwd]
   "acquires"        [":concentrates" 0.8 :fwd]
   "parent-of"       [":concentrates" 0.7 :fwd]})

(defn norm-rel
  "Normalize an extracted relationship word to a rel-map key (tolerant of model variants:
  invest-in/investment → invests-in; cloud/compute/chip → compute-provider; etc.)."
  [r]
  (let [r (-> (str r) str/lower-case str/trim)]
    (cond
      (re-find #"invest" r)                         "invests-in"
      (re-find #"compute|cloud|gpu|chip|infrastructure|systems" r) "compute-provider"
      (re-find #"suppl" r)                          "supplier"
      (re-find #"customer|client" r)                "customer"
      (re-find #"partner|collab|strategic" r)       "partners"
      (re-find #"regulat|govern" r)                 "regulates"
      (re-find #"acqui|merg" r)                     "acquires"
      (re-find #"parent|subsidiar|owns" r)          "parent-of"
      :else r)))

(defn- slug [s]
  (-> (str s) str/lower-case str/trim
      (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))

(def ^:private person-words #"(?i)\b(ceo|cto|founder|chair|chairman|president|director|officer|mr|ms|dr|prof)\b")

(defn person-entity?
  "G1 — true if an extracted entity looks like a natural person (person-role word in the label,
  sector, or kind). Dropped from the ingested graph. (The extraction prompt also instructs
  organisations-only; this is the structural backstop.)"
  [{:keys [label sector kind]}]
  (boolean (or (re-find person-words (str sector)) (re-find person-words (str kind))
               (re-find person-words (str label))
               (= "person" (str/lower-case (str kind)))
               (= "person" (str/lower-case (str sector))))))

(def extraction-prompt-preamble
  (str "You extract DISCLOSED organizational relationships from a public web page. "
       "Return ONLY compact JSON, no prose, of the form: "
       "{\"entities\":[{\"label\":\"<organization name>\",\"sector\":\"<short sector>\"}],"
       "\"edges\":[{\"from\":\"<org>\",\"to\":\"<org>\",\"rel\":\"<one of: invests-in compute-provider supplier customer partners regulates acquires parent-of>\",\"basis\":\"<the exact phrase from the page stating this>\"}]}. "
       "Rules: ORGANIZATIONS ONLY — never natural persons (no CEOs/founders/people). "
       "Only relationships the page EXPLICITLY STATES (no guessing). Every edge MUST have a 'basis' phrase quoted from the page. "
       "If nothing is stated, return {\"entities\":[],\"edges\":[]}. PAGE TEXT:\n"))

#?(:clj
   (defn ollama-extract
     "Murakumo-conformant extraction: POST (preamble + page-text) to local Ollama gemma-4-E4B → parse
     the JSON object. Returns {:entities [...] :edges [...]} (or empty on parse failure — honest)."
     [page-text]
     (let [body (json/generate-string
                 {:model ollama-model :stream false :options {:temperature 0}
                  :prompt (str extraction-prompt-preamble
                               (subs page-text 0 (min 8000 (count page-text))))})
           resp (http/post "http://127.0.0.1:11434/api/generate"
                           {:body body :headers {"content-type" "application/json"} :timeout 120000})
           raw (-> resp :body (json/parse-string true) :response str)
           jstr (let [a (str/index-of raw "{") b (str/last-index-of raw "}")]
                  (if (and a b (< a b)) (subs raw a (inc b)) "{}"))]
       (try (json/parse-string jstr true)
            (catch Exception _ {:entities [] :edges []})))))

#?(:clj
   (defn fetch-text
     "Anonymous public GET of a URL → crude HTML→text (no creds; no-server-key). For the actor
     runtime path; the operator demo passes pre-fetched text instead."
     [url]
     (let [html (-> (http/get url {:timeout 60000 :headers {"user-agent" "etzhayyim-kaname-ingest/1.0"}}) :body str)]
       (-> html
           (str/replace #"(?is)<script.*?</script>" " ")
           (str/replace #"(?is)<style.*?</style>" " ")
           (str/replace #"(?s)<[^>]+>" " ")
           (str/replace #"&[a-z]+;" " ")
           (str/replace #"\s+" " ")
           str/trim))))

(defn drop-persons
  "G1 — remove person entities + any edge that references a known person (or whose endpoint is
  itself person-like); G5 — drop any edge without an on-the-record basis."
  [{:keys [entities edges]}]
  (let [persons (set (map :label (filter person-entity? (or entities []))))
        orgs (remove person-entity? (or entities []))
        edges' (->> (or edges [])
                    (filter (fn [e] (and (:basis e) (not (str/blank? (str (:basis e)))))))   ; G5
                    (remove (fn [e] (or (persons (:from e)) (persons (:to e))                ; G1 known person
                                        (person-entity? {:label (:from e)})
                                        (person-entity? {:label (:to e)})))))]
    {:entities (vec orgs) :edges (vec edges')}))

(defn ->forms
  "Build kaname mirror-format forms (node + edge maps) from an extraction, in `domain`, tagged
  `source-actor`, basis = `url`. Node ids = \"<tag>/<slug>\". Edges carry :en/basis (url + phrase),
  representative load by relationship kind, :organism/sourcing :authoritative. Unknown rels drop."
  [extraction domain source-actor url]
  (let [{:keys [entities edges]} (drop-persons extraction)
        tag (str (if (str/starts-with? (str source-actor) ":") (subs source-actor 1) source-actor) "/")
        nid (fn [label] (str tag (slug label)))
        ;; ensure every edge endpoint has a node (some pages name an org only in an edge)
        edge-labels (mapcat (fn [e] [(:from e) (:to e)]) edges)
        all-labels (distinct (concat (map :label entities) edge-labels))
        node-forms (mapv (fn [label]
                           (let [sect (some #(when (= (:label %) label) (:sector %)) entities)]
                             (cond-> {":organism/id" (nid label)
                                      ":organism/kind" ":sos/entity"
                                      ":organism/label" label
                                      ":organism/sourcing" ":authoritative"
                                      ":sos/source-actors" [source-actor]
                                      ":sos/open?" false}
                               sect (assoc ":sos/sector" (str sect)))))
                         all-labels)
        edge-forms (->> edges
                        (keep (fn [{:keys [from to rel basis]}]
                                (when-let [[k load- dir] (rel-map (norm-rel rel))]
                                  (let [[a b] (if (= dir :rev) [to from] [from to])]
                                    {":en/from" (nid a) ":en/to" (nid b)
                                     ":en/kind" k ":en/domain" domain
                                     ":en/grasping-load" (double load-)
                                     ":en/basis" (str url " :: " basis)
                                     ":en/sourcing" ":authoritative"}))))
                        vec)]
    (into node-forms edge-forms)))

#?(:clj
   (defn ingest-source
     "Full pipeline for one source: (text-or-url, domain, source-actor, url) → mirror forms.
     If `text` is given use it (operator pre-fetch); else fetch-text the url (actor runtime)."
     [{:keys [url text domain source]}]
     (let [page (or text (fetch-text url))]
       (->forms (ollama-extract page) domain source url))))

(defn forms->edn
  "Serialize kaname forms (string-keyed maps) to kotoba-EDN text the sos reader round-trips:
  ':…'-strings print as bare keyword tokens, plain strings quoted, vectors recursively."
  [forms header]
  (letfn [(emit [v]
            (cond
              (and (string? v) (str/starts-with? v ":")) v
              (string? v) (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")
              (vector? v) (str "[" (str/join " " (map emit v)) "]")
              (boolean? v) (str v)
              :else (str v)))
          (emit-map [m] (str "{" (str/join " " (map (fn [[k v]] (str (emit k) " " (emit v))) m)) "}"))]
    (str header "[\n" (str/join "\n" (map emit-map forms)) "\n]\n")))

#?(:clj
   (defn- reconstitute-source
     "data/ingest-sources.edn is now datomic/datascript tx-data (edn-datomize, 2026-07-10):
     [{:db/id -1 :ingest-source/url .. :ingest-source/domain .. :ingest-source/source ..} …].
     Reconstitute the original bare {:url :domain :source} shape so the rest of this fn is
     unchanged — mirrors the repo-wide unblob/reconstitute-entity pattern (no blobbed values
     here since url/domain/source are all scalars)."
     [entity]
     (into {} (map (fn [[k v]] [(keyword (name k)) v])) (dissoc entity :db/id))))

#?(:clj
   (defn ingest-live!
     "G7 LIVE web-fetch leg — runs ENTIRELY in clj (the actor runtime, not an operator tool):
     read a source manifest EDN (datomic tx-data: [{:db/id .. :ingest-source/url ..
     :ingest-source/domain .. :ingest-source/source ..} …]), fetch each PUBLIC page via
     fetch-text (babashka.http-client, anonymous GET, no-server-key), extract DISCLOSED org
     relations via Murakumo gemma (ollama-extract), build basis'd mirror forms. Optionally
     write `out-path` (kotoba-EDN). Returns the forms. (Founder/Council-gated; the committed
     artifact is the default.)"
     [sources-path & [out-path]]
     (let [sources (map reconstitute-source (edn/read-string (slurp (str sources-path))))
           forms (vec (mapcat (fn [{:keys [url domain source]}]
                                (->forms (ollama-extract (fetch-text url))
                                         (str domain) (str source) url))
                              sources))]
       (when out-path
         (spit out-path
               (forms->edn forms
                           (str ";; kaname 要 — LIVE web-ingest (clj runtime fetch+extract). DO NOT hand-edit.\n"
                                ";; fetch-text (babashka.http-client) → Murakumo gemma-4-E4B → basis'd forms (G1/G4/G5).\n"))))
       forms)))

#?(:clj
   (defn ingest-pages-dir
     "Operator demo: extract every pre-fetched *.txt under `dir` (filename stem = source slug) using
     the sidecar `<stem>.url` for the basis URL, all into `domain`/`source`. Writes nothing; returns forms."
     [dir domain source]
     (let [files (->> (.listFiles (io/file dir)) (filter #(str/ends-with? (.getName %) ".txt")) sort)]
       (vec (mapcat (fn [f]
                      (let [stem (str/replace (.getName f) #"\.txt$" "")
                            urlf (io/file dir (str stem ".url"))
                            url (if (.exists urlf) (str/trim (slurp urlf)) (str "ingested:" stem))]
                        (->forms (ollama-extract (slurp f)) domain source url)))
                    files)))))
