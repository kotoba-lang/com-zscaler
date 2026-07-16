(ns kanae.methods.test-methods
  "babashka tests for kanae.methods.assemble-flows and kanae.methods.project-yoro.
  Run: bb --classpath 20-actors 20-actors/kanae/methods/test_methods.clj

  Fixture strategy: hand-built minimal ledger whose structure matches the real seed ledger
  (captured from python3 on gov-fiscal-seed.jp.json), plus a representative subset of expected
  outputs cross-checked against python3 on identical inputs.

  Python oracle cmd used for verification:
    cd /tmp/clj-supply4 && python3 -c '
      import sys, json; sys.path.insert(0,\"20-actors\")
      from danjo.methods.budget_ledger import build_ledger, load_seed
      from kanae.methods.assemble_flows import assemble
      from kanae.methods.project_yoro import project
      SEED=\"20-actors/danjo/data/gov-fiscal-seed.jp.json\"
      ledger=build_ledger(load_seed(SEED))
      edges=assemble(ledger)
      print(len(edges), \"edges\")
      print(json.dumps(edges, ensure_ascii=False))
      datoms=project(edges)
      print(len(datoms), \"datoms\")
      print(json.dumps(datoms, ensure_ascii=False))
    '")

(require '[clojure.test :refer [deftest is testing run-tests]])
(require '[clojure.string :as str])
(require '[kanae.methods.assemble-flows :as af])
(require '[kanae.methods.project-yoro   :as py])

;; ---------------------------------------------------------------------------
;; Fixture ledger — hand-built but byte-identical to the real seed's normalized shape.
;; CIDs are the exact sha256 hashes produced by budget_ledger.record_cid on the real records.
;; ---------------------------------------------------------------------------

;;   FY2023: 1 appropriation + 1 outlay
;;   FY2024: 1 appropriation + 2 outlays
;; → 5 edges total: 2 appropriation + 3 outlay (matches python test_pipeline)

(def APPROP-CID-2023
  "gov.dataset.budgetRecord:jp_yosan:2023:br-jp-2023-mext-appropriation#7bc6f93cc39d34e73106ebc0")
(def UNIV-CID-2023
  "gov.dataset.budgetRecord:jp_yosan:2023:br-jp-2023-mext-univ-grants#a94ff64e326722063231c26f")
(def APPROP-CID-2024
  "gov.dataset.budgetRecord:jp_yosan:2024:br-jp-2024-mext-appropriation#53caa39f889350226d1eb77f")
(def UNIV-CID-2024
  "gov.dataset.budgetRecord:jp_yosan:2024:br-jp-2024-mext-univ-grants#4c6def29a2a52cf14a907ac2")
(def SCITECH-CID-2024
  "gov.dataset.budgetRecord:jp_yosan:2024:br-jp-2024-mext-sci-tech#39d03fc3de796f6006410310")

(def MEXT-NAME
  "文部科学省 (Ministry of Education, Culture, Sports, Science and Technology)")
(def UNIV-NAME
  "国立大学法人 (National University Corporations — recipient class, aggregate)")
(def SCITECH-NAME
  "科学技術振興 諸事業 (Science & technology programs — recipient class, aggregate)")

(def FIXTURE-LEDGER
  {"lines" []  ;; assemble only reads "groups"
   "groups"
   {"JP-MEXT-EDUSCI|2023"
    {"programCode"    "JP-MEXT-EDUSCI"
     "programName"    "文教及び科学振興費 (Education & Science Promotion)"
     "fiscalYear"     2023
     "jurisdiction"   "jpn"
     "appropriations"
     [{"cid"              APPROP-CID-2023
       "recordKind"       "appropriation"
       "jurisdiction"     "jpn"
       "programCode"      "JP-MEXT-EDUSCI"
       "programName"      "文教及び科学振興費 (Education & Science Promotion)"
       "amountLocal"      5294000000000
       "currencyIso4217"  "JPY"
       "fiscalYear"       2023
       "recipientName"    MEXT-NAME
       "recipientLocalId" "jp-corp:7000012090001"
       "recipientLei"     ""
       "awardDateUtc"     "2023-03-28T00:00:00.000Z"
       "sourceUrl"        "https://www.mof.go.jp/policy/budget/budger_workflow/budget/fy2023/"
       "stateAlignedFlag" false}]
     "outlays"
     [{"cid"              UNIV-CID-2023
       "recordKind"       "outlay"
       "jurisdiction"     "jpn"
       "programCode"      "JP-MEXT-EDUSCI"
       "programName"      "国立大学法人運営費交付金 (National University Corporation operating grants)"
       "amountLocal"      1078400000000
       "currencyIso4217"  "JPY"
       "fiscalYear"       2023
       "recipientName"    UNIV-NAME
       "recipientLocalId" "jp-class:national-university-corporations"
       "recipientLei"     ""
       "awardDateUtc"     "2023-04-01T00:00:00.000Z"
       "sourceUrl"        "https://www.mext.go.jp/a_menu/koutou/houjin/"
       "stateAlignedFlag" false}]}

    "JP-MEXT-EDUSCI|2024"
    {"programCode"    "JP-MEXT-EDUSCI"
     "programName"    "文教及び科学振興費 (Education & Science Promotion)"
     "fiscalYear"     2024
     "jurisdiction"   "jpn"
     "appropriations"
     [{"cid"              APPROP-CID-2024
       "recordKind"       "appropriation"
       "jurisdiction"     "jpn"
       "programCode"      "JP-MEXT-EDUSCI"
       "programName"      "文教及び科学振興費 (Education & Science Promotion)"
       "amountLocal"      5464300000000
       "currencyIso4217"  "JPY"
       "fiscalYear"       2024
       "recipientName"    MEXT-NAME
       "recipientLocalId" "jp-corp:7000012090001"
       "recipientLei"     ""
       "awardDateUtc"     "2024-03-28T00:00:00.000Z"
       "sourceUrl"        "https://www.mof.go.jp/policy/budget/budger_workflow/budget/fy2024/"
       "stateAlignedFlag" false}]
     "outlays"
     [{"cid"              UNIV-CID-2024
       "recordKind"       "outlay"
       "jurisdiction"     "jpn"
       "programCode"      "JP-MEXT-EDUSCI"
       "programName"      "国立大学法人運営費交付金 (National University Corporation operating grants)"
       "amountLocal"      1078400000000
       "currencyIso4217"  "JPY"
       "fiscalYear"       2024
       "recipientName"    UNIV-NAME
       "recipientLocalId" "jp-class:national-university-corporations"
       "recipientLei"     ""
       "awardDateUtc"     "2024-04-01T00:00:00.000Z"
       "sourceUrl"        "https://www.mext.go.jp/a_menu/koutou/houjin/"
       "stateAlignedFlag" false}
      {"cid"              SCITECH-CID-2024
       "recordKind"       "outlay"
       "jurisdiction"     "jpn"
       "programCode"      "JP-MEXT-EDUSCI"
       "programName"      "科学技術振興費 (Science & Technology promotion — incl. JST/JSPS program lines)"
       "amountLocal"      1398900000000
       "currencyIso4217"  "JPY"
       "fiscalYear"       2024
       "recipientName"    SCITECH-NAME
       "recipientLocalId" "jp-class:science-technology-programs"
       "recipientLei"     ""
       "awardDateUtc"     "2024-04-01T00:00:00.000Z"
       "sourceUrl"        "https://www.mext.go.jp/a_menu/kagaku/"
       "stateAlignedFlag" false}]}}})

;; ---------------------------------------------------------------------------
;; Test: assemble-flows
;; ---------------------------------------------------------------------------

(deftest test-assemble-edge-count
  (testing "assemble produces 5 edges from the 2-group fixture (2 approp + 3 outlay)"
    (let [edges (af/assemble FIXTURE-LEDGER)]
      (is (= 5 (count edges))))))

(deftest test-assemble-flow-classes
  (testing "edges contain exactly 2 appropriations and 3 outlays"
    (let [edges (af/assemble FIXTURE-LEDGER)
          by-class (frequencies (map #(get % "flowClass") edges))]
      (is (= 2 (get by-class "appropriation")))
      (is (= 3 (get by-class "outlay"))))))

(deftest test-assemble-fy2023-appropriation
  (testing "FY2023 appropriation edge matches python oracle"
    (let [edges  (af/assemble FIXTURE-LEDGER)
          ;; sorted by key → FY2023 comes first
          e      (first edges)]
      (is (= "appropriation" (get e "flowClass")))
      (is (= "FY2023" (get e "period")))
      (is (= "5294000000000" (get e "amount")))
      (is (= "JPY" (get e "currency")))
      (is (= "jpn" (get e "jurisdiction")))
      (is (= "did:web:kanae.etzhayyim.com:cell:flow_assembler" (get e "sourceCellDid")))
      (is (= "did:web:kanae.etzhayyim.com" (get e "attestingDid")))
      (is (= af/METHOD-NOTE (get e "methodNoteCid")))
      (is (= af/DEFAULT-CREATED-AT (get e "createdAt")))
      ;; fromEndpoint is the National Treasury
      (is (str/includes? (get-in e ["fromEndpoint" "label"]) "一般会計"))
      ;; toEndpoint is MEXT
      (is (= MEXT-NAME (get-in e ["toEndpoint" "label"])))
      ;; sourceRecordCids: approp CID + first outlay CID (G5 >=2)
      (is (= [APPROP-CID-2023 UNIV-CID-2023] (get e "sourceRecordCids")))
      ;; projection annotations
      (is (= "JP-MEXT-EDUSCI" (get e "_programCode")))
      (is (= "2023-03-28T00:00:00.000Z" (get e "observedAt"))))))

(deftest test-assemble-fy2023-outlay
  (testing "FY2023 outlay edge matches python oracle"
    (let [edges (af/assemble FIXTURE-LEDGER)
          e     (second edges)]
      (is (= "outlay" (get e "flowClass")))
      (is (= "FY2023" (get e "period")))
      (is (= "1078400000000" (get e "amount")))
      (is (= MEXT-NAME (get-in e ["fromEndpoint" "label"])))
      (is (= UNIV-NAME (get-in e ["toEndpoint" "label"])))
      ;; outlay CID first, then approp CID (G5 chained)
      (is (= [UNIV-CID-2023 APPROP-CID-2023] (get e "sourceRecordCids")))
      (is (= "2023-04-01T00:00:00.000Z" (get e "observedAt"))))))

(deftest test-assemble-g5-all-edges-have-2plus-cids
  (testing "G5: every assembled edge has >=2 sourceRecordCids"
    (let [edges (af/assemble FIXTURE-LEDGER)]
      (doseq [e edges]
        (is (>= (count (get e "sourceRecordCids")) 2))))))

(deftest test-assemble-g10-no-named-party
  (testing "G10: no endpoint has publiclyNamedBasis (no named private party)"
    (let [edges (af/assemble FIXTURE-LEDGER)]
      (doseq [e edges]
        (is (not (contains? (get e "fromEndpoint") "publiclyNamedBasis")))
        (is (not (contains? (get e "toEndpoint") "publiclyNamedBasis")))))))

;; ---------------------------------------------------------------------------
;; Test: validate-edge gate cases (RAISE expected)
;; ---------------------------------------------------------------------------

(deftest test-validate-missing-field
  (testing "validate-edge raises on a missing required field"
    (let [good-edge (first (af/assemble FIXTURE-LEDGER))
          bad       (dissoc good-edge "attestingDid")]
      (is (thrown? Exception (af/validate-edge bad))))))

(deftest test-validate-bad-flow-class
  (testing "validate-edge raises on a flowClass not in the lexicon enum"
    (let [good-edge (first (af/assemble FIXTURE-LEDGER))
          bad       (assoc good-edge "flowClass" "verdict")]
      (is (thrown? Exception (af/validate-edge bad))))))

(deftest test-validate-single-cid
  (testing "G5: validate-edge raises when sourceRecordCids has fewer than 2 entries"
    (let [good-edge (first (af/assemble FIXTURE-LEDGER))
          bad       (assoc good-edge "sourceRecordCids" ["only-one"])]
      (is (thrown? Exception (af/validate-edge bad))))))

;; ---------------------------------------------------------------------------
;; Test: assert-non-adjudicating gate (G4 RAISE)
;; ---------------------------------------------------------------------------

(deftest test-g4-verdict-token-raises
  (testing "G4: assert-non-adjudicating raises when a verdict token appears in the edge"
    (let [good-edge    (first (af/assemble FIXTURE-LEDGER))
          poisoned-from (assoc-in good-edge ["fromEndpoint" "label"] "不正 appropriation")]
      (is (thrown? Exception (af/assert-non-adjudicating poisoned-from))))
    (let [good-edge  (first (af/assemble FIXTURE-LEDGER))
          poisoned-fc (assoc good-edge "flowClass" "crime-transfer")]
      (is (thrown? Exception (af/assert-non-adjudicating poisoned-fc))))))

;; ---------------------------------------------------------------------------
;; Test: _slug-did and _stage-for helpers
;; ---------------------------------------------------------------------------

(deftest test-slug-did-national-treasury
  (testing "_slug_did maps 一般会計 to kokko"
    (is (= "did:web:etzhayyim.com:actor:kokko"
           (py/slug-did "日本国 一般会計 (Japan General Account / National Treasury)")))))

(deftest test-slug-did-mext
  (testing "_slug_did maps Ministry of Education to gov-jp-mext"
    (is (= "did:web:etzhayyim.com:actor:gov-jp-mext"
           (py/slug-did MEXT-NAME)))))

(deftest test-slug-did-univ
  (testing "_slug_did maps 国立大学法人 to gov-jp-mext-univ-grants"
    (is (= "did:web:etzhayyim.com:actor:gov-jp-mext-univ-grants"
           (py/slug-did UNIV-NAME)))))

(deftest test-slug-did-scitech
  (testing "_slug_did maps 科学技術振興 to gov-jp-mext-sci-tech"
    (is (= "did:web:etzhayyim.com:actor:gov-jp-mext-sci-tech"
           (py/slug-did SCITECH-NAME)))))

(deftest test-stage-for-treasury
  (testing "_stage_for: National Treasury → L7"
    (is (= "L7" (py/stage-for "日本国 一般会計 (Japan General Account / National Treasury)")))))

(deftest test-stage-for-mext
  (testing "_stage_for: MEXT → L5"
    (is (= "L5" (py/stage-for MEXT-NAME)))))

(deftest test-stage-for-univ
  (testing "_stage_for: 国立大学法人 → L1"
    (is (= "L1" (py/stage-for UNIV-NAME)))))

;; ---------------------------------------------------------------------------
;; Test: project
;; ---------------------------------------------------------------------------

(deftest test-project-datom-count
  (testing "project produces the expected number of datoms from 5 edges"
    ;; Python oracle: 5 edges → 49 datoms
    ;; 5 edges × 9 fiscal datoms = 45; 1 profile × 4 datoms = 4; total 49
    (let [edges  (af/assemble FIXTURE-LEDGER)
          datoms (py/project edges)]
      (is (= 49 (count datoms))))))

(deftest test-project-mext-incoming-outgoing
  (testing "project: MEXT has 2 incoming edges and 3 outgoing edges (as in python)"
    (let [edges  (af/assemble FIXTURE-LEDGER)
          datoms (py/project edges)
          MEXT-DID "did:web:etzhayyim.com:actor:gov-jp-mext"
          fiscal (filter #(str/starts-with? (get % "a") ":yoro.fiscal/") datoms)
          by-e   (reduce (fn [m d]
                           (update m (get d "e") assoc
                                   (last (str/split (get d "a") #"/"))
                                   (get d "v_edn")))
                         {} fiscal)
          flows  (vals by-e)
          incoming (filter #(= (str "\"" MEXT-DID "\"") (get % "to")) flows)
          outgoing (filter #(= (str "\"" MEXT-DID "\"") (get % "from")) flows)]
      (is (= 2 (count incoming)))
      (is (= 3 (count outgoing))))))

(deftest test-project-mext-has-profile
  (testing "project: MEXT mirror-actor has a yoro.profile datom"
    (let [edges  (af/assemble FIXTURE-LEDGER)
          datoms (py/project edges)
          MEXT-DID "did:web:etzhayyim.com:actor:gov-jp-mext"
          profiles (filter #(str/starts-with? (get % "a") ":yoro.profile/") datoms)
          did-vals (map #(get % "v_edn") (filter #(= ":yoro.profile/did" (get % "a")) profiles))]
      (is (some #(= (str "\"" MEXT-DID "\"") %) did-vals)))))

(deftest test-project-fy2023-approp-datom
  (testing "project: FY2023 appropriation entity has correct from/to/amount datoms (python oracle)"
    (let [edges  (af/assemble FIXTURE-LEDGER)
          datoms (py/project edges)
          expected-e "fiscal:did:web:etzhayyim.com:actor:kokko:did:web:etzhayyim.com:actor:gov-jp-mext:2023:appropriation"
          relevant (filter #(= expected-e (get % "e")) datoms)
          by-a     (into {} (map (fn [d] [(get d "a") (get d "v_edn")]) relevant))]
      (is (= "\"did:web:etzhayyim.com:actor:kokko\""     (get by-a ":yoro.fiscal/from")))
      (is (= "\"did:web:etzhayyim.com:actor:gov-jp-mext\"" (get by-a ":yoro.fiscal/to")))
      (is (= "\"L7\""                                     (get by-a ":yoro.fiscal/stage")))
      (is (= "2023"                                       (get by-a ":yoro.fiscal/fiscalYear")))
      (is (= "5294000000000"                              (get by-a ":yoro.fiscal/amountJpy")))
      (is (= "\"JP-MEXT-EDUSCI\""                        (get by-a ":yoro.fiscal/programCode")))
      (is (= "\"2023-03-28T00:00:00.000Z\""              (get by-a ":yoro.fiscal/observedAt"))))))

(deftest test-project-all-datoms-have-added-true
  (testing "every datom has added:true"
    (let [edges  (af/assemble FIXTURE-LEDGER)
          datoms (py/project edges)]
      (doseq [d datoms]
        (is (= true (get d "added")))))))

;; ---------------------------------------------------------------------------
;; Test: merge-into-seed (uses a temp file)
;; ---------------------------------------------------------------------------

(deftest test-merge-into-seed-idempotent
  (testing "merge-into-seed writes datoms and re-run is idempotent (no duplication)"
    (let [tmp  (java.io.File/createTempFile "kanae-test-seed" ".json")
          path (.getAbsolutePath tmp)]
      (try
        ;; start with an empty JSON array
        (spit path "[]")
        (let [edges  (af/assemble FIXTURE-LEDGER)
              datoms (py/project edges)
              stats1 (py/merge-into-seed path datoms)
              stats2 (py/merge-into-seed path datoms)]
          ;; first run: 0 removed, N added
          (is (= 0 (get stats1 "removed")))
          (is (= (count datoms) (get stats1 "added")))
          (is (= (count datoms) (get stats1 "total")))
          ;; second run: idempotent — same total (old entities replaced by same set)
          (is (= (count datoms) (get stats2 "total"))))
        (finally
          (.delete tmp))))))

;; ---------------------------------------------------------------------------
;; Runner
;; ---------------------------------------------------------------------------

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (clojure.test/run-tests 'kanae.methods.test-methods)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
