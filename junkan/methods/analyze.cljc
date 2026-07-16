#!/usr/bin/env bb
;; junkan 循環 — governance-asymmetry system-dynamics read-off (clj-native, pure stdlib).
(ns junkan.methods.analyze
  "junkan 循環 — the ANALYSIS-ONLY system-dynamics read-off over the global
  governance-asymmetry instruments (ADR-2605290927).

  Each instrument feeds an asymmetry STOCK (information / participation / coercion
  / paradigm / economic) with a polarity (:widen / :narrow / :ambiguous) and a
  disclosed-hypothesis magnitude. junkan reads off:

    1. per-instrument signed contribution  = sign(polarity) · magnitude · confidence
    2. per-stock net pressure (mean of contributions) → REGIME
         :vicious (net widening) / :virtuous (net narrowing) / :neutral / :transitioning
    3. per-LOOP regime (from the loop's dominant stock; HYPOTHESIS only, G5)
    4. Meadows LEVERAGE candidates (the deepest-leverage narrowing instruments +
       the highest-pressure widening instruments whose reversibility is favorable)
       — CANDIDATES WITH UNCERTAINTY, never directives (G11 prescription? false)
    5. coverage (jurisdictions / kinds / stocks / sourcing) + an ingest worklist.

  DISCIPLINE (the analysis-only spine):
    G4  this namespace has NO outward channel — it returns data / writes a local
        ledger; there is no post/mention/email/tx path (enforced by absence).
    G5  every loop/regime is a HYPOTHESIS (`hypothesis? true`); correlation/sign
        only, never proven causation. `causal-proven` is unrepresentable.
    G6  aggregate-only — instruments + institutional enactors; no private individual.
    G7  sober, non-eschatological framing in the report; no doom/ranking-to-shame.
    G11 leverage points are candidates (`prescription? false`); no directive issued."
  (:require [clojure.string :as str]))

;; ── stock catalog (display order + labels) ───────────────────────────────────
(def stock-order
  [:information-asymmetry :participation-barrier :coercion-asymmetry
   :paradigm-subordination :economic-capture])

(def stock-label
  {:information-asymmetry  "A 情報・可視性の非対称 (information)"
   :participation-barrier  "B 参入・代表性の障壁 (participation)"
   :coercion-asymmetry     "C 強制力の非対称 (coercion)"
   :paradigm-subordination "D 思想・価値観の従属 (paradigm)"
   :economic-capture       "E 経済的レバレッジ集中 (economic)"})

;; ── canonical structural loops (HYPOTHESES; mirror ontology :loops) ──────────
;; :stocks = the member stocks the loop's edges connect (from ontology :loops edges).
;; A loop's regime is read off the JOINT pressure of ALL its member stocks (loop
;; "drive"), not the dominant stock alone — a more honest read of a coupled loop.
(def loops
  [{:id "R-secrecy-spiral"        :type :reinforcing :dominant :information-asymmetry
    :stocks [:information-asymmetry :economic-capture]}
   {:id "R-coercion-paradigm-lock":type :reinforcing :dominant :paradigm-subordination
    :stocks [:coercion-asymmetry :paradigm-subordination]}
   {:id "R-capture-barrier"       :type :reinforcing :dominant :economic-capture
    :stocks [:economic-capture :participation-barrier]}
   {:id "B-transparency"          :type :balancing   :dominant :information-asymmetry
    :stocks [:information-asymmetry]}
   {:id "B-participation"         :type :balancing   :dominant :participation-barrier
    :stocks [:participation-barrier]}])

;; ── pure read-off ────────────────────────────────────────────────────────────
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn polarity-sign [p]
  (case p :widen 1.0 :narrow -1.0 :ambiguous 0.0 0.0))

(defn contribution
  "Signed contribution of an instrument to its stock ∈ [-1,1]:
   +ve = widens the citizen↔state asymmetry, -ve = narrows it (HYPOTHESIS, G5)."
  [i]
  (* (polarity-sign (:polarity i))
     (double (or (:magnitude i) 0))
     (double (or (:confidence i) 1.0))))

(defn regime-of
  "Read a regime off a net pressure + spread. `pos`/`neg` = magnitudes of widening
  vs narrowing present. Transitioning = both forces strong (the stock is contested)."
  [net pos neg]
  (cond
    (and (>= pos 0.3) (>= neg 0.3) (< (Math/abs (double net)) 0.2)) :transitioning
    (>  net 0.15) :vicious
    (< net -0.15) :virtuous
    :else :neutral))

