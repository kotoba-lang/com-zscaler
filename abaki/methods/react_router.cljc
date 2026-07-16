(ns abaki.methods.react-router)

(defn simulate-murakumo-compute-routing
  "Intercepting request... Pure Clojure mirror of simulate_murakumo_compute_routing."
  [routing-policy]
  (let [requested-vendor "entity:compute:megacorp_a"
        blocked (mapv #(get % "id") (get routing-policy "blocked_entities"))
        safe (mapv #(get % "id")
                   (filter #(= "compute" (get % "domain"))
                           (get routing-policy "safe_entities")))
        blocked? (boolean (some #(= requested-vendor %) blocked))]
    (str "\n[Murakumo Compute Router] Intercepting request...\n"
         (if blocked?
           (str "🚨 ALERT: Request to '" requested-vendor "' is BLOCKED by abaki policy.\n"
                "   Reason: High Chokepoint Index (Monopolistic behavior).\n"
                (if (seq safe)
                  (str "🔄 ROUTE AROUND: Redirecting workload to safe provider: " (first safe))
                  "❌ FATAL: No safe compute providers available. Failing securely."))
           "✅ Request permitted."))))

(defn simulate-ossekai-survival-tree
  "Generating survival tree... Pure Clojure mirror of simulate_ossekai_survival_tree."
  [routing-policy]
  (let [blocked-domains (set (map #(get % "domain") (get routing-policy "blocked_entities")))
        branches (cond-> []
                   (contains? blocked-domains "biology")
                   (conj "🌱 Biology/Agri branch: Dependency on F1 seeds blocked. Activating 'suki' (Local Heirloom Seed Bank) fallback.")

                   (contains? blocked-domains "logistics")
                   (conj "🚚 Logistics branch: Centralized logistics blocked. Activating 'wadachi' (Autonomous mesh delivery) fallback.")

                   (contains? blocked-domains "compute")
                   (conj "💻 Compute branch: Proprietary API blocked. Activating 'ameno' (WebGPU local inference) fallback."))]
    (str "\n[Ossekai Survival Simulator] Generating survival tree...\n"
         "Survival Branches Activated:\n"
         (when (seq branches)
           (str (clojure.string/join "\n" branches) "\n"))
         "Ossekai simulation updated to reflect the new Charter-aligned constraints.")))
