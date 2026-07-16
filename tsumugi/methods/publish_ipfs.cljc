(ns tsumugi.methods.publish-ipfs
  "publish_ipfs.cljc — 紡ぎ: pin the published power-graph to kotoba IPFS + serve a descriptor.
  Clojure port of methods/publish_ipfs.py (1:1). ADR-2606092000.

  Content-addresses each gzipped artifact to a kotoba IPFS CIDv1 (raw, sha2-256, base32) —
  byte-identical to `ipfs add --cid-version=1 --raw-leaves` for a single <256 KiB raw block,
  verifiable with NO daemon. Writes the gz artifacts + a publish-manifest + the served
  descriptors. Depends on the already-ported same-actor publish.

  Output dirs (80-data + apex public) are injectable so the port is testable against a temp dir
  without touching tracked data. NOTE: the gzip ENCODER (java) differs from Python's zlib, so a
  gz artifact's CID is NOT byte-identical to the Python-published one — it is still a valid
  single-block CIDv1 and round-trips; the cidv1-raw primitive itself IS byte-exact (the
  `ipfs add` vector). SHA-256 + gzip via java.*."
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [tsumugi.methods.publish :as publish]
            [multiformats.core :as mf]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]
           [java.util.zip GZIPOutputStream GZIPInputStream]))

(def DATA-DIR "80-data/tsumugi-power")
(def APEX-PUBLIC "50-infra/etzhayyim-did-web/public")
(def PUBLISHED-AT "2026-06-11")
(def GATEWAYS ["https://ipfs.io/ipfs/" "https://dweb.link/ipfs/" "https://cloudflare-ipfs.com/ipfs/"])
(def ^:private SINGLE-BLOCK (* 256 1024))
(defn cidv1-raw
  "kotoba IPFS CIDv1 (raw 0x55, sha2-256) of a single <256KiB block. Byte-identical to
  `ipfs add --cid-version=1 --raw-leaves`. Delegates to com-junkawasaki/multiformats-clj."
  [^bytes data]
  (mf/cidv1-raw data))

(defn- gz
  "gzip bytes (deterministic — java writes mtime=0)."
  [^bytes data]
  (let [bos (ByteArrayOutputStream.)]
    (with-open [g (GZIPOutputStream. bos)] (.write g data))
    (.toByteArray bos)))

(defn- gunzip [^bytes data]
  (with-open [g (GZIPInputStream. (io/input-stream data)) bos (ByteArrayOutputStream.)]
    (io/copy g bos) (.toByteArray bos)))

(defn- vocab-jsonld []
  (let [terms {"Org" "an organisation / 法人 / institution (a power-holding collective)"
               "PublicSeat" "a public-power SEAT (chair/exec/official) — never a private individual (S2)"
               "Locality" "a place cluster bearing an etzhayyim-derived 産官学報 concentration readout"
               "custodies" "parent organisation controls/owns the object (P749-grade)"
               "dependsOn" "supply / funding / IP dependency"
               "concentration" "etzhayyim-DERIVED 産官学報 cross-sector concentration (edge-primary integral × diversity)"
               "scale" "global→supranational→national→regional→municipal→local→intra-org"
               "sector" "産官学報民金"
               "collectiveKind" "組織/地域/市区町村/コミュニティ/社内派閥/学閥/系列/審議会"
               "derivedBy" "the DID that authored this (etzhayyim-original) assertion"}]
    (json/generate-string
     {"@context" {"@vocab" publish/NS "rdfs" "http://www.w3.org/2000/01/rdf-schema#"}
      "@id" publish/NS "@type" "owl:Ontology"
      "rdfs:label" "etzhayyim power-dynamics vocabulary (tsumugi 紡ぎ)"
      "rdfs:comment" (str "Self-sovereign linked-data vocabulary. Dataset: /dataset/tsumugi-power.json. "
                          "License: " publish/LICENSE ". Publisher: " publish/PUBLISHER-DID)
      "terms" (into {} (map (fn [[k v]] [(str publish/NS k) v]) terms))}
     {:pretty true})))

