(ns organizer.methods.test-agent
  "organizer — auto-organize file commons tests. 1:1 port of py/test_agent.py. Verifies the
  structural invariants of ADR-2606072400: G4 content-addressed dedup, G3 vault-isolation (cross-
  vault read refused), G2 no-mining (classification has no profile/ad field; owner-facing), G6
  no-server-key (only a member signature finalizes), and auto-organize (rule maps category →
  collection)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [organizer.methods.agent :as agent]))

(def VA "did:web:organizer.etzhayyim.com:vault:alice")
(def VB "did:web:organizer.etzhayyim.com:vault:bob")

(defn- ingest* [vault blake3 & {:keys [fn ct existing] :or {fn "doc.pdf" ct "application/pdf"}}]
  (agent/ingest-item vault blake3 "com.etzhayyim.encrypted:blob1" fn ct 1024 "did:plc:alice" (or existing [])))

;; ── dedup ──
(deftest test-content-addressed-id
  (is (= "cid.abcdef0123456789" (agent/content-item-id "abcdef0123456789ff"))))

(deftest test-new-item-staged
  (let [out (ingest* VA (apply str (repeat 40 "a")))]
    (is (= "staged" (get out "state")))
    (is (= false (get out "deduped")))))

(deftest test-identical-content-dedups
  (let [existing [{"vaultDid" VA "blake3" (apply str (repeat 40 "a")) "itemId" "cid.aaaaaaaaaaaaaaaa"}]
        out (ingest* VA (apply str (repeat 40 "a")) :existing existing)]
    (is (get out "deduped"))
    (is (= "cid.aaaaaaaaaaaaaaaa" (get-in out ["item" "itemId"])))))

(deftest test-same-content-different-vault-not-deduped
  (let [existing [{"vaultDid" VB "blake3" (apply str (repeat 40 "a")) "itemId" "x"}]
        out (ingest* VA (apply str (repeat 40 "a")) :existing existing)]   ; different vault → own copy (G3)
    (is (= false (get out "deduped")))))

;; ── upload ──
(deftest test-member-finalizes
  (let [staged (ingest* VA (apply str (repeat 40 "b")))
        out (agent/authorize-upload staged {"origin" "member" "ref" "sig-1"})]
    (is (= "stored" (get out "state")))
    (is (= "sig-1" (get-in out ["item" "postedSig"])))))

(deftest test-server-signature-refused
  (let [staged (ingest* VA (apply str (repeat 40 "b")))
        out (agent/authorize-upload staged {"origin" "server" "ref" "x"})]
    (is (get out "refused"))
    (is (str/includes? (get out "reason") "G6"))))

;; ── classify ──
(deftest test-pdf-is-document
  (let [c (agent/classify {"itemId" "i" "vaultDid" VA "filename" "a.pdf" "contentType" "application/pdf"})]
    (is (= "document" (get c "category")))
    (is (= "rule" (get c "source")))))

(deftest test-extension-fallback
  (let [c (agent/classify {"itemId" "i" "vaultDid" VA "filename" "pic.png" "contentType" "application/octet-stream"})]
    (is (= "image" (get c "category")))))

(deftest test-receipt-label
  (let [c (agent/classify {"itemId" "i" "vaultDid" VA "filename" "receipt-202605.pdf" "contentType" "application/pdf"})]
    (is (some #{"receipt"} (get c "labels")))))

(deftest test-no-profile-or-ad-field
  (let [c (agent/classify {"itemId" "i" "vaultDid" VA "filename" "a.pdf" "contentType" "application/pdf"})]
    (is (every? (fn [k] (let [kl (str/lower-case k)]
                          (and (not (str/includes? kl "profile"))
                               (not (str/includes? (str/replace kl "addr" "") "ad")))))
                (keys c)))
    (is (= VA (get c "vaultDid")))))   ; stays in owner's vault (G2/G3)

;; ── organize ──
(deftest test-rule-assigns-collection
  (let [cls {"itemId" "i" "vaultDid" VA "category" "image" "labels" ["image"]}
        rules [{"id" "r1" "condition" {"category" "image"} "collection" "Photos" "priority" 5}]
        out (agent/apply-rules cls rules)]
    (is (= "Photos" (get out "collection")))))

(deftest test-no-rule-no-force
  (let [cls {"itemId" "i" "vaultDid" VA "category" "archive" "labels" ["archive"]}]
    (is (nil? (agent/apply-rules cls [{"id" "r1" "condition" {"category" "image"} "collection" "Photos"}])))))

;; ── vault isolation ──
(deftest test-owner-reads
  (is (= "ok" (get (agent/read-item {"vaultDid" VA} VA) "state"))))

(deftest test-cross-vault-refused
  (let [out (agent/read-item {"vaultDid" VA} VB)]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G3"))))

;; ── collection membership ──
(defn- coll* ([] (coll* VA)) ([vault] {"collectionId" "c1" "vaultDid" vault "name" "Docs" "members" []}))
(defn- item* ([] (item* VA "cid.x")) ([vault iid] {"itemId" iid "vaultDid" vault}))

(deftest test-add-same-vault
  (let [out (agent/add-to-collection (coll*) (item*))]
    (is (= "ok" (get out "state")))
    (is (some #{"cid.x"} (get-in out ["collection" "members"])))))

(deftest test-add-cross-vault-refused
  (let [out (agent/add-to-collection (coll* VA) (item* VB "cid.x"))]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G3"))))

(deftest test-add-idempotent
  (let [c (get (agent/add-to-collection (coll*) (item*)) "collection")
        c2 (get (agent/add-to-collection c (item*)) "collection")]
    (is (= 1 (count (filter #{"cid.x"} (get c2 "members")))))))

(deftest test-remove-is-noop-for-nonmember
  (is (= [] (get-in (agent/remove-from-collection (coll*) "cid.absent") ["collection" "members"]))))

(deftest test-remove-member
  (let [c (get (agent/add-to-collection (coll*) (item*)) "collection")
        out (agent/remove-from-collection c "cid.x")]
    (is (= [] (get-in out ["collection" "members"])))))

;; ── auto-organize ──
(deftest test-batch-assigns-by-vault-rule
  (let [items [{"itemId" "cid.1" "vaultDid" VA "filename" "a.pdf" "contentType" "application/pdf"}
               {"itemId" "cid.2" "vaultDid" VA "filename" "p.png" "contentType" "image/png"}]
        collections [{"collectionId" "Docs" "vaultDid" VA "autoRules" [{"id" "r1" "condition" {"category" "document"}}]}
                     {"collectionId" "Photos" "vaultDid" VA "autoRules" [{"id" "r2" "condition" {"category" "image"}}]}]
        out (agent/auto-organize items collections)
        got (into {} (map (fn [a] [(get a "itemId") (get a "collection")]) out))]
    (is (= {"cid.1" "Docs" "cid.2" "Photos"} got))))

(deftest test-does-not-cross-vault
  (let [items [{"itemId" "cid.1" "vaultDid" VA "filename" "a.pdf" "contentType" "application/pdf"}]
        collections [{"collectionId" "Docs" "vaultDid" VB "autoRules" [{"id" "r1" "condition" {"category" "document"}}]}]]
    (is (= [] (agent/auto-organize items collections)))))   ; VB collection not used for VA item (G3)
