#!/usr/bin/env bb
;; amime 網目 — multi-site energy MESH flow solver (clj-native, pure stdlib).
(ns amime.methods.mesh
  "amime 網目 — the multi-site energy-FLOW NETWORK the Energy Order suite lacked
  (ADR-2606212020, on ADR-2606211200). hikari 光 designs ONE site; mio 澪 verifies the
  org's aggregate Flowrate; but until now nothing modelled how ORDERED energy flows
  BETWEEN sites across a mesh of capacity-bounded, lossy links.

  amime is that layer. It is a COMMONS mesh, never a market — there is no price, no bid,
  no trade (G1: :amime/price / :amime/trade unrepresentable). It rewards ORDERED FLOW,
  never CONSUMED energy (G2, the mio PoUF stance). It is a resilience MAP, never a
  target-list (G3). ASSESSMENT + SIM ONLY — amime never dispatches; hikari actuates under
  Council gate (G4). No-server-key: pure local computation, no network I/O.

  ── the model ──
  SITE  {:gen kW :load kW :role …}            net_i = gen_i − load_i  (signed injection)
  LINK  {:from :to :capacity kW :loss 0..1}   undirected; `capacity` bounds the SENT kW;
                                              delivered = sent · (1 − loss).

  ── the solve (R0: deterministic single-hop transportation) ──
  Surplus sites (net>0) export; deficit sites (net<0) need import. Greedy, deterministic
  by id: each deficit pulls from adjacent surplus sites over their links, sent bounded by
  the surplus's remaining export AND the link's remaining capacity, delivered net of loss.
  Outputs: served fraction, transmission loss, curtailed surplus, unserved deficit, and
  per-link flow / per-site import-dependence (→ chokepoints). Multi-hop routing is R1.

  ── N-1 contingency ──
  Re-solve with each link removed; the link whose loss-of-service is largest is the
  critical chokepoint, routed to REDUNDANCY (never named as a target — a map of where the
  mesh is brittle so it can be strengthened)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── load ─────────────────────────────────────────────────────────────────────

(defn- reconstitute-entity
  "kotoba/seed.edn is now Datomic/Datascript tx-data (edn-datomize, Phase 4): each row is
  `{:db/id … :site/type :site :site/id … …}` / `{:db/id … :link/type :link …}` instead of the
  original bare `{:type :site :id … …}` / `{:type :link …}` map. Every row's attrs are 100%
  scalar (keyword/long/double) — none were pr-str'd into blob strings — so reconstitution is
  just: drop :db/id, strip the (uniform, per-row) namespace back off every key. Keeps every
  bare-keyword lookup below (:type/:id/:gen/:load/:from/:to/:capacity/:loss) working unchanged
  whether `rows` is the new tx-data shape or (for callers not yet migrated) the legacy bare-map
  shape — `classify` tolerates both."
  [row]
  (into {} (map (fn [[k v]] [(keyword (name k)) v])) (dissoc row :db/id)))

(defn classify
  "Split the flat seed vector by :type → {:sites [...] :links [...]}. Accepts both the current
  tx-data row shape (:db/id + :site/*|:link/* namespaced keys) and the legacy bare-map shape
  (:type/:id/… unnamespaced) — tx-data rows are reconstituted to the bare shape first."
  [rows]
  (let [rows (map (fn [r] (if (contains? r :db/id) (reconstitute-entity r) r)) rows)]
    {:sites (vec (filter #(= (:type %) :site) rows))
     :links (vec (filter #(= (:type %) :link) rows))}))

#?(:clj
   (defn load-edn [path] (clojure.edn/read-string (slurp path))))

(defn net [site] (- (double (or (:gen site) 0)) (double (or (:load site) 0))))

;; ── the flow solve ─────────────────────────────────────────────────────────────

(defn- adjacency
  "site-id → [link …] for every link incident on it (undirected)."
  [links]
  (reduce (fn [m l]
            (-> m (update (:from l) (fnil conj []) l)
                  (update (:to l) (fnil conj []) l)))
          {} links))

(defn solve
  "Deterministic single-hop transportation over the mesh. Returns a map:
     :sites      site-id → {:net :served :unserved :curtailed}
     :links      link-id → {:from :to :capacity :loss :flow :util}
     :delivered  total kW delivered to deficits
     :sent       total kW sent from surpluses
     :loss       total kW lost in transmission
     :need       total deficit
     :surplus    total surplus
     :unserved   total deficit not met
     :curtailed  total surplus not delivered (generated, nowhere to go)
     :served-fraction  delivered / need (1.0 if no need)."
  [{:keys [sites links]}]
  (let [by-id (into {} (map (juxt :id identity) sites))
        adj (adjacency links)
        ;; mutable-ish state carried in a map; deterministic ordering by id throughout
        init-export (into {} (for [s sites :let [n (net s)] :when (> n 0)] [(:id s) n]))
        init-need   (into {} (for [s sites :let [n (net s)] :when (< n 0)] [(:id s) (- n)]))
        init-cap    (into {} (map (juxt :id :capacity) links))
        link-of     (into {} (map (juxt :id identity) links))
        deficits (->> init-need (sort-by (fn [[id need]] [(- need) id])) (mapv first))
        ;; fold over deficits, then over each deficit's incident links (sorted by id)
        final
        (reduce
         (fn [st did]
           (let [incident (->> (get adj did [])
                               (sort-by :id))]
             (reduce
              (fn [st l]
                (let [lid (:id l)
                      other (if (= (:from l) did) (:to l) (:from l))
                      need (get-in st [:need did] 0.0)
                      exp  (get-in st [:export other] 0.0)
                      capr (get-in st [:cap lid] 0.0)
                      loss (double (or (:loss l) 0))]
                  (if (or (<= need 1e-9) (<= exp 1e-9) (<= capr 1e-9)
                          (not (contains? (:export st) other)))
                    st
                    ;; sent ≤ export, ≤ remaining cap, ≤ need/(1−loss); delivered = sent·(1−loss)
                    (let [sent (min exp capr (/ need (max 1e-9 (- 1.0 loss))))
                          delivered (* sent (- 1.0 loss))]
                      (-> st
                          (update-in [:need did] - delivered)
                          (update-in [:export other] - sent)
                          (update-in [:cap lid] - sent)
                          (update-in [:flow lid] (fnil + 0.0) sent)
                          (update-in [:delivered-to did] (fnil + 0.0) delivered)
                          (update :sent (fnil + 0.0) sent)
                          (update :delivered (fnil + 0.0) delivered)
                          (update :loss (fnil + 0.0) (* sent loss)))))))
              st incident)))
         {:need (into {} init-need) :export (into {} init-export) :cap (into {} init-cap)
          :flow {} :delivered-to {} :sent 0.0 :delivered 0.0 :loss 0.0}
         deficits)
        total-need (reduce + 0.0 (vals init-need))
        total-surplus (reduce + 0.0 (vals init-export))
        unserved (reduce + 0.0 (vals (:need final)))
        curtailed (reduce + 0.0 (vals (:export final)))
        site-rows (into {}
                        (for [s sites :let [id (:id s) n (net s)]]
                          [id {:net n :role (:role s)
                               :served (get-in final [:delivered-to id] (if (>= n 0) n 0.0))
                               :unserved (get (:need final) id 0.0)
                               :curtailed (get (:export final) id 0.0)}]))
        link-rows (into {}
                        (for [l links :let [id (:id l) f (get-in final [:flow id] 0.0)]]
                          [id {:from (:from l) :to (:to l) :capacity (:capacity l) :loss (:loss l)
                               :flow f :util (if (pos? (:capacity l)) (/ f (double (:capacity l))) 0.0)}]))]
    {:sites site-rows :links link-rows
     :delivered (:delivered final) :sent (:sent final) :loss (:loss final)
     :need total-need :surplus total-surplus
     :unserved unserved :curtailed curtailed
     :served-fraction (if (> total-need 1e-9) (/ (:delivered final) total-need) 1.0)}))

;; ── N-1 contingency (which link, if lost, hurts service most) ─────────────────

(defn contingency
  "For each link, re-solve with that link removed; return [{:link id :unserved Δ …}…]
  sorted worst-first. The worst link is the critical chokepoint → routed to REDUNDANCY."
  [{:keys [sites links]}]
  (let [base (solve {:sites sites :links links})
        base-unserved (:unserved base)]
    (->> links
         (map (fn [l]
                (let [s (solve {:sites sites :links (remove #(= (:id %) (:id l)) links)})
                      du (- (:unserved s) base-unserved)]
                  {:link (:id l) :from (:from l) :to (:to l)
                   :unserved-with-out (:unserved s)
                   :delta-unserved du
                   :base-flow (get-in base [:links (:id l) :flow] 0.0)})))
         (sort-by (fn [r] [(- (:delta-unserved r)) (:link r)]))
         vec)))

;; ── chokepoint concentration (per-site import-dependence) ─────────────────────

(defn import-dependence
  "For each deficit site, the fraction of its served import that arrives over its single
  most-loaded link (1.0 = a single import path = SPOF). The amime concentration signal —
  the energy-flow 取 routed to REDUNDANCY (this is what kaname's :energy layer consumes)."
  [sln links]
  (->> (:sites sln)
         (keep (fn [[sid sr]]
                 (when (> (:served sr) 1e-9)
                   (let [incident (->> links
                                       (filter #(or (= (:from %) sid) (= (:to %) sid)))
                                       (map #(get-in sln [:links (:id %)]))
                                       (filter some?)
                                       (filter #(> (:flow %) 1e-9)))
                         inflow (->> incident (map :flow) (reduce + 0.0))
                         top (->> incident (map :flow) (reduce max 0.0))]
                     (when (> inflow 1e-9)
                       [sid {:served (:served sr) :n-suppliers (count incident)
                             :dependence (/ top inflow)}])))))
         (into {})))

;; ── markdown report (resilience map, never a target-list) ──────────────────────

(defn- f1 [x] (format "%.1f" (double x)))
(defn- pct [x] (format "%.0f%%" (* 100.0 (double x))))

(defn report-md [seed]
  (let [sln (solve seed)
        cont (contingency seed)
        dep (import-dependence sln (:links seed))
        crit (first cont)]
    (str
     "# amime 網目 — multi-site energy MESH flow report\n\n"
     "A COMMONS mesh, never a market (no price / no trade). Rewards ORDERED FLOW, never "
     "consumed energy (mio PoUF). A resilience MAP, **never a target-list**. SIM ONLY — "
     "amime never dispatches; hikari actuates under Council gate. ADR-2606212020.\n\n"
     "**Mesh balance**: surplus " (f1 (:surplus sln)) " kW · need " (f1 (:need sln))
     " kW · delivered " (f1 (:delivered sln)) " kW (served " (pct (:served-fraction sln))
     ") · transmission loss " (f1 (:loss sln)) " kW · curtailed " (f1 (:curtailed sln))
     " kW · unserved " (f1 (:unserved sln)) " kW.\n\n"
     "## Link utilisation (ordered flow over the mesh)\n\n"
     "| link | from → to | capacity kW | flow kW | util | loss |\n|---|---|---:|---:|---:|---:|\n"
     (str/join "\n"
       (for [[lid lr] (sort-by key (:links sln))]
         (str "| " lid " | " (name (:from lr)) " → " (name (:to lr))
              " | " (f1 (:capacity lr)) " | " (f1 (:flow lr)) " | " (pct (:util lr))
              " | " (pct (:loss lr)) " |")))
     "\n\n## N-1 contingency — critical link → REDUNDANCY (never a target)\n\n"
     (if crit
       (str "Worst single-link loss: **" (name (:link crit)) "** ("
            (name (:from crit)) " → " (name (:to crit)) ") — its loss raises unserved by **"
            (f1 (:delta-unserved crit)) " kW**. Route to redundancy / second path.\n\n")
       "")
     "| link | unserved if lost (kW) | Δ vs base | base flow |\n|---|---:|---:|---:|\n"
     (str/join "\n"
       (for [r cont]
         (str "| " (name (:link r)) " | " (f1 (:unserved-with-out r))
              " | +" (f1 (:delta-unserved r)) " | " (f1 (:base-flow r)) " |")))
     "\n\n## Import-dependence (SPOF map → REDUNDANCY)\n\n"
     "_dependence 1.0 = a single import path; the energy-flow 取 routed to redundancy._\n\n"
     "| site | suppliers | top-path dependence |\n|---|---:|---:|\n"
     (str/join "\n"
       (for [[sid d] (sort-by key dep)]
         (str "| " (name sid) " | " (:n-suppliers d) " | " (pct (:dependence d)) " |")))
     "\n\n_Chokepoints route to REDUNDANCY (hikari multi-site mesh, ADR-2606091800) and feed "
     "the org Flowrate (mio 澪) + kaname 要's :energy domain layer. amime never dispatches (G4).\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/amime/kotoba/seed.edn")
           seed (classify (load-edn seed-path))
           sln (solve seed)]
       (println (report-md seed))
       (println (str "-- " (count (:sites seed)) " sites, " (count (:links seed))
                     " links; served " (pct (:served-fraction sln)) " --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
