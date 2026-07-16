(ns hirameki.methods.dataset
  "hirameki 閃き — patent corpus → EDN → DataLad substrate bridge (G9).

  The 'ingest → edn, datalad に保存' leg. Materializes the public-patent CORPUS (R0 =
  the bounded representative seed; live = the operator G9 ingest of USPTO PatentsView CC0 /
  EPO OPS free / WIPO PATENTSCOPE) into the canonical kotoba EDN under
  80-data/hirameki-patents/ (the DataLad dataset substrate, ADR-2605241500: DataLad +
  git-annex + IPFS), each artifact CONTENT-ADDRESSED to a CIDv1 (raw, sha2-256) byte-
  identical to `ipfs add --cid-version=1 --raw-leaves`.

  DETERMINISTIC: `materialize` is pure (sorted, no wall clock) → the same corpus yields the
  same bytes yields the same CID, so re-running is idempotent and the snapshot is verifiable.
  `write!` is the only I/O; the wall-clock timestamp lives ONLY in provenance metadata, never
  in the content-addressed artifact. No-server-key: writes local files only (IPFS pin / IPNS
  publish is the operator step). The bulk full-world corpus (~200M patents) goes via
  DataLad→IPFS (git-annex); this bounded R0 snapshot is git-tracked directly (no git-lfs)."
  (:require [hirameki.methods.analyze :as a]
            [hirameki.methods.cid :as cid]
            [clojure.string :as str]
            #?(:clj [hirameki.methods.hirameki-edn :as he])
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.edn :as edn])))

(def corpus-file "hirameki-patents.corpus.kotoba.edn")
(def datoms-file "hirameki-patents.datoms.kotoba.edn")

(defn- normalize-patent
  "Canonical, stably-ordered patent record (sorted-map → deterministic pr-str)."
  [p]
  (into (sorted-map)
        (select-keys p [:id :title :jurisdiction :field :assignee
                        :filing-year :grant-year :term-years :status
                        :open-license :essentiality :sourcing :source])))

(defn corpus-edn
  "Deterministic EDN string of the normalized patent corpus (sorted by id)."
  [patents]
  (let [norm (->> patents (map normalize-patent) (sort-by :id) vec)]
    (str ";; hirameki 閃き — public-patent corpus snapshot (kotoba EDN, DataLad substrate)\n"
         ";; ADR-2606212200 · content-addressed (see publish-manifest.json) · no-server-key\n"
         ";; a patent is the GATED OBJECT, never a 取-holder (G2); aggregate, no person-level inventor (G6)\n"
         (str/join "\n" (map pr-str norm)) "\n")))

(defn corpus-datoms-edn
  "Deterministic EDN string of the patent EAVT datoms (one per line)."
  [patents]
  (let [ds (a/datoms (a/analyze {:fields [] :patents (vec patents)}))]
    (str ";; hirameki 閃き — patent EAVT datoms (kotoba [:db/add e a v])\n"
         (str/join "\n" (map pr-str ds)) "\n")))

(defn materialize
  "Pure: returns {:corpus {...} :datoms {...}} each with :file :content :bytes :cid.
  No I/O, no wall clock — same patents → same bytes → same CID."
  [patents]
  (let [c (corpus-edn patents)
        d (corpus-datoms-edn patents)]
    {:corpus {:file corpus-file :content c
              :bytes (count (.getBytes ^String c "UTF-8")) :cid (cid/cidv1-raw c)}
     :datoms {:file datoms-file :content d
              :bytes (count (.getBytes ^String d "UTF-8")) :cid (cid/cidv1-raw d)}}))

(defn publish-manifest
  "Manifest map (the genome/publish-manifest.json shape). `as-of` is a caller-supplied
  timestamp string (metadata only — NOT part of any content-addressed artifact)."
  [mat as-of]
  {"actor" "hirameki"
   "adr" "2606212200"
   "published_at" as-of
   "scope" "PUBLIC patent reference only — bibliographic metadata; aggregate-first; no person-level inventor data (G6); a RELEASE map, never an FTO/infringement verdict (G1)"
   "artifacts" {"corpus" {"file" (get-in mat [:corpus :file])
                          "bytes" (get-in mat [:corpus :bytes])
                          "cid" (get-in mat [:corpus :cid])}
                "datoms" {"file" (get-in mat [:datoms :file])
                          "bytes" (get-in mat [:datoms :bytes])
                          "cid" (get-in mat [:datoms :cid])}}
   "single_block" {"corpus" (< (get-in mat [:corpus :bytes]) cid/single-block-limit)
                   "datoms" (< (get-in mat [:datoms :bytes]) cid/single-block-limit)}
   "gateways" ["https://ipfs.io/ipfs/" "https://dweb.link/ipfs/" "https://cloudflare-ipfs.com/ipfs/"]
   "verify" "20-actors/hirameki/methods/cid.cljc (cidv1-raw) — re-content-address to check"})

#?(:clj
   (defn- ->json
     "Minimal pretty JSON for the manifest (stdlib only)."
     [x indent]
     (let [pad (apply str (repeat indent "  "))
           pad+ (apply str (repeat (inc indent) "  "))]
       (cond
         (map? x) (if (empty? x) "{}"
                      (str "{\n"
                           (str/join ",\n"
                                     (map (fn [[k v]]
                                            (str pad+ "\"" (name k) "\": " (->json v (inc indent))))
                                          x))
                           "\n" pad "}"))
         (sequential? x) (if (empty? x) "[]"
                             (str "[\n"
                                  (str/join ",\n" (map #(str pad+ (->json % (inc indent))) x))
                                  "\n" pad "]"))
         (string? x) (str "\"" (str/replace x "\"" "\\\"") "\"")
         (boolean? x) (str x)
         (number? x) (str x)
         (nil? x) "null"
         :else (str "\"" x "\"")))))

#?(:clj
   (defn write!
     "Write the corpus + datoms EDN + publish-manifest.json into data-dir.
     `as-of` is a caller-supplied timestamp string (metadata only). Returns the
     manifest map. No-server-key: local file I/O only (IPFS pin/IPNS = operator step)."
     [patents data-dir as-of]
     (let [mat (materialize patents)
           dir (io/file data-dir)]
       (io/make-parents (io/file dir "x"))
       (spit (io/file dir (get-in mat [:corpus :file])) (get-in mat [:corpus :content]))
       (spit (io/file dir (get-in mat [:datoms :file])) (get-in mat [:datoms :content]))
       (let [man (publish-manifest mat as-of)]
         (spit (io/file dir "publish-manifest.json") (str (->json man 0) "\n"))
         man))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/hirameki/kotoba/seed.edn")
           data-dir (or (second args) "80-data/hirameki-patents")
           as-of (or (nth args 2 nil) "manual")
           patents (he/patents seed)
           man (write! patents data-dir as-of)]
       (println (str "corpus → " data-dir "/" (get-in man ["artifacts" "corpus" "file"])
                     "  cid=" (get-in man ["artifacts" "corpus" "cid"])
                     "  (" (get-in man ["artifacts" "corpus" "bytes"]) " B)"))
       (println (str "datoms → " data-dir "/" (get-in man ["artifacts" "datoms" "file"])
                     "  cid=" (get-in man ["artifacts" "datoms" "cid"])
                     "  (" (get-in man ["artifacts" "datoms" "bytes"]) " B)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
