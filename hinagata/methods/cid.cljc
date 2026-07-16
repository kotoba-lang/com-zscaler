(ns hinagata.methods.cid
  "hinagata 雛形 — kotoba IPFS content-address (CIDv1, raw, sha2-256, base32).

  Delegates to the shared **com-junkawasaki/multiformats-clj** library — the SAME CID
  `ipfs add --cid-version=1 --raw-leaves` produces for a single raw block (< 256 KiB),
  so anyone can re-derive a published template body's CID and confirm the bytes (G4).
  Public surface (base32 / sha256-bytes / cidv1-raw / sha256-hex / utf8-bytes /
  single-block-limit) is unchanged."
  (:require [multiformats.core :as mf]))

(def base32 mf/base32)   ;; bytes/int-seq → RFC4648 base32-lower, no padding

#?(:clj (def sha256-bytes mf/sha256))            ;; ^bytes → ^bytes sha2-256 digest

#?(:clj
   (defn cidv1-raw
     "CIDv1 / raw (0x55) / sha2-256 of a byte-array (host UTF-8 bytes of the body)."
     [^bytes data]
     (mf/cidv1-raw data)))

#?(:clj
   (defn sha256-hex
     "0x-prefixed lowercase hex SHA-256 — the esign documentSha256 defense-in-depth hash."
     [^bytes data]
     (str "0x" (mf/hexify (mf/sha256 data)))))

(def single-block-limit (* 256 1024))

#?(:clj
   (defn utf8-bytes
     "UTF-8 bytes of a string — the bytes a body content-addresses over."
     ^bytes [^String s]
     (.getBytes s "UTF-8")))
