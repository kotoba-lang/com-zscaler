(ns tanemaki.methods.cid
  "tanemaki 種蒔き — kotoba IPFS content-address (CIDv1, raw, sha2-256, base32).

  Delegates to the shared **com-junkawasaki/multiformats-clj** library — the SAME CID
  `ipfs add --cid-version=1 --raw-leaves` produces for a single raw block (< 256 KiB),
  so anyone can re-derive a published scorecard's CID and confirm the bytes (G4).
  Inputs are BYTE SEQUENCES (vectors/seqs of 0..255 ints), e.g. (utf8-bytes text).
  Public surface (utf8-bytes / cidv1-raw / sha256-hex / SINGLE-BLOCK-LIMIT) unchanged."
  (:require [multiformats.core :as mf]))

#?(:clj
   (defn- ->ba ^bytes [data]
     (cond (string? data) (.getBytes ^String data "UTF-8")
           (bytes? data)  data
           :else (byte-array (map #(unchecked-byte (bit-and (int %) 0xff)) data)))))

(defn utf8-bytes
  "String → seq of unsigned bytes (0..255)."
  [^String s]
  #?(:clj (map #(bit-and % 0xff) (.getBytes s "UTF-8"))
     :default (throw (ex-info "bind a utf-8 encoder on this host" {}))))

(defn cidv1-raw
  "CIDv1 / raw (0x55) / sha2-256. `data` is a byte seq (e.g. (utf8-bytes text))."
  [data]
  #?(:clj (mf/cidv1-raw (->ba data))
     :default (throw (ex-info "bind a sha-256 impl on this host" {}))))

(defn sha256-hex
  "0x-prefixed lowercase hex SHA-256 — the proposal scorecardSha256 defense-in-depth hash."
  [data]
  #?(:clj (str "0x" (mf/hexify (mf/sha256 (->ba data))))
     :default (throw (ex-info "bind a sha-256 impl on this host" {}))))

(def SINGLE-BLOCK-LIMIT (* 256 1024)) ;; ipfs default chunk size
