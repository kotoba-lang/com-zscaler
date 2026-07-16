(ns fuchi.methods.provision
  "provision.cljc — 扶持 (fuchi) R1(a): wire in-kind rails to the real producing actors.
  1:1 Clojure port of `methods/provision.py` (ADR-2606052300).

  Takes the routing rails (from route.cljc) and emits a PROVISIONING INTENT per rail,
  addressed to the real producing actor / commons / infra. An intent is a DRY-RUN at R0/R1:
  published is structurally false (G10), cash is structurally 0 (G2), server-held-key is
  false (G9). The liquidity intent is member-principal (warifu 0% qard-ḥasan; 扶持 never
  holds, lends, or pays).

  House style: ':…' strings stay strings; pure fns; structural gates → ex-info; the abaki
  routing-policy file read is at the #?(:clj) edge. Portable .cljc."
  (:require [clojure.string :as str]
            [fuchi.methods.live-gate :as live-gate]))

;; rail kind → [provider-id provider-kind short-label]. Mirrors route/LINE-TO-RAIL.
(def PROVIDER-REGISTRY
  (array-map
   "housing-commons"  ["commons-land" "commons" "LANDS.md commons"]
   "food-mitsuho"     ["did:web:etzhayyim.com:actor:mitsuho" "actor" "mitsuho 瑞穂"]
   "energy-hikari"    ["did:web:etzhayyim.com:actor:hikari" "actor" "hikari 光"]
   "compute-murakumo" ["murakumo" "infra" "Murakumo mesh"]
   "tooling-okaimono" ["did:web:etzhayyim.com:actor:okaimono" "actor" "okaimono 御買物"]
   "care-iyashi"      ["did:web:etzhayyim.com:actor:iyashi" "actor" "iyashi 癒"]
   "liquidity-warifu" ["did:web:etzhayyim.com:actor:warifu" "actor" "warifu 割符 (0% qard-ḥasan)"]))

(defn make-provisioning-intent
  "ProvisioningIntent dataclass equivalent. __post_init__: cash≡0 (G2), no-server-key (G9),
  published=false (G10), known rail kind (G3)."
  [{:keys [alloc-id rail-kind provider-did provider-kind imputed-usd-micros-yr
           member-principal cash-usd-micros server-held-key published]
    :or {member-principal false cash-usd-micros 0 server-held-key false published false}}]
  (when (not= cash-usd-micros 0)
    (throw (ex-info "cash≡0 INVARIANT (G2): a provisioning intent never moves cash" {})))
  (when server-held-key
    (throw (ex-info "no-server-key INVARIANT (G9): the intent is member/Council-signed" {})))
  (when published
    (throw (ex-info "G10: published must be false — live provisioning is Council Lv6+ + operator gated" {})))
  (when-not (contains? PROVIDER-REGISTRY rail-kind)
    (throw (ex-info (str "G3: rail kind '" rail-kind "' has no provider") {:rail-kind rail-kind})))
  {:alloc-id alloc-id :rail-kind rail-kind :provider-did provider-did
   :provider-kind provider-kind :imputed-usd-micros-yr imputed-usd-micros-yr
   :member-principal (boolean member-principal) :cash-usd-micros 0
   :server-held-key false :published false})

(defn- rail-kind* [r] (or (:kind r) (get r "kind")))
(defn- rail-imputed* [r]
  (long (or (:imputed-usd-micros-yr r)
            (get r "imputedUsdMicrosYr") (get r "imputed_usd_micros_yr") 0)))
(defn- rail-member-principal* [r]
  (let [v (:member-principal r)]
    (if (nil? v) (boolean (or (get r "memberPrincipal") (get r "member_principal") false)) v)))

#?(:clj
   (defn- abaki-blocked-ids
     "Read the abaki Anti-Monopoly routing-policy.json (if present) → set of blocked entity ids.
     File I/O only at this edge; absent/malformed → empty set."
     []
     (try
       (let [f (-> (clojure.java.io/file *file*) .getParentFile .getParentFile .getParentFile
                   (clojure.java.io/file "abaki" "out" "routing-policy.json"))]
         (if (.exists f)
           ;; minimal JSON-free extraction: parse "id" fields under blocked_entities
           (set (map second (re-seq #"\"id\"\s*:\s*\"([^\"]*)\"" (slurp f))))
           #{}))
       (catch Exception _ #{}))))

(defn provision
  "Map routing rails → provisioning intents addressed to real producing actors.
  `rails` are route rail maps (or dicts). Applies the abaki block-list (G-route-around)."
  [rails alloc-id]
  (let [blocked-ids #?(:clj (abaki-blocked-ids) :cljs #{})]
    (mapv
     (fn [r]
       (let [kind (rail-kind* r)
             imputed (rail-imputed* r)
             member-principal (rail-member-principal* r)
             [provider-did provider-kind _label] (get PROVIDER-REGISTRY kind)]
         (doseq [blocked-id blocked-ids]
           (when (and (seq blocked-id) (str/includes? provider-did blocked-id))
             (throw (ex-info (str "Provider " provider-did " blocked by abaki Anti-Monopoly policy. "
                                  "React mechanism triggered: Route Around " blocked-id ".") {}))))
         (make-provisioning-intent
          {:alloc-id alloc-id :rail-kind kind :provider-did provider-did
           :provider-kind provider-kind :imputed-usd-micros-yr imputed
           :member-principal (boolean member-principal)})))
     rails)))

(defn dispatch-live
  "Authorize LIVE provisioning dispatch. require-gate passes (R2 autonomous); cash≡0 / no-key
  remain structural on each wrapped intent."
  [intents gate & {:keys [env]}]
  (live-gate/require-gate gate env)
  (mapv
   (fn [i]
     (when (not= (:cash-usd-micros i) 0)
       (throw (ex-info "cash≡0 INVARIANT (G2) holds in live mode too" {})))
     (when (:server-held-key i)
       (throw (ex-info "no-server-key INVARIANT (G9) holds in live mode too" {})))
     {:intent i :operator-did (:operator-did gate) :council-level (:council-level gate)
      :member-signature (:member-signature gate) :authorized-to-publish true})
   intents))
