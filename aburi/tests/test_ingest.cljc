(ns aburi.tests.test-ingest
  "aburi 炙り — ingest pure-logic tests (cljc, ADR-2606161630).
  Covers: G2/G8 guard, adapter smoke, dedup edge logic, export_to_datoms round-trip.
  No network I/O. All assertions offline."
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [clojure.string :as str]
            [aburi.methods.ingest :as ingest]))

;; ─── test data ────────────────────────────────────────────────────────────────

(def play-doc
  {"apps"
   [{"app" "FreeGame"
     "shared"
     [{"type" "Location"       "purpose" "Advertising or marketing" "collectors" ["AppLovin"]}
      {"type" "Device or other IDs" "purpose" "Advertising or marketing" "collectors" ["AdMob"]}]}]})

(def apple-doc
  {"apps"
   [{"app" "SocialApp"
     "accessed" ["Location" "Identifiers"]
     "domains"  ["graph.facebook.com" "doubleclick.net"]}]})

(def takeout-doc
  {"adPersonalization" true
   "activityControls" {"web" true "location" true "youtube" false}})

(def perm-doc
  {"apps"
   [{"package" "com.example.game"
     "grants"  ["ACCESS_FINE_LOCATION" "AD_ID"]}]})

;; ─── G2/G8 guard ──────────────────────────────────────────────────────────────

(deftest test-guard-pass-on-clean-doc
  (testing "guard does not throw on clean export docs"
    (is (nil? (ingest/guard play-doc)))
    (is (nil? (ingest/guard apple-doc)))
    (is (nil? (ingest/guard takeout-doc)))
    (is (nil? (ingest/guard perm-doc)))))

(deftest test-guard-blocks-credential-key
  (testing "guard throws on credential-shaped keys"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/guard {"password" "hunter2" "apps" []})))))

(deftest test-guard-blocks-token-key
  (testing "guard throws on token key"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/guard {"bearer-token" "abc123"})))))

(deftest test-guard-blocks-pan-value
  (testing "guard throws on 13-digit PAN"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/guard {"type" "Location" "raw" "4111111111111111"})))))

(deftest test-guard-blocks-adid-uuid
  (testing "guard throws on UUID-shaped advertising-id value"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/guard {"id" "38400000-8cf0-11bd-b23e-10b96e40000d"})))))

(deftest test-guard-blocks-email-value
  (testing "guard throws on email-shaped value"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/guard {"contact" "user@example.com"})))))

;; ─── load-graph split ─────────────────────────────────────────────────────────

(deftest test-load-graph-splits
  (testing "load-graph correctly splits nodes and edges"
    (let [forms [{":organism/id" "n1" ":organism/label" "Test"}
                 {":en/from" "n1" ":en/to" "n2" ":en/kind" ":grants" ":en/load" 0.7}]
          {:keys [nodes edges]} (ingest/load-graph forms)]
      (is (= 1 (count nodes)))
      (is (= 1 (count edges)))
      (is (= "n1" (get (get nodes "n1") ":organism/id"))))))

;; ─── adapter: play-data-safety ────────────────────────────────────────────────

(deftest test-adapt-play-basic
  (testing "adapt-play-data-safety produces nodes + edges"
    (let [forms (ingest/adapt-play-data-safety play-doc)
          {:keys [nodes edges]} (ingest/load-graph forms)]
      (is (pos? (count nodes)) "should produce nodes")
      (is (pos? (count edges)) "should produce edges")
      ;; surface node present
      (is (contains? nodes "ax.surface.google-android"))
      ;; permission nodes from Location + Device or other IDs
      (is (contains? nodes "ax.perm.coarse-location"))
      (is (contains? nodes "ax.perm.ad-id"))
      ;; applovin collector
      (is (contains? nodes "ax.col.applovin"))
      ;; admob collector from "AdMob"
      (is (contains? nodes "ax.col.admob")))))

