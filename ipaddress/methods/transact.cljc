(ns ipaddress.methods.transact
  "transact.py — ipaddress kotoba Datomic transact bridge (ADR-2605301400 §T2 save-path).
  1:1 Clojure port of `methods/transact.py`.

  Pushes the kotoba-native IP/ASN graph into a running kotoba node's Datom log via the
  datomic.transact XRPC route. Emits datomic list-form datoms `[:db/add E A V]`.

  House style: rows-to-datoms + schema-datoms are pure & portable; the HTTP push (_post),
  the schema-file read, and the main/argv CLI dispatch are behind #?(:clj …) / omitted (no
  tests cover them) — noted here. There is no clojure.test suite for this module."
  (:require [clojure.string :as str]
            [ipaddress.methods.ip-edn :as ip-edn]))

(def nsid-transact "com.etzhayyim.apps.kotoba.datomic.transact")

(def id-keys
  [":rir/id" ":asn/id" ":iprange/id" ":ip/id" ":net.announce/id"
   ":net.member/id" ":geo/id" ":rdns/id" ":whois/id"])

(def ^:private id-keys-set (set id-keys))

(defn rows-to-datoms
  "Each entity map → [:db/add E A V] strings (E = its id; lists fan out). Port of rows_to_datoms."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some (fn [k] (when (contains? r k) (get r k))) id-keys)]
         (if (nil? e)
           out
           (reduce
            (fn [out [k v]]
              (if (contains? id-keys-set k)
                out
                (reduce (fn [out item]
                          (conj out (str "[:db/add " (ip-edn/edn-str e) " " k " " (ip-edn/edn-val item) "]")))
                        out
                        (if (sequential? v) v [v]))))
            out
            (ip-edn/ordered-items r))))))
   []
   rows))

(defn schema-datoms
  "Ontology :attributes (map-form datomic schema install). Port of schema_datoms.
  `onto` is the parsed ontology EDN map; drops :db/doc (free-text '|' the reader rejects)."
  [onto]
  (let [attrs (if (map? onto) (get onto ":attributes" []) [])]
    (mapv (fn [a]
            (str "{" (str/join " " (->> (ip-edn/ordered-items a)
                                        (remove (fn [[k _]] (= k ":db/doc")))
                                        (map (fn [[k v]] (str k " " (ip-edn/edn-val v)))))) "}"))
          attrs)))

#?(:clj
   (defn load-schema
     "Read + parse the ip-network ontology EDN (file I/O at this edge)."
     [path]
     (ip-edn/load-edn path)))
