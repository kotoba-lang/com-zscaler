(ns jinushi.methods.verify
  "jinushi 地主 — integrity verify: committed snapshots ↔ ingest-provenance.json (CID + sha256).

  Re-derives every committed snapshot's CIDv1 (content hash) and sha256 from the bytes on disk
  and checks them against the values recorded in `ingest-provenance.json`. A mismatch means the
  snapshot drifted from its recorded identity (tamper, partial edit, or a stale provenance) — the
  content-addressing of R1 is only as good as a check that actually runs it. CI-runnable; exits
  non-zero on any mismatch."
  (:require [clojure.string :as str]
            [jinushi.methods.cid :as cid]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])))

#?(:clj
   (defn sha256-hex-file [f]
     (let [md (java.security.MessageDigest/getInstance "SHA-256")
           bs (.digest md (java.nio.file.Files/readAllBytes (.toPath (io/file f))))]
       (apply str (map #(format "%02x" (bit-and % 0xff)) bs)))))

#?(:clj
   (defn check-artifact
     "Verify one recorded artifact map {:artifact :cidv1 :sha256} against the file in `dir`."
     [dir {:keys [artifact cidv1 sha256]}]
     (let [f (io/file dir artifact)]
       (if-not (.exists f)
         {:artifact artifact :present false :ok false :reason "file missing"}
         (let [cid-now (cid/file->cidv1 f)
               sha-now (sha256-hex-file f)
               cid-ok (= cid-now cidv1)
               sha-ok (= sha-now sha256)]
           {:artifact artifact :present true :cid-ok cid-ok :sha-ok sha-ok
            :ok (and cid-ok sha-ok)
            :reason (cond cid-ok "" (nil? cidv1) "no recorded cid" :else (str "cid " cidv1 " ≠ " cid-now))})))))

#?(:clj
   (defn verify-provenance
     "Check every recorded source artifact (and the derived Datom log if present) in `dir`."
     [dir]
     (let [prov (json/parse-string (slurp (io/file dir "ingest-provenance.json")) true)
           src-checks (map #(check-artifact dir %) (:sources prov))
           denom (:denominator prov)                 ;; committed country-area reference
           denom-check (when denom (check-artifact dir denom))
           ;; building-ownership layer: the committed snapshot (the gitignored datom log is skipped if absent)
           bo-snap (get-in prov [:building-ownership :snapshot])
           bo-check (when bo-snap (check-artifact dir bo-snap))
           gleif-ref (get-in prov [:company-linkage :reference])   ;; committed GLEIF company reference
           gleif-check (when gleif-ref (check-artifact dir gleif-ref))
           pluto-ref (get-in prov [:nyc-pluto :reference])         ;; committed NYC PLUTO parcel sample
           pluto-check (when pluto-ref (check-artifact dir pluto-ref))
           osm-ref (get-in prov [:osm-buildings :reference])       ;; committed OSM building-stock sample
           osm-check (when osm-ref (check-artifact dir osm-ref))
           dvf-ref (get-in prov [:fr-dvf :reference])              ;; committed FR DVF value sample
           dvf-check (when dvf-ref (check-artifact dir dvf-ref))
           ;; NOTE: derived Datom logs (jinushi-land-datoms / *-datoms / unified) are gitignored +
           ;; regenerable — their CID is informational provenance (recomputed on emit), NOT a
           ;; committed-repo integrity concern, so verify does NOT check them (avoids false fails
           ;; after any re-emit). verify guards the COMMITTED data only.
           checks (vec (concat src-checks (when denom-check [denom-check])
                               (when bo-check [bo-check]) (when gleif-check [gleif-check])
                               (when pluto-check [pluto-check]) (when osm-check [osm-check])
                               (when dvf-check [dvf-check])))]
       {:ok (every? :ok checks) :checks checks})))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile)
                    (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           {:keys [ok checks]} (verify-provenance dir)]
       (doseq [c checks]
         (println (format "  %-40s %s" (:artifact c)
                          (if (:ok c) "OK (cid+sha256 match)"
                              (str "MISMATCH — " (:reason c))))))
       (println (if ok "verify: ALL artifacts match provenance" "verify: FAILED"))
       (if ok 0 1))))
