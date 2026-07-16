#!/usr/bin/env bb
;; tsubasa 翼 — autonomous READ-ONLY public-fare fetch leg (R3). ADR-2606072802 §R3.
(ns tsubasa.methods.fetch
  "fetch.cljc — tsubasa 翼 AUTONOMOUS read-only public-fare fetch.

  Corrects the earlier over-gating: the no-server-key invariant (ADR-2605231525) bars an
  etzhayyim-operated process from holding a UNILATERAL SIGNING key — it does NOT bar
  automation, and it EXEMPTS read-only operations (the `// no-server-key: read-only`
  marker). A PUBLIC fare source is read-only HTTP, so the ACTOR fetches it ITSELF — no
  operator, no key — exactly like kaname (`ingest/fetch-text`), watari, tsumugi, and the
  organism's read-only inference.

  Only two things still need consent / a sealed key (NOT done here):
    * :member-principal sources (the member's OWN airline-account credentials) → run in the
      MEMBER's runtime + call ingest directly; the autonomous path here REFUSES them.
    * signing the actor's OWN writes → self-generated did:key (sealed seed, present-only) +
      member CACAO leash (ibuki/kaname pattern; see ADR §R3). Appending to the LOCAL log
      needs no key at all.

  read-only + fail-open: any network error returns nil and the batch degrades to empty,
  never throws, never blocks a heartbeat. :paid-terminal is unrepresentable (refused
  downstream by ingest/assert-clean-source)."
  (:require [tsubasa.methods.ingest :as ingest]
            #?(:clj [cheshire.core :as json])
            #?(:clj [babashka.http-client :as http])))

#?(:clj
   (defn fetch-json
     "READ-ONLY HTTP GET of a PUBLIC url → parsed JSON (vector/map), or nil on ANY error
     (fail-open). no-server-key: read-only — no auth header, nothing signed."
     [url & {:keys [timeout] :or {timeout 8000}}]
     (try
       (let [r (http/get url {:timeout timeout})]
         (when (= 200 (:status r))
           (json/parse-string (:body r))))   ; string keys; ingest/normalize-fare reads both
       (catch Exception _ nil))))

(defn fetch-and-ingest
  "Autonomously fetch a PUBLIC fare source and fold it into :authoritative :fare rows.
  opts: {:as-of <stamp> :fetch-fn <url->payload|nil>}. source-kind is forced :public —
  a :member-principal source is REFUSED here (it must run in the member's own runtime).
  fail-open: a nil/blank fetch degrades to an empty batch (never throws on a dead source).
  Returns the ingest result {:rows .. :accepted .. :rejected ..} (rejected per G1/G4/G5)."
  [url {:keys [as-of fetch-fn] :or {as-of "manual"}}]
  (let [f (or fetch-fn #?(:clj fetch-json :cljs (constantly nil)))
        payload (f url)]
    (if (sequential? payload)
      (ingest/ingest (vec payload) {:source url :as-of as-of :source-kind :public})
      {:rows [] :accepted 0 :rejected [] :note :no-payload})))

#?(:clj
   (defn -main [& args]
     ;; Autonomous: the ACTOR runs this read-only fetch itself (no operator, no key).
     ;;   bb fetch.cljc <public-fare-source-url> [as-of]
     (let [url (or (first args)
                   (throw (ex-info "usage: fetch.cljc <public-fare-source-url> [as-of]" {})))
           as-of (or (second args) "manual")
           {:keys [rows accepted rejected note]} (fetch-and-ingest url {:as-of as-of})]
       (println (str ";; tsubasa autonomous fetch — source=" url
                     " accepted=" accepted " rejected=" (count rejected)
                     (when note (str " note=" (name note)))))
       (when (seq rejected)
         (println (str ";; rejected: " (frequencies (map :reason rejected)))))
       (println (pr-str (vec rows))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
