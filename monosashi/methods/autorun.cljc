(ns monosashi.methods.autorun
  "monosashi 物差し — deterministic autonomous heartbeat. ADR-2606271800.

  One cycle: read mitooshi score residuals (+ optional same-series tsuchifumi sysdyn context) →
  skill-band per (actor, baseline) → draft skill posts → emit (G1/G3/G7) → persist ONE
  content-addressed tx to the kotoba Datom log, BUT only when the content changed
  (idempotent-by-content; a re-run with identical inputs is a no-op, so the log is not padded with
  duplicate transactions). Deterministic / resume-safe: the caller supplies :tx-id + :as-of (no wall
  clock, no Math/random), and the tx CID chains onto the log's previous CID (commit-DAG).

  The EXTERNAL AT-Proto relay leg is operator-gated (a `transport` fn carrying a member/operator
  credential — see methods/transport.cljc); with no transport, posts persist on-protocol (kotoba
  log) and the relay is :pending-operator-transport. :published requires a member-DID :author (G7)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])
            [monosashi.methods.score :as score]
            [monosashi.methods.social :as social]
            [monosashi.methods.kotoba :as kotoba]))

;; data/seed-scores.kotoba.edn is now stored datomized (tx-data: a single-entity
;; vector [{:db/id -1 :seed-scores/residuals "<pr-str'd residuals vector>"}], per
;; the repo-wide edn-datomize convention). `unblob`/`reconstitute-entity` restore
;; the original `{:residuals [...]}` shape so this loader (and every downstream
;; caller, incl. the test suite) keeps working unchanged. Also tolerates the
;; pre-datomize raw-map shape for any other seed edn passed in.
#?(:clj
   (defn- unblob [v]
     (if (string? v)
       (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
            (catch Exception _ v))
       v)))

#?(:clj
   (defn- tx-data? [content]
     (and (vector? content) (seq content) (map? (first content))
          (contains? (first content) :db/id))))

#?(:clj
   (defn- reconstitute-entity [tx-data]
     (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
           (dissoc (first tx-data) :db/id))))

#?(:clj
   (defn load-residuals
     "Read a seed EDN of {:residuals [...] :sysdyn {actor {:series :band-width}}}.
     Accepts both the datomized tx-data shape and the legacy raw-map shape."
     [path]
     (let [parsed (with-open [r (io/reader path)] (edn/read-string (slurp r)))]
       (if (tx-data? parsed) (reconstitute-entity parsed) parsed))))

(defn run-cycle
  "Run one heartbeat cycle. opts:
     :as-of (required, G5)  :tx-id (required, deterministic)  :author (member-DID, G7)
     :status (:dry-run|:published)  :transport (operator relay fn, optional)  :log (path)
  Returns {:bands :posts :receipts :tx :appended?}. Idempotent-by-content: if the new datoms
  equal the last tx's datoms, no tx is appended (:appended? false). Pure except the one append."
  [{:keys [residuals sysdyn]}
   {:keys [as-of tx-id author status transport log]
    :or {author "" status ":dry-run" log #?(:clj kotoba/log-default :cljs nil)}}]
  (let [bands (score/evaluate residuals {:as-of as-of :sysdyn sysdyn})
        posts (mapv #(social/draft-skill-post % {:author author :status status}) bands)
        receipts (mapv #(social/emit % transport) posts)
        datoms (vec (concat (mapcat kotoba/band-datoms bands) (kotoba/post-datoms posts)))
        prev (kotoba/last-cid log)
        unchanged? (= datoms (some-> (kotoba/last-tx log) (get ":tx/datoms")))
        tx (kotoba/make-tx datoms {:tx-id tx-id :as-of as-of :prev-cid prev})]
    (when-not unchanged? (kotoba/append-tx! log tx))
    {:bands bands :posts posts :receipts receipts :tx tx :appended? (not unchanged?)}))