(defn pin
  "Regenerate the linked data (publish/publish to a temp), gzip + content-address the 3
  artifacts to a kotoba CIDv1, write them + the manifest + the served descriptors. Returns the
  manifest map. data-dir / apex-dir / seed are injectable (default the tracked locations)."
  [& {:keys [data-dir apex-dir seed]
      :or {data-dir DATA-DIR apex-dir APEX-PUBLIC
           seed "20-actors/tsumugi/data/seed-scale-power.kotoba.edn"}}]
  (let [tmp (str (java.io.File/createTempFile "tsumugi-pub" ""))
        _ (do (.delete (io/file tmp)) (.mkdirs (io/file tmp)))
        manifest (publish/publish tmp seed)
        nt (.getBytes (slurp (str tmp "/etzhayyim-power-graph.nt")) "UTF-8")
        jsonld (.getBytes (slurp (str tmp "/etzhayyim-power-graph.jsonld")) "UTF-8")
        edn (.getBytes (slurp (str "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")) "UTF-8")
        _ (io/make-parents (str data-dir "/x"))
        artifacts (reduce
                   (fn [m [key fname raw]]
                     (let [g (gz raw)]
                       (when (> (alength g) SINGLE-BLOCK)
                         (throw (ex-info (str fname " gz " (alength g) "B > single-block limit") {})))
                       (with-open [o (io/output-stream (str data-dir "/" fname))] (.write o g))
                       (assoc m key {"file" fname "bytes" (alength g) "raw_bytes" (alength raw)
                                     "cid" (cidv1-raw g)
                                     "note" "gzip mtime=0; CID == `ipfs add --cid-version=1 --raw-leaves`"})))
                   {} [["graph" "power-graph.kotoba.edn.gz" edn]
                       ["ntriples" "power-graph.nt.gz" nt]
                       ["jsonld" "power-graph.jsonld.gz" jsonld]])
        pm {"actor" "tsumugi" "adr" "2606092000" "published_at" PUBLISHED-AT
            "title" (get manifest "title") "publisher" publish/PUBLISHER-DID
            "license" publish/LICENSE "vocabulary" publish/NS "entityNamespace" publish/ID
            "contentHash_ntriples" (get manifest "contentHash")
            "counts" (get manifest "counts") "provenance" (get manifest "provenance")
            "artifacts" artifacts "gateways" GATEWAYS
            "verify" "python3 20-actors/tsumugi/methods/publish_ipfs.py --verify  (re-content-address)"}]
    (spit (str data-dir "/publish-manifest.json") (str (json/generate-string pm {:pretty true}) "\n"))
    (io/make-parents (str apex-dir "/ns/x"))
    (io/make-parents (str apex-dir "/dataset/x"))
    (spit (str apex-dir "/ns/power") (str (vocab-jsonld) "\n"))
    (spit (str apex-dir "/dataset/tsumugi-power.json")
          (str (json/generate-string
                (assoc pm "fetch" (into {} (map (fn [[a v]] [a (mapv #(str % (get v "cid")) GATEWAYS)]) artifacts)))
                {:pretty true}) "\n"))
    pm))

(defn verify
  "Re-content-address the data-dir artifacts vs the manifest. Returns 0 if all match else 1."
  [& {:keys [data-dir] :or {data-dir DATA-DIR}}]
  (let [pm (json/parse-string (slurp (str data-dir "/publish-manifest.json")))]
    (if (every? (fn [[_ v]]
                  (= (cidv1-raw (with-open [in (io/input-stream (str data-dir "/" (get v "file")))
                                            bos (ByteArrayOutputStream.)]
                                  (io/copy in bos) (.toByteArray bos)))
                     (get v "cid")))
                (get pm "artifacts"))
      0 1)))

;; exposed for the round-trip test (gz/gunzip are private)
(defn gz* [^bytes data] (gz data))
(defn gunzip* [^bytes data] (gunzip data))

#?(:clj
   (defn -main
     "CLI entry: mirrors publish_ipfs.main — content-address the 80-data/tsumugi-power artifacts
     to kotoba CIDv1 + write the publish manifest + served descriptors (--verify checks an existing
     manifest, exit 0/1). ADR-2606261200 cljc-native operator leg."
     [& argv]
     (if (some #{"--verify"} (vec argv))
       (System/exit (verify))
       (let [pm   (pin)
             arts (or (get pm "artifacts") (:artifacts pm))]
         (println (str "[tsumugi/pin] 80-data/tsumugi-power/ — " (count arts)
                       " artifacts content-addressed to IPFS CIDv1:"))
         (doseq [v arts] (println (str "    " (get v "file") "  " (get v "bytes") "B  " (get v "cid"))))
         (println "  served: https://etzhayyim.com/ns/power · /dataset/tsumugi-power.json (next deploy)")))))
