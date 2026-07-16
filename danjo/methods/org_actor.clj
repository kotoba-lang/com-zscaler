;; org_actor.clj — 実在の日本財政組織を keyless mirror-actor 化 (entity-as-actor, ADR-2606042330).
;; danjo 弾正, ADR-2605301600.
;;
;; 1 公的組織 = 1 keyless mirror-actor (`did:web:etzhayyim.com:actor:jp-<handle>`). etzhayyim は
;; 鍵を持たず (no-server-key, verificationMethod 空) その組織を代理・代表しない — 観測ミラーのみ。
;; このモジュールは各組織の担当スライスを kotoba EAVT (`:gov.org/*`) に射影し、組織別ビュー
;; (徴収する税 / 所管する会計 / それぞれの per-yen 追跡可否) を返す。
;;
;; 徴収機関 (国税庁/税関) と会計所管機関 (復興庁/資源エネルギー庁/財務省理財局・主計局) を
;; 税レジストリ (taxes.clj) と突合して接続する。Pure + JVM stdlib; bb / clojure 両対応。
(ns root.danjo.methods.org-actor
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(load-file "taxes.clj")
(alias 't  'root.danjo.methods.taxes)
(alias 'rl 'root.danjo.methods.revenue-ledger)

(defn- unblob
  "A datomized attribute value may be a pr-str'd blob (nested map/vector-of-map that doesn't
   fit a scalar Datomic valueType) — parse it back to data. Non-blob values pass through."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Reconstitutes a datomized tx-data entity ([{:db/id … :ns/k v …}]) back into the original
   bare, un-namespaced map so downstream key lookups (:orgs, …) keep working unchanged.
   Tolerates both the tx-data shape and a legacy bare map."
  [content]
  (if (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id))
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc (first content) :db/id))
    content))

(defn load-orgs
  ([] (load-orgs nil))
  ([path]
   (let [f (io/file (or path "20-actors/danjo/data/jp-fiscal-orgs.edn"))
         f (if (.exists f) f (io/file "../data/jp-fiscal-orgs.edn"))]
     (reconstitute-entity (edn/read-string (slurp f))))))

