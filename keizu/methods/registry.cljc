(ns keizu.methods.registry
  "registry.cljc — 系図 (keizu) public-source registry access. ADR-2606066000.
  1:1 Clojure port of `methods/registry.py` (same house style as weave/analyze).

  Loads registry/sources.seed.json and exposes the source catalog to the ingest/bridge paths:
    - get-source / source-ids
    - sourcing-for(source-id) — G11 honesty DRIVEN BY the registry: a record from a VERIFIED source
      may be :authoritative; from an unverified-seed source it stays :representative.
    - assert-source-allowed — the Charter Rider §2(e)/N5 commercial-gov-intel deny-list as a
      reusable RUNTIME guard (the same SOURCE-DENY weave.validate-* enforces on derived datoms).

  Requires the merged keizu weave ns for SOURCE-DENY + source-denied (the Python
  `from weave import SOURCE_DENY, source_denied`). House style: closed-vocab / gate violations
  throw ex-info (mirroring Python's KeyError / ValueError); pure fns; file I/O at the #?(:clj)
  edge (the JSON read)."
  (:require [keizu.methods.weave :as w]
            #?(:clj [cheshire.core :as json])))

;; Re-export SOURCE-DENY (Python `from weave import SOURCE_DENY` → registry's __all__).
(def SOURCE-DENY w/SOURCE-DENY)

#?(:clj
   (def ^:private reg-path
     "registry/sources.seed.json relative to the actor root (parents[1] of methods/)."
     (-> *file* clojure.java.io/file .getParentFile .getParentFile
         (clojure.java.io/file "registry" "sources.seed.json")
         str)))

#?(:clj
   (defn load-registry
     "Read + JSON-parse registry/sources.seed.json (string keys, like Python json.loads)."
     ([] (load-registry reg-path))
     ([path] (json/parse-string (slurp path) false))))

#?(:clj
   (defn source-ids
     "[s['sourceId'] for s in load_registry()['sources']]."
     []
     (mapv #(get % "sourceId") (get (load-registry) "sources"))))

#?(:clj
   (defn get-source
     "Return the source map whose sourceId matches; throws (mirror KeyError) if none."
     [source-id]
     (or (some (fn [s] (when (= (get s "sourceId") source-id) s))
               (get (load-registry) "sources"))
         (throw (ex-info (str "no such source '" source-id "'") {})))))

#?(:clj
   (defn sourcing-for
     "G11 — ':authoritative' only when the registry marks the source verified; else
     ':representative'. An unknown source id is treated conservatively as ':representative'
     (never auto-authoritative)."
     [source-id]
     (let [status (try (get (get-source source-id) "verificationStatus" "")
                       (catch Exception _ ::unknown))]
       (if (and (not= status ::unknown) (= status "verified"))
         ":authoritative"
         ":representative"))))

#?(:clj
   (defn assert-source-allowed
     "Charter Rider §2(e)/N5 — throw if any text cites a commercial gov-intel terminal. Reusable
     runtime guard (mirror of the SOURCE-DENY check baked into weave.validate-rel/validate-money)."
     [& texts]
     (let [d (w/source-denied (vec texts))]
       (when (seq d)
         (throw (ex-info (str "Rider §2(e)/N5: '" d "' is a prohibited commercial gov-intel terminal")
                         {}))))))
