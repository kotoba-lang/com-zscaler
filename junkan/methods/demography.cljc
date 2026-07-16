#!/usr/bin/env bb
;; junkan 循環 — demographic-dynamics system-dynamics read-off (clj-native, pure stdlib).
(ns junkan.methods.demography
  "junkan 循環 — the ANALYSIS-ONLY system-dynamics read-off over demographic policy
  LEVERS (ADR-2605290927). Sibling of methods/analyze.cljc (governance asymmetry):
  same stock-flow + R/B loop + Meadows spine, applied to a population question.

  Reference case: China's one-child policy (seed.china-one-child.edn). Each lever
  feeds a demographic STOCK (fertility-rate / reproductive-cohort / small-family-norm
  / elderly-dependency / childrearing-cost) with a polarity:
    :suppress (+) lowers fertility → pushes the age structure toward COLLAPSE (vicious)
    :boost    (-) raises fertility → toward sustainable REPLACEMENT (virtuous)
  and a disclosed-hypothesis magnitude × confidence. junkan reads off:

    1. per-lever signed contribution = sign(polarity) · magnitude · confidence
    2. per-stock net pressure (mean) → REGIME (:vicious collapse-ward / :virtuous
       replacement-ward / :neutral / :transitioning)
    3. per-LOOP regime from the JOINT drive of its member stocks (HYPOTHESIS, G5)
    4. Meadows LEVERAGE candidates — the deepest BOOST levers worth amplifying, and
       the most-tractable SUPPRESS levers where a loop could flip (CANDIDATES, G11)
    5. era trajectory + coverage.

  DISCIPLINE (the analysis-only spine):
    G4  NO outward channel — returns data / writes a local ledger; no dispatch path.
    G5  every loop/regime is a HYPOTHESIS (`hypothesis? true`); lagged-sign only.
    G6  aggregate-only — cohorts + institutional enactors; NO individual / PII.
    G7  sober, non-eschatological — a resilience MAP, never a population target /
        'demographic time-bomb' framing.
   G11  leverage points are candidates (`prescription? false`); junkan NEVER
        prescribes who should reproduce (anti-coercion, §1.13 + §1.4)."
  (:require [junkan.methods.analyze :as az]
            [clojure.string :as str]))

;; ── stock catalog (display order + labels) ───────────────────────────────────
(def stock-order
  [:fertility-rate :reproductive-cohort :small-family-norm
   :elderly-dependency :childrearing-cost])

(def stock-label
  {:fertility-rate      "出生フロー強度 (fertility-rate / TFR)"
   :reproductive-cohort "出産適齢期コホート (reproductive-cohort, lag~25y)"
   :small-family-norm   "小家族規範 (small-family-norm, paradigm)"
   :elderly-dependency  "高齢扶養負担 4-2-1 (elderly-dependency)"
   :childrearing-cost   "育児・教育・住宅コスト (childrearing-cost)"})

;; ── canonical structural loops (HYPOTHESES; mirror ontology :loops) ──────────
;; :stocks = the member stocks the loop's edges connect. A loop's regime is read
;; off the JOINT pressure of ALL its member stocks (loop "drive"), not the dominant
;; stock alone — a more honest read of a coupled loop.
(def loops
  [{:id "B1-birth-control"        :type :balancing   :dominant :fertility-rate
    :stocks [:fertility-rate]}
   {:id "R1-population-momentum"  :type :reinforcing :dominant :reproductive-cohort
    :stocks [:fertility-rate :reproductive-cohort]}
   {:id "R2-norm-lockin"          :type :reinforcing :dominant :small-family-norm
    :stocks [:small-family-norm :fertility-rate]}
   {:id "R3-421-squeeze"          :type :reinforcing :dominant :elderly-dependency
    :stocks [:elderly-dependency :childrearing-cost :fertility-rate]}
   {:id "B2-pronatal-incentive"   :type :balancing   :dominant :childrearing-cost
    :stocks [:childrearing-cost]}])