(defn- collects [org taxes]
  "Tax ids this org collects (from the tax registry's :collected-by)."
  (->> taxes (filter #(= (:id org) (:collected-by %))) (map :id) vec))

(defn- administers-taxes [org taxes]
  "Tax ids whose special-account this org administers (the earmarked taxes it ultimately spends)."
  (let [accts (set (:administers org))]
    (->> taxes (filter #(contains? accts (:special-account %))) (map :id) vec)))

(defn- add [e a v] [:db/add e a v])

(defn org-datoms
  "Flatten the org registry → append-only EAVT `:gov.org/*`. Each org is keyless
   (`:gov.org/keyless true` — no-server-key, ADR-2605231525). Collection + administration edges
   are resolved against the tax registry."
  [registry tax-registry]
  (let [taxes (:taxes tax-registry)]
    (vec
     (mapcat
      (fn [org]
        (let [e (str "org:" (:handle org))]
          (concat
           [(add e :gov.org/did (:did org))
            (add e :gov.org/ja (:ja org))
            (add e :gov.org/en (:en org))
            (add e :gov.org/role (:role org))
            (add e :gov.org/keyless true)                 ; no-server-key (verificationMethod 空)
            (add e :gov.org/sourcing :representative)]
           (when (:parent org) [(add e :gov.org/parent (:parent org))])
           (for [tid (collects org taxes)] (add e :gov.org/collects (str "tax:jp:" (name tid))))
           (for [acc (:administers org)]   (add e :gov.org/administers (str (subs (str acc) 1))))
           (for [tid (administers-taxes org taxes)] (add e :gov.org/spends (str "tax:jp:" (name tid)))))))
      (:orgs registry)))))

(defn org-view
  "Per-organization fiscal view: what it COLLECTS / ADMINISTERS, with honest per-yen flags +
   amounts. This is the org-actor's own slice of the national tax graph."
  [org-id org-registry tax-registry]
  (let [org   (->> (:orgs org-registry) (filter #(= org-id (:id %))) first)
        taxes (:taxes tax-registry)]
    (when-not org (throw (ex-info "no such org" {:org-id org-id})))
    (let [coll (->> taxes (filter #(= org-id (:collected-by %))) (map t/classify))
          adm  (->> taxes (filter #(contains? (set (:administers org)) (:special-account %))) (map t/classify))]
      {:org-id org-id :did (:did org) :ja (:ja org) :role (:role org) :keyless true
       :collects {:count (count coll)
                  :amount-jpy (reduce + 0 (map :fy2024-amount-jpy coll))
                  :per-yen-traceable (mapv :id (filter :per-yen? coll))
                  :fungible          (mapv :id (remove :per-yen? coll))
                  :taxes (mapv (fn [c] (select-keys c [:id :ja :earmark-kind :per-yen? :fy2024-amount-jpy])) coll)}
       :administers {:accounts (vec (:administers org))
                     :spends-taxes (mapv :id adm)
                     :amount-jpy (reduce + 0 (map :fy2024-amount-jpy adm))}})))

;; ── resolvable profile artifacts (entity-as-actor, ADR-2606042330) ──
(defn ->json
  "Minimal dep-free clj → JSON. Inverse of ingest.clj parse-json; bb + clojure."
  [x]
  (cond
    (nil? x)      "null"
    (boolean? x)  (str x)
    (number? x)   (str x)
    (keyword? x)  (->json (subs (str x) 1))     ; :special/reconstruction → "special/reconstruction"
    (string? x)   (str \" (-> x (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \")
    (map? x)      (str "{" (str/join "," (for [[k v] x]
                                           (str (->json (if (keyword? k) (subs (str k) 1) (str k)))
                                                ":" (->json v)))) "}")
    (sequential? x) (str "[" (str/join "," (map ->json x)) "]")
    :else         (->json (str x))))

(defn org-profile
  "The entity-as-actor profile for one org: a keyless (no-server-key) observational mirror of a
   real public entity. NOT a claim to represent or act for the org (ADR-2606042330)."
  [org org-reg tax-reg]
  (let [v (org-view (:id org) org-reg tax-reg)]
    {:handle (:handle org)
     :did (:did org)
     :displayName (:ja org)
     :displayNameEn (:en org)
     :type "gov-fiscal-mirror"
     :actorClass "keyless-mirror"
     :keyless true
     :verificationMethod []                       ; no-server-key (ADR-2605231525)
     :role (:role org)
     :parent (:parent org)
     :sourceUrl (:source-url org)
     :collectsTaxes (mapv (comp #(subs (str %) 1) :id) (get-in v [:collects :taxes]))
     :perYenTraceableCollected (mapv #(subs (str %) 1) (get-in v [:collects :per-yen-traceable]))
     :administersAccounts (mapv #(subs (str %) 1) (get-in v [:administers :accounts]))
     :note (str "Observational mirror of a real public entity (entity-as-actor, ADR-2606042330). "
                "etzhayyim holds no key and does not represent or act for this org.")
     :provenance "representative"}))

(defn generate-profiles!
  "Write one `<handle>.profile.json` per org + an `actors.json` index under `data/actors/`.
   Returns the list of written paths. Deterministic."
  [org-reg tax-reg out-dir]
  (let [dir (io/file (or out-dir "../data/actors"))]
    (io/make-parents (io/file dir "x"))
    (let [paths (doall
                 (for [org (:orgs org-reg)]
                   (let [f (io/file dir (str (:handle org) ".profile.json"))]
                     (spit f (->json (org-profile org org-reg tax-reg)))
                     (.getPath f))))
          index (io/file dir "actors.json")]
      (spit index (->json {:actors (mapv (fn [o] {:handle (:handle o) :did (:did o)
                                                  :displayName (:ja o) :type "gov-fiscal-mirror"
                                                  :keyless true})
                                         (:orgs org-reg))
                           :note "keyless gov-fiscal mirror-actors (ADR-2606042330); observational only."}))
      (conj (vec paths) (.getPath index)))))

(defn -main [& args]
  (let [orgs  (load-orgs (if (= "generate" (first args)) nil (first args)))
        taxes (t/combine (t/load-taxes nil) (t/load-local-taxes nil))]
    (when (= "generate" (first args))
      (let [ps (generate-profiles! orgs taxes nil)]
        (println "wrote" (count ps) "profile artifacts:")
        (doseq [p ps] (println "  " p))))
    (doseq [org (:orgs orgs)]
      (let [v (org-view (:id org) orgs taxes)]
        (println (:did v) "—" (:ja v) "(" (name (:role v)) ", keyless)")
        (when (pos? (:count (:collects v)))
          (println "   collects" (:count (:collects v)) "taxes," (:amount-jpy (:collects v)) "JPY;"
                   "per-yen追跡可:" (:per-yen-traceable (:collects v))))
        (when (seq (:accounts (:administers v)))
          (println "   administers" (:accounts (:administers v)) "— spends taxes" (:spends-taxes (:administers v))))))))
