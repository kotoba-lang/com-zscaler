#!/usr/bin/env bb
;; kaname 要 — install/uninstall the production heartbeat LaunchAgent (ADR-2606172100; deploy 実運用).
;; babashka port of install.sh (clj-native deploy).
;;   bb install.clj install    → render the plist from the template (repo root + bb resolved), load it, kickstart once
;;   bb install.clj uninstall  → bootout + remove the plist
;;   bb install.clj status     → print the agent state + tail the log
;; macOS launchd (per-user LaunchAgent). The repo root is resolved from this script's location, so a
;; post-merge re-install repoints automatically. NOTE: when run from a temporary git worktree the path
;; is ephemeral — re-run install from the merged checkout once kaname lands on main.
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.string :as str])

(def label "com.etzhayyim.kaname.heartbeat")
(def here  (str (fs/parent (fs/absolutize *file*))))                 ; …/20-actors/kaname/deploy
(def repo  (str (fs/normalize (fs/absolutize (fs/path here ".." ".." ".."))))) ; repo root
(def bb    (or (some-> (fs/which "bb") str) "/opt/homebrew/bin/bb"))
(def home  (System/getProperty "user.home"))
(def plist (str home "/Library/LaunchAgents/" label ".plist"))
(def log   (str home "/Library/Logs/kaname-heartbeat.log"))
(def uid   (str/trim (:out (p/shell {:out :string} "id" "-u"))))
(def domain (str "gui/" uid))

(defn- sh! "Run, tolerate non-zero (e.g. bootout when not loaded)." [& args]
  (apply p/shell {:continue true :out :string :err :string} args))

(defn- tail [path n]
  (if (fs/exists? path)
    (str/join "\n" (take-last n (str/split-lines (slurp path))))
    "(no log)"))

(defn install []
  (fs/create-dirs (str home "/Library/LaunchAgents"))
  (fs/create-dirs (str home "/Library/Logs"))
  (let [rendered (-> (slurp (str (fs/path here "com.etzhayyim.kaname.heartbeat.plist.template")))
                     (str/replace "@REPO@" repo)
                     (str/replace "@BB@" bb)
                     (str/replace "@HOME@" home))]
    (spit plist rendered))
  (fs/set-posix-file-permissions (str (fs/path here "run-heartbeat.sh")) "rwxr-xr-x")
  (sh! "launchctl" "bootout" (str domain "/" label))                 ; idempotent reload
  (p/shell "launchctl" "bootstrap" domain plist)
  (println (str "installed + loaded: " plist " (repo=" repo " bb=" bb ")"))
  (println "kickstarting one beat…")
  (p/shell "launchctl" "kickstart" "-k" (str domain "/" label))
  (Thread/sleep 6000)
  (println (tail log 4)))

(defn uninstall []
  (sh! "launchctl" "bootout" (str domain "/" label))
  (fs/delete-if-exists plist)
  (println (str "uninstalled: " label)))

(defn status []
  (let [out (:out (sh! "launchctl" "print" (str domain "/" label)))]
    (if (str/blank? out)
      (println "(not loaded)")
      (->> (str/split-lines out)
           (filter #(re-find #"(?i)state =|program =|last exit|runs =" %))
           (run! #(println (str/trim %))))))
  (println "--- last log ---")
  (println (tail log 6)))

(let [cmd (or (first *command-line-args*) "status")]
  (case cmd
    "install"   (install)
    "uninstall" (uninstall)
    "status"    (status)
    (do (println "usage: bb install.clj [install|uninstall|status]") (System/exit 2))))
