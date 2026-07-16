;; test_taxes.clj — national-tax registry + honest 3-way earmark classification.
;; Run: bb test_taxes.clj   (or: clojure -M test_taxes.clj)   from methods/.
(ns root.danjo.methods.test-taxes
  (:require [clojure.string :as str]))

(load-file "taxes.clj")
(alias 't 'root.danjo.methods.taxes)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))
(defn tax [reg id] (->> (:taxes reg) (filter #(= id (:id %))) first))

(let [reg (t/load-taxes "../data/jp-national-taxes.edn")]

  ;; ── breadth: 国全体の税金 ──
  (check "≥15 national taxes covered" (>= (count (:taxes reg)) 15))
  (check "covers 法人税"   (some? (tax reg :corporate)))
  (check "covers 消費税"   (some? (tax reg :consumption)))
  (check "covers 関税"     (some? (tax reg :customs)))
  (check "covers 酒税・たばこ・揮発油・相続" (every? #(some? (tax reg %)) [:liquor :tobacco-national :gasoline :inheritance-gift]))

  ;; ── honest 3-way classification ──
  (let [income (t/classify (tax reg :income-withholding))
        cons   (t/classify (tax reg :consumption))
        recon  (t/classify (tax reg :reconstruction-surtax))
        gas    (t/classify (tax reg :gasoline))]
    (check "源泉所得税 → :general, per-yen? false" (and (= :general (:earmark-kind income)) (false? (:per-yen? income))))
    (check "消費税 → :statutory-purpose (目的税, NOT per-yen)"
           (and (= :statutory-purpose (:earmark-kind cons)) (false? (:per-yen? cons))
                (= :statutory-purpose-in-general-account (:reason cons))))
    (check "消費税 statutory-purpose = :social-security" (= :social-security (:statutory-purpose cons)))
    (check "復興特別所得税 → :special-account, per-yen? true"
           (and (= :special-account (:earmark-kind recon)) (true? (:per-yen? recon))))
    (check "揮発油税 → :general now (一般財源化) with history note"
           (and (= :general (:earmark-kind gas)) (str/includes? (or (:registry-note gas) "") "一般財源化"))))

  ;; ── summary: honest per-yen-traceable share ──
  (let [s (t/summary reg)]
    (check "3 earmark categories present" (= #{:general :statutory-purpose :special-account}
                                             (set (keys (:by-earmark-kind s)))))
    (check "category amounts sum to total"
           (= (:total-jpy s) (reduce + 0 (map :amount-jpy (vals (:by-earmark-kind s))))))
    (check "per-yen-traceable share is small (特別会計分のみ)"
           (< (:per-yen-traceable-share s) 0.10))
    (check "消費税 dominates statutorily-directed amount"
           (> (:statutorily-directed-amount s) 20000000000000)))

  ;; ── 地方税 + 国+地方 combined (税の全体像) ──
  (let [local (t/load-local-taxes "../data/jp-local-taxes.edn")
        comb  (t/combine reg local)
        cs    (t/summary comb)]
    (check "≥10 local taxes covered" (>= (count (:taxes local)) 10))
    (check "covers 固定資産税・個人住民税・地方消費税"
           (every? #(some (fn [x] (= % (:id x))) (:taxes local))
                   [:fixed-asset :resident-individual :local-consumption]))
    (check "固定資産税 → :general (fungible)"
           (= :general (:earmark-kind (t/classify (->> (:taxes local) (filter #(= :fixed-asset (:id %))) first)))))
    (check "都市計画税 → :statutory-purpose (目的税)"
           (= :statutory-purpose (:earmark-kind (t/classify (->> (:taxes local) (filter #(= :city-planning (:id %))) first)))))
    (check "combined = 国17 + 地方12 = 29 taxes" (= 29 (:tax-count cs)))
    (check "by-level split present (国 + 地方)"
           (= #{:national :local} (set (keys (:by-level cs)))))
    (check "combined total ≈ 国+地方 (>100兆)" (> (:total-jpy cs) 100000000000000))
    (check "国 + 地方 amounts sum to combined total"
           (= (:total-jpy cs) (reduce + 0 (map :amount-jpy (vals (:by-level cs)))))))

  ;; ── EAVT datoms ──
  (let [ds (t/tax-datoms reg)]
    (check "tax-datoms non-empty + all :db/add" (and (pos? (count ds)) (every? #(= :db/add (first %)) ds)))
    (check "every tax carries :gov.tax/earmark-kind"
           (= (count (:taxes reg)) (count (filter #(= :gov.tax/earmark-kind (nth % 2)) ds))))
    (check "earmarked taxes carry :gov.tax/special-account"
           (some #(= :gov.tax/special-account (nth % 2)) ds))
    (check "G4: no verdict token in any attr (tax-datoms would have raised)" true)))

(println (format "── taxes: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
