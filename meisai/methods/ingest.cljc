(ns meisai.methods.ingest
  "ingest.cljc — meisai 明細: member card-statement EDN → kotoba EAVT datoms.
  Clojure port of `methods/ingest.py` (ADR-2606122400). The JP (`:amount_jpy`) path is BYTE-PARITY
  with ingest.py; the worldwide multi-currency branch (`:amount`/`:currency`,
  `:statement/total`/`:statement/currency`) is a clj-native superset (ADR-2606122400 R1, fed by
  `methods/sources.cljc normalize`) — additive, so any JPY intake's datoms are unchanged.

  Reads the statement EDN the MEMBER-PRINCIPAL fetch leg wrote locally, normalizes each row into
  append-only EAVT datoms, and (via kotoba) persists them. meisai itself does NO network I/O and
  holds NO credential. Two gates are STRUCTURAL:

    - **G2 credential-unrepresentable**: a credential-shaped key (password/secret/otp/cvv/pin/
      token/credential) or a PAN-shaped value (13–19-digit run, spaces/dashes allowed) anywhere in
      the intake RAISES — a card number or secret cannot enter the Datom log.
    - **G5 provenance**: every statement tx carries the intake file's content CID; row entity ids
      are deterministic content hashes → re-ingest of the same intake is a no-op (dedup by CID).

  Deterministic (no wall clock, no randomness). Byte-identical row hashes + tx CIDs to ingest.py."
  (:require [clojure.string :as str]
            [meisai.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])))

(def ^:private forbidden-key-tokens ["password" "secret" "otp" "cvv" "credential" "token" "pin"])
;; 13–19 consecutive digits, optionally space/dash-grouped → a primary account number shape.
(def ^:private pan-re #"(?:\d[ -]?){13,19}")

(defn- sha256-hex [^String s]
  (let [b (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b))))

(defn intake-cid
  "Content address of the intake string's UTF-8 bytes (G5 provenance + dedup key)."
  [^String s]
  (str "b" (sha256-hex s)))

(defn- leaves
  "Every scalar leaf of the doc (keys AND values), mirroring Python _walk's yields."
  [node]
  (cond
    (map? node) (mapcat (fn [[k v]] (concat (leaves k) (leaves v))) node)
    (sequential? node) (mapcat leaves node)
    :else [node]))

(defn guard
  "G2 structural gate: refuse credential-shaped keys and PAN-shaped values anywhere (1:1 with
  guard). Throws ex-info on violation; returns nil otherwise."
  [doc]
  (doseq [leaf (leaves doc)]
    (let [s (str leaf) low (str/lower-case s)]
      (when (and (str/starts-with? low ":") (some #(str/includes? low %) forbidden-key-tokens))
        (throw (ex-info (str "G2: credential-shaped key " (pr-str s) " is unrepresentable in meisai")
                        {:gate :G2})))
      (when-let [m (re-find pan-re s)]
        (when (>= (count (str/replace m #"\D" "")) 13)
          (throw (ex-info "G2: PAN-shaped value is unrepresentable in meisai" {:gate :G2})))))))

(defn- kw->name [v] (str/replace (str v) #"^:+" ""))   ; ':sumitclub' → 'sumitclub'

(defn statement-datoms
  "Statement intake map (parse-edn shape — keys like ':statement/month') → append-only EAVT
  datoms (1:1 with statement_datoms). E(statement) deterministic from source+month; E(row) is a
  content hash so re-ingest is a no-op."
  [doc cid]
  (guard doc)
  (let [source (kw->name (get doc ":source" "unknown"))
        month (str (get doc ":statement/month" "?"))
        rows (or (get doc ":statement/rows") [])
        stmt-e (str "meisai-stmt:" source ":" month)
        base [(kotoba/add stmt-e ":meisai.stmt/source" (str ":" source))
              (kotoba/add stmt-e ":meisai.stmt/month" month)
              (kotoba/add stmt-e ":meisai.stmt/row-count" (count rows))
              (kotoba/add stmt-e ":meisai.stmt/intake-cid" cid)]
        base (cond-> base
               (some? (get doc ":statement/total-jpy"))
               (conj (kotoba/add stmt-e ":meisai.stmt/total-jpy" (long (get doc ":statement/total-jpy"))))
               (get doc ":source/url")
               (conj (kotoba/add stmt-e ":meisai.stmt/source-url" (str (get doc ":source/url"))))
               ;; worldwide (clj-native superset, ADR-2606122400 R1): non-JPY statements carry a
               ;; generic total + currency. The JP fixture has neither key → datoms byte-identical.
               (some? (get doc ":statement/total"))
               (conj (kotoba/add stmt-e ":meisai.stmt/total" (long (get doc ":statement/total"))))
               (get doc ":statement/currency")
               (conj (kotoba/add stmt-e ":meisai.stmt/currency" (str (get doc ":statement/currency")))))]
    (into base
          (mapcat
           (fn [i r]
             (let [date (str (get r ":date" "?"))
                   merchant (str (get r ":merchant" "?"))
                   ;; JP rows carry :amount_jpy → canonical :meisai.row/amount-jpy attribute and the
                   ;; EXACT same hash input (byte-parity with sumitclub/ingest.py). Worldwide rows
                   ;; carry generic :amount (integer minor units) + :currency.
                   jpy? (contains? r ":amount_jpy")
                   amount (long (get r (if jpy? ":amount_jpy" ":amount") 0))
                   h (sha256-hex (str stmt-e "|" i "|" date "|" merchant "|" amount))
                   row-e (str "meisai-row:" (subs h 0 16))
                   ds [(kotoba/add row-e ":meisai.row/stmt" stmt-e)
                       (kotoba/add row-e ":meisai.row/index" i)
                       (kotoba/add row-e ":meisai.row/date" date)
                       (kotoba/add row-e ":meisai.row/merchant" merchant)]
                   ds (if jpy?
                        (conj ds (kotoba/add row-e ":meisai.row/amount-jpy" amount))
                        (cond-> (conj ds (kotoba/add row-e ":meisai.row/amount" amount))
                          (get r ":currency")
                          (conj (kotoba/add row-e ":meisai.row/currency" (str (get r ":currency"))))))]
               (if (get r ":note")
                 (conj ds (kotoba/add row-e ":meisai.row/note" (str (get r ":note"))))
                 ds)))
           (range) rows))))

#?(:clj
   (defn load-statement
     "Read one intake EDN file → [doc content-cid] (1:1 with load_statement)."
     [path]
     (let [s (slurp (io/file path))]
       [(kotoba/parse-edn s) (intake-cid s)])))
