(ns haraedo.methods.agent
  "haraedo 祓戸 — bulky-waste disposal cell. 1:1 port of py/agent.py's LOGIC: the pure route-
  optimization helpers (haversine / NN / 2-opt / Or-opt / local-search / VRPTW ETA / Clarke-Wright
  capacitated VRP — float-light, no host) + the kotoba-datalog-backed node functions (intake:
  classify/quote/match-facility/schedule/sticker; dispatch: gather/cluster/build-routes/assign-
  vehicle/assign-crew/optimize-route/select-facility/emit-plan). The kotoba `datalog` host binding
  is modelled by the dynamic var *datalog* (the idiomatic equivalent of the Python module-global the
  test swaps); when nil the nodes degrade exactly like local-dev. OMITTED legs: build_intake_graph/
  build_dispatch_graph/handle_* (the langgraph framework wiring) and fetch_facilities (urllib live-
  fetch CLI, kept as infra)."
  (:require [clojure.string :as str]
            #?(:clj [cheshire.core :as json])))

;; ── kotoba datalog host binding (injected; nil in local dev / cljc default) ───
(def ^:dynamic *datalog* nil)
(defn- dq [query & args] (when *datalog* (apply (:q *datalog*) query args)))
(defn- dtx [datoms] (when *datalog* ((:transact *datalog*) datoms)))

;; ── pure route-optimization helpers ───────────────────────────────────────────
(defn haversine-km
  "Great-circle distance in km (stand-in for road distance, R0)."
  [a-lat a-lon b-lat b-lon]
  (let [r 6371.0
        p1 (Math/toRadians (double a-lat)) p2 (Math/toRadians (double b-lat))
        dphi (Math/toRadians (- (double b-lat) (double a-lat)))
        dlmb (Math/toRadians (- (double b-lon) (double a-lon)))
        h (+ (Math/pow (Math/sin (/ dphi 2)) 2)
             (* (Math/cos p1) (Math/cos p2) (Math/pow (Math/sin (/ dlmb 2)) 2)))]
    (* 2 r (Math/asin (Math/sqrt h)))))

(defn route-length
  "Total tour length from `start` through `order` (list of point ids)."
  [order coords start]
  (first (reduce (fn [[total cur] pid]
                   (let [nxt (get coords pid)]
                     [(+ total (haversine-km (cur 0) (cur 1) (nxt 0) (nxt 1))) nxt]))
                 [0.0 start] order)))

(defn nearest-neighbour
  "Greedy NN tour over point ids starting nearest to `start`."
  [points coords start]
  (loop [remaining (vec points) order [] cur start]
    (if (empty? remaining)
      order
      (let [nxt (apply min-key (fn [p] (haversine-km (cur 0) (cur 1) ((coords p) 0) ((coords p) 1))) remaining)]
        (recur (vec (remove #(= % nxt) remaining)) (conj order nxt) (coords nxt))))))

(defn two-opt
  "2-opt local search to shorten an NN tour."
  [order coords start]
  (loop [best (vec order) best-len (route-length order coords start)]
    (let [n (count best)
          [nb nl improved]
          (loop [i 0 b best bl best-len imp false]
            (if (>= i (dec n))
              [b bl imp]
              (let [[b2 bl2 imp2]
                    (loop [k (inc i) b b bl bl imp imp]
                      (if (>= k n)
                        [b bl imp]
                        (let [cand (vec (concat (subvec b 0 i) (reverse (subvec b i (inc k))) (subvec b (inc k))))
                              cl (route-length cand coords start)]
                          (if (< (+ cl 1e-9) bl)
                            (recur (inc k) cand cl true)
                            (recur (inc k) b bl imp)))))]
                (recur (inc i) b2 bl2 imp2))))]
      (if improved (recur nb nl) [nb nl]))))

(defn or-opt
  "Or-opt: relocate chains of length 1..3 to a better position."
  [order coords start]
  (loop [best (vec order) best-len (route-length order coords start)]
    (let [n (count best)
          step (fn []
                 (reduce
                  (fn [[b bl _ :as acc] seg]
                    (if (>= seg n)
                      (reduced acc)
                      (let [[b2 bl2 imp2]
                            (loop [i 0 b b bl bl imp false]
                              (if (> (+ i seg) n)
                                [b bl imp]
                                (let [chain (subvec b i (+ i seg))
                                      rest (vec (concat (subvec b 0 i) (subvec b (+ i seg))))
                                      [b3 bl3 imp3]
                                      (loop [j 0 b b bl bl imp imp]
                                        (if (> j (count rest))
                                          [b bl imp]
                                          (let [cand (vec (concat (subvec rest 0 j) chain (subvec rest j)))
                                                cl (route-length cand coords start)]
                                            (if (< (+ cl 1e-9) bl)
                                              (recur (inc j) cand cl true)
                                              (recur (inc j) b bl imp)))))]
                                  (recur (inc i) b3 bl3 imp3))))]
                        (if imp2 (reduced [b2 bl2 true]) [b2 bl2 false]))))
                  [best best-len false] [1 2 3]))
          [nb nl improved] (step)]
      (if improved (recur nb nl) [nb nl]))))

(defn local-search
  "R2 route polish: alternate 2-opt and Or-opt until neither improves."
  [order coords start]
  (loop [cur (vec order) cur-len (route-length order coords start)]
    (let [[cur _] (two-opt cur coords start)
          [cur new-len] (or-opt cur coords start)]
      (if (>= (+ new-len 1e-9) cur-len)
        [cur new-len]
        (recur cur new-len)))))

(defn route-eta
  "VRPTW arrival clock: minutes-from-midnight ETA at each stop."
  ([order coords depot start-min] (route-eta order coords depot start-min 20.0 10))
  ([order coords depot start-min speed-kmh service-min]
   (first (reduce (fn [[etas cur t] s]
                    (let [nxt (get coords s)
                          t (+ t (* (/ (haversine-km (cur 0) (cur 1) (nxt 0) (nxt 1)) speed-kmh) 60.0))]
                      [(conj etas [s (/ (Math/round (* t 10.0)) 10.0)]) nxt (+ t service-min)]))
                  [[] depot (double start-min)] order))))

(defn clarke-wright
  "Capacitated VRP (Clarke-Wright savings) → list of capacity-feasible local-searched routes."
  [stops demand coords depot cap]
  (if (empty? stops)
    []
    (let [stops (vec stops)
          dij (fn [a b] (haversine-km ((coords a) 0) ((coords a) 1) ((coords b) 0) ((coords b) 1)))
          ddep (fn [a] (haversine-km (depot 0) (depot 1) ((coords a) 0) ((coords a) 1)))
          rload (fn [r] (reduce + 0 (map #(get demand % 0) r)))
          savings (sort-by first >
                           (for [i (range (count stops)) j (range (inc i) (count stops))
                                 :let [a (stops i) b (stops j)]]
                             [(- (+ (ddep a) (ddep b)) (dij a b)) a b]))
          routes-atom (atom (mapv vector stops))
          find-route (fn [x] (some (fn [r] (when (some #(= % x) r) r)) @routes-atom))]
      (doseq [[_ a b] savings]
        (let [ra (find-route a) rb (find-route b)]
          (when (and ra rb (not (identical? ra rb))
                     (or (= a (first ra)) (= a (last ra)))
                     (or (= b (first rb)) (= b (last rb)))
                     (<= (+ (rload ra) (rload rb)) cap))
            (let [ra (if (= (first ra) a) (vec (reverse ra)) ra)
                  rb (if (= (last rb) b) (vec (reverse rb)) rb)
                  merged (vec (concat ra rb))]
              (swap! routes-atom (fn [rs] (conj (vec (remove #(or (= % ra) (= % rb)) rs)) merged)))))))
      (mapv #(first (local-search % coords depot)) @routes-atom))))

;; ── small helpers ──────────────────────────────────────────────────────────────
(defn- q1 [query & args] (let [rows (apply dq query args)] (when (seq rows) (get-in rows [0 0]))))
(defn- attr [id-attr id-val a]
  (q1 (str "[:find ?v :in $ ?k :where [?e :" id-attr " ?k] [?e :" a " ?v]]") id-val))
(defn- to-int
  ([v] (to-int v 0))
  ([v default] (try (long (Double/parseDouble (str v))) (catch #?(:clj Exception :cljs :default) _ default))))

;; ── intake graph nodes ──────────────────────────────────────────────────────────
(defn classify-node [state]
  (let [acc (reduce (fn [a code]
                      (let [rows (dq "[:find ?h :in $ ?c :where [?e :item-category/code ?c] [?e :item-category/hazardous ?h]]" code)
                            hazardous (boolean (and (seq rows) (get-in rows [0 0])))]
                        (if hazardous (update a :rejected conj code) (update a :accepted conj code))))
                    {:accepted [] :rejected []} (get state "items"))]
    {"accepted_items" (:accepted acc) "rejected_items" (:rejected acc)}))

(defn quote-node [state]
  (if (nil? *datalog*)
    {"fee" 0}
    (let [juris (get state "jurisdiction" "") items (get state "accepted_items")
          model (str/replace-first (or (attr "jurisdiction/id" juris "jurisdiction/bulky-fee-model") "") #"^:+" "")]
      {"fee" (case model
               "free" 0
               "per-sticker" (* (count items) (to-int (attr "jurisdiction/id" juris "jurisdiction/fee-per-sticker")))
               "per-weight" (* (reduce + 0 (map #(to-int (attr "item-category/code" % "item-category/est-weight-kg")) items))
                               (to-int (attr "jurisdiction/id" juris "jurisdiction/fee-per-kg")))
               "flat" (to-int (attr "jurisdiction/id" juris "jurisdiction/fee-flat"))
               (reduce + 0 (map #(to-int (attr "item-category/code" % "item-category/base-fee")) items)))})))

(defn match-facility-node [state]
  (if (nil? *datalog*)
    {"facility" ""}
    (let [facs (dq (str "[:find ?id ?cap ?load :in $ ?j :where [?f :facility/jurisdiction ?j] "
                        "[?f :facility/id ?id] [?f :facility/capacity-tonnes-day ?cap] [?f :facility/load-tonnes-today ?load]]")
                   (get state "jurisdiction"))]
      (or (some (fn [[fid cap load]]
                  (let [accepts (dq "[:find ?cat :in $ ?id :where [?f :facility/id ?id] [?f :facility/accepted-categories ?cat]]" fid)
                        accepted-set (set (map first accepts))]
                    (when (and (> cap load) (every? #(contains? accepted-set %) (get state "accepted_items")))
                      {"facility" fid})))
                facs)
          {"facility" ""}))))

(defn schedule-node [state]
  (if (nil? *datalog*)
    {"scheduled_date" (get state "scheduled_date" "") "slot_id" ""}
    (let [area (attr "collection-point/id" (get state "collection_point") "collection-point/service-area")
          desired (or (get state "scheduled_date") "")
          rows (dq (str "[:find ?id ?date ?cap ?booked ?win :in $ ?j ?a :where "
                        "[?s :slot/jurisdiction ?j] [?s :slot/service-area ?a] [?s :slot/id ?id] "
                        "[?s :slot/date ?date] [?s :slot/capacity ?cap] [?s :slot/booked ?booked] [?s :slot/window ?win]]")
                   (get state "jurisdiction") area)
          winrank {":am" 0 "am" 0 ":allday" 0 "allday" 0 ":pm" 1 "pm" 1}
          cand (sort (for [[sid d cap b w] rows
                           :when (and (< (to-int b) (to-int cap)) (or (empty? desired) (>= (compare d desired) 0)))]
                       [d (get winrank w 2) sid (to-int b)]))]
      (if (empty? cand)
        {"scheduled_date" "" "slot_id" ""}
        (let [[d _ sid booked] (first cand)]
          (dtx [{":slot/id" sid ":slot/booked" (inc booked)}])
          {"scheduled_date" d "slot_id" sid})))))

(defn sticker-node [state]
  (if-not (seq (get state "consent_sig"))
    {"sticker_id" ""}
    (let [juris (str/upper-case (subs (last (str/split (get state "jurisdiction") #"\.")) 0 (min 3 (count (last (str/split (get state "jurisdiction") #"\."))))))
          date (str/replace (or (get state "scheduled_date") "") "-" "")
          sticker (str juris "-" date "-" (format "%05d" (mod (Math/abs (hash (get state "member_did"))) 100000)))]
      (when *datalog*
        (dtx [{":application/id" (str (get state "jurisdiction") ".app." date "-" sticker)
               ":application/member-did" (get state "member_did")
               ":application/jurisdiction" (get state "jurisdiction")
               ":application/items" (vec (get state "accepted_items"))
               ":application/collection-point" (get state "collection_point")
               ":application/scheduled-date" (get state "scheduled_date" "")
               ":application/fee" (get state "fee")
               ":application/sticker-id" sticker
               ":application/consent-sig" (get state "consent_sig")
               ":application/slot-id" (get state "slot_id" "")
               ":application/state" ":scheduled"}]))
      {"sticker_id" sticker})))

;; ── dispatch graph nodes ────────────────────────────────────────────────────────
(defn gather-node [state]
  (if (nil? *datalog*)
    {"applications" []}
    (let [rows (dq (str "[:find ?id ?cp :in $ ?j ?d :where [?a :application/jurisdiction ?j] "
                        "[?a :application/scheduled-date ?d] [?a :application/id ?id] [?a :application/collection-point ?cp]]")
                   (get state "jurisdiction") (get state "date"))]
      {"applications" (mapv (fn [[id cp]] {"app_id" id "collection_point" cp}) rows)})))

(defn assign-vehicle-node [state]
  (if (nil? *datalog*)
    {"vehicle" ""}
    (let [vehs (dq (str "[:find ?id ?cap :in $ ?j :where [?v :vehicle/jurisdiction ?j] "
                        "[?v :vehicle/status :available] [?v :vehicle/id ?id] [?v :vehicle/capacity-kg ?cap]]")
                   (get state "jurisdiction"))
          feasible (sort (for [[vid c] vehs :when (>= c (get state "load_kg"))] [c vid]))]
      {"vehicle" (if (seq feasible) (second (first feasible)) "")})))

(defn assign-crew-node [state]
  (if (nil? *datalog*)
    {"crew" []}
    (let [crew (dq (str "[:find ?id ?role :in $ ?j :where [?c :crew/jurisdiction ?j] "
                        "[?c :crew/shift :early] [?c :crew/id ?id] [?c :crew/role ?role]]")
                   (get state "jurisdiction"))
          drivers (vec (for [[id role] crew :when (contains? #{":driver" "driver"} role)] id))
          loaders (vec (for [[id role] crew :when (contains? #{":loader" "loader"} role)] id))]
      {"crew" (if (seq drivers) (vec (concat (take 1 drivers) (take 2 loaders))) (vec (take 2 loaders)))})))

(defn optimize-route-node [state]
  (let [coords (get state "coords") points (vec (keys coords))]
    (if (empty? points)
      {"stop_order" [] "distance_km" 0.0}
      (let [start (if (and *datalog* (seq (get state "vehicle")))
                    (let [d (dq "[:find ?lat ?lon :in $ ?v :where [?x :vehicle/id ?v] [?x :vehicle/depot-lat ?lat] [?x :vehicle/depot-lon ?lon]]" (get state "vehicle"))]
                      (if (seq d) [(get-in d [0 0]) (get-in d [0 1])] [35.66 139.70]))
                    [35.66 139.70])
            nn (nearest-neighbour points coords start)
            [order length] (two-opt nn coords start)]
        {"stop_order" order "distance_km" (/ (Math/round (* length 100.0)) 100.0)}))))

(defn select-facility-node [state]
  (if (nil? *datalog*)
    {"facility" ""}
    (let [facs (dq (str "[:find ?id ?cap ?load :in $ ?j :where [?f :facility/jurisdiction ?j] "
                        "[?f :facility/id ?id] [?f :facility/capacity-tonnes-day ?cap] [?f :facility/load-tonnes-today ?load]]")
                   (get state "jurisdiction"))
          spare (vec (for [[fid cap load] facs :when (> cap load)] fid))]
      {"facility" (if (seq spare) (first spare) "")})))

(defn cluster-node [state]
  (let [acc (reduce (fn [a app]
                      (let [cp (get app "collection_point")
                            rows (dq "[:find ?lat ?lon :in $ ?cp :where [?p :collection-point/id ?cp] [?p :collection-point/lat ?lat] [?p :collection-point/lon ?lon]]" cp)
                            a (if (seq rows) (assoc-in a [:coords cp] [(get-in rows [0 0]) (get-in rows [0 1])]) a)
                            items (dq (str "[:find ?w :in $ ?aid :where [?a :application/id ?aid] [?a :application/items ?c] "
                                           "[?e :item-category/code ?c] [?e :item-category/est-weight-kg ?w]]") (get app "app_id"))
                            kg (long (reduce + 0 (map first items)))
                            a (-> a (update-in [:demand cp] (fnil + 0) kg) (update :load + kg))
                            srow (dq (str "[:find ?win ?ws ?we :in $ ?aid :where [?a :application/id ?aid] [?a :application/slot-id ?sid] "
                                          "[?s :slot/id ?sid] [?s :slot/window ?win] [?s :slot/window-start ?ws] [?s :slot/window-end ?we]]") (get app "app_id"))]
                        (if (seq srow)
                          (assoc-in a [:window-of cp] {"window" (str (get-in srow [0 0])) "start" (long (get-in srow [0 1])) "end" (long (get-in srow [0 2]))})
                          a)))
                    {:coords {} :demand {} :window-of {} :load 0}
                    (if *datalog* (get state "applications") []))]
    {"coords" (:coords acc) "demand" (:demand acc) "window_of" (:window-of acc) "load_kg" (:load acc)}))

(defn build-routes-node [state]
  (let [coords (get state "coords") demand (get state "demand" {}) stops (vec (keys coords))]
    (if (empty? stops)
      {"routes" [] "unassigned" []}
      (let [vehs (when *datalog*
                   (sort (for [[vid c] (dq (str "[:find ?id ?cap :in $ ?j :where [?v :vehicle/jurisdiction ?j] "
                                                "[?v :vehicle/status :available] [?v :vehicle/id ?id] [?v :vehicle/capacity-kg ?cap]]") (get state "jurisdiction"))]
                           [(long c) vid])))
            depot (if (and (seq vehs) *datalog*)
                    (let [d (dq "[:find ?lat ?lon :in $ ?v :where [?x :vehicle/id ?v] [?x :vehicle/depot-lat ?lat] [?x :vehicle/depot-lon ?lon]]" (second (first vehs)))]
                      (if (seq d) [(get-in d [0 0]) (get-in d [0 1])] [35.66 139.70]))
                    [35.66 139.70])
            facility (when *datalog*
                       (let [facs (dq (str "[:find ?id ?cap ?load :in $ ?j :where [?f :facility/jurisdiction ?j] "
                                           "[?f :facility/id ?id] [?f :facility/capacity-tonnes-day ?cap] [?f :facility/load-tonnes-today ?load]]") (get state "jurisdiction"))
                             spare (vec (for [[fid cap load] facs :when (> cap load)] fid))]
                         (if (seq spare) (first spare) "")))
            facility (or facility "")
            crew (when *datalog*
                   (dq (str "[:find ?id ?role :in $ ?j :where [?c :crew/jurisdiction ?j] [?c :crew/shift :early] "
                            "[?c :crew/id ?id] [?c :crew/role ?role]]") (get state "jurisdiction")))
            drivers (atom (vec (for [[id role] crew :when (contains? #{":driver" "driver"} role)] id)))
            loaders (atom (vec (for [[id role] crew :when (contains? #{":loader" "loader"} role)] id)))
            cap (if (seq vehs) (first (last vehs)) 4000)
            window-of (get state "window_of" {})
            speed 20.0 service 10
            groups (reduce (fn [g s]
                             (let [w (get window-of s {"window" "allday" "start" 480 "end" 1020})
                                   k [(get w "window") (get w "start") (get w "end")]]
                               (update g k (fnil conj []) s)))
                           {} stops)
            pool (atom (mapv (fn [[c v]] {:cap c :vid v :free-at Double/NEGATIVE_INFINITY}) vehs))
            result (atom {:routes [] :unassigned []})]
        (doseq [[[win w-start w-end] gstops] (sort-by (fn [[k _]] (k 1)) groups)]
          (doseq [order (clarke-wright gstops demand coords depot cap)]
            (let [load (reduce + 0 (map #(get demand % 0) order))
                  cand (some (fn [p] (when (and (>= (:cap p) load) (<= (:free-at p) w-start)) p)) @pool)]
              (if (nil? cand)
                (swap! result update :unassigned conj
                       {"stop_order" order "load_kg" (long load) "window" win
                        "reason" "no vehicle free (capacity ≥ load AND back by window start) — G15"})
                (let [reused (> (:free-at cand) Double/NEGATIVE_INFINITY)
                      etas (route-eta order coords depot w-start speed service)
                      return-min (if (seq order)
                                   (* (/ (haversine-km ((coords (last order)) 0) ((coords (last order)) 1) (depot 0) (depot 1)) speed) 60.0)
                                   0.0)
                      new-free (+ (if (seq etas) (second (last etas)) w-start) service return-min)
                      crewset (vec (concat (when (seq @drivers) (let [d (first @drivers)] (swap! drivers subvec 1) [d]))
                                           (repeatedly (min 2 (count @loaders)) (fn [] (let [l (first @loaders)] (swap! loaders subvec 1) l)))))
                      tw-violations (vec (for [[s e] etas :when (> e w-end)] {"stop" s "eta_min" e}))]
                  (swap! pool (fn [ps] (mapv (fn [p] (if (identical? p cand) (assoc p :free-at new-free) p)) ps)))
                  (swap! result update :routes conj
                         {"vehicle" (:vid cand) "stop_order" order "load_kg" (long load)
                          "distance_km" (/ (Math/round (* (route-length order coords depot) 100.0)) 100.0)
                          "facility" facility "crew" crewset "window" win "window_start" w-start "window_end" w-end
                          "etas" etas "tw_violations" tw-violations "vehicle_reused" reused}))))))
        {"routes" (:routes @result) "unassigned" (:unassigned @result)}))))

(defn emit-plan-node [state]
  (let [area (get state "service_area" "all")
        date-compact (str/replace (get state "date") "-" "")
        written (vec (map-indexed
                      (fn [idx r]
                        (let [n (inc idx)
                              rid (str (get state "jurisdiction") ".route." date-compact "-" area "-" (format "%02d" n))]
                          (when (and *datalog* (seq (get r "vehicle")))
                            (dtx [{":route/id" rid ":route/jurisdiction" (get state "jurisdiction") ":route/date" (get state "date")
                                   ":route/vehicle" (get r "vehicle") ":route/crew" (vec (get r "crew")) ":route/stops" (vec (get r "stop_order"))
                                   ":route/stop-order" (json/generate-string (get r "stop_order"))
                                   ":route/facility-destination" (get r "facility") ":route/distance-km" (get r "distance_km")
                                   ":route/load-kg" (long (get r "load_kg")) ":route/window" (str ":" (get r "window" "allday"))
                                   ":route/state" ":planned"}]))
                          (assoc r "route_id" rid "state" ":planned")))
                      (get state "routes" [])))]
    {"plan" {"routes" written "unassigned" (get state "unassigned" [])}}))
