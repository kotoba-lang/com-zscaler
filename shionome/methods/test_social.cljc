(ns shionome.methods.test-social
  "Cross-language oracle tests for shionome.methods.social — the Clojure port of
  methods/social.py.

  Ported 1:1 from the REAL Python test_social.py (the cross-language oracle):
  same inline fixtures, same invariant assertions (dry-run / mirror / no-trade /
  server-held-key false), the same G2 no-trade body scan (English + Japanese
  tokens refused, the disclaimer exempt), the same G3 / Rider source guards, and
  the G8 build-live refusal."
  (:require [clojure.test :refer [deftest is testing]]
            [shionome.methods.social :as social]))

(def SRC ["https://fred.stlouisfed.org/" "https://www.ici.org/research"])
(def NET [{"bucket" "us-equities" "label" "US equities" "net" 13.1 "inflow" 23.2 "outflow" 10.1}
          {"bucket" "us-govt-bonds" "label" "US Treasuries" "net" -21.7 "inflow" 0.0 "outflow" 21.7}])
(def ROT [{"from" "us-govt-bonds" "from_label" "US Treasuries" "to" "us-equities"
           "to_label" "US equities" "magnitude" 12.4}])
(def REGIME {"regime" "risk-on" "risk_net" 27.3 "safe_net" -26.6 "no_trade_notice" true})

(deftest netflow-post-pins-invariants
  (let [p (social/draft-netflow-post NET SRC)]
    (is (= ":dry-run" (get p ":post/status")))
    (is (= true (get p ":post/is-mirror")))
    (is (= true (get p ":post/no-trade-notice")))
    (is (= false (get p ":post/server-held-key")))))

(deftest netflow-post-body-has-disclaimer
  (is (clojure.string/includes? (get (social/draft-netflow-post NET SRC) ":post/body")
                                "トレードはしない")))

(deftest rotation-post-ok
  (let [p (social/draft-rotation-post ROT SRC)]
    (is (clojure.string/includes? (get p ":post/body") "US Treasuries"))
    (is (= ":dry-run" (get p ":post/status")))))

(deftest regime-post-states-descriptor
  (let [p (social/draft-regime-post REGIME SRC)]
    (is (clojure.string/includes? (get p ":post/body") "risk-on"))
    (is (clojure.string/includes? (get p ":post/body") "助言ではありません"))))

(deftest post-requires-two-sources-g3
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"G3"
                        (social/draft-netflow-post NET ["only-one"]))))

(deftest post-refuses-denied-source
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"Rider"
                        (social/draft-netflow-post NET ["bloomberg terminal" "x"]))))

(deftest no-trade-guard-blocks-buy
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"G2"
                        (social/guard-no-trade "you should buy equities"))))

(deftest no-trade-guard-blocks-japanese
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"G2"
                        (social/guard-no-trade "目標株価は5000円"))))

(deftest no-trade-guard-allows-disclaimer
  (testing "the disclaimer NAMES the prohibited acts; it must not trip the guard"
    (is (nil? (social/guard-no-trade social/DISCLAIMER)))))

(deftest build-live-refuses-g8
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"G8"
                        (social/build-live))))

(deftest empty-rotation-post-safe
  (is (= ":dry-run" (get (social/draft-rotation-post [] SRC) ":post/status"))))
