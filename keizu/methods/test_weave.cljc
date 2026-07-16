(ns keizu.methods.test-weave
  "test_weave.cljc — 系図 (keizu) weave validation + concentration. ADR-2606066000.
  1:1 Clojure port of `methods/test_weave.py` (clojure.test). Every Python assertion ported,
  plus a byte/numeric-parity check on the seed concentration output."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [keizu.methods.weave :as w]
            #?(:clj [keizu.methods.edn :as e])))

(def seed-path "20-actors/keizu/data/seed-relation-graph.kotoba.edn")

#?(:clj (defn- g [] (w/weave (e/load-edn seed-path))))

;; expect_raises(fn, contains=…) → assert an ex-info whose message contains `frag`.
(defn- raises? [f frag]
  (try (f) false
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) ex
         (str/includes? (#?(:clj #(.getMessage %) :cljs ex-message) ex) frag))
       (catch #?(:clj Exception :cljs js/Error) ex
         (str/includes? (str (#?(:clj #(.getMessage %) :cljs ex-message) ex)) frag))))

#?(:clj
   (deftest test-seed-weaves-clean
     (let [gg (g)]
       (is (>= (count (#'w/omap-items (get gg "nodes"))) 15))
       (is (= 3 (count (#'w/omap-items (get gg "committees")))))
       (is (>= (count (get gg "rels")) 14))
       (is (= 6 (count (get gg "money")))))))

(deftest test-g1-private-person-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":private-person"
                                  ":node/sourcing" ":representative"}) "G1")))

(deftest test-g4-power-score-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":public-role"
                                  ":node/power-score" 9 ":node/sourcing" ":representative"}) "G4")))

(deftest test-g9-no-doxxing-email-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":public-role"
                                  ":node/email" "a@b.jp" ":node/sourcing" ":representative"}) "no-doxxing")))

(deftest test-g9-no-doxxing-home-address-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":public-role"
                                  ":node/address" "1-2-3" ":node/sourcing" ":representative"}) "no-doxxing")))

(deftest test-g9-no-doxxing-mynumber-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":public-role"
                                  ":node/mynumber" "999" ":node/sourcing" ":representative"}) "no-doxxing")))

(deftest test-public-organ-node-still-valid
  ;; a normal public-seat node (label/jurisdiction/organ) must NOT trip the PII guard
  (is (nil? (w/validate-node {":node/id" "x" ":node/scope" ":public-role" ":node/label" "会長 (seat)"
                              ":node/jurisdiction" "jp" ":node/organ" "財務省" ":node/sourcing" ":representative"}))))

(deftest test-g2-verdict-rel-kind-rejected
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":corruption"
                                 ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative"
                                 ":rel/sources" ["u1" "u2"]}) "G2")))

(deftest test-g2-notice-must-be-true
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":funding-tie"
                                 ":rel/non-adjudicating-notice" false ":rel/sourcing" ":representative"
                                 ":rel/sources" ["u1" "u2"]}) "non-adjudicating")))

(deftest test-g3-rel-needs-two-sources
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":funding-tie"
                                 ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative"
                                 ":rel/sources" ["only-one"]}) "G3")))

(deftest test-rider-deny-commercial-gov-intel-source-rel
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":funding-tie"
                                 ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative"
                                 ":rel/sources" ["https://about.bloomberg.com/government" "https://x.gov/"]})
               "Rider §2(e)")))

(deftest test-rider-deny-commercial-gov-intel-source-money
  (is (raises? #(w/validate-money {":money/id" "m" ":money/payer" "a" ":money/payee" "b" ":money/kind" ":subsidy"
                                   ":money/sourcing" ":representative"
                                   ":money/sources" ["fiscalnote feed" "https://x.gov/"]}) "Rider §2(e)")))

(deftest test-g2-bribe-money-rejected
  (is (raises? #(w/validate-money {":money/id" "m" ":money/payer" "a" ":money/payee" "b" ":money/kind" ":bribe"
                                   ":money/sourcing" ":representative" ":money/sources" ["u1" "u2"]}) "G2")))

