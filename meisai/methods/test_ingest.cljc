(ns meisai.methods.test-ingest
  "test_ingest.py — meisai statement-EDN → EAVT invariants. ADR-2606122400.
  1:1 Clojure port of methods/test_ingest.py (the pytest check() asserts → clojure.test).

  Guards the ingestion contract:
    - the REAL fetch-leg EDN shape parses and lands as :meisai.stmt/* + :meisai.row/* datoms;
    - determinism: same intake → byte-identical datoms (entity ids are content hashes);
    - G2 (the defining gate): a credential-shaped key or a PAN-shaped value anywhere in the
      intake RAISES — a card number or secret is unrepresentable in the Datom log."
  (:require [clojure.test :refer [deftest is run-tests]]
            [meisai.methods.ingest :as ingest]
            [meisai.methods.kotoba :as kotoba]))

;; the exact shape the Clojure fetch leg pprints (verified against a live run 2026-06-12)
(def intake-edn
  "{:source :sumitclub,
 :source/url
 \"https://www.sumitclub.jp/JPCRD/col/action/WA2020101Action/RWA2020105\",
 :statement/month \"2026-05\",
 :statement/total-jpy 46540,
 :statement/rows
 [{:date \"2026-05-02\", :merchant \"AMAZON.CO.JP\", :amount_jpy 3980}
  {:date \"2026-05-15\", :merchant \"JR東日本\", :amount_jpy 42560}]}
")

(deftest test-parse-and-datoms
  (let [doc (kotoba/parse-edn intake-edn)
        cid (ingest/intake-cid intake-edn)
        datoms (ingest/statement-datoms doc cid)
        stmt (filter #(= (nth % 1) "meisai-stmt:sumitclub:2026-05") datoms)
        rows (filter #(clojure.string/starts-with? (nth % 1) "meisai-row:") datoms)]
    (is (>= (count stmt) 4)
        "statement entity id derives from source+month")
    (is (every? #(= (nth % 0) ":db/add") datoms)
        "every datom is :db/add (append-only)")
    (is (some #(and (= (nth % 2) ":meisai.stmt/intake-cid") (= (nth % 3) cid)) stmt)
        "intake CID persisted for provenance (G5)")
    (is (some #(and (= (nth % 2) ":meisai.stmt/total-jpy") (= (nth % 3) 46540)) stmt)
        "total lands as int yen")
    (is (= (count rows) 10) "2 rows × 5 attrs")
    (is (every? #(= (nth % 3) "meisai-stmt:sumitclub:2026-05")
                (filter #(= (nth % 2) ":meisai.row/stmt") rows))
        "row links back to statement")
    (is (some #(and (= (nth % 2) ":meisai.row/merchant") (= (nth % 3) "JR東日本")) rows)
        "merchant survives UTF-8 (JR東日本)")))

(deftest test-determinism
  (let [doc (kotoba/parse-edn intake-edn)
        cid (ingest/intake-cid intake-edn)]
    (is (= (ingest/statement-datoms doc cid) (ingest/statement-datoms doc cid))
        "same intake → identical datoms")))

(deftest test-g2-credential-unrepresentable
  ;; credential-shaped key raises (G2)
  (let [doc (kotoba/parse-edn intake-edn)
        poisoned (assoc doc ":password" "hunter2")]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (ingest/statement-datoms poisoned "bdead"))
        "credential-shaped key raises (G2)"))

  ;; PAN-shaped value raises (G2)
  (let [pan (assoc (kotoba/parse-edn intake-edn)
                   ":statement/rows"
                   [{":date" "2026-05-02"
                     ":merchant" "card 4111 1111 1111 1111 memo"
                     ":amount_jpy" 1}])]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                          (ingest/statement-datoms pan "bdead"))
        "PAN-shaped value raises (G2)"))

  ;; short digit runs (order numbers) pass
  (let [clean-long (assoc (kotoba/parse-edn intake-edn)
                          ":statement/rows"
                          [{":date" "2026-05-02"
                            ":merchant" "ORDER 123-4567890-12"
                            ":amount_jpy" 1}])]
    (is (= (ingest/statement-datoms clean-long "bok")
           (ingest/statement-datoms clean-long "bok"))
        "short digit runs (order numbers) pass")))

#?(:clj (defn -main [& _] (run-tests 'meisai.methods.test-ingest)))
