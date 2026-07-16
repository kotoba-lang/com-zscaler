(ns mimamori.tests.test-bond
  "mimamori gate tests — every NEVER clause is asserted as a thrown gate violation.
  Port of tests/test_bond.py."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mimamori.methods.bond :as bond]))

(def A "did:web:etzhayyim.com:member:fictional:aleph")
(def B "did:web:etzhayyim.com:member:fictional:bet")
(def C "did:web:etzhayyim.com:member:fictional:gimel")

(defn- seed []
  (bond/load-seed-file (io/resource "mimamori/data/seed-mimamori-bonds.json")))

(defmacro gate-thrown?
  "Asserts a gate violation whose message contains `frag`."
  [frag & body]
  `(let [e# (try ~@body nil (catch clojure.lang.ExceptionInfo e# e#))]
     (is (some? e#) (str "expected GateViolation containing '" ~frag "'"))
     (when e#
       (is (bond/gate-violation? e#))
       (is (str/includes? (ex-message e#) ~frag)
           (str "expected '" ~frag "' in '" (ex-message e#) "'")))))

(deftest lifecycle
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/consent A B) (bond/heartbeat A B))]
    (is (some (fn [[_e a v _t _o]] (and (= a ":mishmeret.keep/act") (= v ":reached-out")))
              (:datoms m)))))

(deftest no-keep-without-consent ;; G3
  (let [m (-> (bond/new-engine) (bond/offer A B))]
    (gate-thrown? "G3" (bond/heartbeat m A B))))

(deftest decline-and-unilateral-exit ;; G3
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/decline A B))]
    (gate-thrown? "cooldown" (bond/offer m A B)) ;; anti-pestering
    (let [m (-> m (bond/offer A C) (bond/consent A C) (bond/exit-bond A C))]
      ;; unconditional, no penalty — just succeeds
      (is (= ":exited" (:state (first (bond/bonds-of m C))))))))

(deftest g1-care-whitelist-only
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/consent A B))]
    (is (map? (bond/route-care m A B ":kokoro" true))) ;; ok
    (gate-thrown? "G1" (bond/route-care m A B ":police" true))
    (gate-thrown? "G1" (bond/route-care m A B ":authority" true))
    (gate-thrown? "G1" (bond/route-care m A B ":council" true))))

(deftest g1b-route-needs-per-act-consent ;; G3
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/consent A B))]
    (gate-thrown? "G3" (bond/route-care m A B ":kokoro" false))))

(deftest g2-no-score-of-soul
  (gate-thrown? "G2" (bond/validate-attr ":mishmeret.person/risk-score"))
  (gate-thrown? "G2" (bond/validate-attr ":mishmeret.person/anything"))
  (gate-thrown? "G2" (bond/validate-attr ":mishmeret.bond/isolation-index"))
  (gate-thrown? "G2" (bond/validate-attr ":mishmeret.bond/danger-rating")))

(deftest g4-symmetric-visibility
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/consent A B))
        kept-view (bond/bonds-of m B)    ;; the kept always sees who keeps them
        keeper-view (bond/bonds-of m A)]
    (is (and (seq kept-view) (= A (:keeper (first kept-view)))))
    (is (and (seq keeper-view) (= B (:kept (first keeper-view)))))
    (is (= [] (bond/bonds-of m C)))))    ;; a non-party sees nothing (G5: own-DID-only)

(deftest g5-relay-no-sleepless-center
  (let [m (-> (bond/new-engine) (bond/offer A B) (bond/consent A B) (bond/handoff A B C))
        states (set (map :state (bond/bonds-of m B)))]
    ;; G3: the relay does NOT carry consent — the incoming bond is only :offered
    (is (contains? states ":handed-off"))
    (is (contains? states ":offered"))
    (gate-thrown? "G3" (bond/heartbeat m C B))   ;; no keeping before consent
    (let [m (-> m (bond/consent C B) (bond/heartbeat C B))] ;; the kept consents to C explicitly
      (is (map? m)))))                            ;; now the relay keeper keeps

(deftest g7-synthetic-only
  (gate-thrown? "G7" (bond/offer (bond/new-engine)
                                 "did:web:etzhayyim.com:member:real-person" B)))

(deftest append-only-and-determinism
  (let [s (seed)
        m1 (bond/replay s)
        m2 (bond/replay s)]
    (is (= (bond/emit m1) (bond/emit m2)))                    ;; deterministic
    (is (every? (fn [[_e _a _v _t op]] (= op ":add")) (:datoms m1))) ;; :add only
    (let [n (count (:datoms m1))
          m1' (bond/exit-bond m1
                              "did:web:etzhayyim.com:member:fictional:vav"
                              "did:web:etzhayyim.com:member:fictional:he")]
      (is (= (inc n) (count (:datoms m1')))))))               ;; exit appends, never removes
