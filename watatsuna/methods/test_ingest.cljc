(ns watatsuna.methods.test-ingest
  "Tests for ingest.cljc — the watatsuna 綿津綱 TeleGeography-bridge ingester.
  ADR-2606012600. ingest.py carries NO direct Python test (test_analyze.py covers
  the shared EDN reader + analyze), so this suite exercises the port against the
  actor's REAL seed EDN + the REAL bounded sample JSON under data/ingest/ with
  concrete structural asserts, then adds a Python↔Clojure PARITY test.

  There is NO content-addressing in ingest.py (no sha256/canonical-JSON CID — it
  writes plain EDN files), so parity is over the bridged-record shape, not a CID.

  HERMETIC: asserted against the committed seed + sample with exact, known counts."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [watatsuna.methods.ingest :as ingest]
            [watatsuna.methods._edn :as edn]))

(defn- repo-root []
  (let [cwd (io/file (System/getProperty "user.dir"))]
    (loop [d cwd]
      (cond
        (nil? d) cwd
        (.exists (io/file d "20-actors" "watatsuna" "data" "seed-cable-graph.kotoba.edn")) d
        :else (recur (.getParentFile d))))))

(def ^:private actor (io/file (repo-root) "20-actors" "watatsuna"))
(def ^:private methods-dir (io/file actor "methods"))
(def ^:private seed-path (io/file actor "data" "seed-cable-graph.kotoba.edn"))
(def ^:private sample-json (io/file actor "data" "ingest" "telegeography-sample.json"))

(defn- as-map [r] (if (map? r) r (into {} r)))

;; ── _slug ──────────────────────────────────────────────────────────────────

(deftest slug-mirrors-python
  ;; comma+space collapse to a single '-'; leading/trailing '-' stripped (cf. _slug)
  (is (= "hermosa-beach-ca" (ingest/slug "Hermosa Beach, CA")))
  (is (= "ras-ghareb" (ingest/slug "Ras Ghareb")))
  (is (= "sea-me-we-6" (ingest/slug "SEA-ME-WE 6")))
  (is (= "piti-guam" (ingest/slug "Piti, Guam")))
  (is (= "abc" (ingest/slug "  ABC!! "))))

;; ── bridge over the REAL sample (4 cables / 12 stations / 14 links) ─────────

(deftest bridge-sample-exact-counts
  (let [r (ingest/bridge-source sample-json)]
    (is (= 4  (count (:cables r))))
    (is (= 12 (count (:stations r))))
    (is (= 14 (count (:links r))))))

(deftest bridge-cable-record-shape
  (let [r (ingest/bridge-source sample-json)
        echo (as-map (first (:cables r)))]
    (is (= "cable.echo" (get echo ":cable/id")))
    (is (= "Echo" (get echo ":cable/name")))
    (is (= ":in-service" (get echo ":cable/status")))
    (is (= 17184 (get echo ":cable/length-km")))         ;; int
    (is (= 160.0 (get echo ":cable/design-capacity-tbps")))   ;; float
    (is (= 2024 (get echo ":cable/rfs-year")))
    (is (= ":representative" (get echo ":cable/sourcing")))
    (is (= ["Google" "Meta" "XL Axiata"] (get echo ":cable/owner-consortium")))))

