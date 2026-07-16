(ns fuchi.methods.test-provision
  "Tests for 扶持 (fuchi) provision.cljc — 1:1 port of methods/test_provision.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.provision :as p]
            [fuchi.methods.route :as route]))

(defn- env [line imputed]
  {":envelope/line" (str ":" line) ":envelope/imputed-usd-micros-yr" imputed
   ":envelope/cash-usd-micros" 0})

(deftest test-every-rail-has-a-provider
  (let [rails (route/route-envelope (mapv #(env % 1000000)
                                          ["housing" "food" "energy" "compute" "tooling" "care" "liquidity"]))
        intents (p/provision rails "alloc-1")
        providers (set (map :provider-did intents))]
    (is (= (count intents) 7))
    (is (contains? providers "did:web:etzhayyim.com:actor:mitsuho"))
    (is (and (contains? providers "commons-land") (contains? providers "murakumo")))))

(deftest test-provider-kinds-are-classified
  (let [rails (route/route-envelope [(env "housing" 1) (env "food" 1) (env "compute" 1)])
        by (into {} (map (fn [i] [(:rail-kind i) i]) (p/provision rails "a")))]
    (is (= (:provider-kind (get by "housing-commons")) "commons"))
    (is (= (:provider-kind (get by "food-mitsuho")) "actor"))
    (is (= (:provider-kind (get by "compute-murakumo")) "infra"))))

(deftest test-liquidity-is-member-principal
  (let [rails (route/route-envelope [(env "liquidity" 5) (env "food" 5)])
        by (into {} (map (fn [i] [(:rail-kind i) i]) (p/provision rails "a")))]
    (is (true? (:member-principal (get by "liquidity-warifu"))))
    (is (clojure.string/ends-with? (:provider-did (get by "liquidity-warifu")) "warifu"))
    (is (false? (:member-principal (get by "food-mitsuho"))))))

(deftest test-intent-is-dry-run-cashless-keyless
  (doseq [i (p/provision (route/route-envelope [(env "food" 1)]) "a")]
    (is (= (:cash-usd-micros i) 0))
    (is (false? (:server-held-key i)))
    (is (false? (:published i)))))

(deftest test-published-true-is-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G10"
                        (p/make-provisioning-intent {:alloc-id "a" :rail-kind "food-mitsuho"
                                                     :provider-did "did:..:mitsuho" :provider-kind "actor"
                                                     :imputed-usd-micros-yr 1 :published true}))))

(deftest test-cash-intent-is-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"(?i)cash"
                        (p/make-provisioning-intent {:alloc-id "a" :rail-kind "food-mitsuho"
                                                     :provider-did "did:..:mitsuho" :provider-kind "actor"
                                                     :imputed-usd-micros-yr 1 :cash-usd-micros 5}))))

(deftest test-server-key-intent-is-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-server-key"
                        (p/make-provisioning-intent {:alloc-id "a" :rail-kind "food-mitsuho"
                                                     :provider-did "did:..:mitsuho" :provider-kind "actor"
                                                     :imputed-usd-micros-yr 1 :server-held-key true}))))

(deftest test-registry-covers-every-route-rail
  (let [rail-kinds (set (map first (vals route/LINE-TO-RAIL)))]
    (is (= (set (keys p/PROVIDER-REGISTRY)) rail-kinds))))
