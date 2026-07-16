(ns shionome.methods.registry
  "registry.cljc — 潮目 (shionome) public-source registry access. ADR-2606072200.
  Clojure port of methods/registry.py (1:1).

  Loads registry/sources.seed.json and exposes the source catalog to the ingest
  path:
    - get-source / source-ids
    - sourcing-for — G11 honesty DRIVEN BY the registry: a record from a VERIFIED
      source may be :authoritative; from an unverified-seed source it stays
      :representative (an unknown id is treated conservatively as :representative).
    - assert-source-allowed — the Charter Rider §2(e)/N5 commercial market-data
      deny-list as a reusable RUNTIME guard (the same SOURCE-DENY weave enforces on
      derived datoms).

  The seed registry is JSON (sources.seed.json), so the read uses cheshire WITHOUT
  keywordizing — string keys (\"sourceId\", \"jurisdiction\", \"verificationStatus\")
  exactly as the Python dict carries them. File I/O at the #?(:clj) edge."
  (:require [shionome.methods.weave :as weave]
            #?(:clj [cheshire.core :as json])))

(def ^:private reg-path
  "Repo-relative path to the seed registry (bb runs at the worktree root;
  mirrors `pathlib … parents[1] / registry / sources.seed.json`)."
  "20-actors/shionome/registry/sources.seed.json")

#?(:clj
   (defn load-registry
     "Read + parse the JSON source registry (string keys, like the Python dict)."
     ([] (load-registry reg-path))
     ([path] (json/parse-string (slurp path)))))

(defn source-ids
  "All sourceId strings in the registry."
  []
  (mapv #(get % "sourceId") (get (load-registry) "sources")))

(defn get-source
  "The source record for `source-id`, or throw (mirror of Python KeyError)."
  [source-id]
  (or (some (fn [s] (when (= (get s "sourceId") source-id) s))
            (get (load-registry) "sources"))
      (throw (ex-info (str "no such source " (pr-str source-id))
                      {:source-id source-id}))))

(defn sourcing-for
  "G11 — :authoritative only when the registry marks the source verified; else
  :representative. An unknown source id is treated conservatively as
  :representative (never auto-authoritative)."
  [source-id]
  (let [status (try (get (get-source source-id) "verificationStatus" "")
                    (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) _
                      ::unknown))]
    (if (= status "verified") ":authoritative" ":representative")))

(defn assert-source-allowed
  "Charter Rider §2(e)/N5 — throw if any text cites a commercial market-data
  terminal. Reusable runtime guard (mirror of the SOURCE-DENY check baked into
  weave's validators). Note: weave/source-denied returns \"\" when clean, which is
  truthy in Clojure — so the guard checks for a NON-empty match."
  [& texts]
  (let [d (weave/source-denied (vec texts))]
    (when (seq d)
      (throw (ex-info (str "Rider §2(e)/N5: " (pr-str d)
                           " is a prohibited commercial market-data terminal")
                      {:denied d})))))