;; ── pure read-off (demography-specific polarity; generic math reused from az) ──
(defn polarity-sign
  "+1 = suppresses fertility (collapse-ward / vicious sign); -1 = boosts (corrective)."
  [p]
  (case p :suppress 1.0 :boost -1.0 :ambiguous 0.0 0.0))

(defn contribution
  "Signed contribution of a lever to its stock ∈ [-1,1]:
   +ve = pushes toward demographic collapse, -ve = toward replacement (HYPOTHESIS, G5)."
  [i]
  (* (polarity-sign (:polarity i))
     (double (or (:magnitude i) 0))
     (double (or (:confidence i) 1.0))))

(defn stock-pressure
  "For one stock: net pressure (mean signed contribution), suppress/boost force totals,
  lever count, and the read-off regime. HYPOTHESIS (G5)."
  [instrs]
  (let [cs (map contribution instrs)
        n (count cs)
        net (if (zero? n) 0.0 (/ (reduce + cs) n))
        pos (reduce + (filter pos? cs))
        neg (- (reduce + (filter neg? cs)))]
    {:count n
     :net (az/round3 net)
     :suppress-force (az/round3 pos)
     :boost-force (az/round3 neg)
     :regime (az/regime-of net pos neg)
     :hypothesis? true}))

(defn by-stock [instruments]
  (reduce (fn [m s] (assoc m s (stock-pressure (filter #(= s (:stock %)) instruments))))
          {} stock-order))

(defn loop-drive
  "JOINT pressure of a loop's member stocks: mean net, summed suppress/boost forces."
  [stocks member-stocks]
  (let [sps (keep #(get stocks %) member-stocks)
        n (count sps)
        net (if (zero? n) 0.0 (/ (reduce + (map :net sps)) n))
        pos (reduce + (map :suppress-force sps))
        neg (reduce + (map :boost-force sps))]
    {:drive (az/round3 net) :suppress-force (az/round3 pos) :boost-force (az/round3 neg)}))

(defn loop-regimes
  "Read each canonical loop's regime off the JOINT drive of its member stocks
  (HYPOTHESIS, G5). A reinforcing loop whose coupled stocks are jointly suppressing
  fertility is :vicious (collapse-ward); a balancing loop actually boosting fertility
  is :virtuous (doing its job); a balancing loop being overwhelmed reads :vicious."
  [stocks]
  (mapv
   (fn [{:keys [type dominant] :as lp0}]
     (let [member (or (:stocks lp0) [dominant])
           {:keys [drive suppress-force boost-force]} (loop-drive stocks member)
           base (az/regime-of drive suppress-force boost-force)]
       (assoc (dissoc lp0 :stocks)
              :member-stocks (vec member)
              :dominant-net (:net (get stocks dominant))
              :drive drive :regime base :hypothesis? true)))
   loops))

(defn leverage-candidates
  "Meadows leverage CANDIDATES (G11 prescription? false): the deepest-leverage BOOST
  levers already pushing toward replacement (amplify-worthy), and the most-tractable
  SUPPRESS levers where a reinforcing loop could flip. Reuses az/amplify-score (depth
  + magnitude) and az/flip-score (reversibility tractability + magnitude·confidence)
  — the scoring is domain-agnostic. Each carries a DISCLOSED :score. Candidates WITH
  uncertainty — never directives."
  [instruments]
  (let [boosters (->> instruments
                      (filter #(= :boost (:polarity %)))
                      (map (fn [i] (merge {:id (:id i) :name (:name i) :jurisdiction (:jurisdiction i)
                                           :stock (:stock i) :meadows (:meadows i)
                                           :role :amplify-boost :prescription? false}
                                          (az/amplify-score i))))
                      (sort-by #(- (:score %))) (take 6) vec)
        suppressors (->> instruments
                         (filter #(= :suppress (:polarity %)))
                         (map (fn [i] (merge {:id (:id i) :name (:name i) :jurisdiction (:jurisdiction i)
                                              :stock (:stock i) :meadows (:meadows i)
                                              :reversibility (:reversibility i)
                                              :role :flip-suppress :prescription? false}
                                             (az/flip-score i))))
                         (sort-by #(- (:score %))) (take 6) vec)]
    {:amplify boosters :flip suppressors :prescription? false}))

;; ── temporal era trajectory (system-dynamics over time; structural, not a ranking) ──
(defn era-trajectory
  "Fold dated levers by era → suppress/boost force + net pressure per era, reading the
  long-run TRAJECTORY from mandate (suppress) toward reversal (boost). Structural over
  time — NOT a ranking (G7). HYPOTHESIS (G5). Reuses az/era-of for the bucketing."
  [instruments]
  (let [dated (filter #(az/era-of (:year %)) instruments)
        by-era (group-by #(az/era-of (:year %)) dated)]
    (vec
     (for [era az/era-order
           :let [is (get by-era era)]
           :when (seq is)
           :let [cs (map contribution is)
                 pos (reduce + (filter pos? cs))
                 neg (- (reduce + (filter neg? cs)))]]
       {:era era :count (count is)
        :suppress-force (az/round3 pos) :boost-force (az/round3 neg)
        :net (az/round3 (/ (reduce + cs) (count cs)))
        :hypothesis? true}))))

(defn headline
  "A compact, structured digest of the most salient read-off (HYPOTHESIS, G5)."
  [instruments stocks-map traj]
  (let [most-pressured (->> stocks-map (map (fn [[k v]] [k (:net v)]))
                            (sort-by (comp - second)) first)
        latest-era (last traj)
        top-suppress (->> instruments (sort-by #(- (contribution %))) first)]
    {:most-pressured-stock (some-> most-pressured first name)
     :most-pressured-net (some-> most-pressured second)
     :latest-era (:era latest-era)
     :latest-era-net (:net latest-era)
     :strongest-suppress-lever (:name top-suppress)
     :strongest-suppress-meadows (:meadows top-suppress)
     :hypothesis? true}))

(defn coverage [instruments]
  (let [stocks-covered (set (map :stock instruments))
        missing-stocks (remove stocks-covered stock-order)
        boost-by-stock (set (map :stock (filter #(= :boost (:polarity %)) instruments)))
        no-corrective (remove boost-by-stock stock-order)
        stock-counts (into {} (map (fn [s] [s (count (filter #(= s (:stock %)) instruments))]) stock-order))
        thinnest (when (seq stock-counts) (key (apply min-key val stock-counts)))]
    {:instruments (count instruments)
     :jurisdictions (count (distinct (map :jurisdiction instruments)))
     :kinds (frequencies (map :kind instruments))
     :stocks (frequencies (map :stock instruments))
     :polarity (frequencies (map :polarity instruments))
     :sourcing (frequencies (map :sourcing instruments))
     :stocks-without-data (vec missing-stocks)
     :stocks-without-corrective (vec no-corrective)
     :thinnest-stock (some-> thinnest name)
     :worklist (vec (concat
                     (map #(str "add data for stock " (name %)) missing-stocks)
                     (map #(str "add a boosting/corrective lever for stock " (name %)) no-corrective)
                     (when thinnest [(str "deepen thinnest stock: " (name thinnest)
                                          " (n=" (get stock-counts thinnest) ")")])
                     ["extend to peer low-fertility cases (KR / JP / IT / SG) for cross-society contrast"]))}))

;; ── cross-society contrast (per-jurisdiction read-off; structural, not a ranking) ──
(def jurisdiction-label
  {"CN" "中国 (China)" "KR" "韓国 (South Korea)" "JP" "日本 (Japan)"
   "IT" "イタリア (Italy)" "SG" "シンガポール (Singapore)"})

(defn by-jurisdiction
  "Per-society read-off: net pressure, suppress/boost force, regime, and the BINDING
  constraint = the demographic stock under the highest collapse-ward pressure in that
  society, plus its strongest single driver lever. AGGREGATE + structural (a society's
  own stock mix, NEVER a cross-country good/bad ranking, G7); HYPOTHESIS (G5)."
  [instruments]
  (into {}
        (for [j (sort (distinct (map :jurisdiction instruments)))
              :let [in-j (filter #(= j (:jurisdiction %)) instruments)
                    cs (map contribution in-j)
                    n (count cs)
                    net (if (zero? n) 0.0 (/ (reduce + cs) n))
                    pos (reduce + (filter pos? cs))
                    neg (- (reduce + (filter neg? cs)))
                    stocks (by-stock in-j)
                    present (filter (fn [[_ sp]] (pos? (:count sp))) stocks)
                    dom (when (seq present) (key (apply max-key (comp :net val) present)))
                    top-driver (->> in-j (sort-by #(- (contribution %))) first)]]
          [j {:count n
              :net (az/round3 net)
              :suppress-force (az/round3 pos)
              :boost-force (az/round3 neg)
              :regime (az/regime-of net pos neg)
              :binding-stock (some-> dom name)
              :binding-stock-net (some-> dom (#(:net (get stocks %))))
              :top-driver (:name top-driver)
              :top-driver-stock (some-> (:stock top-driver) name)}])))

(defn society-contrast
  "Vector of per-society summaries sorted by net collapse-ward pressure (most-pressured
  first). The cross-society MAP: each society's distinct binding constraint side by
  side (HYPOTHESIS, G5; structural, not a shaming ranking, G7)."
  [instruments]
  (->> (by-jurisdiction instruments)
       (map (fn [[j s]] (assoc s :jurisdiction j :label (jurisdiction-label j j))))
       (sort-by #(- (:net %)))
       vec))

(defn analyze
  "Full read-off bundle. Pure; no I/O; no outward channel (G4)."
  [instruments]
  (let [stocks (by-stock instruments)
        traj (era-trajectory instruments)]
    {"stocks" (into {} (map (fn [[k v]] [(name k) v]) stocks))
     "headline" (headline instruments stocks traj)
     "loops" (loop-regimes stocks)
     "leverage" (leverage-candidates instruments)
     "trajectory" traj
     "coverage" (coverage instruments)
     "hypothesis_only" true
     "actuation_taken" false}))

;; ── datom emission (append-only EAVT; flagged; HYPOTHESIS) ───────────────────
(defn- add [e a v] [":db/add" e a v])

(defn lever-datoms
  "Append-only EAVT datoms for the disclosed lever facts + derived signed contribution.
  Person-free (G6). No :junkan/actuate or :junkan/dispatch attribute is ever emitted (G4)."
  [instruments]
  (vec
   (mapcat
    (fn [i]
      (let [e (str "junkan-demog-lever:" (:id i))]
        [(add e ":junkan.demog.lever/name" (str (:name i)))
         (add e ":junkan.demog.lever/jurisdiction" (str (:jurisdiction i)))
         (add e ":junkan.demog.lever/kind" (str (:kind i)))
         (add e ":junkan.demog.lever/year" (long (or (:year i) 0)))
         (add e ":junkan.demog.lever/enactor" (str (:enactor i)))
         (add e ":junkan.demog.lever/origin" (str (:origin i)))
         (add e ":junkan.demog.lever/stock" (str (:stock i)))
         (add e ":junkan.demog.lever/polarity" (str (:polarity i)))
         (add e ":junkan.demog.lever/contribution" (az/round3 (contribution i)))
         (add e ":junkan.demog.lever/meadows" (long (or (:meadows i) 0)))
         (add e ":junkan.demog.lever/basis" (str (:basis i)))
         (add e ":junkan/sourcing" (str (:sourcing i)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    instruments)))

(defn stock-datoms [analysis]
  (vec
   (mapcat
    (fn [[s sp]]
      (let [e (str "junkan-demog-stock:" s)]
        [(add e ":junkan.demog.stock/net" (:net sp))
         (add e ":junkan.demog.stock/suppress-force" (:suppress-force sp))
         (add e ":junkan.demog.stock/boost-force" (:boost-force sp))
         (add e ":junkan.demog.stock/regime" (str (:regime sp)))
         (add e ":junkan.demog.stock/count" (long (:count sp)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    (get analysis "stocks"))))

(defn loop-datoms [analysis]
  (vec
   (mapcat
    (fn [lp]
      (let [e (str "junkan-demog-loop:" (:id lp))]
        [(add e ":junkan.demog.loop/type" (str (:type lp)))
         (add e ":junkan.demog.loop/dominant-stock" (str (:dominant lp)))
         (add e ":junkan.demog.loop/drive" (:drive lp))
         (add e ":junkan.demog.loop/regime" (str (:regime lp)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    (get analysis "loops"))))

(defn jurisdiction-datoms
  "Append-only EAVT datoms for the per-society contrast read-off (HYPOTHESIS, G5; G4 no actuation)."
  [instruments]
  (vec
   (mapcat
    (fn [[j s]]
      (let [e (str "junkan-demog-society:" j)]
        [(add e ":junkan.demog.society/net" (:net s))
         (add e ":junkan.demog.society/regime" (str (:regime s)))
         (add e ":junkan.demog.society/binding-stock" (str (:binding-stock s)))
         (add e ":junkan.demog.society/count" (long (:count s)))
         (add e ":junkan/hypothesis" ":true")
         (add e ":junkan/derived" true)]))
    (by-jurisdiction instruments))))

(defn datoms
  "All findings datoms for one analysis (levers + stocks + loops)."
  [instruments analysis]
  (vec (concat (lever-datoms instruments) (stock-datoms analysis) (loop-datoms analysis))))

(defn render-datoms [instruments analysis]
  (str "[\n " (str/join "\n " (map pr-str (datoms instruments analysis))) "\n]\n"))

;; ── lightweight substrate validate (polarity is :suppress/:boost here) ───────
(defn validate
  "Demography-specific integrity check (the governance validate.cljc hardwires
  :widen/:narrow). Returns {:errors :warnings}. Pure (G4)."
  [instruments enums]
  (let [errs (atom []) warns (atom [])
        err! #(swap! errs conj %) warn! #(swap! warns conj %)]
    (doseq [i instruments]
      (when (str/blank? (str (:id i)))         (err! (str "lever missing :id: " (:name i))))
      (when (str/blank? (str (:enactor i)))    (err! (str (:id i) " missing :enactor (誰が)")))
      (when (str/blank? (str (:origin i)))     (err! (str (:id i) " missing :origin (経緯)")))
      (when-not (seq (:stakeholders i))        (err! (str (:id i) " missing :stakeholders (関係者)")))
      (doseq [[k kw] [[:stock :stock] [:polarity :polarity] [:kind :kind]
                      [:reversibility :reversibility] [:sourcing :sourcing]]]
        (when-not (contains? (get enums kw) (get i k))
          (err! (str (:id i) " invalid " k " " (get i k)))))
      (when-not (<= 0.0 (double (or (:magnitude i) -1)) 1.0)  (err! (str (:id i) " :magnitude out of 0..1")))
      (when-not (<= 0.0 (double (or (:confidence i) -1)) 1.0) (err! (str (:id i) " :confidence out of 0..1")))
      (when-not (<= 1 (or (:meadows i) 0) 12)                 (err! (str (:id i) " :meadows out of 1..12"))))
    (doseq [[id n] (frequencies (map :id instruments)) :when (> n 1)]
      (err! (str "duplicate :id " id " (" n "×)")))
    (let [stocks (set (map :stock instruments))]
      (doseq [s (:stock enums)] (when-not (contains? stocks s) (warn! (str "no lever for stock " s)))))
    (let [pol (frequencies (map :polarity instruments))]
      (when (zero? (get pol :suppress 0)) (err! "no suppressing levers"))
      (when (zero? (get pol :boost 0))    (err! "no boosting/corrective levers")))
    {:errors @errs :warnings @warns
     :stats {:levers (count instruments)}}))

;; ── markdown report (sober / non-eschatological / map-not-rank, G7) ──────────
(defn render-report [analysis]
  (let [stocks (get analysis "stocks")
        cov (get analysis "coverage")
        h (get analysis "headline")]
    (str
     "# junkan 循環 — 中国 一人っ子政策の system-dynamics read-off\n\n"
     "中国の **具体的な人口政策レバー** (一人っ子政策 1979 → 単独二孩 2013 → 全面二孩 2015 "
     "→ 三孩 2021) が出生率・年齢構造を **崩壊 (悪循環)** か **持続的再生産 (好循環)** の"
     "どちらに押しているかを、5 つの demographic STOCK と feedback LOOP で読み取る。"
     "**分析専用 (G4): junkan は観るだけで触れない。** 各 regime / leverage は **仮説 (G5)** であり"
     "因果の証明ではない。これは resilience/leverage の MAP であって、人口目標でも国家 ranking でもない (G7)。\n\n"
     "_coverage_: " (:instruments cov) " levers · " (:jurisdictions cov)
     " jurisdiction · sourcing " (pr-str (:sourcing cov)) "\n\n"
     "**要点 (HYPOTHESIS, G5):** いま最も崩壊方向に圧力がかかる stock は "
     "**" (:most-pressured-stock h) "** (net " (:most-pressured-net h) ")。"
     "直近 era **" (:latest-era h) "** の net は " (:latest-era-net h) " (正 = 崩壊方向)。"
     "最も強く出生を抑える lever は **" (:strongest-suppress-lever h)
     "** (Meadows L" (:strongest-suppress-meadows h) ")。\n\n"
     "## Demographic stocks (regime = HYPOTHESIS)\n\n"
     "| stock | n | net pressure | suppress | boost | regime |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [s stock-order :let [sp (get stocks (name s))] :when sp]
                 (str "| " (stock-label s) " | " (:count sp) " | " (:net sp)
                      " | " (:suppress-force sp) " | " (:boost-force sp)
                      " | " (name (:regime sp)) " |")))
     "\n\n_net > 0 = 出生抑制が優勢 (崩壊方向 / 悪循環傾向); net < 0 = 再生産方向 (好循環)。_\n\n"
     "## Structural loops (HYPOTHESIS, G5 — drive = joint pressure of member stocks)\n\n"
     "| loop | type | member stocks | drive | regime |\n"
     "|---|---|---|---|---|\n"
     (str/join "\n"
               (for [lp (get analysis "loops")]
                 (str "| " (:id lp) " | " (name (:type lp))
                      " | " (str/join ", " (map name (:member-stocks lp)))
                      " | " (:drive lp) " | " (name (:regime lp)) " |")))
     "\n\n_B1 (intended control) は 1980–2010 に機能したが今や defunct/overshot; R1 momentum・"
     "R2 norm-lockin・R3 4-2-1 が崩壊方向に回り、B2 pronatal-incentive はそれに圧倒されている。_\n\n"
     "## Era trajectory (mandate→reversal over time, HYPOTHESIS, G5)\n\n"
     "| era | n | suppress | boost | net |\n|---|---|---|---|---|\n"
     (str/join "\n"
               (for [e (get analysis "trajectory")]
                 (str "| " (:era e) " | " (:count e) " | " (:suppress-force e)
                      " | " (:boost-force e) " | " (:net e) " |")))
     "\n\n## Meadows leverage CANDIDATES (G11 — candidates, never directives)\n\n"
     "_amplify = 0.6·depth + 0.4·magnitude; flip = 0.5·tractability + 0.5·magnitude·confidence "
     "(disclosed, auditable, never a directive). junkan は誰が産むべきかを指示しない (anti-coercion)。_\n\n"
     "**増幅候補 (既に再生産方向に働く深いレバレッジ lever):**\n"
     (str/join "\n" (for [c (get-in analysis ["leverage" :amplify])]
                      (str "- [" (:score c) "] L" (:meadows c) " · " (:name c) " ("
                           (name (:stock c)) ")")))
     "\n\n**反転候補 (出生を抑えており、最も是正余地のある lever):**\n"
     (str/join "\n" (for [c (get-in analysis ["leverage" :flip])]
                      (str "- [" (:score c) "] L" (:meadows c) " · " (:name c) " ("
                           (name (:stock c)) ", reversibility=" (name (or (:reversibility c) :-)) ")")))
     "\n\n_読みの核: 反転を試みた 2015/2021 のレバーは Meadows L12 (子供数という数値) に集中し、"
     "拘束条件である L2 (小家族規範) ・ L3 (システムの目標) ・ L5 (育児コスト/住宅/ジェンダー分業) には"
     "ほとんど届いていない — 最弱のレバーを引いたために動かない、という Meadows の教科書例。_\n\n"
     "## Coverage worklist (next /loop iterations)\n\n"
     (str/join "\n" (map #(str "- " %) (:worklist cov)))
     "\n\n_findings are append-only; surfacing beyond Council is performed by ossekai/kataribe on "
     "junkan's behalf, never by junkan (G13). actuation_taken=false throughout._\n")))

;; ── cross-society contrast report (sober / map-not-rank, G7) ─────────────────
(defn render-contrast-report [instruments]
  (let [sc (society-contrast instruments)]
    (str
     "# junkan 循環 — 低出生社会の system-dynamics 比較 (cross-society contrast)\n\n"
     "同じ 5-stock / 5-loop / Meadows フレームを **中国・韓国・日本・イタリア・"
     "シンガポール** に当て、各社会の **binding constraint (拘束 stock)** を並べて読む。"
     "**分析専用 (G4)・仮説 (G5)。** これは各社会が抱える構造の MAP であって、"
     "どの国が良い/悪いという ranking ではない (G7)。\n\n"
     "| society | levers | net (崩壊圧) | regime | 拘束 stock (binding) | 最強ドライバー |\n"
     "|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [s sc]
                 (str "| " (:label s) " | " (:count s) " | " (:net s)
                      " | " (name (:regime s))
                      " | " (or (:binding-stock s) "·") " (net " (:binding-stock-net s) ")"
                      " | " (:top-driver s) " |")))
     "\n\n_net > 0 = 出生抑制が優勢 (崩壊方向)。binding stock = その社会で最も崩壊圧の高い"
     " demographic stock。_\n\n"
     "**読みの核 (HYPOTHESIS, G5):** どの社会も sub-replacement だが **拘束条件は異なる** — "
     "中国は規範ロックイン (small-family-norm) と 4-2-1、韓国は教育コストとジェンダー・ペナルティ、"
     "日本は非婚化と労働・性別文化、イタリアは若年 precarity と familism、シンガポールは教育コストと"
     "結晶化した小家族規範。**共通項は『最弱の Meadows レバー (出産奨励金/許可数) では拘束 stock に"
     "届かない』こと。** 是正は各社会の binding stock — コスト・住宅・ジェンダー分業・非婚・規範 — "
     "を動かす深いレバー (L2–L5) を要する。junkan は誰が産むべきかを指示しない (G11, anti-coercion)。\n\n"
     "_findings are append-only; surfacing beyond Council は ossekai/kataribe が junkan に代わって"
     "行う (G13)。actuation_taken=false。_\n")))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn- load-levers [path]
     (vec (filter #(= (:type %) :instrument) (clojure.edn/read-string (slurp path))))))

#?(:clj
   (defn -main [& args]
     ;; args = seed paths (default: china + peer low-fertility societies). The LAST
     ;; jurisdiction-pool drives the cross-society contrast when >1 jurisdiction.
     (let [seeds (if (seq args) (vec args)
                     ["20-actors/junkan/kotoba/seed.china-one-child.edn"
                      "20-actors/junkan/kotoba/seed.low-fertility-societies.edn"])
           onto "20-actors/junkan/kotoba/ontology.junkan-demography.edn"
           is (vec (mapcat load-levers seeds))
           enums (:enums (clojure.edn/read-string (slurp onto)))
           {:keys [errors warnings]} (validate is enums)
           china (filter #(= "CN" (:jurisdiction %)) is)]
       (when (seq china) (println (render-report (analyze china))))
       (when (> (count (distinct (map :jurisdiction is))) 1)
         (println "\n") (println (render-contrast-report is)))
       (println (str "\n-- validate: " (count errors) " errors · " (count warnings) " warnings --"))
       (doseq [e errors] (println "  ERROR  " e))
       (doseq [w warnings] (println "  warn   " w))
       (println (str "-- " (count is) " levers · "
                     (count (distinct (map :jurisdiction is))) " societies · substrate "
                     (if (empty? errors) "OK ✅" "BROKEN ❌") " --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
