(ns maps.methods.kotoba-local-server
  "kotoba_local_server.py — a stdlib HTTP stand-in for the kotoba XRPC surface (ADR-2606064500 R1).
  1:1 Clojure port of `methods/kotoba_local_server.py`.

  NOT the production kotoba engine — a tiny HTTP server that speaks the EXACT wire shapes
  `src/kotoba-spatial.ts` and `methods/ingest.py` use, backed by the maps.methods.kotoba-local
  EAVT/AVET reference store, so the maps↔kotoba HTTP loop can be exercised END-TO-END offline.

  The Python module used http.server + ThreadingHTTPServer; here the equivalent is a self-contained
  raw-socket HTTP/1.1 server (java.net.ServerSocket), behind #?(:clj ...) since it is host I/O.
  `serve` returns a server map {:socket :port :store :token}; start it with `serve-forever`,
  stop it with `shutdown`. JSON is inlined. The __main__ CLI is omitted."
  (:require [clojure.string :as str]
            [maps.methods.kotoba-local :as kl]))

;; ── inlined JSON ──────────────────────────────────────────────────────────────
(defn- json-escape ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- json-encode ^String [v]
  (cond
    (nil? v)        "null"
    (string? v)     (str "\"" (json-escape v) "\"")
    (boolean? v)    (if v "true" "false")
    (integer? v)    (str v)
    (number? v)     (str v)
    (map? v)        (str "{" (str/join "," (map (fn [[k val]] (str "\"" (json-escape (str k)) "\":" (json-encode val))) v)) "}")
    (sequential? v) (str "[" (str/join "," (map json-encode v)) "]")
    :else           (str "\"" (json-escape (str v)) "\"")))

