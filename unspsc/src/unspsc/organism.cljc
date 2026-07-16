(ns unspsc.organism
  "The UNSPSC actor = the generic kotodama organism runtime + injected domain:
    :validate = unspsc.capability/run   (the commodity's real check)
    :emit     = the UNSPSC runtime-contract result {code,title,segment,did,ok,...}
  18,342 codes, each a living actor persisted as-of on the kotoba Datom log.
    DID = did:web:etzhayyim.com:actor:c<code>"
  (:require [kotodama.organism :as korg]
            [unspsc.capability :as cap]
            [unspsc.taxonomy :as tax]))

(defn- unspsc-emit
  "The UNSPSC runtime contract result map."
  [taxon verdict reasoning]
  {:code      (:code taxon)
   :title     (:title taxon)
   :segment   (:segment taxon)
   :did       (:did taxon)
   :domain    (:domain verdict)
   :ok        (:ok verdict)
   :missing   (:missing verdict)
   :checks    (:checks verdict)
   :reasoning reasoning})

(defn actor
  "Compiles a UNSPSC organism actor for a taxon. `model` is an optional ChatModel
  (Murakumo); nil → deterministic template reasoning. `opts` → langgraph compile
  opts (e.g. {:checkpointer cp} for kotoba-Datom persistence)."
  ([taxon] (actor taxon nil {}))
  ([taxon model] (actor taxon model {}))
  ([taxon model opts]
   (korg/actor {:taxon taxon
                :validate (fn [t input] (cap/run t input))
                :emit unspsc-emit
                :model model
                :compile-opts opts})))

(defn actor-for-code
  "Builds an actor for a UNSPSC code (looked up in the taxonomy)."
  ([code] (actor-for-code code nil {}))
  ([code model] (actor-for-code code model {}))
  ([code model opts]
   (if-let [t (tax/taxon code)]
     (actor t model opts)
     (throw (ex-info (str "unknown UNSPSC code: " code) {:code code})))))

(defn thread-id
  "Per-actor checkpoint thread (stable across invocations = the organism's life)."
  [code]
  (str "unspsc-" code))

(defn run-actor
  "Invokes a code's actor on an input map; returns the :result. The thread-id
  makes successive invocations accrete on one as-of history. A :prior-consensus
  key in `input` drives the opt-in shortcut (handled by kotodama.organism/run)."
  ([code input] (run-actor code input nil {}))
  ([code input model] (run-actor code input model {}))
  ([code input model opts]
   (korg/run (actor-for-code code model opts) input {:thread-id (thread-id code)})))

;; Re-exports from the kotodama runtime (convenience / back-compat).
(def murakumo-model    korg/murakumo-model)
(def reason-text       korg/reason-text)
(def template-reasoning korg/template-reasoning)