(defn stock-pressure
  "For one stock: net pressure (mean signed contribution), pos/neg force totals,
  instrument count, and the read-off regime. HYPOTHESIS (G5)."
  [instrs]
  (let [cs (map contribution instrs)
        n (count cs)
        net (if (zero? n) 0.0 (/ (reduce + cs) n))
        pos (reduce + (filter pos? cs))
        neg (- (reduce + (filter neg? cs)))]
    {:count n
     :net (round3 net)
     :widen-force (round3 pos)
     :narrow-force (round3 neg)
     :regime (regime-of net pos neg)
     :hypothesis? true}))

(defn by-stock [instruments]
  (reduce (fn [m s] (assoc m s (stock-pressure (filter #(= s (:stock %)) instruments))))
          {} stock-order))

(defn loop-drive
  "JOINT pressure of a loop's member stocks: mean net, summed widen/narrow forces.
  This is the loop's 'drive' — read from ALL connected stocks, not just the
  dominant one (HYPOTHESIS, G5)."
  [stocks member-stocks]
  (let [sps (keep #(get stocks %) member-stocks)
        n (count sps)
        net (if (zero? n) 0.0 (/ (reduce + (map :net sps)) n))
        pos (reduce + (map :widen-force sps))
        neg (reduce + (map :narrow-force sps))]
    {:drive (round3 net) :widen-force (round3 pos) :narrow-force (round3 neg)}))

(defn loop-regimes
  "Read each canonical loop's regime off the JOINT pressure of its member stocks
  (loop drive), not the dominant stock alone (HYPOTHESIS, G5). A reinforcing loop
  whose coupled stocks are jointly widening is :vicious; a balancing loop that is
  actually narrowing its stock is :virtuous (it is doing its job)."
  [stocks]
  (mapv
   (fn [{:keys [type dominant] :as lp0}]
     (let [lp (dissoc lp0 :stocks)
           member (or (:stocks lp0) [dominant])
           {:keys [drive widen-force narrow-force]} (loop-drive stocks member)
           base (regime-of drive widen-force narrow-force)
           regime (if (= type :balancing)
                    (cond (= base :virtuous) :virtuous
                          (= base :vicious)  :vicious   ;; balancer is being overwhelmed
                          :else base)
                    base)]
       (assoc lp :member-stocks (vec member)
                 :dominant-net (:net (get stocks dominant))
                 :drive drive :regime regime :hypothesis? true)))
   loops))

;; DISCLOSED leverage-scoring weights (public + auditable, like the score itself —
;; a candidate's rank is explainable, never a black box; G11 transparency).
(def leverage-weights
  {:amplify {:depth 0.6 :magnitude 0.4}            ;; deeper Meadows level + bigger narrowing pull
   :flip    {:tractability 0.5 :effective 0.5}})    ;; easier to flip + bigger widening pull
(def tractability-score {:statutory 3 :institutional 2 :constitutional 1 :cultural 1 :entrenched 0})

(defn amplify-score
  "Disclosed score for a NARROWING candidate worth amplifying. Returns {:score :components}."
  [i]
  (let [depth (/ (- 13 (double (:meadows i))) 12.0)     ;; 0..1, deeper level → higher
        mag (double (or (:magnitude i) 0))
        w (:amplify leverage-weights)
        score (+ (* (:depth w) depth) (* (:magnitude w) mag))]
    {:score (round3 score)
     :components {:depth (round3 depth) :magnitude (round3 mag) :weights w}}))

(defn flip-score
  "Disclosed score for a WIDENING candidate where a loop could flip. {:score :components}."
  [i]
  (let [tract (/ (double (get tractability-score (:reversibility i) 0)) 3.0)  ;; 0..1
        eff (* (double (or (:magnitude i) 0)) (double (or (:confidence i) 1.0)))
        w (:flip leverage-weights)
        score (+ (* (:tractability w) tract) (* (:effective w) eff))]
    {:score (round3 score)
     :components {:tractability (round3 tract) :effective (round3 eff) :weights w}}))

(defn leverage-candidates
  "Meadows leverage CANDIDATES (G11 prescription? false): the deepest-leverage
  NARROWING instruments already pushing toward balance (amplify-worthy), and the
  highest-magnitude WIDENING instruments whose reversibility is most tractable
  (where a loop could flip). Each candidate carries a DISCLOSED :score + :components
  so the rank is auditable (transparency, not a directive). Candidates WITH
  uncertainty — never directives."
  [instruments]
  (let [narrowers (->> instruments
                       (filter #(= :narrow (:polarity %)))
                       (map (fn [i] (merge {:id (:id i) :name (:name i) :jurisdiction (:jurisdiction i)
                                            :stock (:stock i) :meadows (:meadows i)
                                            :role :amplify-narrowing :prescription? false}
                                           (amplify-score i))))
                       (sort-by #(- (:score %)))
                       (take 6) vec)
        wideners (->> instruments
                      (filter #(= :widen (:polarity %)))
                      (map (fn [i] (merge {:id (:id i) :name (:name i) :jurisdiction (:jurisdiction i)
                                           :stock (:stock i) :meadows (:meadows i)
                                           :reversibility (:reversibility i)
                                           :role :flip-widening :prescription? false}
                                          (flip-score i))))
                      (sort-by #(- (:score %)))
                      (take 6) vec)]
    {:amplify narrowers :flip wideners :weights leverage-weights :prescription? false}))

;; ── continental region map (for coverage balance; coarse + uncontroversial) ──
(def jurisdiction-region
  {;; Africa
   "NG" :africa "ZA" :africa "KE" :africa "ET" :africa "RW" :africa "UG" :africa
   "ZW" :africa "AO" :africa "TZ" :africa "SD" :africa "ML" :africa "ZM" :africa
   "SN" :africa "DZ" :africa "EG" :africa "MA" :africa "TN" :africa "BW" :africa
   "GH" :africa "CD" :africa "MZ" :africa "NA" :africa "CI" :africa "ER" :africa
   "GQ" :africa "SZ" :africa "LY" :africa "MU" :africa "CV" :africa "GM" :africa
   "LR" :africa "SS" :africa "MW" :africa "GA" :africa "BF" :africa "NE" :africa
   "BJ" :africa "MG" :africa "TG" :africa "SO" :africa "GN" :africa "MR" :africa
   "TD" :africa "CF" :africa "DJ" :africa "SC" :africa "LS" :africa "KM" :africa
   "GW" :africa "ST" :africa "CM" :africa "CG" :africa "BI" :africa
   ;; Americas
   "US" :americas "BR" :americas "MX" :americas "CL" :americas "AR" :americas
   "VE" :americas "CU" :americas "CO" :americas "PE" :americas "BO" :americas
   "NI" :americas "CR" :americas "UY" :americas "CA" :americas "SV" :americas
   "HN" :americas "GT" :americas "HT" :americas "EC" :americas "JM" :americas
   "TT" :americas "BB" :americas "DO" :americas "PA" :americas "GY" :americas
   "BS" :americas "SR" :americas "LC" :americas "GD" :americas "BZ" :americas
   "AG" :americas "DM" :americas "KN" :americas "VC" :americas
   ;; Asia (incl. Middle East / Central / South / SE / East)
   "CN" :asia "KP" :asia "IN" :asia "ID" :asia "TH" :asia "PH" :asia "PK" :asia
   "VN" :asia "BD" :asia "KH" :asia "TM" :asia "AZ" :asia "KZ" :asia "LK" :asia
   "NP" :asia "MM" :asia "KR" :asia "TW" :asia "SG" :asia "MN" :asia "UZ" :asia
   "KG" :asia "TJ" :asia "LA" :asia "BT" :asia "BN" :asia "MV" :asia "IL" :asia
   "IR" :asia "IQ" :asia "SY" :asia "JO" :asia "LB" :asia "KW" :asia "BH" :asia
   "QA" :asia "SA" :asia "AE" :asia "OM" :asia "AF" :asia "TR" :asia
   ;; Europe
   "GB" :europe "EU" :europe "DE" :europe "FR" :europe "IT" :europe "HU" :europe
   "PL" :europe "SE" :europe "NO" :europe "CH" :europe "RS" :europe "BA" :europe
   "RU" :europe "BY" :europe "EE" :europe "IS" :europe "IE" :europe "AL" :europe
   "GR" :europe "ES" :europe "PT" :europe "UA" :europe "CZ" :europe "RO" :europe
   "NL" :europe "FI" :europe "BE" :europe "AT" :europe "LT" :europe "HR" :europe
   "SK" :europe "BG" :europe "GE" :europe "SI" :europe "LV" :europe "MD" :europe
   "AM" :europe "ME" :europe "MK" :europe "CY" :europe "MT" :europe "LU" :europe
   "XK" :europe "LI" :europe "AD" :europe "SM" :europe "MC" :europe "VA" :europe
   ;; Asia (Malaysia, Yemen, Hong Kong)
   "MY" :asia "YE" :asia "HK" :asia
   ;; Oceania
   "NZ" :oceania "AU" :oceania "FJ" :oceania "PG" :oceania "WS" :oceania
   "TO" :oceania "VU" :oceania "SB" :oceania "PW" :oceania "KI" :oceania
   "FM" :oceania "TV" :oceania "MH" :oceania "NR" :oceania
   ;; Asia (Timor-Leste, Japan)
   "TL" :asia "JP" :asia
   ;; transnational
   "GLOBAL" :transnational "UN" :transnational})

(defn region-of [jurisdiction] (get jurisdiction-region jurisdiction :other))

(defn coverage [instruments]
  (let [js (sort (distinct (map :jurisdiction instruments)))
        stocks-covered (set (map :stock instruments))
        missing-stocks (remove stocks-covered stock-order)
        ;; stocks with no narrowing (balancing) instrument yet = ingest worklist
        narrow-by-stock (set (map :stock (filter #(= :narrow (:polarity %)) instruments)))
        no-balancer (remove narrow-by-stock stock-order)
        ;; continental balance (distinct jurisdictions per region)
        region-juris (reduce (fn [m j] (update m (region-of j) (fnil conj #{}) j)) {} js)
        regions (into {} (map (fn [[r s]] [r (count s)]) region-juris))
        unmapped (vec (sort (get region-juris :other #{})))
        ;; self-balancing: the asymmetry stock with the FEWEST instruments
        stock-counts (into {} (map (fn [s] [s (count (filter #(= s (:stock %)) instruments))]) stock-order))
        thinnest (when (seq stock-counts) (key (apply min-key val stock-counts)))]
    {:instruments (count instruments)
     :jurisdictions (count js)
     :jurisdiction-list (vec js)
     :kinds (frequencies (map :kind instruments))
     :stocks (frequencies (map :stock instruments))
     :polarity (frequencies (map :polarity instruments))
     :sourcing (frequencies (map :sourcing instruments))
     :regions regions
     :unmapped-jurisdictions unmapped
     :stocks-without-data (vec missing-stocks)
     :stocks-without-balancer (vec no-balancer)
     :thinnest-stock (some-> thinnest name)
     :worklist (vec (concat
                     (map #(str "add data for stock " (name %)) missing-stocks)
                     (map #(str "add a narrowing/balancing instrument for stock " (name %)) no-balancer)
                     (when thinnest [(str "deepen thinnest stock: " (name thinnest)
                                          " (n=" (get stock-counts thinnest) ")")])
                     (when (seq unmapped) [(str "map region for jurisdictions: " (str/join " " unmapped))])
                     (let [thin (->> (dissoc regions :transnational)
                                     (filter (fn [[_ n]] (< n 6))) (map (comp name first)) sort)]
                       (when (seq thin) [(str "thin continental coverage: " (str/join " " thin))]))
                     ["broaden jurisdiction coverage (small states / Pacific / Caribbean still light)"]))}))

;; ── temporal era trajectory (system-dynamics over time; structural, not a ranking) ──
(def era-order
  ["pre-1800" "1800–1899" "1900–1944" "1945–1989" "1990–2009" "2010–"])

(defn era-of
  "Map an enactment year to a coarse era bucket. Undated (year 0/nil) → nil."
  [year]
  (let [y (long (or year 0))]
    (cond
      (<= y 0)   nil
      (< y 1800) "pre-1800"
      (< y 1900) "1800–1899"
      (< y 1945) "1900–1944"
      (< y 1990) "1945–1989"
      (< y 2010) "1990–2009"
      :else      "2010–")))

(defn era-trajectory
  "Fold dated instruments by era → widen/narrow force + net pressure per era. This
  reads the long-run TRAJECTORY of the citizen↔state balance (whether instruments
  enacted in each era lean toward widening or narrowing asymmetry). Structural over
  time — NOT a country ranking (G7). HYPOTHESIS (G5). Undated transnational values
  are excluded (no era)."
  [instruments]
  (let [dated (filter #(era-of (:year %)) instruments)
        by-era (group-by #(era-of (:year %)) dated)]
    (vec
     (for [era era-order
           :let [is (get by-era era)]
           :when (seq is)
           :let [cs (map contribution is)
                 pos (reduce + (filter pos? cs))
                 neg (- (reduce + (filter neg? cs)))]]
       {:era era :count (count is)
        :widen-force (round3 pos) :narrow-force (round3 neg)
        :net (round3 (/ (reduce + cs) (count cs)))
        :hypothesis? true}))))

;; ── stock × region cross-tab (which asymmetry is most active on which continent) ──
(def region-order [:africa :americas :asia :europe :oceania])

(defn region-stock-matrix
  "For each continent × asymmetry stock, the net pressure (mean signed contribution)
  and instrument count. AGGREGATE + structural (continent × stock, never a per-country
  ranking, G7); HYPOTHESIS (G5). transnational instruments are excluded (no continent)."
  [instruments]
  (into {}
        (for [r region-order
              :let [in-r (filter #(= r (region-of (:jurisdiction %))) instruments)]
              :when (seq in-r)]
          [r (into {}
                   (for [s stock-order
                         :let [is (filter #(= s (:stock %)) in-r)
                               cs (map contribution is)]
                         :when (seq is)]
                     [s {:net (round3 (/ (reduce + cs) (count cs))) :count (count is)}]))])))

(def kind-order [:law :institution :doctrine :value])

(defn kind-polarity-matrix
  "For each instrument KIND, the net pressure (mean signed contribution) + count +
  polarity mix. Answers: do laws vs institutions vs doctrines vs values systematically
  widen or narrow the citizen↔state asymmetry? Fully aggregate, no geography;
  HYPOTHESIS (G5)."
  [instruments]
  (into {}
        (for [k kind-order
              :let [in-k (filter #(= k (:kind %)) instruments)
                    cs (map contribution in-k)]
              :when (seq in-k)]
          [k {:count (count in-k)
              :net (round3 (/ (reduce + cs) (count cs)))
              :polarity (frequencies (map :polarity in-k))}])))

(defn leverage-by-region
  "Per-continent, the single most TRACTABLE flip candidate (widening instrument
  whose loop could most plausibly flip, by flip-score). CANDIDATES with uncertainty,
  never directives (G11); grouped structurally by continent, never a country shame-
  ranking (G7). Returns {region {…candidate…}}."
  [instruments]
  (into {}
        (for [r region-order
              :let [wideners (->> instruments
                                  (filter #(and (= :widen (:polarity %))
                                                (= r (region-of (:jurisdiction %)))))
                                  (map (fn [i] (merge {:id (:id i) :name (:name i)
                                                       :jurisdiction (:jurisdiction i)
                                                       :stock (:stock i) :prescription? false}
                                                      (flip-score i))))
                                  (sort-by #(- (:score %))))]
              :when (seq wideners)]
          [r (first wideners)])))

(defn extreme-instruments
  "The strongest concrete signals: top-n instruments by signed contribution at each
  pole — the most widening and the most narrowing named laws/institutions globally.
  Concrete examples behind the aggregates (HYPOTHESIS, G5)."
  [instruments n]
  (let [scored (map (fn [i] {:id (:id i) :name (:name i) :jurisdiction (:jurisdiction i)
                             :stock (:stock i) :contribution (round3 (contribution i))})
                    instruments)
        sorted (sort-by :contribution scored)]
    {:most-widening (vec (reverse (take-last n sorted)))
     :most-narrowing (vec (take n sorted))}))

(defn headline
  "A compact, structured digest of the most salient read-off (HYPOTHESIS, G5):
  the most-pressured asymmetry stock, the latest-era net trend, and the kind whose
  instruments lean most toward narrowing. Aggregate + sober (G7); a summary, never
  a directive (G11)."
  [instruments stocks-map kind-mat traj]
  (let [most-pressured (->> stocks-map
                            (map (fn [[k v]] [k (:net v)]))
                            (sort-by (comp - second))
                            first)
        latest-era (last traj)
        narrowest-kind (->> kind-mat
                            (map (fn [[k v]] [k (:net v)]))
                            (sort-by second)
                            first)
        top-widen (->> instruments (sort-by #(- (contribution %))) first)]
    {:most-pressured-stock (some-> most-pressured first name)
     :most-pressured-net (some-> most-pressured second)
     :latest-era (:era latest-era)
     :latest-era-net (:net latest-era)
     :narrowest-kind (some-> narrowest-kind first name)
     :narrowest-kind-net (some-> narrowest-kind second)
     :strongest-widening-instrument (:name top-widen)
     :strongest-widening-jurisdiction (:jurisdiction top-widen)
     :hypothesis? true}))

(defn analyze
  "Full read-off bundle. Pure; no I/O; no outward channel (G4)."
  [instruments]
  (let [stocks (by-stock instruments)
        kind-mat (kind-polarity-matrix instruments)
        traj (era-trajectory instruments)]
    {"stocks" (into {} (map (fn [[k v]] [(name k) v]) stocks))
     "headline" (headline instruments stocks kind-mat traj)
     "extremes" (extreme-instruments instruments 5)
     "loops" (loop-regimes stocks)
     "leverage" (leverage-candidates instruments)
     "trajectory" traj
     "region_stock" (into {} (map (fn [[r m]]
                                    [(name r) (into {} (map (fn [[s v]] [(name s) v]) m))])
                                  (region-stock-matrix instruments)))
     "leverage_by_region" (into {} (map (fn [[r c]] [(name r) c])
                                        (leverage-by-region instruments)))
     "kind_polarity" (into {} (map (fn [[k v]] [(name k) v]) kind-mat))
     "coverage" (coverage instruments)
     "hypothesis_only" true
     "actuation_taken" false}))

;; ── datom emission (append-only EAVT; flagged; HYPOTHESIS) ───────────────────
(defn- add [e a v] [":db/add" e a v])

(defn instrument-datoms
  "Append-only EAVT datom vectors for the disclosed instrument facts + the derived
  signed contribution. Person-free (G6): :enactor/:stakeholders are institutional
  strings. No :junkan/actuate or :junkan/dispatch attribute is ever emitted (G4)."
  [instruments]
  (vec
   (mapcat
    (fn [i]
      (let [e (str "junkan-instr:" (:id i))]
        [(add e ":junkan.gov.instr/name" (str (:name i)))
         (add e ":junkan.gov.instr/jurisdiction" (str (:jurisdiction i)))
         (add e ":junkan.gov.instr/kind" (str (:kind i)))
         (add e ":junkan.gov.instr/year" (long (or (:year i) 0)))
         (add e ":junkan.gov.instr/enactor" (str (:enactor i)))
         (add e ":junkan.gov.instr/origin" (str (:origin i)))
         (add e ":junkan.gov.instr/stock" (str (:stock i)))
         (add e ":junkan.gov.instr/polarity" (str (:polarity i)))
         (add e ":junkan.gov.instr/contribution" (round3 (contribution i)))
         (add e ":junkan.gov.instr/meadows" (long (or (:meadows i) 0)))
         (add e ":junkan.gov.instr/basis" (str (:basis i)))
         (add e ":junkan/sourcing" (str (:sourcing i)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    instruments)))

(defn stock-datoms [analysis]
  (vec
   (mapcat
    (fn [[s sp]]
      (let [e (str "junkan-stock:" s)]
        [(add e ":junkan.gov.stock/net" (:net sp))
         (add e ":junkan.gov.stock/widen-force" (:widen-force sp))
         (add e ":junkan.gov.stock/narrow-force" (:narrow-force sp))
         (add e ":junkan.gov.stock/regime" (str (:regime sp)))
         (add e ":junkan.gov.stock/count" (long (:count sp)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    (get analysis "stocks"))))

(defn loop-datoms [analysis]
  (vec
   (mapcat
    (fn [lp]
      (let [e (str "junkan-loop:" (:id lp))]
        [(add e ":junkan.gov.loop/type" (str (:type lp)))
         (add e ":junkan.gov.loop/dominant-stock" (str (:dominant lp)))
         (add e ":junkan.gov.loop/drive" (:drive lp))
         (add e ":junkan.gov.loop/regime" (str (:regime lp)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    (get analysis "loops"))))

(defn era-datoms [analysis]
  (vec
   (mapcat
    (fn [e]
      (let [ent (str "junkan-era:" (:era e))]
        [(add ent ":junkan.gov.era/net" (:net e))
         (add ent ":junkan.gov.era/widen-force" (:widen-force e))
         (add ent ":junkan.gov.era/narrow-force" (:narrow-force e))
         (add ent ":junkan.gov.era/count" (long (:count e)))
         (add ent ":junkan/hypothesis" ":true")
         (add ent ":junkan/derived" true)]))
    (get analysis "trajectory"))))

(defn region-stock-datoms [analysis]
  (vec
   (mapcat
    (fn [[region m]]
      (mapcat
       (fn [[stock {:keys [net count]}]]
         (let [ent (str "junkan-region-stock:" region ":" stock)]
           [(add ent ":junkan.gov.rs/region" (str ":" region))
            (add ent ":junkan.gov.rs/stock" (str ":" stock))
            (add ent ":junkan.gov.rs/net" net)
            (add ent ":junkan.gov.rs/count" (long count))
            (add ent ":junkan/hypothesis" ":true")
            (add ent ":junkan/derived" true)]))
       m))
    (get analysis "region_stock"))))

(defn datoms
  "All findings datoms for one analysis (instruments + stocks + loops + era + region×stock)."
  [instruments analysis]
  (vec (concat (instrument-datoms instruments)
               (stock-datoms analysis)
               (loop-datoms analysis)
               (era-datoms analysis)
               (region-stock-datoms analysis))))

(defn render-datoms [instruments analysis]
  (str "[\n " (str/join "\n " (map pr-str (datoms instruments analysis))) "\n]\n"))

;; ── markdown report (sober / non-eschatological / map-not-rank, G7) ──────────
(defn render-report [analysis]
  (let [stocks (get analysis "stocks")
        cov (get analysis "coverage")]
    (str
     "# junkan 循環 — 国民↔政府 非対称の system-dynamics read-off\n\n"
     "全世界の **具体的な法律・制度・思想・価値観** が citizen↔state の構造的非対称をどう"
     "広げる/狭めるかを、5 つの asymmetry STOCK と feedback LOOP で読み取る。"
     "**分析専用 (G4): junkan は観るだけで触れない。** 各 regime / leverage は **仮説 (G5)** であり"
     "因果の証明ではない。これは resilience/leverage の MAP であって、国家を晒す ranking ではない (G7)。\n\n"
     "_coverage_: " (:instruments cov) " instruments · " (:jurisdictions cov)
     " jurisdictions · sourcing " (pr-str (:sourcing cov)) "\n\n"
     "_continental balance_: "
     (str/join " · " (for [[r n] (sort-by (comp - val) (:regions cov))]
                       (str (name r) " " n))) "\n\n"
     (let [h (get analysis "headline")]
       (str "**要点 (HYPOTHESIS, G5):** いま最も非対称が広がる方向に圧力がかかる stock は "
            "**" (:most-pressured-stock h) "** (net " (:most-pressured-net h) ")。"
            "直近 era **" (:latest-era h) "** の net は " (:latest-era-net h) "。"
            "instrument 種類のうち最も是正方向に傾くのは **" (:narrowest-kind h)
            "** (net " (:narrowest-kind-net h) ")。"
            "最も強く非対称を広げる instrument は **" (:strongest-widening-instrument h)
            "** (" (:strongest-widening-jurisdiction h) ")。\n\n"))
     "## Strongest concrete signals (HYPOTHESIS, G5)\n\n"
     "**最も非対称を広げる instrument:**\n"
     (str/join "\n" (for [c (get-in analysis ["extremes" :most-widening])]
                      (str "- [" (:contribution c) "] " (:name c) " (" (:jurisdiction c)
                           ", " (name (:stock c)) ")")))
     "\n\n**最も非対称を是正する instrument:**\n"
     (str/join "\n" (for [c (get-in analysis ["extremes" :most-narrowing])]
                      (str "- [" (:contribution c) "] " (:name c) " (" (:jurisdiction c)
                           ", " (name (:stock c)) ")")))
     "\n\n## Asymmetry stocks (regime = HYPOTHESIS)\n\n"
     "| stock | n | net pressure | widen | narrow | regime |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [s stock-order
                     :let [sp (get stocks (name s))]
                     :when sp]
                 (str "| " (stock-label s)
                      " | " (:count sp)
                      " | " (:net sp)
                      " | " (:widen-force sp)
                      " | " (:narrow-force sp)
                      " | " (name (:regime sp)) " |")))
     "\n\n_net > 0 = 非対称が広がる方向に loop が回っている (悪循環傾向); net < 0 = 是正方向 (好循環)。_\n\n"
     "## Structural loops (HYPOTHESIS, G5 — drive = joint pressure of member stocks)\n\n"
     "| loop | type | member stocks | drive | regime |\n"
     "|---|---|---|---|---|\n"
     (str/join "\n"
               (for [lp (get analysis "loops")]
                 (str "| " (:id lp) " | " (name (:type lp))
                      " | " (str/join ", " (map name (:member-stocks lp)))
                      " | " (:drive lp)
                      " | " (name (:regime lp)) " |")))
     "\n\n## Stock × continent (where each asymmetry is most active, HYPOTHESIS, G5)\n\n"
     "_net pressure per continent × stock (aggregate, structural — NOT a per-country ranking, G7). net>0 = widening-leaning._\n\n"
     "| continent | info | participation | coercion | paradigm | economic |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r region-order
                     :let [m (get (get analysis "region_stock") (name r))]
                     :when m]
                 (str "| " (name r)
                      (apply str (for [s stock-order]
                                   (let [c (get m (name s))]
                                     (str " | " (if c (:net c) "·"))))) " |")))
     "\n\n## Instrument kind × net pressure (do laws/institutions/doctrines/values widen or narrow?)\n\n"
     "| kind | n | net | polarity mix |\n|---|---|---|---|\n"
     (str/join "\n" (for [k kind-order
                          :let [m (get (get analysis "kind_polarity") (name k))]
                          :when m]
                      (str "| " (name k) " | " (:count m) " | " (:net m)
                           " | " (pr-str (:polarity m)) " |")))
     "\n\n## Era trajectory (system-dynamics over time, HYPOTHESIS, G5)\n\n"
     "_各時代に制定された instrument が非対称を広げる/狭める方向にどれだけ傾くか。構造の時系列であって国家 ranking ではない (G7)。undated な transnational values は除外。_\n\n"
     "| era | n | widen | narrow | net |\n"
     "|---|---|---|---|---|\n"
     (str/join "\n"
               (for [e (get analysis "trajectory")]
                 (str "| " (:era e) " | " (:count e)
                      " | " (:widen-force e) " | " (:narrow-force e)
                      " | " (:net e) " |")))
     "\n\n## Meadows leverage CANDIDATES (G11 — candidates, never directives)\n\n"
     "_scores are DISCLOSED + weighted (amplify = 0.6·depth + 0.4·magnitude; flip = "
     "0.5·tractability + 0.5·magnitude·confidence) — the rank is auditable, not a directive._\n\n"
     "**増幅候補 (既に是正方向に働く深いレバレッジ instrument):**\n"
     (str/join "\n" (for [c (get-in analysis ["leverage" :amplify])]
                      (str "- [" (:score c) "] L" (:meadows c) " · " (:name c) " ("
                           (:jurisdiction c) ", " (name (:stock c)) ")")))
     "\n\n**反転候補 (非対称を広げており、最も是正余地のある instrument):**\n"
     (str/join "\n" (for [c (get-in analysis ["leverage" :flip])]
                      (str "- [" (:score c) "] L" (:meadows c) " · " (:name c) " ("
                           (:jurisdiction c) ", " (name (:stock c))
                           ", reversibility=" (name (or (:reversibility c) :-)) ")")))
     "\n\n## Most tractable flip candidate per continent (G11 — candidates, never directives)\n\n"
     (str/join "\n" (for [r region-order
                          :let [c (get (get analysis "leverage_by_region") (name r))]
                          :when c]
                      (str "- **" (name r) "**: [" (:score c) "] " (:name c)
                           " (" (:jurisdiction c) ", " (name (:stock c)) ")")))
     "\n\n## Coverage worklist (next /loop iterations)\n\n"
     (str/join "\n" (map #(str "- " %) (:worklist cov)))
     "\n\n_findings are append-only; surfacing beyond Council is performed by ossekai/kataribe on junkan's behalf, never by junkan (G13). actuation_taken=false throughout._\n")))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
           rows (clojure.edn/read-string (slurp seed))
           is (vec (filter #(= (:type %) :instrument) rows))
           a (analyze is)]
       (println (render-report a))
       (println (str "-- " (count is) " instruments · "
                     (get-in a ["coverage" :jurisdictions]) " jurisdictions analysed --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
