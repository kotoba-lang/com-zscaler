(ns kadode.methods.cid
  "kadode 門出 — kotoba IPFS content-address (CIDv1, raw, sha2-256, base32).

  Delegates to the shared **com-junkawasaki/multiformats-clj** library — the SAME CID
  `ipfs add --cid-version=1 --raw-leaves` produces for a single raw block (< 256 KiB),
  so a kadode-drafted document's content-address is verifiable with or without the
  `ipfs` daemon. Public surface (base32 / sha256-bytes / ->bytes / cidv1-raw /
  sha256-hex / single-block-limit) is unchanged."
  (:require [multiformats.core :as mf]))

(def base32 mf/base32)   ;; bytes/int-seq → RFC4648 base32-lower, no padding

#?(:clj
   (defn sha256-bytes
     "sha2-256 of a byte-array → seq of unsigned ints (0..255)."
     [^bytes ba]
     (map #(bit-and (int %) 0xff) (mf/sha256 ba))))

#?(:clj
   (defn ->bytes
     "Coerce a String (UTF-8) or byte-array to a byte-array (the document body)."
     ^bytes [data]
     (if (string? data) (.getBytes ^String data "UTF-8") data)))

#?(:clj
   (defn cidv1-raw
     "CIDv1 / raw (0x55) / sha2-256. `data` is a String (UTF-8) or byte-array."
     [data]
     (mf/cidv1-raw (->bytes data))))

#?(:clj
   (defn sha256-hex
     "0x-prefixed lowercase hex SHA-256 — the esign documentSha256 defense-in-depth hash."
     [data]
     (str "0x" (mf/hexify (mf/sha256 (->bytes data))))))

;; ipfs default chunk size; above this the raw CID no longer applies (dag-pb tree).
(def single-block-limit (* 256 1024))
