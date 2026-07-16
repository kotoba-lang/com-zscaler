(ns hakoniwa.methods.cid
  "hakoniwa 箱庭 — kotoba IPFS content-address (CIDv1 / raw 0x55 / sha2-256 / base32-lower).

  Delegates to the shared **com-junkawasaki/multiformats-clj** library — the SAME CID
  `ipfs add --cid-version=1 --raw-leaves` produces for a single raw block (< 256 KiB).
  Single-block by design (hakoniwa ingests a BOUNDED slice → one raw block). Public
  surface (base32 / cidv1-raw / single-block-limit) is unchanged."
  (:require [multiformats.core :as mf]))

#?(:clj
   (defn- ->ba ^bytes [data]
     (cond (string? data) (.getBytes ^String data "UTF-8")
           (bytes? data)  data
           :else (byte-array (map #(unchecked-byte (bit-and (int %) 0xff)) data)))))

(def base32 mf/base32)   ;; bytes/int-seq → RFC4648 base32-lower, no padding

(defn cidv1-raw
  "CIDv1 / raw (0x55) / sha2-256 of the bytes (UTF-8 string OR byte-array OR int seq)."
  [data]
  #?(:clj (mf/cidv1-raw (->ba data))
     :cljs (throw (ex-info "cidv1-raw is :clj-only" {}))))

(def single-block-limit (* 256 1024))