(deftest bridge-station-id-and-country-lower
  (let [r (ingest/bridge-source sample-json)
        by-id (into {} (map (juxt #(get (as-map %) ":station/id") as-map) (:stations r)))]
    ;; station.<country-lower>.<slug name>
    (is (contains? by-id "station.us.eureka-ca"))
    (is (= "US" (get (by-id "station.us.eureka-ca") ":station/country")))
    (is (= 40.80 (get (by-id "station.us.eureka-ca") ":station/lat")))))

(deftest bridge-chokepoint-only-known-from-input-g2
  (let [r (ingest/bridge-source sample-json)
        changi (->> (:stations r) (map as-map)
                    (filter #(= "station.sg.changi" (get % ":station/id"))) first)]
    ;; G2: chokepoints come ONLY from input + only KNOWN names, never synthesized
    (is (= ["malacca"] (get changi ":station/chokepoint")))
    ;; a station w/ no input chokepoint carries no :station/chokepoint key
    (let [jakarta (->> (:stations r) (map as-map)
                       (filter #(= "station.id.jakarta" (get % ":station/id"))) first)]
      (is (not (contains? jakarta ":station/chokepoint"))))))

(deftest bridge-link-shape
  (let [r (ingest/bridge-source sample-json)
        first-link (as-map (first (:links r)))]
    (is (= "lk.echo.eureka-ca" (get first-link ":cable.link/id")))
    (is (= "cable.echo" (get first-link ":cable.link/cable")))
    (is (= "station.us.eureka-ca" (get first-link ":cable.link/station")))
    (is (= ":representative" (get first-link ":cable.link/sourcing")))))

(deftest bridge-sourcing-honesty-g5-tag-overridable
  ;; default :representative; an explicit sourcing flows through to every record (G5)
  (let [r (ingest/bridge-source sample-json {:sourcing "authoritative"})]
    (is (every? #(= ":authoritative" (get (as-map %) ":cable/sourcing")) (:cables r)))
    (is (every? #(= ":authoritative" (get (as-map %) ":station/sourcing")) (:stations r)))))

;; ── seed read + merge (seed wins; ids dedup) ───────────────────────────────

(deftest seed-loads-with-expected-buckets
  (let [seed (edn/load-edn seed-path)
        seed-recs (filter map? seed)]
    (is (= 14 (ingest/count-by-attr seed-recs ":cable/id")))
    (is (= 22 (ingest/count-by-attr seed-recs ":station/id")))))

(deftest merge-seed-plus-bridge-exact-totals
  (let [seed (edn/load-edn seed-path)
        bridged (ingest/bridged-flat (ingest/bridge-source sample-json))
        merged (ingest/merge-graph seed bridged)]
    (is (= 18 (ingest/count-by-attr merged ":cable/id")))   ;; 14 seed + 4 bridge
    (is (= 26 (ingest/count-by-attr merged ":station/id"))) ;; 22 seed + 4 new
    (is (= 114 (count merged)))))

(deftest merge-seed-wins-on-id-conflict
  ;; build a bridge record that collides with a seed id; seed's value must survive.
  (let [seed (edn/load-edn seed-path)
        seed-jupiter (->> seed (filter map?)
                          (filter #(= "cable.jupiter" (get % ":cable/id"))) first)
        bridged [[[":cable/id" "cable.jupiter"]
                  [":cable/name" "IMPOSTER"]
                  [":cable/sourcing" ":representative"]]]
        merged (ingest/merge-graph seed bridged)
        out-jupiter (->> merged (map as-map)
                         (filter #(= "cable.jupiter" (get % ":cable/id"))) first)]
    (is (some? seed-jupiter))
    (is (= "JUPITER" (get out-jupiter ":cable/name")))   ;; seed won; imposter dropped
    ;; only one cable.jupiter in the merged graph
    (is (= 1 (count (filter #(= "cable.jupiter" (get (as-map %) ":cable/id")) merged))))))

(deftest merge-drops-non-records-and-keyless
  (let [bridged (ingest/bridged-flat (ingest/bridge-source sample-json))
        ;; non-map / non-record junk + a keyless map should be filtered out;
        ;; cable.solo (seed) survives alongside the 4 bridged cables.
        seed [{":no/id" "x"} "a-string" 42 {":cable/id" "cable.solo" ":cable/name" "Solo"}]
        merged (ingest/merge-graph seed bridged)]
    (is (= 5 (ingest/count-by-attr merged ":cable/id"))) ;; cable.solo + 4 bridged
    (is (= 1 (count (filter #(= "cable.solo" (get (as-map %) ":cable/id")) merged))))
    (is (not-any? string? merged))   ;; "a-string" dropped (no id)
    (is (not-any? number? merged))   ;; 42 dropped
    (is (not-any? #(get (as-map %) ":no/id") merged))))  ;; keyless map dropped

;; ── to-edn round-trips through the reader (faithful EDN emit) ──────────────

(deftest to-edn-emits-readable-kotoba-edn
  (let [bridged (ingest/bridged-flat (ingest/bridge-source sample-json))
        text (ingest/to-edn bridged ingest/bridge-header)
        reparsed (edn/parse-edn text)]
    (is (str/starts-with? text ";; watatsuna"))   ;; header preserved
    (is (vector? reparsed))
    (is (= (count bridged) (count reparsed)))
    ;; a float renders with a decimal point so the reader yields a double again
    (is (str/includes? text ":cable/design-capacity-tbps 160.0"))
    ;; keyword-strings stay strings, names quoted
    (is (str/includes? text ":cable/status :in-service"))
    (is (str/includes? text "\"Echo\""))
    ;; reparsed echo cable matches the bridged record's values
    (let [echo (->> reparsed (filter map?)
                    (filter #(= "cable.echo" (get % ":cable/id"))) first)]
      (is (= 160.0 (get echo ":cable/design-capacity-tbps")))
      (is (= 17184 (get echo ":cable/length-km")))
      (is (= ["Google" "Meta" "XL Axiata"] (get echo ":cable/owner-consortium"))))))

;; ── G7: live ingest is refused (offline default needs no flag) ─────────────

(deftest live-flag-is-refused-g7
  (is (thrown? clojure.lang.ExceptionInfo (ingest/-main "--live" "http://example/x"))))

;; ── Python↔Clojure parity: bridge_source produces the SAME records ─────────
;; Golden-file parity (ADR-2606131300): `test_ingest_golden.json` is the REAL Python
;; ingest.bridge_source output over telegeography-sample.json, captured byte-for-byte
;; (json.dumps sort_keys=True) BEFORE the Python prune. Freezing it keeps the cross-language
;; parity guarantee after ingest.py is gone — the cljc port must still reproduce these exact
;; (cables, stations, links) record sets.

(deftest python-clojure-bridge-parity
  (let [parse-json (requiring-resolve 'cheshire.core/parse-string)
        py (parse-json (slurp (io/file methods-dir "test_ingest_golden.json")))
        clj (ingest/bridge-source sample-json)
        ;; normalize both sides to sets of attr->value maps (order-independent)
        norm (fn [recs] (set (map (fn [r] (as-map r)) recs)))]
    (is (= (set (get py "cables")) (norm (:cables clj))))
    (is (= (set (get py "stations")) (norm (:stations clj))))
    (is (= (set (get py "links")) (norm (:links clj))))))
