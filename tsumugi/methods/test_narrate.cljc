(ns tsumugi.methods.test-narrate
  "Cross-language oracle tests for tsumugi.methods.narrate — the Murakumo-only narration
  PURE core (ADR-2606092000 + 2605215000). Ported 1:1 from the REAL Python narrate tests.

  Pins: (A) locality/broker/vertical + (B) camp/bridge prompt text; G6 — an external host RAISES
  even in dry-run, the fleet host with gate=false is a clean (nil,\"dry-run\"); digest+datoms are
  valid kotoba-EDN with published=false (G7) and no per-person entity (S2)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [tsumugi.methods.narrate :as N]
            [tsumugi.methods.analyze-scale :as asc]))

;; synthetic aggregate readouts (string-keyed, mirroring analyze_scale/analyze_banner dict shapes)
(def scale
  {"localities" [{"locality" "jp.nagasaki" "sectors" ["産" "官" "学" "報"]
                  "sector_diversity" 4 "concentration" 12.08}
                 {"locality" "jp.toyota" "sectors" ["産" "官"]
                  "sector_diversity" 2 "concentration" 5.0}]
   "collective_kinds" [{"kind" ":region" "ja" "地域" "node_count" 7 "load" 3.2}]
   "brokers" [{"id" "org.mhi.nagasaki" "sector" "産" "bridges_to" ["官" "学" "報"] "span" 3}]
   "vertical" [{"label" "Toyota" "root" "org.toyota" "scale_span" 3
                "scales" [":national" ":regional" ":municipal"]
                "locality_span" 2 "load" 9.1}]})
(def banner
  {"camps" [{"banner" ":progressive" "label" "Progressive camp" "kind" ":policy"
             "reach" 5 "member_count" 3}]
   "bridges" [{"ent" "org.x" "label" "Bridge Org" "banners" [":a" ":b"] "span" 2}]})

(deftest prompt-carries-aggregate-intel
  (let [p (N/build-prompt scale banner)]
    (is (str/includes? p "jp.nagasaki"))                       ; (A) locality
    (is (str/includes? p "産官学報"))
    (is (str/includes? p "- jp.nagasaki: sectors 産 官 学 報 (diversity 4, concentration 12.08)"))
    (is (str/includes? p "(A) granularity (粒度) — incident load by collective-kind:"))
    (is (str/includes? p "- 地域: 7 nodes, load 3.2"))
    (is (str/includes? p "- org.mhi.nagasaki (産 → 官 学 報, span 3)"))
    (is (str/includes? p "vertically-integrated"))
    (is (str/includes? p "- Toyota: spans 3 scales (:national :regional :municipal) across 2 localities (load 9.1)"))
    (is (str/includes? p "reach"))                             ; (B) camp
    (is (str/includes? p "- Progressive camp (kind :policy, reach 5, 3 members)"))
    (is (str/includes? p "bridges"))
    (is (str/includes? p "- Bridge Org: :a · :b"))))

(deftest prompt-aggregate-only-and-non-adjudicating
  (let [p (N/build-prompt scale banner)]
    ;; S2 — no private-person marker
    (is (not (str/includes? p ":private-person")))
    (is (not (str/includes? p "private-person")))
    ;; non-adjudicating — no verdict token in the USER prompt
    (doseq [v ["corruption" "collusion" "癒着" "談合" "extremist" "過激"]]
      (is (not (str/includes? p v)) (str "verdict token leaked: " v)))))

(deftest determinism-identical-prompt
  (is (= (N/build-prompt scale banner) (N/build-prompt scale banner))))

(deftest digest-is-valid-kotoba-edn
  (let [d (N/build-digest scale banner)
        parsed (asc/read-edn d)]
    (is (vector? parsed))
    (is (seq parsed))
    (is (= ":tsumugi-power-intel" (get (first parsed) ":digest/kind")))
    (is (= false (get (first parsed) ":digest/published")))   ; G7
    (is (contains? (first parsed) ":digest/scale-top-localities"))
    (is (contains? (first parsed) ":digest/banner-camps"))
    (is (contains? (first parsed) ":digest/vertical"))))

(deftest datoms-are-valid-eavt-entity-maps
  (let [dt (N/build-datoms scale banner)
        parsed (asc/read-edn dt)]
    (is (seq parsed))
    (is (every? #(contains? % ":db/id") parsed))
    ;; mirror + published=false present
    (is (some #(= false (get % ":tsumugi/published")) parsed))
    ;; S2 — only cluster/vertical/camp entities, never a per-person id
    (is (every? (fn [d] (let [id (get d ":db/id")]
                          (or (str/starts-with? id "tsumugi.cluster/")
                              (str/starts-with? id "tsumugi.vertical/")
                              (str/starts-with? id "tsumugi.camp/")))) parsed))))

(deftest g6-external-host-raises
  ;; external inference host must RAISE even in dry-run (Murakumo-only)
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G6"
                        (N/infer "x" {:gate false :base-url "https://api.openai.com"})))
  ;; also raises with no gate kwarg (default dry-run) against external host
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Murakumo-only"
                        (N/infer "x" {:base-url "https://api.openai.com"}))))

(deftest g6-fleet-host-dry-run
  ;; fleet host, gate=false → clean dry-run, no network
  (is (= [nil "dry-run"] (N/infer "x" {:gate false :base-url "http://127.0.0.1:4000"})))
  ;; the LAN fleet node is also allowed
  (is (= [nil "dry-run"] (N/infer "x" {:base-url "http://192.168.1.70:4000"}))))

(deftest assert-murakumo-host-parsing
  ;; loopback + localhost + bracketed IPv6 all pass
  (is (nil? (N/assert-murakumo "http://127.0.0.1:4000")))
  (is (nil? (N/assert-murakumo "http://localhost:4000")))
  (is (nil? (N/assert-murakumo "http://[::1]:4000")))
  ;; external provider raises
  (is (thrown? clojure.lang.ExceptionInfo (N/assert-murakumo "https://api.anthropic.com"))))
