(ns tate.tests.test-respond
  "tate 盾 — response-planner tests (ADR-2606112301 + worldwide 2606112400). 1:1 port of
  test_respond.py. clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [tate.methods.terms-scan :as terms]
            [tate.methods.respond-plan :as r]))

(defn- by-id []
  (let [[_ notices] (terms/load-docs)
        procs (r/load-procs)]
    [(into {} (for [n notices] [(get n ":notice/id") (r/build-plan n procs)])) procs]))

(defn- any-dl [p pred] (some pred (get p "deadlines")))
(defn- dl-anchor? [p sub] (any-dl p #(str/includes? (get % "anchor") sub)))
(defn- dl-rule? [p sub] (any-dl p #(str/includes? (get % "rule") sub)))
(defn- opt-id? [p id] (some #(= (get % "id") id) (get p "options")))
(defn- ref? [p sub] (some #(str/includes? % sub) (get p "referrals")))

(deftest test-fake-sms-guard
  (let [[ps _] (by-id)
        p (get ps "ntc:fake-sms")
        verbs (mapv #(get % "verb") (get p "steps"))]
    (is (= (get p "status") ":suspected-fake"))
    (is (= (first verbs) "do-not-contact-sender"))
    (is (some #{"preserve-evidence"} verbs))
    (is (every? #(or (not (str/includes? % "contact-sender")) (= % "do-not-contact-sender")) verbs))
    (is (and (= (get p "deadlines") []) (= (get p "options") [])))
    (is (ref? p "tasuke")) (is (ref? p "#9110")) (is (ref? p "188"))))

(deftest test-channel-discriminator
  (let [[_ procs] (by-id)
        base {":notice/id" "ntc:x" ":notice/text" "支払督促を発する。" ":notice/claim-jpy" 10000
              ":notice/sourcing" ":synthetic"}
        [_ s1] (r/classify (assoc base ":notice/channel" ":special-service") procs)
        [_ s2] (r/classify (assoc base ":notice/channel" ":email") procs)]
    (is (and (= s1 ":genuine") (= s2 ":suspected-fake")))))

(deftest test-tokusoku-genuine
  (let [[ps _] (by-id)
        p (get ps "ntc:tokusoku-real")
        dl (first (get p "deadlines"))]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:shiharai-tokusoku")))
    (is (opt-id? p ":tokusoku-igi"))
    (is (and (str/includes? (get dl "rule") "2週間") (str/includes? (get dl "anchor") "民事訴訟法391条")))))

(deftest test-shougaku-transfer-option
  (let [[ps procs] (by-id)
        p (get ps "ntc:shougaku-real")
        proc (first (filter #(= (get % ":proc/id") "proc:shougaku-sosho") procs))]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:shougaku-sosho")))
    (is (opt-id? p ":ikou"))
    (is (dl-anchor? p "民事訴訟法373条"))
    (is (= (get proc ":proc/claim-ceiling-jpy") 600000))))

(deftest test-sojou-referral-forward
  (let [[ps _] (by-id) p (get ps "ntc:sojou-big")]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:sojou")))
    (is (ref? p "法テラス") "G7: 本訴 must referral-forward")))

(deftest test-gyousei-deadlines
  (let [[ps _] (by-id) p (get ps "ntc:gyousei")
        anchors (str/join " / " (map #(get % "anchor") (get p "deadlines")))
        rules (str/join " / " (map #(get % "rule") (get p "deadlines")))]
    (is (= (get p "status") ":genuine"))
    (is (and (str/includes? anchors "行政不服審査法18条1項") (str/includes? anchors "行政事件訴訟法14条1項")))
    (is (and (str/includes? rules "3月以内") (str/includes? rules "6箇月以内")))))

(deftest test-deadline-honesty-no-computed-dates
  (let [[ps _] (by-id)]
    (doseq [p (vals ps) d (get p "deadlines")]
      (is (= (get d "verify_service_date") true) [(get p "notice") d])
      (is (get d "anchor") d)
      (is (and (not (contains? d "deadline_date")) (not (contains? d "due")))))))

(deftest test-upl-gates
  (is (thrown? clojure.lang.ExceptionInfo
               (r/make-option {":opt/id" ":dairi" ":opt/kind" ":representation" ":opt/label" "代理"})))
  (let [[ps _] (by-id)]
    (doseq [p (vals ps)]
      (is (= (get p "mode") "dry-run"))
      (doseq [o (get p "options")]
        (is (and (= (get o "submitted_by") "member")
                 (contains? #{":self-submit" ":self-decide"} (get o "kind")))))
      (when (= (get p "status") ":genuine")
        (is (some #(= (get % "verb") "self-submit") (get p "steps")))))))

(deftest test-plans-cover-all-notices
  (let [[_ notices] (terms/load-docs)
        ps (r/plans notices (r/load-procs))]
    (is (= (count ps) (count notices)))
    (is (clojure.set/subset? (set (map #(get % "status") ps))
                             #{":genuine" ":suspected-fake" ":unknown" ":unknown-jurisdiction"}))))

;; ── worldwide ──────────────────────────────────────────────────────────────

(deftest test-us-summons-genuine-and-referral-forward
  (let [[ps _] (by-id) p (get ps "ntc:us-summons-real")]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:us-summons")))
    (is (dl-anchor? p "FRCP 12(a)"))
    (is (ref? p "state bar"))))

(deftest test-us-fake-email-guard
  (let [[ps _] (by-id) p (get ps "ntc:us-fake-email")]
    (is (= (get p "status") ":suspected-fake"))
    (is (= (get (first (get p "steps")) "verb") "do-not-contact-sender"))
    (is (and (= (get p "deadlines") []) (= (get p "options") [])))
    (is (ref? p "FTC"))))

(deftest test-de-mahnbescheid
  (let [[ps _] (by-id) p (get ps "ntc:de-mahn")]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:de-mahnbescheid")))
    (is (dl-anchor? p "ZPO")) (is (opt-id? p ":widerspruch"))))

(deftest test-eu-order-for-payment
  (let [[ps _] (by-id) p (get ps "ntc:eu-ofp") dl (first (get p "deadlines"))]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:eu-order-for-payment")))
    (is (and (str/includes? (get dl "rule") "30日") (str/includes? (get dl "anchor") "1896/2006")))))

(deftest test-uk-claim-referral-over-line
  (let [[ps _] (by-id) p (get ps "ntc:uk-claim")]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:uk-claim-form")))
    (is (dl-anchor? p "CPR")) (is (ref? p "Citizens Advice"))))

(deftest test-unknown-jurisdiction-degrades-honestly
  (let [[ps _] (by-id) p (get ps "ntc:cl-unknown")]
    (is (= (get p "status") ":unknown-jurisdiction"))
    (is (and (= (get p "deadlines") []) (= (get p "options") [])))
    (is (= (get (first (get p "steps")) "verb") "declare-uncovered"))
    (is (seq (get p "referrals")))))

(deftest test-kr-jigeup-genuine-and-fake
  (let [[ps _] (by-id) p (get ps "ntc:kr-jigeup") f (get ps "ntc:kr-fake-sms")]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:kr-jigeup-myeongryeong")))
    (is (dl-anchor? p "민사소송법 470조")) (is (opt-id? p ":i-ui-sincheong"))
    (is (= (get f "status") ":suspected-fake"))
    (is (= (get (first (get f "steps")) "verb") "do-not-contact-sender"))
    (is (some #(or (str/includes? % "경찰청") (str/includes? % "금융감독원")) (get f "referrals")))))

(deftest test-fr-injonction-genuine
  (let [[ps _] (by-id) p (get ps "ntc:fr-injonction") dl (first (get p "deadlines"))]
    (is (and (= (get p "status") ":genuine") (= (get p "proc") "proc:fr-injonction-de-payer")))
    (is (and (str/includes? (get dl "rule") "1か月") (str/includes? (get dl "anchor") "1416")))
    (is (opt-id? p ":opposition"))))

(deftest test-wave3-au-ca-it-genuine
  (let [[ps _] (by-id)
        au (get ps "ntc:au-soc") ca (get ps "ntc:ca-claim") it (get ps "ntc:it-decreto")]
    (is (and (= (get au "status") ":genuine") (= (get au "proc") "proc:au-statement-of-claim")))
    (is (dl-anchor? au "UCPR"))
    (is (and (= (get ca "status") ":genuine") (= (get ca "proc") "proc:ca-plaintiffs-claim")))
    (is (dl-anchor? ca "9.01"))
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-decreto-ingiuntivo")))
    (is (dl-anchor? it "641")) (is (opt-id? it ":opposizione"))))

(deftest test-arbitration-inversion-us-vs-ca-vs-in
  (let [patterns (terms/load-patterns)
        base {":doc/context" ":consumer" ":doc/sourcing" ":synthetic"
              ":doc/text" "Any dispute shall be resolved by binding arbitration."}
        us (terms/scan-doc (assoc base ":doc/id" "d:us" ":doc/jurisdiction" ":us") patterns)
        ca (terms/scan-doc (assoc base ":doc/id" "d:ca" ":doc/jurisdiction" ":ca") patterns)
        in (terms/scan-doc (assoc base ":doc/id" "d:in" ":doc/jurisdiction" ":in") patterns)]
    (is (some #(or (str/includes? (get % "anchor") "ENFORCEABLE") (str/includes? (get % "anchor") "原則")) us))
    (is (some #(str/includes? (get % "anchor") "無効") ca))
    (is (some #(str/includes? (get % "anchor") "排除されない") in))))

(deftest test-wave5-tw-sg-in-genuine
  (let [[ps _] (by-id)
        tw (get ps "ntc:tw-payment") sg (get ps "ntc:sg-sct") ind (get ps "ntc:in-summons")]
    (is (and (= (get tw "status") ":genuine") (= (get tw "proc") "proc:tw-payment-order")))
    (is (dl-anchor? tw "516條"))
    (is (and (= (get sg "status") ":genuine") (= (get sg "proc") "proc:sg-sct")))
    (is (dl-rule? sg "弁護士代理は禁止"))
    (is (and (= (get ind "status") ":genuine") (= (get ind "proc") "proc:in-summons")))
    (is (dl-anchor? ind "Order VIII Rule 1"))))

(deftest test-digital-channel-never-genuine
  (let [procs (r/load-procs)]
    (doseq [p procs ch [":sms" ":email"]
            :when (not (contains? (set (get p ":proc/genuine-channels" [])) ch))]
      (let [n {":notice/id" "ntc:synth" ":notice/jurisdiction" (get p ":proc/jurisdiction")
               ":notice/channel" ch ":notice/text" (first (get p ":proc/trigger-keywords"))
               ":notice/sourcing" ":synthetic"}
            [_ status] (r/classify n procs)]
        (is (= status ":suspected-fake") [(get p ":proc/id") ch status])))))

(deftest test-wave4-es-nl-br-genuine
  (let [[ps _] (by-id)
        es (get ps "ntc:es-monitorio") nl (get ps "ntc:nl-dagvaarding") br (get ps "ntc:br-citacao")]
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-monitorio")))
    (is (dl-anchor? es "815.1"))
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-dagvaarding")))
    (is (dl-anchor? nl "Rv art. 111"))
    (is (and (= (get br "status") ":genuine") (= (get br "proc") "proc:br-citacao")))
    (is (= (get br "channel") ":mail"))
    (is (dl-anchor? br "335")) (is (dl-rule? br "dias úteis"))))

(deftest test-wave6-cn-genuine-and-script-separation
  (let [[ps _] (by-id) cn (get ps "ntc:cn-zhifuling") tw (get ps "ntc:tw-payment")]
    (is (and (= (get cn "status") ":genuine") (= (get cn "proc") "proc:cn-zhifuling")))
    (is (dl-rule? cn "15日"))
    (is (and (= (get tw "proc") "proc:tw-payment-order") (not= (get cn "proc") (get tw "proc"))))))

(deftest test-us-state-sub-jurisdiction
  (let [[ps _] (by-id)
        ca (get ps "ntc:us-summons-ca")
        state-dls (filter #(str/starts-with? (get % "label") "州規則") (get ca "deadlines"))
        stateless (get ps "ntc:us-summons-real")
        honest (filter #(= (get % "label") "州規則 (州不明)") (get stateless "deadlines"))]
    (is (= (get ca "status") ":genuine"))
    (is (and (= (count state-dls) 1) (str/includes? (get (first state-dls) "label") "California")))
    (is (and (str/includes? (get (first state-dls) "anchor") "412.20")
             (str/includes? (get (first state-dls) "rule") "$12,500")))
    (is (and (= (count honest) 1) (str/includes? (get (first honest) "rule") "提示しない")))))

(deftest test-wave7-pl-se-genuine
  (let [[ps _] (by-id) pl (get ps "ntc:pl-nakaz") se (get ps "ntc:se-bf")]
    (is (and (= (get pl "status") ":genuine") (= (get pl "proc") "proc:pl-nakaz-zaplaty")))
    (is (dl-anchor? pl "480²"))
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-betalningsforelaggande")))
    (is (dl-anchor? se "1990:746")) (is (opt-id? se ":bestrida"))))

(deftest test-currency-mismatch-refers-conservatively
  (let [[_ procs] (by-id)
        base {":notice/id" "ntc:cur" ":notice/jurisdiction" ":us"
              ":notice/channel" ":personal-service"
              ":notice/text" "small claims notice" ":notice/sourcing" ":synthetic"}
        same (r/build-plan (assoc base ":notice/claim-amount" 500 ":notice/claim-currency" "USD") procs)
        mismatch (r/build-plan (assoc base ":notice/claim-amount" 500 ":notice/claim-currency" "EUR") procs)]
    (is (not (ref? same "state bar")))
    (is (ref? mismatch "外貨建て")) (is (ref? mismatch "state bar"))))

(deftest test-wave8-at-pt-genuine
  (let [[ps _] (by-id) at (get ps "ntc:at-zb") pt (get ps "ntc:pt-injuncao")]
    (is (and (= (get at "status") ":genuine") (= (get at "proc") "proc:at-zahlungsbefehl")))
    (is (dl-anchor? at "§248"))
    (is (and (= (get pt "status") ":genuine") (= (get pt "proc") "proc:pt-injuncao")))
    (is (dl-anchor? pt "269/98"))))

(deftest test-wave8-labor-track
  (let [[ps procs] (by-id)
        jp (get ps "ntc:jp-kaiko") de (get ps "ntc:de-kuendigung") uk (get ps "ntc:uk-dismissal")
        by (into {} (for [p procs] [(get p ":proc/id") p]))]
    (is (and (= (get jp "status") ":genuine") (= (get jp "proc") "proc:jp-kaiko")))
    (is (dl-anchor? jp "労働基準法22条"))
    (is (and (= (get de "status") ":genuine") (= (get de "proc") "proc:de-kuendigung")))
    (is (any-dl de #(and (str/includes? (get % "rule") "3週間") (str/includes? (get % "anchor") "KSchG"))))
    (is (dl-anchor? de "BGB §623"))
    (is (and (= (get uk "status") ":genuine") (= (get uk "proc") "proc:uk-dismissal")))
    (is (dl-rule? uk "ACAS"))
    (doseq [pid ["proc:jp-kaiko" "proc:de-kuendigung" "proc:uk-dismissal"]]
      (is (= (get (by pid) ":proc/track") ":labor")))
    (let [n {":notice/id" "ntc:x" ":notice/jurisdiction" ":de" ":notice/channel" ":email"
             ":notice/text" "Kündigung Ihres Arbeitsverhältnisses" ":notice/sourcing" ":synthetic"}
          [_ status] (r/classify n procs)]
      (is (= status ":suspected-fake")))))

(deftest test-wave9-housing-track
  (let [[ps procs] (by-id)
        jp (get ps "ntc:jp-kaiyaku-m") de (get ps "ntc:de-miet")
        uk (get ps "ntc:uk-s21") us (get ps "ntc:us-eviction-ca")]
    (is (and (= (get jp "status") ":genuine") (= (get jp "proc") "proc:jp-chintai-kaiyaku")))
    (is (dl-anchor? jp "借地借家法27条")) (is (dl-rule? jp "退去義務は生じない"))
    (is (and (= (get de "status") ":genuine") (= (get de "proc") "proc:de-mietkuendigung")))
    (is (dl-anchor? de "§574"))
    (is (and (= (get uk "status") ":genuine") (= (get uk "proc") "proc:uk-s21-s8")))
    (is (dl-anchor? uk "Protection from Eviction Act 1977"))
    (is (and (= (get us "status") ":genuine") (= (get us "proc") "proc:us-eviction")))
    (is (dl-anchor? us "§1161"))
    (is (any-dl us #(str/starts-with? (get % "label") "州規則 (California")))
    (doseq [p procs :when (= (get p ":proc/track") ":housing")]
      (is (some #(= (get % ":opt/id") ":no-self-help-protection") (get p ":proc/options"))
          (get p ":proc/id")))))

(deftest test-de-kuendigung-disambiguation
  (let [[_ procs] (by-id)
        base {":notice/id" "ntc:x" ":notice/jurisdiction" ":de" ":notice/channel" ":mail"
              ":notice/sourcing" ":synthetic"}
        [p1 s1] (r/classify (assoc base ":notice/text" "Kündigung Ihres Arbeitsverhältnisses") procs)
        [p2 s2] (r/classify (assoc base ":notice/text" "Kündigung des Mietverhältnisses") procs)
        [p3 s3] (r/classify (assoc base ":notice/text" "Kündigung") procs)]
    (is (and (= s1 ":genuine") (= (get p1 ":proc/id") "proc:de-kuendigung")))
    (is (and (= s2 ":genuine") (= (get p2 ":proc/id") "proc:de-mietkuendigung")))
    (is (and (nil? p3) (= s3 ":unknown")))))

(deftest test-wave10-enforcement-track
  (let [[ps procs] (by-id)
        jp (get ps "ntc:jp-sashiosae") us (get ps "ntc:us-garnish") de (get ps "ntc:de-pfaendung")]
    (is (and (= (get jp "status") ":genuine") (= (get jp "proc") "proc:jp-sashiosae")))
    (is (dl-anchor? jp "民事執行法152条")) (is (ref? jp "法テラス"))
    (is (and (= (get us "status") ":genuine") (= (get us "proc") "proc:us-garnishment")))
    (is (dl-anchor? us "§1673"))
    (is (and (= (get de "status") ":genuine") (= (get de "proc") "proc:de-kontopfaendung")))
    (is (dl-anchor? de "§850k"))
    (doseq [p procs :when (= (get p ":proc/track") ":enforcement")]
      (is (some #(= (get % ":opt/protective") true) (get p ":proc/options")) (get p ":proc/id")))))

(deftest test-sashiosae-sms-scam-guard
  (let [[_ procs] (by-id)
        n {":notice/id" "ntc:x" ":notice/jurisdiction" ":jp" ":notice/channel" ":sms"
           ":notice/text" "【差押え最終通告】本日中にご連絡なき場合、給与の差押えを執行します。"
           ":notice/sourcing" ":synthetic"}
        [_ status] (r/classify n procs)]
    (is (= status ":suspected-fake"))))

(deftest test-wave11-insolvency-track
  (let [[ps procs] (by-id)
        jp (get ps "ntc:jp-hasan") us (get ps "ntc:us-bk") de (get ps "ntc:de-inso")]
    (is (and (= (get jp "status") ":genuine") (= (get jp "proc") "proc:jp-hasan-tsuchi")))
    (is (dl-anchor? jp "破産法31条・111条"))
    (is (and (= (get us "status") ":genuine") (= (get us "proc") "proc:us-bankruptcy-notice")))
    (is (dl-anchor? us "3002"))
    (is (and (= (get de "status") ":genuine") (= (get de "proc") "proc:de-insolvenz")))
    (is (dl-anchor? de "§174"))
    (doseq [p procs :when (= (get p ":proc/track") ":insolvency")]
      (is (some #(= (get % ":opt/protective") true) (get p ":proc/options")) (get p ":proc/id")))))

(deftest test-wave11-ie-ch
  (let [[ps _] (by-id) ie (get ps "ntc:ie-summons") ch (get ps "ntc:ch-zb") at (get ps "ntc:at-zb")]
    (is (and (= (get ie "status") ":genuine") (= (get ie "proc") "proc:ie-civil-summons")))
    (is (and (= (get ch "status") ":genuine") (= (get ch "proc") "proc:ch-zahlungsbefehl")))
    (is (any-dl ch #(and (str/includes? (get % "rule") "10日") (str/includes? (get % "anchor") "SchKG"))))
    (is (opt-id? ch ":register-cleanup"))
    (is (and (= (get at "proc") "proc:at-zahlungsbefehl") (not= (get at "proc") (get ch "proc"))))))

(deftest test-wave12-family-track
  (let [[ps procs] (by-id)
        jp (get ps "ntc:jp-chotei") us (get ps "ntc:us-divorce") de (get ps "ntc:de-scheidung")]
    (is (and (= (get jp "status") ":genuine") (= (get jp "proc") "proc:jp-kaji-chotei")))
    (is (dl-anchor? jp "家事事件手続法51条"))
    (is (and (= (get us "status") ":genuine") (= (get us "proc") "proc:us-divorce-petition")))
    (is (dl-rule? us "30日"))
    (is (and (= (get de "status") ":genuine") (= (get de "proc") "proc:de-scheidungsantrag")))
    (is (dl-anchor? de "FamFG §114")) (is (opt-id? de ":vkh"))
    (doseq [p procs :when (= (get p ":proc/track") ":family")]
      (is (some #(str/includes? % "kokoro 心") (get p ":proc/refer-when" [])) (get p ":proc/id")))))

(deftest test-wave12-dk-fi
  (let [[ps _] (by-id) dk (get ps "ntc:dk-bp") fi (get ps "ntc:fi-haaste")]
    (is (and (= (get dk "status") ":genuine") (= (get dk "proc") "proc:dk-betalingspaakrav")))
    (is (dl-anchor? dk "44a"))
    (is (and (= (get fi "status") ":genuine") (= (get fi "proc") "proc:fi-haastehakemus")))
    (is (dl-rule? fi "yksipuolinen tuomio"))))

(deftest test-wave13-track-expansion-kr-fr
  (let [[ps procs] (by-id)
        kr-l (get ps "ntc:kr-haego") kr-h (get ps "ntc:kr-gaengsin")
        fr-l (get ps "ntc:fr-licenciement") fr-h (get ps "ntc:fr-conge")
        by (into {} (for [p procs] [(get p ":proc/id") p]))]
    (is (and (= (get kr-l "status") ":genuine") (= (get kr-l "proc") "proc:kr-budang-haego")))
    (is (dl-anchor? kr-l "근로기준법 28조")) (is (dl-anchor? kr-l "근로기준법 27조"))
    (is (and (= (get kr-h "status") ":genuine") (= (get kr-h "proc") "proc:kr-gaengsin-geojeol")))
    (is (dl-anchor? kr-h "6조의3"))
    (is (and (= (get fr-l "status") ":genuine") (= (get fr-l "proc") "proc:fr-licenciement")))
    (is (dl-anchor? fr-l "L.1471-1"))
    (is (and (= (get fr-h "status") ":genuine") (= (get fr-h "proc") "proc:fr-conge-bailleur")))
    (is (dl-rule? fr-h "trêve hivernale"))
    (is (= (get (by "proc:kr-budang-haego") ":proc/track") ":labor"))
    (is (= (get (by "proc:kr-gaengsin-geojeol") ":proc/track") ":housing"))
    (is (= (get (by "proc:fr-licenciement") ":proc/track") ":labor"))
    (is (= (get (by "proc:fr-conge-bailleur") ":proc/track") ":housing"))))

(deftest test-wave13-no-forliksraadet
  (let [[ps _] (by-id) no (get ps "ntc:no-forlik")]
    (is (and (= (get no "status") ":genuine") (= (get no "proc") "proc:no-forliksklage")))
    (is (dl-anchor? no "tvisteloven")) (is (dl-rule? no "fraværsdom"))))

(deftest test-wave14-matrix-fill-and-mx
  (let [[ps _] (by-id)
        us (get ps "ntc:us-term") uk (get ps "ntc:uk-noe")
        fr (get ps "ntc:fr-saisie") mx (get ps "ntc:mx-empl")]
    (is (and (= (get us "status") ":genuine") (= (get us "proc") "proc:us-termination")))
    (is (any-dl us #(and (str/includes? (get % "rule") "at-will") (str/includes? (get % "anchor") "§2000e-5"))))
    (is (and (= (get uk "status") ":genuine") (= (get uk "proc") "proc:uk-notice-of-enforcement")))
    (is (dl-rule? uk "7 clear days")) (is (opt-id? uk ":exempt-goods"))
    (is (and (= (get fr "status") ":genuine") (= (get fr "proc") "proc:fr-saisie-attribution")))
    (is (dl-anchor? fr "R.162-2"))
    (is (and (= (get mx "status") ":genuine") (= (get mx "proc") "proc:mx-emplazamiento")))
    (is (dl-rule? mx "entidad federativa"))))

(deftest test-wave15-insolvency-family-expansion-and-be
  (let [[ps _] (by-id)
        fr (get ps "ntc:fr-creance") uk-i (get ps "ntc:uk-pod")
        uk-f (get ps "ntc:uk-divorce") be (get ps "ntc:be-citation")]
    (is (and (= (get fr "status") ":genuine") (= (get fr "proc") "proc:fr-declaration-creance")))
    (is (any-dl fr #(and (str/includes? (get % "anchor") "L.622-24") (str/includes? (get % "rule") "forclusion"))))
    (is (and (= (get uk-i "status") ":genuine") (= (get uk-i "proc") "proc:uk-proof-of-debt")))
    (is (dl-anchor? uk-i "Rules 2016"))
    (is (and (= (get uk-f "status") ":genuine") (= (get uk-f "proc") "proc:uk-divorce-response")))
    (is (any-dl uk-f #(and (str/includes? (get % "rule") "14日") (str/includes? (get % "anchor") "2020"))))
    (is (and (= (get be "status") ":genuine") (= (get be "proc") "proc:be-citation")))
    (is (dl-rule? be "最低8日"))))

(deftest test-wave16-ar-and-kr-family
  (let [[ps _] (by-id) ar (get ps "ntc:ar-traslado") kr (get ps "ntc:kr-ihon")]
    (is (and (= (get ar "status") ":genuine") (= (get ar "proc") "proc:ar-traslado")))
    (is (any-dl ar #(and (str/includes? (get % "anchor") "CPCCN") (str/includes? (get % "rule") "días hábiles"))))
    (is (and (= (get kr "status") ":genuine") (= (get kr "proc") "proc:kr-ihon-jojeong")))
    (is (any-dl kr #(or (str/includes? (get % "rule") "조정전치") (str/includes? (get % "anchor") "조정전치"))))))

(deftest test-wave17-au-ca-labor-housing
  (let [[ps _] (by-id)
        au-l (get ps "ntc:au-dismissal") au-h (get ps "ntc:au-termnotice")
        ca-l (get ps "ntc:ca-dismissal") ca-h (get ps "ntc:ca-n12")]
    (is (and (= (get au-l "status") ":genuine") (= (get au-l "proc") "proc:au-unfair-dismissal")))
    (is (any-dl au-l #(and (str/includes? (get % "rule") "21日") (str/includes? (get % "anchor") "s.394"))))
    (is (and (= (get au-h "status") ":genuine") (= (get au-h "proc") "proc:au-termination-notice")))
    (is (dl-rule? au-h "NCAT"))
    (is (and (= (get ca-l "status") ":genuine") (= (get ca-l "proc") "proc:ca-dismissal")))
    (is (dl-rule? ca-l "common law"))
    (is (and (= (get ca-h "status") ":genuine") (= (get ca-h "proc") "proc:ca-n12")))
    (is (any-dl ca-h #(or (str/includes? (get % "rule") "bad faith") (str/includes? (get % "rule") "T5"))))))

(deftest test-court-vocabulary-derived
  (let [procs (r/load-procs)
        vocab (set (r/court-vocabulary procs))]
    (doseq [p procs k (get p ":proc/trigger-keywords")]
      (is (contains? vocab k) [(get p ":proc/id") k]))
    (is (clojure.set/subset? (set r/curated-tripwires) vocab))
    (let [n {":notice/id" "ntc:x" ":notice/jurisdiction" ":au" ":notice/channel" ":sms"
             ":notice/text" "URGENT: unfair dismissal compensation owed to you, call now"
             ":notice/sourcing" ":synthetic"}
          [_ status] (r/classify n procs)]
      (is (= status ":suspected-fake")))))

(deftest test-wave18-kr-enforcement-fr-family
  (let [[ps _] (by-id) kr (get ps "ntc:kr-apnyu") fr (get ps "ntc:fr-divorce")]
    (is (and (= (get kr "status") ":genuine") (= (get kr "proc") "proc:kr-apnyu")))
    (is (dl-anchor? kr "246조"))
    (is (and (= (get fr "status") ":genuine") (= (get fr "proc") "proc:fr-divorce-assignation")))
    (is (dl-rule? fr "avocat")) (is (opt-id? fr ":aj"))))

(deftest test-critical-deadlines-surface-first
  (let [[ps procs] (by-id)]
    (doseq [[nid pid] [["ntc:de-kuendigung" "proc:de-kuendigung"]
                       ["ntc:ch-zb" "proc:ch-zahlungsbefehl"]
                       ["ntc:au-dismissal" "proc:au-unfair-dismissal"]
                       ["ntc:fr-creance" "proc:fr-declaration-creance"]
                       ["ntc:kr-haego" "proc:kr-budang-haego"]
                       ["ntc:tokusoku-real" "proc:shiharai-tokusoku"]]]
      (let [p (get ps nid)]
        (is (= (get p "proc") pid))
        (is (= (get (first (get p "deadlines")) "critical") true) nid)))
    (doseq [p procs dl (get p ":proc/deadline-rules")]
      (is (contains? #{nil true} (get dl ":dl/critical")) [(get p ":proc/id") dl]))))

(deftest test-wave19-kr-insolvency-au-family
  (let [[ps _] (by-id) kr (get ps "ntc:kr-singo") au (get ps "ntc:au-divorce")]
    (is (and (= (get kr "status") ":genuine") (= (get kr "proc") "proc:kr-chaegwon-singo")))
    (is (dl-rule? kr "실권"))
    (is (and (= (get au "status") ":genuine") (= (get au "proc") "proc:au-divorce-response")))
    (is (dl-rule? au "28日"))
    (is (some #(str/includes? (get % "label") "12か月") (get au "options")))))

(deftest test-universal-protective-invariant
  (let [[_ procs] (by-id)]
    (doseq [p procs :when (not= (get p ":proc/track" ":civil") ":civil")]
      (is (some #(= (get % ":opt/protective") true) (get p ":proc/options")) (get p ":proc/id")))))

(deftest test-wave20-enforcement-au-ca-and-nz
  (let [[ps _] (by-id) au (get ps "ntc:au-garnishee") ca (get ps "ntc:ca-garnish") nz (get ps "ntc:nz-dt")]
    (is (and (= (get au "status") ":genuine") (= (get au "proc") "proc:au-garnishee")))
    (is (dl-anchor? au "ss.119"))
    (is (and (= (get ca "status") ":genuine") (= (get ca "proc") "proc:ca-garnishment")))
    (is (dl-rule? ca "80%"))
    (is (and (= (get nz "status") ":genuine") (= (get nz "proc") "proc:nz-disputes-tribunal")))
    (is (= (get nz "channel") ":email"))
    (is (any-dl nz #(or (str/includes? (get % "rule") "弁護士代理は法律で原則排除") (str/includes? (get % "anchor") "s.38"))))))

(deftest test-wave21-it-es-labor-critical
  (let [[ps _] (by-id) it (get ps "ntc:it-licenziamento") es (get ps "ntc:es-despido")]
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-licenziamento")))
    (is (= (get (first (get it "deadlines")) "critical") true))
    (is (str/includes? (get (first (get it "deadlines")) "rule") "60日"))
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-despido")))
    (is (= (get (first (get es "deadlines")) "critical") true))
    (is (and (str/includes? (get (first (get es "deadlines")) "rule") "20日")
             (str/includes? (get (first (get es "deadlines")) "rule") "caducidad")))))

(deftest test-wave22-br-tw-labor
  (let [[ps _] (by-id) br (get ps "ntc:br-dispensa") tw (get ps "ntc:tw-zigian")]
    (is (and (= (get br "status") ":genuine") (= (get br "proc") "proc:br-dispensa")))
    (is (dl-anchor? br "XXIX"))
    (is (and (= (get tw "status") ":genuine") (= (get tw "proc") "proc:tw-jiegu")))
    (is (dl-anchor? tw "11條")) (is (opt-id? tw ":feiziyuan"))))

(deftest test-wave23-cn-nl-labor
  (let [[ps _] (by-id) cn (get ps "ntc:cn-jiechu") nl (get ps "ntc:nl-ontslag")]
    (is (and (= (get cn "status") ":genuine") (= (get cn "proc") "proc:cn-jiechu")))
    (is (dl-anchor? cn "27条")) (is (dl-rule? cn "2N"))
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-ontslag")))
    (is (= (get (first (get nl "deadlines")) "critical") true))
    (is (str/includes? (get (first (get nl "deadlines")) "anchor") "7:686a"))))

(deftest test-wave24-es-it-housing-ch-labor
  (let [[ps _] (by-id) es (get ps "ntc:es-desahucio") it (get ps "ntc:it-sfratto") ch (get ps "ntc:ch-kuendigung")]
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-desahucio")))
    (is (= (get (first (get es "deadlines")) "critical") true))
    (is (dl-rule? es "enervación"))
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-sfratto")))
    (is (= (get (first (get it "deadlines")) "critical") true))
    (is (dl-rule? it "termine di grazia"))
    (is (and (= (get ch "status") ":genuine") (= (get ch "proc") "proc:ch-arbeitskuendigung")))
    (is (dl-anchor? ch "336b"))))

(deftest test-critical-banner-in-report
  (let [[_ notices] (terms/load-docs)
        ps (r/plans notices (r/load-procs))
        text (r/report ps)]
    (is (str/includes? text "⚠ 期限ルール"))))

(deftest test-wave25-sg-pt-labor
  (let [[ps _] (by-id) sg (get ps "ntc:sg-dismissal") pt (get ps "ntc:pt-despedimento")]
    (is (and (= (get sg "status") ":genuine") (= (get sg "proc") "proc:sg-dismissal")))
    (is (= (get (first (get sg "deadlines")) "critical") true))
    (is (str/includes? (get (first (get sg "deadlines")) "rule") "1か月"))
    (is (and (= (get pt "status") ":genuine") (= (get pt "proc") "proc:pt-despedimento")))
    (is (= (get (first (get pt "deadlines")) "critical") true))
    (is (and (str/includes? (get (first (get pt "deadlines")) "rule") "60日")
             (str/includes? (get (first (get pt "deadlines")) "rule") "5日")))))

(deftest test-wave26-se-pl-ie-labor
  (let [[ps _] (by-id) se (get ps "ntc:se-uppsagning") pl (get ps "ntc:pl-wypowiedzenie") ie (get ps "ntc:ie-dismissal")]
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-uppsagning")))
    (is (and (= (get (first (get se "deadlines")) "critical") true)
             (str/includes? (get (first (get se "deadlines")) "anchor") "LAS")))
    (is (and (= (get pl "status") ":genuine") (= (get pl "proc") "proc:pl-wypowiedzenie")))
    (is (str/includes? (get (first (get pl "deadlines")) "rule") "21 dni"))
    (is (and (= (get ie "status") ":genuine") (= (get ie "proc") "proc:ie-dismissal")))
    (is (dl-anchor? ie "s.41"))))

(deftest test-wave27-no-nz-in-labor
  (let [[ps _] (by-id) no (get ps "ntc:no-oppsigelse") nz (get ps "ntc:nz-dismissal") ind (get ps "ntc:in-retrench")]
    (is (and (= (get no "status") ":genuine") (= (get no "proc") "proc:no-oppsigelse")))
    (is (and (= (get (first (get no "deadlines")) "critical") true)
             (str/includes? (get (first (get no "deadlines")) "anchor") "§17-3")))
    (is (and (= (get nz "status") ":genuine") (= (get nz "proc") "proc:nz-dismissal")))
    (is (= (get nz "channel") ":email"))
    (is (str/includes? (get (first (get nz "deadlines")) "rule") "90日"))
    (is (and (= (get ind "status") ":genuine") (= (get ind "proc") "proc:in-termination")))
    (is (dl-anchor? ind "25F"))
    (is (some #(str/includes? (get % "label") "Form I") (get ind "options")))))

(deftest test-wave28-civil-only-eliminated
  (let [[ps _] (by-id)]
    (doseq [[nid pid] [["ntc:dk-opsigelse" "proc:dk-opsigelse"]
                       ["ntc:fi-irtisanominen" "proc:fi-irtisanominen"]
                       ["ntc:be-licenciement" "proc:be-licenciement"]
                       ["ntc:mx-despido" "proc:mx-despido"]
                       ["ntc:ar-despido" "proc:ar-despido"]]]
      (let [p (get ps nid)]
        (is (and (= (get p "status") ":genuine") (= (get p "proc") pid)) nid)))
    (is (= (get (first (get (get ps "ntc:mx-despido") "deadlines")) "critical") true))
    (is (= (get (first (get (get ps "ntc:be-licenciement") "deadlines")) "critical") true))
    (is (some #(str/includes? (get % "label") "telegrama") (get (get ps "ntc:ar-despido") "options")))
    (let [at (get ps "ntc:at-kuendigung")]
      (is (and (= (get at "status") ":genuine") (= (get at "proc") "proc:at-kuendigung")))
      (is (and (= (get (first (get at "deadlines")) "critical") true)
               (str/includes? (get (first (get at "deadlines")) "anchor") "§105"))))))

(deftest test-wave29-nl-br-se-housing
  (let [[ps procs] (by-id) nl (get ps "ntc:nl-huur") br (get ps "ntc:br-despejo") se (get ps "ntc:se-hyres")]
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-huuropzegging")))
    (is (dl-anchor? nl "7:272"))
    (is (and (= (get br "status") ":genuine") (= (get br "proc") "proc:br-despejo")))
    (is (and (= (get (first (get br "deadlines")) "critical") true)
             (str/includes? (get (first (get br "deadlines")) "rule") "purga da mora")))
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-hyresuppsagning")))
    (is (dl-rule? se "hyresnämnden"))
    (let [base {":notice/id" "ntc:x" ":notice/channel" ":mail" ":notice/sourcing" ":synthetic"}
          [p1 _] (r/classify (assoc base ":notice/jurisdiction" ":se" ":notice/text" "Uppsägning av din anställning") procs)
          [p2 _] (r/classify (assoc base ":notice/jurisdiction" ":se" ":notice/text" "Uppsägning") procs)]
      (is (= (get p1 ":proc/id") "proc:se-uppsagning"))
      (is (nil? p2)))))

(deftest test-wave30-es-br-it-enforcement
  (let [[ps _] (by-id) es (get ps "ntc:es-embargo") br (get ps "ntc:br-penhora") it (get ps "ntc:it-pignoramento")]
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-embargo")))
    (is (dl-anchor? es "607"))
    (is (and (= (get br "status") ":genuine") (= (get br "proc") "proc:br-penhora")))
    (is (dl-anchor? br "833"))
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-pignoramento")))
    (is (any-dl it #(and (str/includes? (get % "anchor") "545") (str/includes? (get % "rule") "un quinto"))))))

(deftest test-wave31-insolvency-es-it-nl
  (let [[ps _] (by-id) es (get ps "ntc:es-concurso") it (get ps "ntc:it-insinuazione") nl (get ps "ntc:nl-faillissement")]
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-concurso")))
    (is (= (get (first (get es "deadlines")) "critical") true))
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-insinuazione")))
    (is (dl-rule? it "PEC"))
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-faillissement")))
    (is (dl-anchor? nl "108-110"))))

(deftest test-insolvency-kaiyaku-crosscheck-invariant
  (let [[_ procs] (by-id)]
    (doseq [p procs :when (= (get p ":proc/track") ":insolvency")]
      (is (some #(str/includes? (get % ":opt/label") "kaiyaku") (get p ":proc/options")) (get p ":proc/id")))))

(deftest test-wave32-es-it-br-family
  (let [[ps _] (by-id) es (get ps "ntc:es-divorcio") it (get ps "ntc:it-separazione") br (get ps "ntc:br-divorcio")]
    (is (and (= (get es "status") ":genuine") (= (get es "proc") "proc:es-divorcio")))
    (is (= (get (first (get es "deadlines")) "critical") true))
    (is (some #(str/includes? (get % "label") "justicia gratuita") (get es "options")))
    (is (and (= (get it "status") ":genuine") (= (get it "proc") "proc:it-separazione")))
    (is (dl-anchor? it "473-bis"))
    (is (and (= (get br "status") ":genuine") (= (get br "proc") "proc:br-divorcio")))
    (is (some #(str/includes? (get % "label") "cartório") (get br "options")))))

(deftest test-wave33-pl-at-ch-housing
  (let [[ps procs] (by-id) pl (get ps "ntc:pl-eksmisja") at (get ps "ntc:at-aufkuendigung") ch (get ps "ntc:ch-mietkuendigung")]
    (is (and (= (get pl "status") ":genuine") (= (get pl "proc") "proc:pl-eksmisja")))
    (is (dl-rule? pl "okres ochronny"))
    (is (and (= (get at "status") ":genuine") (= (get at "proc") "proc:at-mietkuendigung")))
    (is (and (= (get (first (get at "deadlines")) "critical") true)
             (str/includes? (get (first (get at "deadlines")) "anchor") "§33")))
    (is (and (= (get ch "status") ":genuine") (= (get ch "proc") "proc:ch-mietkuendigung")))
    (is (dl-anchor? ch "266l"))
    (let [base {":notice/id" "ntc:x" ":notice/jurisdiction" ":ch" ":notice/channel" ":mail" ":notice/sourcing" ":synthetic"}
          [p1 _] (r/classify (assoc base ":notice/text" "Kündigung des Arbeitsverhältnisses") procs)
          [p2 _] (r/classify (assoc base ":notice/text" "Kündigung des Mietverhältnisses") procs)]
      (is (= (get p1 ":proc/id") "proc:ch-arbeitskuendigung"))
      (is (= (get p2 ":proc/id") "proc:ch-mietkuendigung")))))

(deftest test-wave34-nl-se-tw-enforcement
  (let [[ps _] (by-id) nl (get ps "ntc:nl-beslag") se (get ps "ntc:se-utmatning") tw (get ps "ntc:tw-qiangzhi")]
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-beslag")))
    (is (dl-rule? nl "beslagvrije voet"))
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-utmatning")))
    (is (dl-rule? se "förbehållsbelopp"))
    (is (and (= (get tw "status") ":genuine") (= (get tw "proc") "proc:tw-qiangzhi")))
    (is (dl-anchor? tw "115-1"))))

(deftest test-wave36-cn-housing
  (let [[ps _] (by-id) cn (get ps "ntc:cn-tuizu")]
    (is (and (= (get cn "status") ":genuine") (= (get cn "proc") "proc:cn-tuizu")))
    (is (dl-anchor? cn "725"))))

(deftest test-wave37-au-ca-insolvency
  (let [[ps _] (by-id) au (get ps "ntc:au-va") ca (get ps "ntc:ca-bia")]
    (is (and (= (get au "status") ":genuine") (= (get au "proc") "proc:au-insolvency-notice")))
    (is (= (get au "channel") ":email")) (is (dl-rule? au "FEG"))
    (is (and (= (get ca "status") ":genuine") (= (get ca "proc") "proc:ca-bia-notice")))
    (is (dl-rule? ca "WEPP"))))

(deftest test-wave38-tw-se-nl-family
  (let [[ps _] (by-id) tw (get ps "ntc:tw-lihun") se (get ps "ntc:se-skilsmassa") nl (get ps "ntc:nl-echtscheiding")]
    (is (and (= (get tw "status") ":genuine") (= (get tw "proc") "proc:tw-lihun")))
    (is (dl-rule? tw "調解前置"))
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-skilsmassa")))
    (is (dl-rule? se "betänketid"))
    (is (and (= (get nl "status") ":genuine") (= (get nl "proc") "proc:nl-echtscheiding")))
    (is (= (get (first (get nl "deadlines")) "critical") true))
    (is (some #(str/includes? (get % "label") "toevoeging") (get nl "options")))))

(deftest test-wave39-pl-at-pt-enforcement
  (let [[ps _] (by-id) pl (get ps "ntc:pl-zajecie") at (get ps "ntc:at-exekution") pt (get ps "ntc:pt-penhora")]
    (is (and (= (get pl "status") ":genuine") (= (get pl "proc") "proc:pl-egzekucja")))
    (is (dl-rule? pl "kwota wolna"))
    (is (and (= (get at "status") ":genuine") (= (get at "proc") "proc:at-exekution")))
    (is (dl-rule? at "Existenzminimum"))
    (is (and (= (get pt "status") ":genuine") (= (get pt "proc") "proc:pt-penhora")))
    (is (dl-anchor? pt "738"))))

(deftest test-wave40-nordic-housing
  (let [[ps procs] (by-id) no (get ps "ntc:no-leie") dk (get ps "ntc:dk-leje") fi (get ps "ntc:fi-vuokra")]
    (is (and (= (get no "status") ":genuine") (= (get no "proc") "proc:no-husleie")))
    (is (and (= (get (first (get no "deadlines")) "critical") true)
             (str/includes? (get (first (get no "deadlines")) "anchor") "§9-8")))
    (is (and (= (get dk "status") ":genuine") (= (get dk "proc") "proc:dk-lejemaal")))
    (is (str/includes? (get (first (get dk "deadlines")) "rule") "6 uger"))
    (is (and (= (get fi "status") ":genuine") (= (get fi "proc") "proc:fi-vuokra")))
    (let [base {":notice/id" "ntc:x" ":notice/channel" ":mail" ":notice/sourcing" ":synthetic"}
          [p1 _] (r/classify (assoc base ":notice/jurisdiction" ":no" ":notice/text" "Oppsigelse av ditt arbeidsforhold") procs)]
      (is (= (get p1 ":proc/id") "proc:no-oppsigelse")))))

(deftest test-wave42-nordic-insolvency
  (let [[ps _] (by-id) se (get ps "ntc:se-konkurs") dk (get ps "ntc:dk-konkurs") no (get ps "ntc:no-konkurs")]
    (is (and (= (get se "status") ":genuine") (= (get se "proc") "proc:se-konkurs")))
    (is (some #(str/includes? (get % "label") "lönegaranti") (get se "options")))
    (is (and (= (get dk "status") ":genuine") (= (get dk "proc") "proc:dk-konkurs")))
    (is (some #(str/includes? (get % "label") "Garantifond") (get dk "options")))
    (is (and (= (get no "status") ":genuine") (= (get no "proc") "proc:no-konkurs")))
    (is (= (get no "channel") ":email"))))

(deftest test-wave44-pl-at-ch-family
  (let [[ps _] (by-id) pl (get ps "ntc:pl-rozwod") at (get ps "ntc:at-scheidung") ch (get ps "ntc:ch-scheidung")]
    (is (and (= (get pl "status") ":genuine") (= (get pl "proc") "proc:pl-rozwod")))
    (is (and (= (get at "status") ":genuine") (= (get at "proc") "proc:at-scheidung")))
    (is (dl-rule? at "§55a"))
    (is (and (= (get ch "status") ":genuine") (= (get ch "proc") "proc:ch-scheidung")))
    (is (dl-rule? ch "2年の別居"))))

(deftest test-wave45-ch-be-fi-enforcement
  (let [[ps _] (by-id) ch (get ps "ntc:ch-lohn") be (get ps "ntc:be-saisie") fi (get ps "ntc:fi-ulosmittaus")]
    (is (and (= (get ch "status") ":genuine") (= (get ch "proc") "proc:ch-lohnpfaendung")))
    (is (dl-anchor? ch "Art. 93"))
    (is (and (= (get be "status") ":genuine") (= (get be "proc") "proc:be-saisie")))
    (is (dl-rule? be "申告しないと反映されない"))
    (is (and (= (get fi "status") ":genuine") (= (get fi "proc") "proc:fi-ulosmittaus")))
    (is (dl-rule? fi "suojaosuus"))))

(deftest test-critical-implies-protective
  (let [[_ procs] (by-id)]
    (doseq [p procs :when (some #(get % ":dl/critical") (get p ":proc/deadline-rules" []))]
      (is (some #(get % ":opt/protective") (get p ":proc/options")) (get p ":proc/id")))))

(deftest test-procedures-never-cross-jurisdictions
  (let [[_ procs] (by-id)
        n {":notice/id" "ntc:x" ":notice/jurisdiction" ":us" ":notice/channel" ":special-service"
           ":notice/text" "支払督促を発する。" ":notice/sourcing" ":synthetic"}
        [proc status] (r/classify n procs)]
    (is (and (nil? proc) (contains? #{":unknown" ":suspected-fake"} status)))))

(defn -main [& _] (run-tests 'tate.tests.test-respond))
