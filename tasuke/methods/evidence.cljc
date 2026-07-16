(ns tasuke.methods.evidence
  "evidence.cljc — 助 (tasuke) evidence preservation. ADR-2606060900.
  1:1 Clojure port of `methods/evidence.py`.

  A cybercrime victim's evidence (screenshots, headers, chat logs, tx records) is exactly the
  要配慮 material the charter says must live ENCRYPTED (G6, ADR-2605181100). This module never
  stores the plaintext. For each item it records kind / envelope-ref (the encrypted CID) /
  sha256 (chain-of-custody integrity hash) / captured-at.

  `preserve` REFUSES (throws) any attempt to attach a plaintext PII blob — the only representable
  form is the encrypted-envelope ref + the hash. That is the G6 invariant in code.

  House style: Python ':…' keyword strings stay strings; pure fns; sha256 at the #?(:clj) edge."
  (:require [clojure.string :as str]))

(def EVIDENCE-KINDS
  ["url" "email-header" "screenshot" "chat-log" "transaction-record" "wallet-address"
   "phone-number" "account-id" "file-hash" "audio" "other"])

;; field names that would smuggle plaintext PII into the clear record — refused (G6).
(def ^:private PLAINTEXT-PII-FIELDS
  [":evidence/plaintext" ":evidence/raw" ":evidence/pii" "plaintext" "raw-bytes"])

(defn sha256-hex
  "Hex sha256 of a byte array (or the UTF-8 bytes of a string)."
  [data]
  #?(:clj (let [bytes (if (string? data) (.getBytes ^String data "UTF-8") data)
                md (java.security.MessageDigest/getInstance "SHA-256")
                digest (.digest md bytes)]
            (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))
     :cljs (throw (ex-info "sha256-hex requires the :clj edge" {}))))

(defn- kw*
  "_kw — strip leading ':' , take the last '/'-segment, lower-case. Empty for nil."
  [v]
  (-> (str (or v ""))
      (str/replace #"^:+" "")
      (str/split #"/")
      (last)
      (str/lower-case)))

(defn- to-int
  [v]
  (cond
    (nil? v) 0
    (false? v) 0
    (number? v) (long v)
    (string? v) (if (str/blank? v) 0 (try (Long/parseLong (str/trim v)) (catch Exception _ 0)))
    :else 0))

(defn preserve
  "Preserve one evidence item as an encrypted-by-reference, hash-anchored record.

  `item` carries :evidence/kind, :evidence/envelope-ref, and EITHER an :evidence/sha256 (already
  computed) or :evidence/bytes (raw bytes to hash here and then discard). Throws on a G6 violation
  or an unknown kind."
  [item]
  (doseq [f PLAINTEXT-PII-FIELDS]
    (when (contains? item f)
      (throw (ex-info (str "G6: plaintext PII field '" f "' is unrepresentable — evidence lives "
                           "encrypted (com.etzhayyim.encrypted.*); store an envelope-ref + a hash only") {}))))
  (let [kind (kw* (get item ":evidence/kind" ""))]
    (when-not (some #{kind} EVIDENCE-KINDS)
      (throw (ex-info (str "unknown evidence kind '" kind "' (allowed: " EVIDENCE-KINDS ")") {})))
    (let [ref (str/trim (str (get item ":evidence/envelope-ref" "")))]
      (when (str/blank? ref)
        (throw (ex-info "G6: an evidence item needs an encrypted envelope-ref (the ciphertext CID)" {})))
      (let [digest0 (str/trim (str (get item ":evidence/sha256" "")))
            digest (if-not (str/blank? digest0)
                     digest0
                     (let [raw (get item ":evidence/bytes")]
                       (when (nil? raw)
                         (throw (ex-info "evidence needs either :evidence/sha256 or :evidence/bytes to hash" {})))
                       (sha256-hex raw)))]    ;; hash then discard the bytes (never stored)
        {":evidence/id" (get item ":evidence/id" "?")
         ":evidence/case" (get item ":evidence/case" "?")
         ":evidence/kind" (str ":" kind)
         ":evidence/envelope-ref" ref
         ":evidence/sha256" digest
         ":evidence/captured-at" (to-int (get item ":evidence/captured-at" 0))}))))

(defn index
  "Preserve a batch → the 証拠目録 (evidence index) rows, in capture order (stable sort)."
  [items]
  (let [rows (mapv preserve items)]
    (vec (sort-by #(to-int (get % ":evidence/captured-at" 0)) rows))))

(defn custody-intact?
  "Verify an item's chain of custody: does the current ciphertext still match the recorded hash?"
  [record current-bytes]
  (= (sha256-hex current-bytes) (get record ":evidence/sha256" "")))
