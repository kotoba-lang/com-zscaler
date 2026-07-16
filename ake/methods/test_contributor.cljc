(ns ake.methods.test-contributor
  "test_contributor.cljc — 朱 (ake) anti-vandalism rate + recoverable Wellbecoming trajectory (G9).
  1:1 Clojure port of `methods/test_contributor.py` (clojure.test). Every Python assertion ported."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [ake.methods.contributor :as contrib]))

;; round(2/3, 4) reference (HALF_EVEN over the exact double, same as the Python round())
(def ^:private two-thirds-4
  #?(:clj (-> (java.math.BigDecimal. (double (/ 2 3)))
              (.setScale 4 java.math.RoundingMode/HALF_EVEN) (.doubleValue))
     :cljs (/ (js/Math.round (* (/ 2 3) 10000)) 10000)))

(deftest test-record-is-append-only-and-non-mutating
  (let [t0 (contrib/empty*)
        t1 (contrib/record t0 "did:m:a" "accepted" 100)]
    (is (= {} t0))
    (is (= {"accepted" 1 "refused" 0} (contrib/counts t1 "did:m:a")))
    (let [t2 (contrib/record t1 "did:m:a" "refused" 200)]
      (is (= 0 (get (contrib/counts t1 "did:m:a") "refused")))   ;; t1 untouched
      (is (= {"accepted" 1 "refused" 1} (contrib/counts t2 "did:m:a"))))))

(deftest test-record-rejects-bad-outcome
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (contrib/record (contrib/empty*) "did:m:a" "maybe" 1))))

(deftest test-acceptance-rate
  (let [t0 (contrib/empty*)]
    (is (nil? (contrib/acceptance-rate t0 "did:m:a")))            ;; no events
    (let [t (reduce (fn [t o] (contrib/record t "did:m:a" o 1))
                    t0 ["accepted" "accepted" "refused"])]
      (is (= two-thirds-4 (contrib/acceptance-rate t "did:m:a"))))))

(deftest test-rate-limit-blocks-a-flood
  (let [t (reduce (fn [t i] (contrib/record t "did:m:flood" "accepted" (+ 1000 i)))
                  (contrib/empty*) (range contrib/RATE-MAX-IN-WINDOW))]
    ;; the ceiling is reached within the window → next is blocked
    (is (not (contrib/rate-ok t "did:m:flood" (+ 1000 contrib/RATE-MAX-IN-WINDOW))))
    ;; but far in the future the window has slid past → allowed again
    (is (contrib/rate-ok t "did:m:flood" (+ 1000 contrib/RATE-WINDOW 10000)))))

(deftest test-throttle-only-on-an-unbroken-refusal-run
  (let [t (reduce (fn [t i] (contrib/record t "did:m:vandal" "refused" (+ 100 i)))
                  (contrib/empty*) (range contrib/THROTTLE-REFUSED-RUN))]
    (is (contrib/throttled? t "did:m:vandal"))))

(deftest test-throttle-is-recoverable
  (let [t (reduce (fn [t i] (contrib/record t "did:m:x" "refused" (+ 100 i)))
                  (contrib/empty*) (range contrib/THROTTLE-REFUSED-RUN))]
    (is (contrib/throttled? t "did:m:x"))
    ;; …but a single subsequent accepted edit breaks the run → un-throttled (no score-of-soul)
    (let [t (contrib/record t "did:m:x" "accepted" 999)]
      (is (not (contrib/throttled? t "did:m:x"))))))

(deftest test-new-contributor-is-never-throttled
  (let [t (contrib/record (contrib/empty*) "did:m:new" "refused" 1)]
    (is (not (contrib/throttled? t "did:m:new")))))             ;; too few events to judge

(deftest test-trajectory-view-shape
  (let [t (contrib/record (contrib/empty*) "did:m:a" "accepted" 1)
        v (contrib/trajectory t "did:m:a")]
    (is (= "did:m:a" (get v "did")))
    (is (= 1 (get v "accepted")))
    (is (false? (get v "throttled")))
    (is (contains? v "acceptanceRate"))
    (is (contains? v "events"))))

;; ── G9 structural: per-DID, NEVER a cross-contributor ranking (no score-of-soul) ──
(deftest test-contributors-are-isolated-no-cross-did-leak
  (let [t (reduce (fn [t i] (contrib/record t "did:m:vandal" "refused" (+ 100 i)))
                  (contrib/empty*) (range contrib/THROTTLE-REFUSED-RUN))
        t (contrib/record t "did:m:saint" "accepted" 200)]
    (is (contrib/throttled? t "did:m:vandal"))
    (is (not (contrib/throttled? t "did:m:saint")))
    (is (= {"accepted" 1 "refused" 0} (contrib/counts t "did:m:saint")))
    (is (false? (get (contrib/trajectory t "did:m:saint") "throttled")))))

(deftest test-no-ranking-or-score-of-soul-api-exists
  ;; G9 forbids a minted reputation number or a contributor ranking. Lock the surface:
  ;; no public var name may imply ordering/comparison/scoring of contributors against each other.
  (let [forbidden ["rank" "leaderboard" "compare" "score" "reputation" "best" "worst" "top"]
        public (->> (ns-publics 'ake.methods.contributor)
                    keys (map (comp str/lower-case name)))
        leaks (for [n public f forbidden :when (str/includes? n f)] n)]
    (is (empty? leaks) (str "G9: ranking/score-of-soul-shaped API leaked: " (vec leaks)))))

(deftest test-events-are-returned-in-as-of-order-regardless-of-record-order
  (let [t (reduce (fn [t [o ts]] (contrib/record t "did:m:a" o ts))
                  (contrib/empty*) [["accepted" 300] ["refused" 100] ["accepted" 200]])
        order (mapv #(get % "as_of") (contrib/events t "did:m:a"))]
    (is (= [100 200 300] order))))

(deftest test-throttle-recovers-with-a-recent-accept-amid-later-refusals
  ;; an accept anywhere inside the recent window breaks the run — even if more refusals follow.
  (let [seq* (concat (repeat 3 "refused") ["accepted"] (repeat 3 "refused"))
        t (reduce (fn [t [i o]] (contrib/record t "did:m:x" o (+ 100 i)))
                  (contrib/empty*) (map-indexed vector seq*))]
    (is (not (contrib/throttled? t "did:m:x")))))

(deftest test-rate-window-lower-edge-is-exclusive
  ;; an event exactly at (now - window) has aged OUT of the sliding window (strict `>`).
  (let [t (reduce (fn [t i] (contrib/record t "did:m:edge" "accepted" (+ 1000 i)))
                  (contrib/empty*) (range contrib/RATE-MAX-IN-WINDOW))
        now (+ 1000 contrib/RATE-WINDOW)]   ;; oldest event (t=1000) now exactly at the edge
    (is (contrib/rate-ok t "did:m:edge" now))))   ;; edge event excluded → under ceiling again

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-contributor)))