#?(:clj
   (do
     (declare json-value)
     (defn- skip-ws [^String s i]
       (loop [i i]
         (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
           (recur (inc i)) i)))
     (defn- json-string* [^String s i]
       (loop [i (inc i), sb (StringBuilder.)]
         (let [c (nth s i)]
           (cond
             (= c \") [(.toString sb) (inc i)]
             (= c \\)
             (let [e (nth s (inc i))]
               (case e
                 \" (do (.append sb \") (recur (+ i 2) sb))
                 \\ (do (.append sb \\) (recur (+ i 2) sb))
                 \/ (do (.append sb \/) (recur (+ i 2) sb))
                 \b (do (.append sb \backspace) (recur (+ i 2) sb))
                 \f (do (.append sb \formfeed) (recur (+ i 2) sb))
                 \n (do (.append sb \newline) (recur (+ i 2) sb))
                 \r (do (.append sb \return) (recur (+ i 2) sb))
                 \t (do (.append sb \tab) (recur (+ i 2) sb))
                 \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                      (.append sb (char cp)) (recur (+ i 6) sb))
                 (do (.append sb e) (recur (+ i 2) sb))))
             :else (do (.append sb c) (recur (inc i) sb))))))
     (defn- json-number* [^String s i]
       (let [end (loop [j i]
                   (if (and (< j (count s))
                            (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                     (recur (inc j)) j))
             tok (subs s i end)]
         [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))
     (defn- json-array* [^String s i]
       (loop [i (skip-ws s (inc i)), out []]
         (if (= (nth s i) \])
           [out (inc i)]
           (let [[v i] (json-value s i) i (skip-ws s i)]
             (if (= (nth s i) \,)
               (recur (skip-ws s (inc i)) (conj out v))
               [(conj out v) (inc i)])))))
     (defn- json-object* [^String s i]
       (loop [i (skip-ws s (inc i)), out {}]
         (if (= (nth s i) \})
           [out (inc i)]
           (let [[k i] (json-string* s i) i (skip-ws s i)
                 [v i] (json-value s (skip-ws s (inc i))) out (assoc out k v) i (skip-ws s i)]
             (if (= (nth s i) \,)
               (recur (skip-ws s (inc i)) out)
               [out (inc i)])))))
     (defn- json-value [^String s i]
       (let [i (skip-ws s i) c (nth s i)]
         (cond
           (= c \{) (json-object* s i)
           (= c \[) (json-array* s i)
           (= c \") (json-string* s i)
           (= c \t) [true (+ i 4)]
           (= c \f) [false (+ i 5)]
           (= c \n) [nil (+ i 4)]
           :else (json-number* s i))))
     (defn- parse-json [text] (first (json-value (or text "{}") 0)))

     ;; ── raw HTTP plumbing ──
     (defn- read-line-crlf [^java.io.InputStream in]
       (let [sb (StringBuilder.)]
         (loop []
           (let [c (.read in)]
             (cond
               (= c -1) (if (pos? (.length sb)) (.toString sb) nil)
               (= c 13) (do (.read in) (.toString sb))
               :else (do (.append sb (char c)) (recur)))))))
     (defn- read-n [^java.io.InputStream in n]
       (let [buf (byte-array n)]
         (loop [off 0]
           (if (>= off n) (String. buf "UTF-8")
               (let [r (.read in buf off (- n off))]
                 (if (neg? r) (String. buf 0 off "UTF-8") (recur (+ off r))))))))
     (defn- send-resp [^java.io.OutputStream out code obj]
       (let [body (json-encode obj)
             rb (.getBytes body "UTF-8")
             reason (case code 200 "OK" 400 "Bad Request" 401 "Unauthorized" 404 "Not Found" "OK")]
         (.write out (.getBytes (str "HTTP/1.1 " code " " reason "\r\n"
                                     "content-type: application/json\r\n"
                                     "content-length: " (count rb) "\r\n\r\n") "UTF-8"))
         (.write out rb)
         (.flush out)))

     (defn- handle-conn [server ^java.net.Socket sock]
       (try
         (let [in (.getInputStream sock)
               out (.getOutputStream sock)
               reqline (read-line-crlf in)]
           (when reqline
             (let [parts (str/split reqline #" ")
                   method (first parts)
                   raw-path (second parts)
                   [path query] (let [qi (str/index-of raw-path "?")]
                                  (if qi [(subs raw-path 0 qi) (subs raw-path (inc qi))] [raw-path nil]))
                   ;; collect headers
                   [headers cl]
                   (loop [hs {} cl 0]
                     (let [l (read-line-crlf in)]
                       (if (or (nil? l) (= l ""))
                         [hs cl]
                         (let [ci (str/index-of l ":")
                               k (str/lower-case (str/trim (subs l 0 ci)))
                               v (str/trim (subs l (inc ci)))]
                           (recur (assoc hs k v)
                                  (if (= k "content-length") (Integer/parseInt v) cl))))))
                   body-str (when (pos? cl) (read-n in cl))
                   store (:store server)
                   token (:token server)]
               (cond
                 (= method "GET")
                 (if (or (str/ends-with? path "/kg.entity") (str/ends-with? path ".kg.entity"))
                   (let [ids (when query
                               (->> (str/split query #"&")
                                    (map #(str/split % #"=" 2))
                                    (filter #(= (first %) "id"))
                                    (map second)))
                         ent (when (seq ids) (kl/entity store (first ids)))]
                     (if (nil? ent)
                       (send-resp out 404 {"error" "not found"})
                       (send-resp out 200 {"entity" ent})))
                   (send-resp out 404 {"error" "unknown path"}))

                 (= method "POST")
                 (let [body (try (parse-json body-str) (catch Exception e {:bad (str e)}))]
                   (if (and (map? body) (contains? body :bad))
                     (send-resp out 400 {"error" (str "bad json: " (:bad body))})
                     (cond
                       (str/ends-with? path "kg.ingest_batch")
                       (let [auth (get headers "authorization" "")]
                         (if (and (some? token) (not= auth (str "Bearer " token)))
                           (send-resp out 401 {"error" "missing/invalid Bearer (no-server-key: member-signed)"})
                           (let [n (kl/ingest-batch store body)]
                             (send-resp out 200 {"ok" true "ingested" n}))))

                       (str/ends-with? path "graph.sparql")
                       (let [flt (or (get body "filter") {})
                             ents (kl/avet-query store
                                                 (get body "predicate")
                                                 (get body "objects" [])
                                                 (get flt "predicate")
                                                 (get flt "in")
                                                 (int (get body "limit" 500)))]
                         (send-resp out 200 {"entities" ents}))

                       :else (send-resp out 404 {"error" "unknown path"}))))

                 :else (send-resp out 404 {"error" "unknown path"})))))
         (catch Exception _ nil)
         (finally (try (.close sock) (catch Exception _ nil)))))

     (defn serve
       "Create a server bound to 127.0.0.1:port (0 = ephemeral) with an optional Bearer token.
       Returns {:socket :port :store :token :running}. Call serve-forever to accept connections."
       [port token]
       (let [ss (java.net.ServerSocket. port 0 (java.net.InetAddress/getByName "127.0.0.1"))]
         {:socket ss
          :server-address [(.getInetAddress ss) (.getLocalPort ss)]
          :port (.getLocalPort ss)
          :store (kl/new-store)
          :token token
          :running (atom true)}))

     (defn serve-forever
       "Accept connections in a daemon thread until shutdown."
       [server]
       (let [ss (:socket server)
             running (:running server)
             t (Thread.
                (fn []
                  (while @running
                    (try
                      (let [sock (.accept ss)]
                        (let [ct (Thread. #(handle-conn server sock))]
                          (.setDaemon ct true)
                          (.start ct)))
                      (catch Exception _ nil)))))]
         (.setDaemon t true)
         (.start t)
         server))

     (defn shutdown [server]
       (reset! (:running server) false)
       (try (.close ^java.net.ServerSocket (:socket server)) (catch Exception _ nil)))

     (defn server-close [server] (shutdown server))))