(deftest test-g3-money-needs-two-sources
  (is (raises? #(w/validate-money {":money/id" "m" ":money/payer" "a" ":money/payee" "b" ":money/kind" ":subsidy"
                                   ":money/sourcing" ":representative" ":money/sources" ["u1"]}) "G3")))

(defn- money [amount]
  {":money/id" "m" ":money/payer" "a" ":money/payee" "b" ":money/kind" ":subsidy"
   ":money/sourcing" ":representative" ":money/sources" ["u1" "u2"] ":money/amount" amount})

(deftest test-negative-amount-rejected
  (is (raises? #(w/validate-money (money -1.0)) "≥ 0")))

(deftest test-nan-amount-rejected
  (is (raises? #(w/validate-money (money #?(:clj Double/NaN :cljs js/NaN))) "finite")))

(deftest test-inf-amount-rejected
  (is (raises? #(w/validate-money (money #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))) "finite")))

(deftest test-non-numeric-amount-rejected
  (is (raises? #(w/validate-money (money "lots")) "number")))

(deftest test-zero-amount-allowed
  (is (nil? (w/validate-money (money 0.0)))))   ;; 0 is degenerate but not corrupting; allowed

#?(:clj
   (deftest test-cross-committee-seat-detected
     (let [c (w/concentration (g))
           seats (set (map #(get % "seat") (get c "cross_committee_seats")))]
       (is (contains? seats "jp-fsc-biz-1")))))

#?(:clj
   (deftest test-committee-cross-organ
     (let [c (w/concentration (g))
           fsc (first (filter #(= "jp-fiscal-system-council" (get % "committee"))
                              (get c "committee_cross_organ")))]
       (is (= 3 (get fsc "member_count")))
       (is (>= (get fsc "distinct_organs") 2)))))

#?(:clj
   (deftest test-money-hhi-in-range
     (let [c (w/concentration (g))
           hhi (get-in c ["money_concentration" "hhi"])]
       (is (and (< 0.0 hhi) (<= hhi 1.0)))
       ;; jp-vendor-x receives the most flows → should be the top share
       (is (= "jp-vendor-x" (first (first (get-in c ["money_concentration" "shares"]))))))))

#?(:clj
   (deftest test-revolving-door-detected
     (let [c (w/concentration (g))]
       (is (some #(= "jp-meti" (get % "from")) (get c "revolving_door"))))))

;; ── edge branches ────────────────────────────────────────────────────────────────
(deftest test-g11-node-missing-sourcing-rejected
  (is (raises? #(w/validate-node {":node/id" "x" ":node/scope" ":public-role"}) "G11")))

(deftest test-g11-rel-missing-sourcing-rejected
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":funding-tie"
                                 ":rel/non-adjudicating-notice" true ":rel/sources" ["u1" "u2"]}) "G11")))

(deftest test-rel-sources-not-a-list-rejected
  (is (raises? #(w/validate-rel {":rel/id" "r" ":rel/source" "a" ":rel/target" "b" ":rel/kind" ":funding-tie"
                                 ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative"
                                 ":rel/sources" "u1,u2"}) "G3")))

(deftest test-empty-graph-concentration-is-safe
  (let [c (w/concentration (w/weave {}))]
    (is (= 0 (get c "node_count")))
    (is (= 0.0 (get-in c ["money_concentration" "hhi"])))     ;; no div-by-zero
    (is (= 0.0 (get-in c ["money_concentration" "total"])))
    (is (= [] (get c "cross_committee_seats")))
    (is (= [] (get c "revolving_door")))))

#?(:clj
   (deftest test-payer-concentration
     (let [c (w/concentration (g))
           pc (get c "payer_concentration")]
       (is (and (< 0.0 (get pc "hhi")) (<= (get pc "hhi") 1.0)))
       ;; jp-meti disburses the most flows in the seed → top payer share
       (is (= "jp-meti" (first (first (get pc "shares"))))))))

(deftest test-payer-concentration-empty-safe
  (let [pc (get (w/concentration (w/weave {})) "payer_concentration")]
    (is (and (= 0.0 (get pc "hhi")) (= 0.0 (get pc "total")) (= [] (get pc "shares"))))))

#?(:clj
   (deftest test-award-and-fund-co-occurrence
     (let [c (w/concentration (g))
           af (get c "award_and_fund")
           vx (first (filter #(= "jp-vendor-x" (get % "node")) af))]
       (is (some? vx))
       (is (contains? (set (get vx "received_from")) "jp-meti"))
       (is (contains? (set (get vx "donated_to")) "jp-party-a"))
       (is (and (> (get vx "received_total") 0) (> (get vx "donated_total") 0))))))

#?(:clj
   (deftest test-award-and-fund-requires-both-legs
     ;; us-vendor-y received an award but made no donation → must NOT appear
     (let [nodes (set (map #(get % "node") (get (w/concentration (g)) "award_and_fund")))]
       (is (not (contains? nodes "us-vendor-y")))
       (is (not (contains? nodes "ec-eg-ind-1"))))))   ;; got a grant, no donation

(deftest test-award-and-fund-empty-safe
  (is (= [] (get (w/concentration (w/weave {})) "award_and_fund"))))

#?(:clj
   (deftest test-connector-seat-bridges-two-organs
     (let [c (w/concentration (g))
           conn (first (filter #(= "jp-fsc-biz-1" (get % "seat")) (get c "connector_seats")))]
       (is (some? conn))
       (is (>= (get conn "organs_bridged") 2)))))

(deftest test-connector-requires-distinct-organs
  ;; two committees under the SAME organ → not a cross-organ connector
  (let [g (w/weave
           {":nodes" [{":node/id" "s1" ":node/scope" ":public-role" ":node/sourcing" ":representative"}]
            ":committees" [{":committee/id" "c1" ":committee/organ" "X" ":committee/members" ["s1"]
                            ":committee/sources" ["u"] ":committee/sourcing" ":representative"}
                           {":committee/id" "c2" ":committee/organ" "X" ":committee/members" ["s1"]
                            ":committee/sources" ["u"] ":committee/sourcing" ":representative"}]
            ":rels" [{":rel/id" "a" ":rel/source" "s1" ":rel/target" "c1" ":rel/kind" ":committee-membership"
                      ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative" ":rel/sources" ["u" "v"]}
                     {":rel/id" "b" ":rel/source" "s1" ":rel/target" "c2" ":rel/kind" ":committee-membership"
                      ":rel/non-adjudicating-notice" true ":rel/sourcing" ":representative" ":rel/sources" ["u" "v"]}]})]
    (is (= [] (w/connector-seats g)))))   ;; same organ → no cross-organ bridge

#?(:clj
   (deftest test-active-as-of-is-monotonic
     (let [gg (g)
           early (w/active-as-of gg 20240101)   ;; before everything
           mid (w/active-as-of gg 20250301)
           late (w/active-as-of gg 20260101)]   ;; after everything
       (is (= 0 (get early "active_rels")))
       (is (<= (get early "active_rels") (get mid "active_rels") (get late "active_rels")))
       (is (= (get late "active_rels") (get late "total_rels")))
       (is (= (get late "active_committees") (get late "total_committees"))))))

#?(:clj
   (deftest test-active-as-of-partial-window
     ;; the revolving-door edge (as-of 20241001) is active by end-2024 but no 2025 memberships are
     (let [snap (w/active-as-of (g) 20241101)]
       (is (< 0 (get snap "active_rels") (get snap "total_rels"))))))

;; ── statements (発言) ──────────────────────────────────────────────────────────
(deftest test-statement-needs-speaker
  (is (raises? #(w/validate-statement {":statement/id" "s" ":statement/sources" ["u"]
                                       ":statement/sourcing" ":representative"}) "speaker")))

(deftest test-statement-needs-source
  (is (raises? #(w/validate-statement {":statement/id" "s" ":statement/speaker" "a"
                                       ":statement/sources" [] ":statement/sourcing" ":representative"}) "G3")))

(deftest test-statement-needs-sourcing
  (is (raises? #(w/validate-statement {":statement/id" "s" ":statement/speaker" "a"
                                       ":statement/sources" ["u"]}) "G11")))

(defn- committee [& {:as over}]
  (merge {":committee/id" "c1" ":committee/members" ["s1"]
          ":committee/sources" ["u"] ":committee/sourcing" ":representative"} over))

(deftest test-committee-valid
  (is (nil? (w/validate-committee (committee)))))

(deftest test-committee-needs-member
  (is (raises? #(w/validate-committee (committee ":committee/members" [])) "G1")))

(deftest test-committee-needs-source
  (is (raises? #(w/validate-committee (committee ":committee/sources" [])) "G3")))

(deftest test-committee-needs-sourcing
  (is (raises? #(w/validate-committee (dissoc (committee) ":committee/sourcing")) "G11")))

(deftest test-committee-deny-commercial-gov-intel-source
  (is (raises? #(w/validate-committee (committee ":committee/sources" ["bloomberg gov feed"])) "Rider §2(e)")))

#?(:clj
   (deftest test-seed-committees-validate
     (doseq [[_ c] (#'w/omap-items (get (g) "committees"))]
       (is (nil? (w/validate-committee c))))))

(deftest test-statement-deny-commercial-gov-intel-source
  ;; the SOURCE_DENY list must apply to statements too, not only rels/money
  (is (raises? #(w/validate-statement {":statement/id" "s" ":statement/speaker" "a" ":statement/sourcing" ":representative"
                                       ":statement/sources" ["https://about.bloomberg.com/government"]}) "Rider §2(e)")))

#?(:clj
   (deftest test-statement-index-by-speaker-and-topic
     (let [si (get (w/concentration (g)) "statement_index")
           speakers (into {} (get si "by_speaker"))
           topics (set (map #(get % "topic") (get si "by_topic")))]
       (is (= 3 (get si "count")))
       (is (contains? speakers "jp-fsc-chair"))
       (is (some #(str/includes? (str/lower-case %) "fiscal") topics)))))

(deftest test-statement-index-empty-safe
  (let [si (get (w/concentration (w/weave {})) "statement_index")]
    (is (and (= 0 (get si "count")) (= [] (get si "by_speaker")) (= [] (get si "by_topic"))))))

;; ── by-jurisdiction ────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-by-jurisdiction-covers-global-seed
     (let [bj (into {} (map (fn [j] [(get j "jurisdiction") j]) (w/by-jurisdiction (g))))]
       (is (clojure.set/subset? #{"jp" "us" "eu" "oecd"} (set (keys bj))))
       (is (and (>= (get-in bj ["jp" "nodes"]) 1) (>= (get-in bj ["jp" "committees"]) 1)))
       ;; jp-meti disburses the JP procurement/subsidy flows → jp money_total is the largest
       (is (= (get-in bj ["jp" "money_total"])
              (apply max (map #(get % "money_total") (vals bj))))))))

#?(:clj
   (deftest test-by-jurisdiction-money-attributed-to-payer
     ;; an EU grant (ec-digit payer, jurisdiction eu) lands under eu, not the payee's jurisdiction
     (let [bj (into {} (map (fn [j] [(get j "jurisdiction") j]) (w/by-jurisdiction (g))))]
       (is (> (get-in bj ["eu" "money_total"]) 0)))))

(deftest test-by-jurisdiction-empty-safe
  (is (= [] (w/by-jurisdiction (w/weave {})))))

;; ── referential integrity ────────────────────────────────────────────────────────
#?(:clj
   (deftest test-seed-has-no-dangling-refs
     (let [rep (w/check-integrity (g))]
       (is (= 0 (get rep "dangling_count")))
       (is (nil? (w/assert-integrity (g)))))))   ;; strict mode must not raise on a clean seed

(deftest test-dangling-rel-target-detected
  (let [g (w/weave
           {":nodes" [{":node/id" "s1" ":node/scope" ":public-role" ":node/sourcing" ":representative"}]
            ":rels" [{":rel/id" "r" ":rel/source" "s1" ":rel/target" "ghost"
                      ":rel/kind" ":appointment" ":rel/non-adjudicating-notice" true
                      ":rel/sourcing" ":representative" ":rel/sources" ["u" "v"]}]})
        rep (w/check-integrity g)]
    (is (= 1 (get rep "dangling_count")))
    (is (and (= "ghost" (get-in rep ["dangling" 0 "ref"]))
             (= "target" (get-in rep ["dangling" 0 "field"]))))
    (is (raises? #(w/assert-integrity g) "dangling"))))

(deftest test-dangling-money-payee-detected
  (let [g (w/weave
           {":nodes" [{":node/id" "jp-meti" ":node/scope" ":public-org" ":node/sourcing" ":representative"}]
            ":money" [{":money/id" "m" ":money/payer" "jp-meti" ":money/payee" "nope"
                       ":money/kind" ":subsidy" ":money/sourcing" ":representative"
                       ":money/sources" ["u" "v"]}]})
        rep (w/check-integrity g)]
    (is (and (= 1 (get rep "dangling_count")) (= "payee" (get-in rep ["dangling" 0 "field"]))))))

(deftest test-dangling-committee-member-detected
  (let [g (w/weave
           {":committees" [{":committee/id" "c1" ":committee/members" ["ghost-seat"]
                            ":committee/sources" ["u"] ":committee/sourcing" ":representative"}]})
        rep (w/check-integrity g)]
    (is (and (= 1 (get rep "dangling_count")) (= "member" (get-in rep ["dangling" 0 "field"]))))))

(deftest test-rel-target-may-be-a-committee
  ;; a tie pointing at a committee id (not a node) is NOT dangling — rel id-space includes committees
  (let [g (w/weave
           {":nodes" [{":node/id" "s1" ":node/scope" ":public-role" ":node/sourcing" ":representative"}]
            ":committees" [{":committee/id" "c1" ":committee/members" ["s1"]
                            ":committee/sources" ["u"] ":committee/sourcing" ":representative"}]
            ":rels" [{":rel/id" "r" ":rel/source" "s1" ":rel/target" "c1"
                      ":rel/kind" ":committee-membership" ":rel/non-adjudicating-notice" true
                      ":rel/sourcing" ":representative" ":rel/sources" ["u" "v"]}]})]
    (is (= 0 (get (w/check-integrity g) "dangling_count")))))

(deftest test-unknown-organ-member-is-tolerated
  (let [g (w/weave
           {":nodes" [{":node/id" "s1" ":node/scope" ":public-role"
                       ":node/sourcing" ":representative"}]   ;; no :node/organ
            ":committees" [{":committee/id" "c1" ":committee/members" ["s1" "ghost"]
                            ":committee/sources" ["u"] ":committee/sourcing" ":representative"}]})
        rows (get (w/concentration g) "committee_cross_organ")]
    (is (= 2 (get (first rows) "member_count")))                 ;; both counted
    (is (contains? (set (get (first rows) "organs")) "(unknown)"))))  ;; missing-organ seat folded to (unknown)

;; ── byte/numeric parity on the seed concentration output ──────────────────────────
;; This exact string is `python3 -c "json.dumps(concentration(weave(load_edn(SEED))),
;; ensure_ascii=False)"` over the committed seed. The cljc to-json must reproduce it byte-for-byte
;; (insertion-order keys, Python float repr, HALF_EVEN round, ensure_ascii=False).
(def expected-seed-json
  (str "{\"node_count\": 18, \"committee_count\": 3, \"rel_count\": 15, \"money_count\": 6, "
       "\"statement_count\": 3, \"committee_cross_organ\": [{\"committee\": \"jp-fiscal-system-council\", "
       "\"label\": \"財政制度等審議会\", \"member_count\": 3, \"distinct_organs\": 3, \"organs\": "
       "[\"(academia)\", \"(industry)\", \"財務省\"]}, {\"committee\": \"jp-regulatory-reform-council\", "
       "\"label\": \"規制改革推進会議\", \"member_count\": 3, \"distinct_organs\": 2, \"organs\": "
       "[\"(industry)\", \"内閣府\"]}, {\"committee\": \"us-fiscal-advisory\", \"label\": "
       "\"US Treasury Federal Advisory Committee (representative)\", \"member_count\": 2, "
       "\"distinct_organs\": 2, \"organs\": [\"(industry)\", \"Treasury\"]}], \"cross_committee_seats\": "
       "[{\"seat\": \"jp-fsc-biz-1\", \"committee_count\": 2, \"committees\": [\"jp-fiscal-system-council\", "
       "\"jp-regulatory-reform-council\"]}], \"connector_seats\": [{\"seat\": \"jp-fsc-biz-1\", "
       "\"committees\": [\"jp-fiscal-system-council\", \"jp-regulatory-reform-council\"], "
       "\"organs_bridged\": 2, \"organs\": [\"内閣府\", \"財務省\"]}], \"money_concentration\": "
       "{\"total\": 2347000000.0, \"hhi\": 0.9606, \"shares\": [[\"jp-vendor-x\", 0.97997443544951], "
       "[\"us-vendor-y\", 0.017043033659991477], [\"jp-party-a\", 0.0021303792074989347], "
       "[\"ec-eg-ind-1\", 0.0008521516829995739]], \"by_payee\": {\"jp-vendor-x\": 2300000000.0, "
       "\"jp-party-a\": 5000000.0, \"us-vendor-y\": 40000000.0, \"ec-eg-ind-1\": 2000000.0}}, "
       "\"payer_concentration\": {\"total\": 2347000000.0, \"hhi\": 0.9606, \"shares\": "
       "[[\"jp-meti\", 0.97997443544951], [\"us-gsa\", 0.017043033659991477], [\"jp-vendor-x\", "
       "0.0021303792074989347], [\"ec-digit\", 0.0008521516829995739]], \"by_payer\": "
       "{\"jp-meti\": 2300000000.0, \"jp-vendor-x\": 5000000.0, \"us-gsa\": 40000000.0, "
       "\"ec-digit\": 2000000.0}}, \"revolving_door\": [{\"from\": \"jp-meti\", \"from_label\": "
       "\"経済産業省 (METI)\", \"to\": \"jp-rrc-biz-1\", \"to_label\": \"規制改革推進会議 産業界委員 (seat)\", "
       "\"as_of\": 20241001}], \"award_and_fund\": [{\"node\": \"jp-vendor-x\", \"received_from\": "
       "[\"jp-meti\"], \"received_total\": 2300000000.0, \"donated_to\": [\"jp-party-a\"], "
       "\"donated_total\": 5000000.0}], \"statement_index\": {\"count\": 3, \"by_speaker\": "
       "[[\"jp-fsc-chair\", 1], [\"jp-rrc-chair\", 1], [\"us-faca-chair\", 1]], \"by_topic\": "
       "[{\"topic\": \"advisory recommendation (representative)\", \"speakers\": [\"us-faca-chair\"]}, "
       "{\"topic\": \"中期財政フレーム / fiscal consolidation\", \"speakers\": [\"jp-fsc-chair\"]}, "
       "{\"topic\": \"規制改革 / deregulation agenda\", \"speakers\": [\"jp-rrc-chair\"]}]}, "
       "\"by_jurisdiction\": [{\"jurisdiction\": \"jp\", \"nodes\": 10, \"committees\": 2, "
       "\"money_total\": 2305000000.0}, {\"jurisdiction\": \"us\", \"nodes\": 5, \"committees\": 1, "
       "\"money_total\": 40000000.0}, {\"jurisdiction\": \"eu\", \"nodes\": 2, \"committees\": 0, "
       "\"money_total\": 2000000.0}, {\"jurisdiction\": \"oecd\", \"nodes\": 1, \"committees\": 0, "
       "\"money_total\": 0.0}], \"integrity\": {\"dangling_count\": 0, \"dangling\": []}}"))

#?(:clj
   (deftest test-seed-concentration-byte-parity
     ;; the canonical JSON of the seed concentration must match Python's json.dumps byte-for-byte.
     (is (= expected-seed-json (w/to-json (w/concentration (g)))))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-weave)))
