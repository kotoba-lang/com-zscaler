(ns fuchi.methods.route
  "route.cljc — 扶持 (fuchi) in-kind rail decomposition + governance gate. 1:1 Clojure port
  of `methods/route.py` (ADR-2606052300).

  1. route-envelope — decompose a maintainer's sustenance envelope into delivery RAILS over
     the EXISTING producing actors / commons (housing→commons-land, food→mitsuho, …). The
     irreducible external fiat need is routed ONLY as member-principal warifu 0% liquidity
     (the member is the payer; 扶持 never the creditor). A :cash rail is UNREPRESENTABLE
     (cash≡0) → ex-info.
  2. gov-route — the governance gate, a PURE FUNCTION of (imputed total, invariant touch,
     rider hit). rider → :refused; invariant-touch → :council-lv7; above ceiling → :sbt-vote;
     else → :auto. 扶持 computes + routes; never DECIDES accept/reject (非裁定).

  House style: ':…' strings stay strings; pure fns; closed-vocab/gate → ex-info. Portable .cljc."
  (:require [clojure.string :as str]))

;; G3 — envelope line → [rail-kind provider-actor]. The closed map (mirror of the ontology).
(def LINE-TO-RAIL
  (array-map
   "housing"   ["housing-commons" "commons-land"]
   "food"      ["food-mitsuho" "mitsuho"]
   "energy"    ["energy-hikari" "hikari"]
   "compute"   ["compute-murakumo" "murakumo"]
   "tooling"   ["tooling-okaimono" "okaimono"]
   "care"      ["care-iyashi" "iyashi"]
   "liquidity" ["liquidity-warifu" "warifu"]))

(def IN-KIND-LINES ["housing" "food" "energy" "compute" "tooling" "care"])

;; G7 — governance thresholds (imputed USD micros / yr).
(def OPTIMISTIC-CEILING-USD-MICROS-YR 24000000000)   ;; ~$24k/yr in-kind: auto fast-path below
;; Charter-Rider §2(a)-(h) hard-gate tokens (local mirror of charter_rider.scan()).
(def RIDER-FORBIDDEN
  ["advertis" "affiliate" "adsense" "weapon" "munition" "fire-control"
   "surveillance" "biometric" "addictive" "dark-pattern" "広告" "兵器"])
;; Allocation contexts that touch a constitutional invariant → Council Lv7+.
(def INVARIANT-TOUCH-TOKENS
  ["commons-land" "land-grant" "new-land" "force" "license-change" "charter"])

(defn- kw* [v]
  (-> (str (or v "")) (#(if (str/starts-with? % ":") (subs % 1) %))
      (str/split #"/") last str/lower-case))

;; ── Rail record ─────────────────────────────────────────────────────────────
(defn make-rail
  [{:keys [kind provider-actor imputed-usd-micros-yr member-principal]
    :or {member-principal false}}]
  {:kind kind :provider-actor provider-actor
   :imputed-usd-micros-yr imputed-usd-micros-yr :member-principal member-principal})

(defn route-envelope
  "Decompose envelope lines → in-kind delivery rails. The liquidity line becomes a
  member-principal warifu rail (扶持 never pays); a :cash line is an ex-info (cash≡0)."
  [envelope]
  (mapv
   (fn [line]
     (let [kind-kw (kw* (get line ":envelope/line" ""))]
       (when (contains? #{"cash" "cash-disbursement" "stipend"} kind-kw)
         (throw (ex-info "cash≡0 INVARIANT: a cash/stipend rail is UNREPRESENTABLE (扶持 never pays cash)" {})))
       (when (not= (long (get line ":envelope/cash-usd-micros" 0)) 0)
         (throw (ex-info "cash≡0 INVARIANT: :envelope/cash-usd-micros must be 0" {})))
       (when-not (contains? LINE-TO-RAIL kind-kw)
         (throw (ex-info (str "G3: envelope line '" kind-kw "' has no in-kind rail") {:line kind-kw})))
       (let [[rail-kind provider] (get LINE-TO-RAIL kind-kw)
             imputed (long (get line ":envelope/imputed-usd-micros-yr" 0))]
         (make-rail {:kind rail-kind :provider-actor provider
                     :imputed-usd-micros-yr imputed
                     :member-principal (= kind-kw "liquidity")}))))
   envelope))

(defn- round4 [x]
  #?(:clj (-> (BigDecimal. (double x)) (.setScale 4 java.math.RoundingMode/HALF_EVEN) .doubleValue)
     :cljs (let [s (* x 1e4) f (js/Math.floor s) d (- s f)]
             (/ (cond (< d 0.5) f (> d 0.5) (inc f) :else (if (even? f) f (inc f))) 1e4))))

(defn in-kind-coverage
  "Fraction of total imputed value delivered IN KIND (vs member-principal liquidity)."
  [rails]
  (let [total (reduce + 0 (map :imputed-usd-micros-yr rails))]
    (if (<= total 0)
      1.0
      (let [in-kind (reduce + 0 (map :imputed-usd-micros-yr (remove :member-principal rails)))]
        (round4 (/ (double in-kind) (double total)))))))

(defn rider-hit
  "Return the first forbidden token found in the joined lowercased texts, else \"\"."
  [& texts]
  (let [blob (str/lower-case (str/join " " (map #(or % "") texts)))]
    (or (some #(when (str/includes? blob %) %) RIDER-FORBIDDEN) "")))

(defn touches-invariant
  [& texts]
  (let [blob (str/lower-case (str/join " " (map #(or % "") texts)))]
    (boolean (some #(str/includes? blob %) INVARIANT-TOUCH-TOKENS))))

(defn gov-route
  "G7 INVARIANT — route is a PURE FUNCTION of (imputed total, invariant touch, rider).
  扶持 never decides; this only ROUTES to the body that decides (非裁定)."
  [imputed-total-usd-micros-yr invariant-touch rider]
  (cond
    (and rider (not= rider "")) "refused"   ;; Charter-Rider §2 hit
    invariant-touch "council-lv7"           ;; e.g. a new commons-land grant
    (> imputed-total-usd-micros-yr OPTIMISTIC-CEILING-USD-MICROS-YR) "sbt-vote"
    :else "auto"))