(deftest test-adapt-play-edge-dedup-takes-max
  (testing "duplicate edges take max load"
    ;; Two apps both share Location for advertising → same (surface, perm, :grants) edge deduped
    (let [doc {"apps"
               [{"app" "A" "shared" [{"type" "Location" "purpose" "Advertising or marketing"
                                       "collectors" ["AppLovin"]}]}
                {"app" "B" "shared" [{"type" "Location" "purpose" "Advertising or marketing"
                                       "collectors" ["AppLovin"]}]}]}
          forms (ingest/adapt-play-data-safety doc)
          {:keys [edges]} (ingest/load-graph forms)
          grants-edges (filter #(= (get % ":en/kind") ":grants") edges)]
      ;; dedup: only one :grants edge surface→perm (max load = 0.7)
      (is (= 1 (count grants-edges)))
      (is (= 0.7 (get (first grants-edges) ":en/load"))))))

;; ─── adapter: apple-app-privacy-report ───────────────────────────────────────

(deftest test-adapt-apple-basic
  (testing "adapt-apple-app-privacy-report produces nodes + edges"
    (let [forms (ingest/adapt-apple-app-privacy-report apple-doc)
          {:keys [nodes edges]} (ingest/load-graph forms)]
      (is (contains? nodes "ax.surface.apple-appstore"))
      ;; accessed Location → coarse-location perm + datatype
      (is (contains? nodes "ax.perm.coarse-location"))
      ;; accessed Identifiers → ad-id
      (is (contains? nodes "ax.perm.ad-id"))
      ;; contacted facebook.com domain → meta collector
      (is (contains? nodes "ax.col.meta-audience"))
      ;; contacted doubleclick.net → google-admanager collector
      (is (contains? nodes "ax.col.google-admanager"))
      (is (pos? (count edges))))))

;; ─── adapter: google-takeout-ads ─────────────────────────────────────────────

(deftest test-adapt-takeout
  (testing "adapt-google-takeout-ads produces expected nodes"
    (let [forms (ingest/adapt-google-takeout-ads takeout-doc)
          {:keys [nodes]} (ingest/load-graph forms)]
      (is (contains? nodes "ax.surface.google-search"))
      ;; adPersonalization=true
      (is (contains? nodes "ax.perm.ad-personalization"))
      ;; web=true
      (is (contains? nodes "ax.perm.browsing-history"))
      ;; location=true
      (is (contains? nodes "ax.perm.precise-location")))))

;; ─── adapter: android-permission-dump ────────────────────────────────────────

(deftest test-adapt-android-perms
  (testing "adapt-android-permission-dump grants + ad-id plausible flows"
    (let [forms (ingest/adapt-android-permission-dump perm-doc)
          {:keys [nodes edges]} (ingest/load-graph forms)]
      (is (contains? nodes "ax.surface.free-mobile-app"))
      (is (contains? nodes "ax.perm.precise-location"))
      (is (contains? nodes "ax.perm.ad-id"))
      ;; AD_ID → admob + meta plausible flows
      (is (contains? nodes "ax.col.admob"))
      (is (contains? nodes "ax.col.meta-audience"))
      (is (pos? (count edges))))))

;; ─── export-to-datoms round-trip ─────────────────────────────────────────────

(deftest test-export-to-datoms-play
  (testing "export-to-datoms returns non-empty EAVT datom list"
    (let [datoms (ingest/export-to-datoms play-doc ":play-data-safety" "btest123")]
      (is (pos? (count datoms)))
      ;; every datom is a 4-element vector starting with ":db/add"
      (is (every? #(and (= 4 (count %)) (= ":db/add" (first %))) datoms))
      ;; intake cid is embedded
      (is (some #(= "btest123" (nth % 3)) datoms))
      ;; intake kind is embedded
      (is (some #(= ":play-data-safety" (nth % 3)) datoms)))))

(deftest test-export-to-datoms-apple
  (testing "export-to-datoms apple produces datoms"
    (let [datoms (ingest/export-to-datoms apple-doc ":apple-app-privacy-report" "bapple456")]
      (is (pos? (count datoms)))
      (is (some #(= "bapple456" (nth % 3)) datoms)))))

(deftest test-export-to-datoms-unknown-kind
  (testing "export-to-datoms throws on unknown kind"
    (is (thrown?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         (ingest/export-to-datoms play-doc ":unknown-export" "bx")))))

(deftest test-export-to-datoms-blocks-credential-in-raw
  (testing "export-to-datoms raises on credential-key in raw"
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         #"G8"
         (ingest/export-to-datoms
          {"password" "xxx" "apps" []}
          ":play-data-safety"
          "bcred")))))

;; ─── resolve-collector-name ───────────────────────────────────────────────────

(deftest test-resolve-collector-name
  (testing "resolve-collector-name exact + substring"
    (is (= "ax.col.admob"          (ingest/resolve-collector-name "AdMob")))
    (is (= "ax.col.admob"          (ingest/resolve-collector-name "Google AdMob")))
    (is (= "ax.col.meta-audience"  (ingest/resolve-collector-name "Meta")))
    (is (= "ax.col.applovin"       (ingest/resolve-collector-name "applovin")))
    (is (nil?                      (ingest/resolve-collector-name "UnknownSDK123")))))

;; ─── G8 allowlist — no credential/raw-id attr in datoms ──────────────────────

(deftest test-no-credential-attr-in-datoms
  (testing "no credential/raw-id attr in emitted datoms"
    (let [datoms (ingest/export-to-datoms play-doc ":play-data-safety" "b0")]
      (doseq [[_op _e attr _v] datoms]
        (let [low (str/lower-case (str attr))]
          (is (not (some #(str/includes? low %) ["password" "token" "bearer" "secret"
                                                  "cookie" "idfa" "gaid" "imei"]))
              (str "credential attr leaked to datom: " attr)))))))

;; ─── entry point ──────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& _args]
     (let [result (run-tests 'aburi.tests.test-ingest)]
       (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1)))))
