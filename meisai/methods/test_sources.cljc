(ns meisai.methods.test-sources
  "test_sources.cljc — meisai 明細 worldwide coverage registry invariants. ADR-2606122400 R1.

  Guards the multi-issuer contract:
    - the PUBLIC coverage registry loads and emits well-formed :meisai.source/* EAVT datoms;
    - coverage is HONEST (supported vs registry-only worklist; networks counted);
    - resolve accepts keyword / string / ':id' forms;
    - normalize maps a raw issuer statement (any field names, any currency) → the canonical
      meisai intake, JPY → legacy :amount_jpy, others → generic :amount + :currency (minor units);
    - the G2 credential/PAN guard STILL fires on a normalized worldwide intake (defining gate)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [meisai.methods.sources :as sources]
            [meisai.methods.ingest :as ingest]
            [meisai.methods.kotoba :as kotoba]))

(def reg (sources/load-registry))

(deftest test-registry-loads
  (is (pos? (count (:sources reg))) "registry has sources")
  (let [su (sources/resolve-source reg :sumitclub)]
    (is (some? su) "sumitclub present")
    (is (= :supported (:status su)) "sumitclub is the one fetch-supported source")
    (is (:shape su) "supported source declares an adapter shape")))

(deftest test-registry-datoms
  (let [ds (sources/registry-datoms reg)]
    (is (every? #(= ":db/add" (nth % 0)) ds) "every registry datom is :db/add (append-only)")
    (is (every? #(str/starts-with? (nth % 1) "meisai-source:") ds) "entity ids are meisai-source:*")
    (is (some #(and (= ":meisai.source/name" (nth % 2)) (= "SuMi TRUST CLUB" (nth % 3))) ds)
        "sumitclub name lands")
    (is (some #(and (= ":meisai.source/network" (nth % 2)) (= ":visa" (nth % 3))) ds)
        "multi-valued networks emit one datom each")
    (is (= ds (sources/registry-datoms reg)) "registry datoms are deterministic")
    (is (= (kotoba/tx-cid (vec ds)) (kotoba/tx-cid (vec ds))) "registry tx CID is stable")))

(deftest test-coverage-honest
  (let [c (sources/coverage reg)]
    (is (= (:total c) (count (:sources reg))) "total counts every source")
    (is (= 1 (:supported c)) "exactly one fetch-supported source today (sumitclub)")
    (is (= (:registry-only c) (count (:worklist c))) "every registry-only source is on the worklist")
    (is (>= (:networks c) 12) "the networks layer is near-complete")
    (is (some #{"rakuten-card"} (:worklist c)) "a known unsupported issuer appears on the worklist")
    (is (= (sort (:worklist c)) (:worklist c)) "worklist is sorted (deterministic)")))

(deftest test-resolve-forms
  (is (= :sumitclub (:id (sources/resolve-source reg :sumitclub))) "resolve by keyword")
  (is (= :rakuten-card (:id (sources/resolve-source reg "rakuten-card"))) "resolve by bare string")
  (is (= :visa (:id (sources/resolve-source reg ":visa"))) "resolve by ':id' string")
  (is (nil? (sources/resolve-source reg :no-such-issuer)) "unknown id → nil"))

(deftest test-normalize-jpy
  ;; a JPY issuer with issuer-specific field names → canonical, keeping the legacy :amount_jpy path
  (let [entry {:id :rakuten-card :name "楽天カード" :kind :issuer :country :jp :region :apac
               :currency :jpy :status :supported
               :shape {:rows ":meisai" :month ":getsu" :date ":hi" :merchant ":mise"
                       :amount ":kingaku" :amount-scale :minor}}
        raw {":meisai" [{":hi" "2026-05-02" ":mise" "AMAZON.CO.JP" ":kingaku" 3980}]
             ":getsu" "2026-05"}
        doc (sources/normalize entry raw)
        datoms (ingest/statement-datoms doc (ingest/intake-cid (pr-str raw)))]
    (is (= ":rakuten-card" (get doc ":source")) "source set from id")
    (is (= "2026-05" (get doc ":statement/month")) "month remapped")
    (is (= 3980 (get-in doc [":statement/rows" 0 ":amount_jpy"])) "JPY row keeps :amount_jpy")
    (is (nil? (get doc ":statement/currency")) "JPY carries no generic currency tag")
    (is (some #(= ":meisai.row/amount-jpy" (nth % 2)) datoms) "lands on the canonical JPY attribute")
    (is (not-any? #(= ":meisai.row/currency" (nth % 2)) datoms) "no generic currency datom for JPY")))

(deftest test-normalize-foreign-currency
  ;; a USD issuer, amounts in MAJOR units (39.80 dollars) → minor units (3980 cents)
  (let [entry {:id :amex-us :name "American Express (US)" :kind :issuer :country :us :region :namer
               :currency :usd :networks [:amex] :status :supported
               :shape {:rows ":txns" :month ":period" :date ":posted" :merchant ":desc"
                       :amount ":amt" :amount-scale :major}}
        raw {":txns" [{":posted" "2026-05-03" ":desc" "STEAM GAMES" ":amt" 39.80}
                      {":posted" "2026-05-09" ":desc" "DELTA AIR" ":amt" 412.50}]
             ":period" "2026-05"}
        doc (sources/normalize entry raw)
        datoms (ingest/statement-datoms doc (ingest/intake-cid (pr-str raw)))]
    (is (= 3980 (get-in doc [":statement/rows" 0 ":amount"])) "39.80 USD → 3980 cents (minor units)")
    (is (= 41250 (get-in doc [":statement/rows" 1 ":amount"])) "412.50 USD → 41250 cents")
    (is (= ":usd" (get doc ":statement/currency")) "statement carries currency")
    (is (some #(and (= ":meisai.row/amount" (nth % 2)) (= 3980 (nth % 3))) datoms)
        "generic :amount datom lands")
    (is (some #(and (= ":meisai.row/currency" (nth % 2)) (= ":usd" (nth % 3))) datoms)
        "generic :currency datom lands")
    (is (some #(= ":meisai.stmt/currency" (nth % 2)) datoms) "statement currency datom lands")
    (is (not-any? #(= ":meisai.row/amount-jpy" (nth % 2)) datoms) "no JPY attribute for a USD card")))

(deftest test-g2-still-fires-through-normalize
  ;; a PAN in a foreign-currency statement must still raise after normalize → ingest (G2 defining)
  (let [entry {:id :chase :name "Chase" :kind :issuer :country :us :region :namer :currency :usd
               :status :supported
               :shape {:rows ":r" :month ":m" :date ":d" :merchant ":who" :amount ":a"
                       :amount-scale :minor}}
        raw {":r" [{":d" "2026-05-02" ":who" "card 4111 1111 1111 1111" ":a" 100}] ":m" "2026-05"}
        doc (sources/normalize entry raw)]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (ingest/statement-datoms doc "bdead"))
        "a PAN survives normalize but is refused at ingest (G2)")))

#?(:clj (defn -main [& _] (run-tests 'meisai.methods.test-sources)))
