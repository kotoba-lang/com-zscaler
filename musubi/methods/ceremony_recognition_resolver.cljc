#!/usr/bin/env bb
;; musubi 結 — INFORMATIONAL civil-recognition resolver (ADR-2605263400, G8/G14).
(ns musubi.methods.ceremony-recognition-resolver
  "ceremony_recognition_resolver.cljc — musubi's first real computed-logic method.

  Closes a gap the actor's own MATURITY.md iteration record (2026-06-02, '民法R1
  recognition resolver') already SPECIFIED but never landed as committed code (the
  Python it describes is not present anywhere in the tree -- 'working-tree only',
  since lost): a pure query over registry/ceremony-recognition.seed.json (54 entries /
  36 jurisdictions, all :unverified-seed per G14) mapping jurisdiction (+ optional
  ceremonyType) -> the informational civil-registration entries a member would need
  to separately handle themselves (musubi performs the covenant ceremony; it confers
  NO civil status -- Reformed 万人祭司, G3 no-clergy-class means there is no officiant
  authority a state would recognize).

  Spec (1:1 from MATURITY.md, no invented policy):
    jurisdiction (+ optional ceremonyType) -> matching entries, confidence-then-title
    sorted (high -> medium -> low, ties broken alphabetically by title), unknown
    jurisdiction -> []. Every returned entry is coded (not merely documented)
    isLegalOpinion=false + confersCivilStatus=false -- the 'informational only /
    gives no legal advice / does NOT confer civil status' boundary each seed entry's
    own `notes` field already states in prose (registry/ceremony-recognition.seed.json,
    UPL-adjacent principle mirroring chigiri's G14) is now ASSERTED into the OUTPUT
    shape itself, not left to callers to remember. NOTE: the seed's `$schema` points at
    `com.etzhayyim.musubi.ceremonyRecognition`, a lexicon that (like the resolver
    itself) was only ever drafted in a prior iteration's working tree and never
    landed -- this ns does not depend on it and does not create it (out of scope
    this slice, same as musubi's Lexicons/Cells being out of scope for credits'
    analogous first slice).

  Read-only local file access only (mirrors chigiri.methods.registry's own loader
  idiom for its legal-aid seed) -- no network I/O, no writes, no live member data.
  The registry entries are PUBLIC jurisdiction civil-procedure facts (e.g. 'Japan's
  Civil Code requires 婚姻届 for marriage to take legal effect') -- not personal
  records of any real person. resolve-recognition itself is a pure function over a
  caller-supplied registry vector; load-registry is the (:clj-only) convenience
  reading the actual shipped seed."
  (:require [cheshire.core :as json]
            #?(:clj [clojure.java.io :as io])))

(def confidence-rank
  "Sort key: high < medium < low (ascending = most-confident first). Any other/unknown
  confidence value sorts last (rank 3) rather than erroring -- unrecognized confidence
  strings degrade to 'least trusted', they never crash the query."
  {"high" 0 "medium" 1 "low" 2})

(defn- rank [entry] (get confidence-rank (get entry "confidence") 3))

#?(:clj
   (def ^:private here-dir
     ;; captured at namespace-LOAD time (sci rebinds *file* to the caller / -e expr at
     ;; call time) -- robust under the bb -e suite runner. Actor root = methods/../.
     (-> *file* io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn default-seed-path []
     (str (io/file here-dir "registry" "ceremony-recognition.seed.json"))))

#?(:clj
   (defn load-registry
     "Vector of raw (string-keyed) recognition maps from the JSON seed (default or
     given path), in registry order. Read-only; no normalization (unlike
     chigiri.methods.registry -- this resolver has no downstream datom emitter yet,
     so the JSON's own camelCase keys are returned as-is)."
     ([] (load-registry (default-seed-path)))
     ([path] (get (json/parse-string (slurp path)) "recognitions"))))

(defn resolve-recognition
  "registry (vector of raw recognition maps, string keys 'jurisdiction'/'ceremonyType'/
  'confidence'/'title'/…) + jurisdiction (non-blank string) + optional ceremony-type
  ('marriage'|'naming'|'funeral'|'cross-border-recognition'|nil = all types for that
  jurisdiction) -> vector of matching entries, confidence-desc then title-asc order,
  each with 'isLegalOpinion'=false + 'confersCivilStatus'=false ASSERTED into the
  returned map (G14: chigiri/musubi render no advice; musubi confers no civil status).

  Unknown jurisdiction (zero matches) -> [] -- never an error; there is nothing
  structurally wrong with asking about a jurisdiction this seed hasn't reached yet,
  the honest answer is 'no informational mapping on file'."
  ([registry jurisdiction] (resolve-recognition registry jurisdiction nil))
  ([registry jurisdiction ceremony-type]
   (when-not (and (string? jurisdiction) (seq jurisdiction))
     (throw (ex-info "jurisdiction must be a non-blank string" {:jurisdiction jurisdiction})))
   (->> registry
        (filter #(= jurisdiction (get % "jurisdiction")))
        (filter #(or (nil? ceremony-type) (= ceremony-type (get % "ceremonyType"))))
        (sort-by (juxt rank #(get % "title")))
        (mapv #(assoc % "isLegalOpinion" false "confersCivilStatus" false)))))

(defn has-recognition-mapping?
  "true iff resolve-recognition would return at least one entry for jurisdiction (+
  optional ceremony-type) -- the informational-only 'do I need a separate civil step
  here, per this registry's current coverage' check a musubi_marriage_ceremony /
  musubi_naming_ceremony / musubi_funeral_ceremony cell would consult (R1+; NOT wired
  to any cell yet -- cells remain R0 RuntimeError scaffolds, this is method-layer only)."
  ([registry jurisdiction] (has-recognition-mapping? registry jurisdiction nil))
  ([registry jurisdiction ceremony-type]
   (boolean (seq (resolve-recognition registry jurisdiction ceremony-type)))))
