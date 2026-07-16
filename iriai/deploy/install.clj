#!/usr/bin/env bb
;; iriai 入会 — heartbeat LaunchAgent installer (bb-native, no shell). ADR-2606280900.
;;
;;   bb 20-actors/iriai/deploy/install.clj install     ; render plist + load LaunchAgent
;;   bb 20-actors/iriai/deploy/install.clj uninstall   ; unload + remove
;;   bb 20-actors/iriai/deploy/install.clj status       ; show launchctl state + last beat
;;
;; Per the repo rule (root CLAUDE.md §"Operational code = clj/bb"): residence is a launchd
;; LaunchAgent (OS config), invoking the `bb` cell task hourly at :44 — NOT a nohup bash loop.
;; The generated plist + logs are MACHINE-LOCAL (gitignored); only this installer + the
;; template are committed. No-server-key: the cell depends on the LOCAL seed + bb only.
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.string :as str])

(def label "com.etzhayyim.iriai.heartbeat")
;; this file is 20-actors/iriai/deploy/install.clj → repo root is 3 parents up
(def repo (str (fs/absolutize (fs/parent (fs/parent (fs/parent (fs/parent *file*)))))))
(def template (str repo "/20-actors/iriai/deploy/" label ".plist.template"))
(def agents-dir (str (System/getProperty "user.home") "/Library/LaunchAgents"))
(def plist-out (str agents-dir "/" label ".plist"))
(def log-dir (str (System/getProperty "user.home") "/Library/Logs/etzhayyim"))

(defn- which-bb []
  (or (some-> (p/sh "bash" "-lc" "command -v bb") :out str/trim not-empty)
      "/opt/homebrew/bin/bb"))

(defn render! []
  (fs/create-dirs agents-dir)
  (fs/create-dirs log-dir)
  (let [t (slurp template)
        out (-> t
                (str/replace "@@BB@@" (which-bb))
                (str/replace "@@REPO@@" repo)
                (str/replace "@@LOGDIR@@" log-dir))]
    (spit plist-out out)
    (println "wrote" plist-out)))

(defn install! []
  (render!)
  (p/sh "launchctl" "unload" plist-out)            ; idempotent
  (let [r (p/sh "launchctl" "load" plist-out)]
    (println "launchctl load:" (if (zero? (:exit r)) "ok" (:err r))))
  (println "installed" label "— hourly at :44 (node judah, healthz 13093)"))

(defn uninstall! []
  (p/sh "launchctl" "unload" plist-out)
  (when (fs/exists? plist-out) (fs/delete plist-out))
  (println "uninstalled" label))

(defn status! []
  (let [r (p/sh "bash" "-lc" (str "launchctl list | grep " label " || echo '(not loaded)'"))]
    (println "launchctl:" (str/trim (:out r))))
  (println "plist:" (if (fs/exists? plist-out) plist-out "(none)")))

(case (first *command-line-args*)
  "install"   (install!)
  "uninstall" (uninstall!)
  "status"    (status!)
  (do (println "usage: bb 20-actors/iriai/deploy/install.clj [install|uninstall|status]")
      (System/exit 1)))
