(ns maps.methods.verify
  "maps — kotoba read-surface readiness verifier (ADR-2606064500 R1). stdlib only.
  1:1 Clojure port of `methods/verify.py`.

  Runs all four kotoba-native reads against a live endpoint and reports:
    {:chunk {:ok bool :count N}
     :search {:ok bool :count N}
     :reverse {:ok bool :nearest id}
     :transit {:ok bool :count N}
     :all-ok bool}

  Each read degrades to ok=false on error (fail-soft, report always emitted).
  Portable .cljc."
  (:require [maps.methods.chunk   :as chunk]
            [maps.methods.search  :as search-ns]
            [maps.methods.reverse :as reverse-ns]
            [maps.methods.transit :as transit]))

;; Tokyo Station anchor — the maps-3d walkable default.
(def defaults {:lat 35.6812 :lon 139.7671 :res 10 :ring 2
               :query "tok" :stop-id "f.station.tokyo"})

(defn verify-reads
  "Run all four reads against query-fn + ring-cells-fn.
  Returns {:chunk :search :reverse :transit :all-ok}."
  [query-fn ring-cells-fn & {:keys [lat lon res ring query stop-id]
                              :or   {lat      (:lat defaults)
                                     lon      (:lon defaults)
                                     res      (:res defaults)
                                     ring     (:ring defaults)
                                     query    (:query defaults)
                                     stop-id  (:stop-id defaults)}}]
  (let [cells    (when ring-cells-fn (ring-cells-fn lat lon res ring))
        ch       (if (seq cells)
                   (try (chunk/get-chunk query-fn cells res)
                        (catch #?(:clj Exception :cljs :default) _ {:total 0}))
                   {:total 0})
        sr       (try (search-ns/search-places query-fn query)
                      (catch #?(:clj Exception :cljs :default) _ []))
        rg       (try (reverse-ns/reverse-geocode query-fn ring-cells-fn lat lon
                                                  :res res :ring ring)
                      (catch #?(:clj Exception :cljs :default) _ []))
        td       (try (transit/next-departures-at-stop query-fn stop-id)
                      (catch #?(:clj Exception :cljs :default) _ []))
        report   {:chunk   {:ok    (pos? (get ch :total 0))
                            :count (get ch :total 0)
                            :note  (when-not (seq cells) "ring-cells-fn unavailable")}
                  :search  {:ok (pos? (count sr)) :count (count sr)}
                  :reverse {:ok (pos? (count rg)) :nearest (:id (first rg))}
                  :transit {:ok (pos? (count td)) :count (count td)}}]
    (assoc report :all-ok (every? #(:ok (val %)) report))))
