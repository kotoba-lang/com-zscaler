(ns haraedo.methods.test-agent
  "haraedo 祓戸 — verification harness (langgraph-independent). 1:1 port of py/test_agent.py minus the
  2 fetch_facilities tests (importlib + urllib live-fetch CLI = ADR-skip; fetch_facilities.py is kept
  as infra). Runs the pure route helpers + the node functions (datalog host binding stubbed via the
  *datalog* dynamic var, the cljc equivalent of the Python module-global swap) + seed-EDN gate checks."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [haraedo.methods.agent :as agent]))

(def COORDS {"jinnan" [35.6645 139.6975] "udagawa" [35.6615 139.6980]
             "dogenzaka" [35.6575 139.6960] "ebisu" [35.6465 139.7100]})
(def DEPOT [35.6600 139.7020])

;; ── 1. pure route-optimization helpers ──
(deftest test-haversine-symmetric-and-zero
  (is (= 0.0 (agent/haversine-km 35.0 139.0 35.0 139.0)))
  (let [a (agent/haversine-km 35.66 139.70 35.6465 139.71)
        b (agent/haversine-km 35.6465 139.71 35.66 139.70)]
    (is (< (Math/abs (- a b)) 1e-9))
    (is (> a 0))))

(deftest test-two-opt-never-worse-than-nn
  (let [pts (vec (keys COORDS))
        nn (agent/nearest-neighbour pts COORDS DEPOT)
        [order length] (agent/two-opt nn COORDS DEPOT)
        nn-len (agent/route-length nn COORDS DEPOT)]
    (is (= (set order) (set pts)))
    (is (<= length (+ nn-len 1e-9)))))

(deftest test-route-visits-every-stop
  (let [pts (vec (keys COORDS))]
    (is (= (sort (agent/nearest-neighbour pts COORDS DEPOT)) (sort pts)))))

;; ── 2. node functions with stubbed datalog ──
(defn- fake-datalog []
  (let [t (atom [])]
    {:transacted t
     :q (fn [query & args]
          (cond
            (str/includes? query ":item-category/hazardous") [[(contains? #{"battery" "appliance-recycle-law"} (first args))]]
            (str/includes? query ":item-category/base-fee") [[(get {"furniture" 1000 "bedding" 1500 "bicycle" 800} (first args) 0)]]
            (and (str/includes? query ":vehicle/capacity-kg") (str/includes? query ":vehicle/status :available")) [["veh-small" 1000] ["veh-big" 4000]]
            :else []))
     :transact (fn [d] (swap! t into d) true)}))

(deftest test-classify-splits-hazardous-g3
  (binding [agent/*datalog* (fake-datalog)]
    (let [out (agent/classify-node {"items" ["furniture" "battery" "bedding" "appliance-recycle-law"]})]
      (is (= ["furniture" "bedding"] (get out "accepted_items")))
      (is (= ["battery" "appliance-recycle-law"] (get out "rejected_items"))))))

(deftest test-quote-sums-accepted-fees
  (binding [agent/*datalog* (fake-datalog)]
    (is (= 2500 (get (agent/quote-node {"jurisdiction" "us.sf" "accepted_items" ["furniture" "bedding"]}) "fee")))))

(deftest test-sticker-requires-consent-g1
  (let [fake (fake-datalog)]
    (binding [agent/*datalog* fake]
      (let [out (agent/sticker-node {"member_did" "did:x" "consent_sig" "" "jurisdiction" "jp.shibuya"
                                     "accepted_items" ["furniture"] "collection_point" "cp" "fee" 1000 "scheduled_date" "2026-06-05"})]
        (is (= "" (get out "sticker_id")))
        (is (= [] @(:transacted fake)))))))

(deftest test-sticker-with-consent-emits-application
  (let [fake (fake-datalog)]
    (binding [agent/*datalog* fake]
      (let [out (agent/sticker-node {"member_did" "did:x" "consent_sig" "sig" "jurisdiction" "jp.shibuya"
                                     "accepted_items" ["furniture"] "collection_point" "cp" "fee" 1000 "scheduled_date" "2026-06-05"})]
        (is (seq (get out "sticker_id")))
        (is (= 1 (count @(:transacted fake))))
        (is (= ":scheduled" (get (first @(:transacted fake)) ":application/state")))))))

(deftest test-assign-vehicle-respects-capacity-g15
  (binding [agent/*datalog* (fake-datalog)]
    (is (= "veh-big" (get (agent/assign-vehicle-node {"jurisdiction" "jp.shibuya" "load_kg" 1500}) "vehicle")))
    (is (= "veh-small" (get (agent/assign-vehicle-node {"jurisdiction" "jp.shibuya" "load_kg" 500}) "vehicle")))))

;; ── fee models ──
(defn- fake-juris [model & {:keys [per-sticker per-kg flat weights base] :or {per-sticker 0 per-kg 0 flat 0 weights {} base {}}}]
  {:q (fn [query & a]
        (cond
          (str/includes? query "jurisdiction/bulky-fee-model") (if (some? model) [[model]] [])
          (str/includes? query "jurisdiction/fee-per-sticker") [[per-sticker]]
          (str/includes? query "jurisdiction/fee-per-kg") [[per-kg]]
          (str/includes? query "jurisdiction/fee-flat") [[flat]]
          (str/includes? query "item-category/est-weight-kg") [[(get weights (first a) 0)]]
          (str/includes? query "item-category/base-fee") [[(get base (first a) 0)]]
          :else []))
   :transact (fn [_] nil)})

(defn- quote-with [fake state] (binding [agent/*datalog* fake] (get (agent/quote-node state) "fee")))

(deftest test-fee-model-per-sticker
  (is (= 800 (quote-with (fake-juris ":per-sticker" :per-sticker 400) {"jurisdiction" "jp.shibuya" "accepted_items" ["a" "b"]}))))
(deftest test-fee-model-per-weight
  (is (= 6000 (quote-with (fake-juris ":per-weight" :per-kg 100 :weights {"furniture" 35 "bedding" 25}) {"jurisdiction" "gb.camden" "accepted_items" ["furniture" "bedding"]}))))
(deftest test-fee-model-flat-and-free
  (is (= 5000 (quote-with (fake-juris ":flat" :flat 5000) {"jurisdiction" "de.berlin" "accepted_items" ["x" "y" "z"]})))
  (is (= 0 (quote-with (fake-juris ":free") {"jurisdiction" "us.nyc" "accepted_items" ["x" "y"]}))))
(deftest test-fee-model-per-item-default
  (is (= 2500 (quote-with (fake-juris ":per-item" :base {"furniture" 1000 "bedding" 1500}) {"jurisdiction" "us.sf" "accepted_items" ["furniture" "bedding"]}))))

;; ── slot scheduling ──
(defn- fake-slots []
  (let [t (atom [])]
    {:transacted t
     :q (fn [query & _]
          (cond
            (str/includes? query "collection-point/service-area") [["shibuya-north"]]
            (str/includes? query ":slot/jurisdiction") [["s-pm" "2026-06-05" 20 0 ":pm"] ["s-am" "2026-06-05" 20 3 ":am"] ["s-full" "2026-06-04" 2 2 ":am"]]
            :else []))
     :transact (fn [d] (swap! t into d))}))

(deftest test-schedule-picks-earliest-open-slot-and-books
  (let [fake (fake-slots)]
    (binding [agent/*datalog* fake]
      (let [out (agent/schedule-node {"jurisdiction" "jp.shibuya" "collection_point" "cp" "scheduled_date" ""})]
        (is (= "s-am" (get out "slot_id")))
        (is (= "2026-06-05" (get out "scheduled_date")))
        (is (= 4 (get (first @(:transacted fake)) ":slot/booked")))))))

(deftest test-schedule-no-open-slot-returns-empty
  (binding [agent/*datalog* {:q (fn [query & _]
                                  (cond (str/includes? query "collection-point/service-area") [["shibuya-north"]]
                                        (str/includes? query ":slot/jurisdiction") [["s1" "2026-06-05" 5 5 ":am"]]
                                        :else []))
                             :transact (fn [_] nil)}]
    (let [out (agent/schedule-node {"jurisdiction" "jp.shibuya" "collection_point" "cp" "scheduled_date" ""})]
      (is (and (= "" (get out "slot_id")) (= "" (get out "scheduled_date")))))))

;; ── capacitated VRP (Clarke-Wright) ──
(def VRP-COORDS {"a" [35.660 139.700] "b" [35.665 139.701] "c" [35.670 139.702] "d" [35.700 139.750]})
(def VRP-DEPOT [35.659 139.700])

(deftest test-clarke-wright-respects-capacity-and-covers-all
  (let [demand {"a" 2000 "b" 2000 "c" 2000 "d" 2000}
        routes (agent/clarke-wright (vec (keys VRP-COORDS)) demand VRP-COORDS VRP-DEPOT 4000)]
    (doseq [r routes] (is (<= (reduce + 0 (map demand r)) 4000)))
    (is (= (sort (mapcat identity routes)) (sort (keys VRP-COORDS))))
    (is (>= (count routes) 2))))

(deftest test-clarke-wright-single-route-when-it-all-fits
  (let [demand (into {} (map (fn [k] [k 100]) (keys VRP-COORDS)))
        routes (agent/clarke-wright (vec (keys VRP-COORDS)) demand VRP-COORDS VRP-DEPOT 4000)]
    (is (= 1 (count routes)))
    (is (= (sort (first routes)) (sort (keys VRP-COORDS))))))

;; ── R2 solver upgrade + VRPTW ETA ──
(deftest test-or-opt-never-worse
  (let [pts (vec (keys VRP-COORDS))
        base (agent/route-length pts VRP-COORDS VRP-DEPOT)
        [order length] (agent/or-opt pts VRP-COORDS VRP-DEPOT)]
    (is (= (set order) (set pts)))
    (is (<= length (+ base 1e-9)))))

(deftest test-local-search-at-least-as-good-as-two-opt
  (let [pts (vec (keys VRP-COORDS))
        [_ two-opt-len] (agent/two-opt pts VRP-COORDS VRP-DEPOT)
        [ls-order ls-len] (agent/local-search pts VRP-COORDS VRP-DEPOT)]
    (is (= (set ls-order) (set pts)))
    (is (<= ls-len (+ two-opt-len 1e-9)))))

(deftest test-route-eta-monotonic-and-window-flag
  (let [etas (agent/route-eta ["a" "b" "c" "d"] VRP-COORDS VRP-DEPOT 480 20.0 10)
        times (mapv second etas)]
    (is (= times (vec (sort times))))
    (is (every? #(>= % 480) times))
    (is (>= (count (filter (fn [[_ t]] (> t 485)) etas)) 1))))

;; ── seed-EDN gate checks ──
(defn- top-level-maps [edn]
  (loop [i 0 depth 0 start nil instr false maps []]
    (if (>= i (count edn))
      maps
      (let [c (nth edn i)]
        (cond
          instr (cond (= c \\) (recur (+ i 2) depth start instr maps)
                      (= c \") (recur (inc i) depth start false maps)
                      :else (recur (inc i) depth start instr maps))
          (= c \") (recur (inc i) depth start true maps)
          (= c \;) (recur (loop [j i] (if (or (>= j (count edn)) (= (nth edn j) \newline)) j (recur (inc j)))) depth start instr maps)
          (= c \{) (recur (inc i) (inc depth) (if (zero? depth) i start) instr maps)
          (= c \}) (let [d (dec depth)]
                     (if (and (zero? d) start)
                       (recur (inc i) d nil instr (conj maps (subs edn start (inc i))))
                       (recur (inc i) d start instr maps)))
          :else (recur (inc i) depth start instr maps))))))

(defn- seed [] (slurp (io/file "20-actors/haraedo/kotoba/seed.edn")))

(deftest test-hazardous-items-not-charged-g3
  (doseq [m (top-level-maps (seed))]
    (when (str/includes? m ":item-category/hazardous true")
      (is (re-find #":item-category/base-fee\s+0\b" m)))))

(deftest test-every-facility-has-capacity-and-sourcing-g14-g15
  (doseq [m (top-level-maps (seed))]
    (when (str/includes? m ":facility/id")
      (is (str/includes? m ":facility/capacity-tonnes-day"))
      (is (str/includes? m ":facility/sourcing"))
      (is (str/includes? m ":facility/accepted-categories")))))

(deftest test-seed-is-representative-not-authoritative
  (let [s (seed)
        facilities (filter #(str/includes? % ":facility/id") (top-level-maps s))]
    (is (not (str/includes? s ":sourcing :authoritative")))
    (is (seq facilities))
    (doseq [m facilities] (is (str/includes? m ":facility/sourcing :representative")))))

(deftest test-route-load-within-vehicle-capacity-g15
  (let [maps (top-level-maps (seed))
        vehicles (into {} (keep (fn [m] (let [vid (re-find #":vehicle/id \"([^\"]+)\"" m) cap (re-find #":vehicle/capacity-kg (\d+)" m)]
                                          (when (and vid cap) [(second vid) (Long/parseLong (second cap))]))) maps))]
    (doseq [m maps]
      (when (str/includes? m ":route/id")
        (let [veh (re-find #":route/vehicle \"([^\"]+)\"" m) load (re-find #":route/load-kg (\d+)" m)]
          (when (and veh load) (is (<= (Long/parseLong (second load)) (get vehicles (second veh))))))))))

(deftest test-every-jurisdiction-has-currency-and-fee-params
  (doseq [m (top-level-maps (seed))]
    (when (str/includes? m ":jurisdiction/id")
      (is (str/includes? m ":jurisdiction/currency"))
      (is (str/includes? m ":jurisdiction/bulky-fee-model")))))

(deftest test-slots-booked-within-capacity
  (doseq [m (top-level-maps (seed))]
    (when (str/includes? m ":slot/id")
      (let [cap (Long/parseLong (second (re-find #":slot/capacity (\d+)" m)))
            booked (Long/parseLong (second (re-find #":slot/booked (\d+)" m)))]
        (is (<= booked cap))))))

;; ── R3 inter-window vehicle reuse ──
(defn- fake-dispatch-dl []
  {:q (fn [query & _]
        (cond
          (str/includes? query ":vehicle/status :available") [["v1" 2000]]
          (str/includes? query ":vehicle/depot-lat") [[35.660 139.700]]
          (and (str/includes? query ":facility/jurisdiction") (str/includes? query ":facility/capacity-tonnes-day")) [["f1" 100.0 0.0]]
          (str/includes? query ":crew/shift :early") []
          :else []))
   :transact (fn [_] nil)})

(deftest test-inter-window-vehicle-reuse-r3
  (binding [agent/*datalog* (fake-dispatch-dl)]
    (let [out (agent/build-routes-node {"jurisdiction" "x"
                                        "coords" {"a" [35.661 139.701] "b" [35.662 139.702]}
                                        "demand" {"a" 500 "b" 500}
                                        "window_of" {"a" {"window" "am" "start" 480 "end" 720}
                                                     "b" {"window" "pm" "start" 780 "end" 1020}}})
          routes (get out "routes")
          by-win (into {} (map (fn [r] [(get r "window") r]) routes))]
      (is (= [] (get out "unassigned")))
      (is (= 2 (count routes)))
      (is (and (= "v1" (get-in by-win ["am" "vehicle"])) (= "v1" (get-in by-win ["pm" "vehicle"]))))
      (is (= false (get-in by-win ["am" "vehicle_reused"])))
      (is (= true (get-in by-win ["pm" "vehicle_reused"]))))))

(deftest test-no-reuse-when-vehicle-cannot-return-in-time-r3
  (binding [agent/*datalog* (fake-dispatch-dl)]
    (let [out (agent/build-routes-node {"jurisdiction" "x"
                                        "coords" {"a" [36.50 140.60] "b" [35.662 139.702]}
                                        "demand" {"a" 500 "b" 500}
                                        "window_of" {"a" {"window" "am" "start" 480 "end" 720}
                                                     "b" {"window" "pm" "start" 900 "end" 1020}}})
          wins (set (map #(get % "window") (get out "routes")))]
      (is (contains? wins "am"))
      (is (and (not (contains? wins "pm")) (>= (count (get out "unassigned")) 1))))))
