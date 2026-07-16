(ns unspsc.react
  "A genuine ReAct tool-calling loop per UNSPSC actor, on the kotodama ReAct
  template. Each actor exposes its commodity capability as the two canonical
  tools (inspect_requirements, validate_line); inference is Murakumo-only."
  (:require [kotodama.react :as kreact]
            [unspsc.capability :as cap]))

(defn react-actor
  "Compiles a ReAct loop actor for a taxon. `model` = any ChatModel (Murakumo in
  prod, mock in tests); `compile-opts` forwards to langgraph (e.g. {:checkpointer cp})."
  ([taxon model] (react-actor taxon model {}))
  ([taxon model compile-opts]
   (kreact/react-actor
    {:model model
     :tools (kreact/capability-tools
             {:requirements (fn [] (select-keys (cap/run taxon {}) [:domain :required :checks]))
              :validate     (fn [line] (cap/run taxon line))})
     :system (str "You are UNSPSC commodity actor " (:code taxon)
                  " \"" (:title taxon) "\". First call inspect_requirements, then "
                  "call validate_line with the buyer's line. State the verdict: "
                  "OK, or exactly which fields are missing or failing.")
     :compile-opts compile-opts})))
