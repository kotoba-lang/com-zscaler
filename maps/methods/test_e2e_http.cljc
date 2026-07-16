(ns maps.methods.test-e2e-http
  "test_e2e_http.py — end-to-end HTTP loop test against the kotoba stand-in (ADR-2606064500 R1).
  unittest → clojure.test. 1:1 port; h3-independent (deterministic mock cell stamp). Proves the
  maps↔kotoba WIRE contract: ingest.py's real push path (Bearer auth = no-server-key §4) + the
  EXACT AVET cell query the TS adapter issues. The only stand-in is the kotoba engine.

  The Python urllib `_post`/GET helpers are reimplemented as self-contained raw-socket HTTP
  (behind #?(:clj)), with inlined JSON. The __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.string :as str]
            [maps.methods.ingest :as ingest]
            #?(:clj [maps.methods.kotoba-local-server :as kls])))

(def ^:private token "test-operator-did-bearer")
(def ^:private cell-res [2 4 6 8 10 12])
(def ^:private features
  [["feature.station.tokyo" ":station" "Tokyo Station" 35.6812 139.7671]
   ["feature.building.marunouchi-bldg" ":building" "Marunouchi Building" 35.6809 139.7644]
   ["feature.airport.haneda" ":airport" "Tokyo Haneda" 35.5494 139.7798]])

(defn- mock-cell [lat lon res]
  (let [size (/ 10.0 (Math/pow 3 res))]
    (str "r" res "/" (long (Math/floor (/ lat size))) "/" (long (Math/floor (/ lon size))))))

(defn- batch []
  {"entities"
   (vec
    (for [[fid label name lat lon] features]
      (let [claims (into [{"pred" "feature/label" "value" label}
                          {"pred" "feature/name" "value" name}
                          {"pred" "feature/lat" "value" (str lat)}
                          {"pred" "feature/lon" "value" (str lon)}
                          {"pred" "feature/sourcing" "value" ":representative"}]
                         (for [r cell-res] {"pred" (str "feature.cell/r" r) "value" (mock-cell lat lon r)}))]
        {"id" fid "type" "maps-feature" "label_en" name "claims" claims "relations" []})))})

;; ── self-contained raw-socket HTTP + JSON (test-side _post / GET) ──
#?(:clj
   (do
     (defn- json-escape ^String [^String s]
       (str/escape s {\" "\\\"" \\ "\\\\" \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))
     (defn- json-encode ^String [v]
       (cond
         (nil? v) "null"
         (string? v) (str "\"" (json-escape v) "\"")
         (boolean? v) (if v "true" "false")
         (integer? v) (str v)
         (number? v) (str v)
         (map? v) (str "{" (str/join "," (map (fn [[k val]] (str "\"" (json-escape (str k)) "\":" (json-encode val))) v)) "}")
         (sequential? v) (str "[" (str/join "," (map json-encode v)) "]")
         :else (str "\"" (json-escape (str v)) "\"")))
     (declare json-value)
     (defn- skip-ws [^String s i] (loop [i i] (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i))) (recur (inc i)) i)))
     (defn- json-string* [^String s i]
       (loop [i (inc i), sb (StringBuilder.)]
         (let [c (nth s i)]
           (cond
             (= c \") [(.toString sb) (inc i)]
             (= c \\) (let [e (nth s (inc i))]
                        (case e
                          \" (do (.append sb \") (recur (+ i 2) sb)) \\ (do (.append sb \\) (recur (+ i 2) sb))
                          \/ (do (.append sb \/) (recur (+ i 2) sb)) \b (do (.append sb \backspace) (recur (+ i 2) sb))
                          \f (do (.append sb \formfeed) (recur (+ i 2) sb)) \n (do (.append sb \newline) (recur (+ i 2) sb))
                          \r (do (.append sb \return) (recur (+ i 2) sb)) \t (do (.append sb \tab) (recur (+ i 2) sb))
                          \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)] (.append sb (char cp)) (recur (+ i 6) sb))
                          (do (.append sb e) (recur (+ i 2) sb))))
             :else (do (.append sb c) (recur (inc i) sb))))))
     (defn- json-number* [^String s i]
       (let [end (loop [j i] (if (and (< j (count s)) (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j))) (recur (inc j)) j))
             tok (subs s i end)]
         [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))
     (defn- json-array* [^String s i]
       (loop [i (skip-ws s (inc i)), out []]
         (if (= (nth s i) \]) [out (inc i)]
             (let [[v i] (json-value s i) i (skip-ws s i)]
               (if (= (nth s i) \,) (recur (skip-ws s (inc i)) (conj out v)) [(conj out v) (inc i)])))))
     (defn- json-object* [^String s i]
       (loop [i (skip-ws s (inc i)), out {}]
         (if (= (nth s i) \}) [out (inc i)]
             (let [[k i] (json-string* s i) i (skip-ws s i) [v i] (json-value s (skip-ws s (inc i))) out (assoc out k v) i (skip-ws s i)]
               (if (= (nth s i) \,) (recur (skip-ws s (inc i)) out) [out (inc i)])))))
     (defn- json-value [^String s i]
       (let [i (skip-ws s i) c (nth s i)]
         (cond (= c \{) (json-object* s i) (= c \[) (json-array* s i) (= c \") (json-string* s i)
               (= c \t) [true (+ i 4)] (= c \f) [false (+ i 5)] (= c \n) [nil (+ i 4)] :else (json-number* s i))))
     (defn- parse-json [text] (first (json-value text 0)))
     (defn- read-line-crlf [^java.io.InputStream in]
       (let [sb (StringBuilder.)]
         (loop [] (let [c (.read in)] (cond (= c -1) (if (pos? (.length sb)) (.toString sb) nil) (= c 13) (do (.read in) (.toString sb)) :else (do (.append sb (char c)) (recur)))))))
     (defn- read-n [^java.io.InputStream in n]
       (let [buf (byte-array n)] (loop [off 0] (if (>= off n) (String. buf "UTF-8") (let [r (.read in buf off (- n off))] (if (neg? r) (String. buf 0 off "UTF-8") (recur (+ off r))))))))
     (defn- read-status+headers [^java.io.InputStream in]
       (let [status (read-line-crlf in)
             code (try (Integer/parseInt (second (str/split status #" "))) (catch Exception _ 0))]
         (loop [cl 0]
           (let [l (read-line-crlf in)]
             (if (or (nil? l) (= l "")) [code cl]
                 (recur (if (str/starts-with? (str/lower-case l) "content-length:")
                          (Integer/parseInt (str/trim (subs l (inc (str/index-of l ":"))))) cl)))))))
     (defn- request [method url body-map token]
       (let [u (java.net.URI. url) host (.getHost u) port (let [p (.getPort u)] (if (pos? p) p 80))
             path (str (.getRawPath u) (when (.getRawQuery u) (str "?" (.getRawQuery u))))
             body (when body-map (json-encode body-map))
             bb (when body (.getBytes body "UTF-8"))
             sock (java.net.Socket.)]
         (try
           (.connect sock (java.net.InetSocketAddress. host port) 5000)
           (.setSoTimeout sock 5000)
           (let [out (.getOutputStream sock) in (.getInputStream sock)
                 hdr (str method " " path " HTTP/1.1\r\nHost: " host "\r\n"
                          (when token (str "authorization: Bearer " token "\r\n"))
                          (when bb (str "content-type: application/json\r\nContent-Length: " (count bb) "\r\n"))
                          "Connection: close\r\n\r\n")]
             (.write out (.getBytes hdr "UTF-8"))
             (when bb (.write out bb))
             (.flush out)
             (let [[code cl] (read-status+headers in)]
               [code (parse-json (read-n in cl))]))
           (finally (.close sock)))))
     (defn- post [url body token] (request "POST" url body token))
     (defn- get* [url] (request "GET" url nil nil))))

#?(:clj (def ^:private server (atom nil)))
#?(:clj (def ^:private base (atom nil)))

#?(:clj
   (defn- with-server [f]
     (let [srv (kls/serve 0 token)]
       (kls/serve-forever srv)
       (reset! server srv)
       (reset! base (str "http://127.0.0.1:" (:port srv)))
       ;; Seed the store in the fixture so the read tests do not depend on inter-test ordering
       ;; (clojure.test does not guarantee the Python test_1/test_2/... name order). test-2 below
       ;; still exercises the real push path independently.
       (ingest/push-batch (batch) token @base)
       (try (f) (finally (kls/shutdown srv))))))

#?(:clj (use-fixtures :once with-server))

(deftest test-1-ingest-refused-without-bearer
  #?(:clj
     (let [[code _] (post (str @base "/xrpc/com.etzhayyim.apps.kotobase.kg.ingest_batch") (batch) nil)]
       (is (= code 401)))))

(deftest test-2-ingest-via-real-ingest-push-path
  #?(:clj
     (let [[status body] (ingest/push-batch (batch) token @base)
           parsed (parse-json body)]
       (is (= status 200))
       (is (= (get parsed "ok") true))
       (is (= (get parsed "ingested") (count features))))))

(deftest test-3-avet-cell-query-roundtrip
  #?(:clj
     (let [cell (mock-cell 35.6812 139.7671 12)
           [code body] (post (str @base "/xrpc/com.etzhayyim.apps.kotoba.graph.sparql")
                             {"index" "avet" "predicate" "feature.cell/r12" "objects" [cell]
                              "filter" {"predicate" "feature/label" "in" [":station"]} "limit" 500}
                             nil)
           ids (mapv #(get % "id") (get body "entities"))
           claims (into {} (map (fn [c] [(get c "pred") (get c "value")]) (get (first (get body "entities")) "claims")))]
       (is (= code 200))
       (is (= ids ["feature.station.tokyo"]))
       (is (= (get claims "feature/label") ":station"))
       (is (= (get claims "feature/name") "Tokyo Station")))))

(deftest test-4-label-filter-excludes-other-labels
  #?(:clj
     (let [cell (mock-cell 35.6809 139.7644 2)
           [_ body] (post (str @base "/xrpc/com.etzhayyim.apps.kotoba.graph.sparql")
                         {"index" "avet" "predicate" "feature.cell/r2" "objects" [cell]
                          "filter" {"predicate" "feature/label" "in" [":building"]} "limit" 500}
                         nil)
           ids (set (map #(get % "id") (get body "entities")))
           labels (set (for [e (get body "entities") c (get e "claims") :when (= (get c "pred") "feature/label")] (get c "value")))]
       (is (contains? ids "feature.building.marunouchi-bldg"))
       (is (not (contains? ids "feature.station.tokyo")))
       (is (= labels #{":building"})))))

(deftest test-5-kg-entity-point-lookup
  #?(:clj
     (let [[_ body] (get* (str @base "/xrpc/com.etzhayyim.apps.kotobase.kg.entity?id=feature.station.tokyo"))
           ent (get body "entity")]
       (is (= (get ent "id") "feature.station.tokyo"))
       (is (some #(= (get % "pred") "feature/name") (get ent "claims"))))))
