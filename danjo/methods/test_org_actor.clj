;; test_org_actor.clj — per-organization keyless mirror-actors (entity-as-actor).
;; Run: bb test_org_actor.clj   (or: clojure -M test_org_actor.clj)   from methods/.
(ns root.danjo.methods.test-org-actor
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(load-file "org_actor.clj")
(alias 'o  'root.danjo.methods.org-actor)
(alias 't  'root.danjo.methods.taxes)
(alias 'rl 'root.danjo.methods.revenue-ledger)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))

(let [orgs  (o/load-orgs "../data/jp-fiscal-orgs.edn")
      taxes (t/combine (t/load-taxes "../data/jp-national-taxes.edn")
                       (t/load-local-taxes "../data/jp-local-taxes.edn"))]

  ;; ── registry (国 + 地方) ──
  (check "8 fiscal orgs (6 national + 2 local aggregate)" (= 8 (count (:orgs orgs))))
  (check "includes the 2 local aggregate orgs"
         (every? #(some (fn [o] (= % (:id o))) (:orgs orgs)) [:prefecture-agg :municipality-agg]))
  (check "every org has a did:web actor DID"
         (every? #(str/starts-with? (:did %) "did:web:etzhayyim.com:actor:jp-") (:orgs orgs)))

  ;; ── org datoms: keyless mirror-actors ──
  (let [ds (o/org-datoms orgs taxes)]
    (check "org-datoms all :db/add" (every? #(= :db/add (first %)) ds))
    (check "every org is keyless (no-server-key)"
           (= (count (:orgs orgs)) (count (filter #(and (= :gov.org/keyless (nth % 2)) (true? (nth % 3))) ds))))
    (check "NTA has :gov.org/collects edges"
           (some #(and (= :gov.org/collects (nth % 2)) (str/includes? (nth % 3) "income-withholding")) ds))
    (check "復興庁 has :gov.org/administers special/reconstruction"
           (some #(and (= :gov.org/administers (nth % 2)) (= "special/reconstruction" (nth % 3))) ds)))

  ;; ── per-org views ──
  (let [nta  (o/org-view :nta orgs taxes)
        cust (o/org-view :customs orgs taxes)
        fk   (o/org-view :fukko orgs taxes)
        mof  (o/org-view :mof-budget orgs taxes)]
    (check "国税庁 collects the bulk of taxes (≥15)" (>= (:count (:collects nta)) 15))
    (check "国税庁 per-yen-traceable list includes 復興特別所得税"
           (some #{:reconstruction-surtax} (:per-yen-traceable (:collects nta))))
    (check "税関 collects exactly 関税 + とん税" (= 2 (:count (:collects cust))))
    (check "復興庁 administers special/reconstruction"
           (= [:special/reconstruction] (:accounts (:administers fk))))
    (check "復興庁 spends 復興特別所得税" (= [:reconstruction-surtax] (:spends-taxes (:administers fk))))
    (check "財務省主計局 administers 一般会計, spends no per-yen-traceable tax (honest)"
           (and (= [:general] (:accounts (:administers mof))) (empty? (:spends-taxes (:administers mof))))))

  ;; ── local aggregate orgs collect local taxes (国 + 地方) ──
  (let [muni (o/org-view :municipality-agg orgs taxes)
        pref (o/org-view :prefecture-agg orgs taxes)]
    (check "市町村(集約) collects local taxes"   (pos? (:count (:collects muni))))
    (check "市町村(集約) collects 固定資産税"      (some #{:fixed-asset} (map :id (:taxes (:collects muni)))))
    (check "都道府県(集約) collects 地方消費税"    (some #{:local-consumption} (map :id (:taxes (:collects pref))))))

  ;; ── resolvable profile artifacts (entity-as-actor, keyless) ──
  (load-file "ingest.clj")
  (let [in (find-ns 'root.danjo.methods.ingest)
        prof (o/org-profile (->> (:orgs orgs) (filter #(= :nta (:id %))) first) orgs taxes)]
    (check "org-profile is keyless"            (true? (:keyless prof)))
    (check "org-profile has empty verificationMethod (no-server-key)" (= [] (:verificationMethod prof)))
    (check "org-profile type is gov-fiscal-mirror" (= "gov-fiscal-mirror" (:type prof)))
    (check "org-profile did matches the org DID"
           (= "did:web:etzhayyim.com:actor:jp-nta" (:did prof)))
    (check "->json round-trips through parse-json"
           (= (get ((ns-resolve in 'parse-json) (o/->json prof)) "did") (:did prof))))
  (let [dir (str (System/getProperty "java.io.tmpdir") "/danjo-actors-" (rand-int 1000000))
        paths (o/generate-profiles! orgs taxes dir)]
    (check "generate-profiles! writes 8 profiles + index (9)" (= 9 (count paths)))
    (check "every profile file exists"  (every? #(.exists (io/file %)) paths))
    (doseq [p paths] (.delete (io/file p)))
    (.delete (io/file dir)))

  ;; ── org + tax datoms persist + bridge through the existing pipeline ──
  (let [log (str (System/getProperty "java.io.tmpdir") "/danjo-org-test-" (rand-int 1000000) ".kotoba.edn")
        _   (when (.exists (io/file log)) (.delete (io/file log)))
        seed (rl/load-seed "../data/gov-revenue-seed.jp.edn")
        extra (concat (t/tax-datoms taxes) (o/org-datoms orgs taxes))
        r   (rl/run-cycle! {:seed seed :log-path log :as-of 1 :extra-datoms (vec extra)})]
    (check "tax + org datoms persisted with the cycle" (pos? (:datom-count r)))
    (check "chain verifies with org-actor datoms" (:ok (rl/verify-chain log)))
    (.delete (io/file log))))

(println (format "── org-actor: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
