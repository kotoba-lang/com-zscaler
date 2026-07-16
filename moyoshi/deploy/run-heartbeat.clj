#!/usr/bin/env bb
;; moyoshi 催し — production heartbeat runner (ADR-2606272100 R3; deploy 実運用). clj/bb-native
;; (NO shell — repo rule ADR-2606072802). One beat: ingest kizuna → design → govern → record →
;; settle → persist to the local kotoba commit-DAG → push to the LIVE kotoba engine (--bridge).
;; Idempotent-by-content + FAIL-OPEN (engine down → the beat still completes locally).
;;
;; Constitutional notes:
;;   - the operator DID is read DYNAMICALLY from the running kotoba node's own env
;;     (KOTOBA_AGENT_DID) — the loopback "node persists on the actor's behalf" path. It is a
;;     PUBLIC identifier, never a secret; absent → the bridge fail-opens and the beat is local-only.
;;   - no platform signing key is held (the bearer is unsigned; loopback trust boundary). no-server-key.
(require '[babashka.process :as p]
         '[clojure.string :as str])

(def repo (or (System/getenv "MOYOSHI_REPO") (System/getProperty "user.dir")))
(def bb   (or (System/getenv "MOYOSHI_BB") "/opt/homebrew/bin/bb"))

(defn- node-operator-did []
  (let [pid (str/trim (str (:out (p/shell {:continue true :out :string :err :string}
                                          "bash" "-c" "pgrep -f kotoba-server | head -1"))))]
    (when (seq pid)
      (let [env (str (:out (p/shell {:continue true :out :string :err :string} "ps" "eww" pid)))]
        (some (fn [tok] (when (str/starts-with? tok "KOTOBA_AGENT_DID=")
                          (subs tok (count "KOTOBA_AGENT_DID="))))
              (str/split env #"\s+"))))))

(let [did   (node-operator-did)
      extra (cond-> {"MOYOSHI_KOTOBA_LIVE" "1"}
              (and did (seq did)) (assoc "MOYOSHI_KOTOBA_OPERATOR_DID" did))]
  (println (str "[moyoshi heartbeat] bridge=" (if (and did (seq did)) "on" "fail-open")))
  ;; run one --bridge beat from the repo root so bb.edn :paths resolve (kotoba.datom etc.).
  (let [{:keys [exit]} (p/shell {:dir repo :extra-env extra :continue true
                                 :inherit true}
                                bb "20-actors/moyoshi/autorun.cljc" "20-actors/moyoshi" "--bridge")]
    (System/exit (or exit 0))))
