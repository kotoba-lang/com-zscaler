(ns maps.methods.test-maps3d-net-http
  "Exercises the REAL default HTTP I/O seam impls (*http-get* / *http-post* via
  java.net) end-to-end against a one-shot in-process socket server — the only
  code path the seam-stubbed unit tests never run. Deterministic (no external
  node, no reify): a raw ServerSocket serves a single canned HTTP response.
  #?(:clj ...) — JVM/bb only."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [maps.methods.maps3d-net-tasks :as n]))

#?(:clj
   (defn- one-shot-server
     "Start a daemon thread serving ONE HTTP request with `status` + JSON `body`.
     Returns the bound port. Drains the request line+headers (and any body bytes
     already buffered) before replying so the client's write side completes."
     [status body]
     (let [ss (java.net.ServerSocket. 0)
           port (.getLocalPort ss)
           t (Thread.
              (fn []
                (try
                  (with-open [sock (.accept ss)]
                    (let [in (.getInputStream sock)
                          out (.getOutputStream sock)
                          rdr (java.io.BufferedReader. (java.io.InputStreamReader. in "UTF-8"))
                          ;; read request line + headers; capture Content-Length
                          hdrs (loop [acc []]
                                 (let [l (.readLine rdr)]
                                   (if (and l (not= l "")) (recur (conj acc l)) acc)))
                          cl (some (fn [h]
                                     (when (str/starts-with? (str/lower-case h) "content-length:")
                                       (Integer/parseInt (str/trim (subs h (inc (.indexOf h ":")))))))
                                   hdrs)]
                      ;; read the FULL request body before replying (avoid resetting
                      ;; the client mid-write → flaky "Connection reset")
                      (when (and cl (pos? cl))
                        (let [buf (char-array cl)]
                          (loop [off 0]
                            (when (< off cl)
                              (let [r (.read rdr buf off (- cl off))]
                                (when (pos? r) (recur (+ off r))))))))
                      (let [bs (.getBytes ^String body "UTF-8")
                            head (str "HTTP/1.1 " status "\r\n"
                                      "Content-Type: application/json\r\n"
                                      "Content-Length: " (count bs) "\r\n"
                                      "Connection: close\r\n\r\n")]
                        (.write out (.getBytes head "UTF-8"))
                        (.write out bs)
                        (.flush out))))
                  (catch Exception _ nil)
                  (finally (.close ss)))))]
       (.setDaemon t true)
       (.start t)
       port)))

#?(:clj
   (deftest test-default-http-get-parses-json
     (let [port (one-shot-server "200 OK" "{\"data\":[{\"id\":\"a\"}],\"ok\":true}")
           resp (n/*http-get* (str "http://127.0.0.1:" port "/x") {})]
       (is (= true (get resp "ok")))
       (is (= "a" (get-in resp ["data" 0 "id"]))))))

#?(:clj
   (deftest test-default-http-post-parses-json
     (let [port (one-shot-server "200 OK" "{\"jobId\":\"j1\"}")
           resp (n/*http-post* (str "http://127.0.0.1:" port "/jobs") {} "{\"tileH3\":\"t\"}")]
       (is (= "j1" (get resp "jobId"))))))

#?(:clj
   (deftest test-default-http-get-reads-error-stream-on-4xx
     ;; http-default must read the ERROR stream for >=400 and still parse the body
     (let [port (one-shot-server "404 Not Found" "{\"error\":\"nope\"}")
           resp (n/*http-get* (str "http://127.0.0.1:" port "/missing") {})]
       (is (= "nope" (get resp "error"))))))

#?(:clj
   (deftest test-fetch-mapillary-over-real-http
     ;; the whole fetch-mapillary task driven through the REAL default *http-get*
     (let [port (one-shot-server
                 "200 OK"
                 "{\"data\":[{\"id\":\"a\",\"thumb_1024_url\":\"u\",\"quality_score\":0.9,\"computed_geometry\":{\"coordinates\":[139.7,35.6]}},{\"id\":\"b\",\"quality_score\":0.1}]}")
           out (n/fetch-mapillary {:tile-h3 "h" :token "tok"
                                   :bbox [139 35 140 36] :min-quality 0.5
                                   :base-url (str "http://127.0.0.1:" port)})]
       (is (:ok out))
       (is (= 1 (:totalAvailable out)))          ; quality filter applied on a real response
       (is (= "a" (:id (first (:candidates out))))))))

#?(:clj
   (deftest test-colmap-submit-over-real-http
     ;; real POST submit; NO_JOB_ID branch when the worker returns an unexpected body
     (let [port (one-shot-server "200 OK" "{\"unexpected\":1}")
           out (n/colmap-tile {:tile-h3 "t" :selected-ids ["a"]
                               :worker-url (str "http://127.0.0.1:" port)})]
       (is (= "NO_JOB_ID" (:error-code out))))))
