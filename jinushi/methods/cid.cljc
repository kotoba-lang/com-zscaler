(ns jinushi.methods.cid
  "jinushi 地主 — content-addressing (R1): CIDv1 (raw codec / sha2-256) for acquisition snapshots.

  A snapshot's CIDv1 is its self-certifying identity on the kotoba/IPFS substrate
  (ADR-2605241500 + ADR-2605262130): the same bytes always hash to the same CID, so a snapshot
  recorded in `ingest-provenance.json` is tamper-evident and fetch-verifiable from any IPFS
  gateway by content.

  Scope (honest): this is the **raw single-block** CIDv1 — `multibase(base32, 0x01 0x55 0x12 0x20
  <sha2-256>)` — the content hash of the file bytes. It is NOT the dag-pb/UnixFS CID that
  `ipfs add` (default, chunked) produces for large files; UnixFS parity is a later leg.

  The CID machinery now delegates to the shared **com-junkawasaki/multiformats-clj** library
  (`.cljc`, byte-identical to the prior local copy + to `ipfs add --raw-leaves`). The public
  surface (sha256-bytes / base32-nopad / cidv1-raw / string->cidv1 / file->cidv1) is unchanged."
  (:require [clojure.string :as str]
            [multiformats.core :as mf]
            #?(:clj [clojure.java.io :as io])))

(def sha256-bytes mf/sha256)        ;; ^bytes → sha2-256 digest
(def base32-nopad mf/base32)        ;; RFC4648 base32 lower, no padding (multibase 'b' body)
(def cidv1-raw    mf/cidv1-raw)     ;; content bytes → "bafkrei…" (raw 0x55 / sha2-256)

(defn string->cidv1 [^String s]
  #?(:clj (cidv1-raw (.getBytes s "UTF-8")) :cljs (throw (ex-info "cid: :clj only" {}))))

;; NB: cidv1-raw of the whole file bytes (single-block, any size) — NOT mf/cid-of-file,
;; which caps at 256 KiB; jinushi's raw-CID contract intentionally hashes large
;; snapshots too (it never claimed dag-pb/UnixFS parity).
#?(:clj (defn file->cidv1 [f] (cidv1-raw (java.nio.file.Files/readAllBytes (.toPath (io/file f))))))

#?(:clj
   (defn -main [& argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile)
                    (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           files (if (seq argv) (map io/file argv)
                     (filter #(str/ends-with? (.getName %) ".kotoba.edn") (.listFiles dir)))]
       (doseq [f (sort-by #(.getName %) files)]
         (println (file->cidv1 f) " " (.getName f)))
       0)))
