(ns shomei.methods.blake2b
  "blake2b.cljc — 証明 (shomei) hand-ported BLAKE2b (RFC 7693). ADR-2606072100.

  The JVM stdlib ships SHA-256 + HmacSHA256 but NO BLAKE2b, and shomei (claims /
  aggregate) hashes external identifiers + DIDs with `hashlib.blake2b(..., digest_size=N)`.
  Adding BouncyCastle would break the SCI/WASM-portable pure-.cljc invariant, so — exactly
  as kabuto hand-ports CPython siphash13 (`kabuto.methods.analyze`) — we reproduce BLAKE2b
  here in pure portable bit-twiddling, verified byte-for-byte against `hashlib.blake2b`
  (see `shomei.methods.test-blake2b`).

  Parameterisation as used by shomei: digest_size ∈ {12, 32}; NO key, NO salt, NO person
  (the simple unkeyed hash). The default (digest_size 64) is also supported + pinned. We
  implement the general unkeyed BLAKE2b so the parity vectors generalise.

  The compression function operates on 64-bit words. On the JVM a `long` is signed 64-bit,
  so unchecked add / xor / rotate ARE the BLAKE2b mod-2^64 arithmetic exactly. We avoid any
  reliance on `Long` only where the host can't (a :cljs/wasm impl would slot a BigInt path
  here); the :clj path uses primitive longs.

  Stdlib only / no host crypto dependency."
  (:require [clojure.string :as str]))

;; ── IV (RFC 7693 §2.6) ────────────────────────────────────────────────────────
(def ^:private IV
  (long-array
    [(unchecked-long 0x6a09e667f3bcc908) (unchecked-long 0xbb67ae8584caa73b)
     (unchecked-long 0x3c6ef372fe94f82b) (unchecked-long 0xa54ff53a5f1d36f1)
     (unchecked-long 0x510e527fade682d1) (unchecked-long 0x9b05688c2b3e6c1f)
     (unchecked-long 0x1f83d9abfb41bd6b) (unchecked-long 0x5be0cd19137e2179)]))

;; ── message schedule σ (RFC 7693 §2.7) — 12 rounds × 16 ─────────────────────────
(def ^:private SIGMA
  [[0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15]
   [14 10 4 8 9 15 13 6 1 12 0 2 11 7 5 3]
   [11 8 12 0 5 2 15 13 10 14 3 6 7 1 9 4]
   [7 9 3 1 13 12 11 14 2 6 5 10 4 0 15 8]
   [9 0 5 7 2 4 10 15 14 1 11 12 6 8 3 13]
   [2 12 6 10 0 11 8 3 4 13 7 5 15 14 1 9]
   [12 5 1 15 14 13 4 10 0 7 6 3 9 2 8 11]
   [13 11 7 14 12 1 3 9 5 0 15 4 8 6 2 10]
   [6 15 14 9 11 3 0 8 12 2 13 7 1 4 10 5]
   [10 2 8 4 7 6 1 5 15 11 9 14 3 12 13 0]
   [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15]
   [14 10 4 8 9 15 13 6 1 12 0 2 11 7 5 3]])

(def ^:private BLOCKBYTES 128)

(defn- ^long rotr
  "64-bit rotate-right (BLAKE2b uses right-rotations of 32/24/16/63)."
  [^long x ^long n]
  (bit-or (unsigned-bit-shift-right x n) (bit-shift-left x (- 64 n))))

(defn- mix!
  "The G mixing function (RFC 7693 §3.1) over the 16-long working vector `v` (a long-array)."
  [^longs v a b c d ^long x ^long y]
  (let [va (aget v a) vb (aget v b) vc (aget v c) vd (aget v d)
        va (unchecked-add (unchecked-add va vb) x)
        vd (rotr (bit-xor vd va) 32)
        vc (unchecked-add vc vd)
        vb (rotr (bit-xor vb vc) 24)
        va (unchecked-add (unchecked-add va vb) y)
        vd (rotr (bit-xor vd va) 16)
        vc (unchecked-add vc vd)
        vb (rotr (bit-xor vb vc) 63)]
    (aset v a va) (aset v b vb) (aset v c vc) (aset v d vd)))

(defn- compress!
  "F compression (RFC 7693 §3.2): mix the 16 message words `m` (long-array) into the 8-word
  state `h` (long-array) with the 128-bit byte-counter `t` (only low 64 bits used here —
  shomei inputs are ≪ 2^64 bytes) and final-block flag `last?`."
  [^longs h ^longs m ^long t last?]
  (let [v (long-array 16)]
    (dotimes [i 8]
      (aset v i (aget h i))
      (aset v (+ i 8) (aget IV i)))
    (aset v 12 (bit-xor (aget v 12) t))          ;; t0 = low 64 bits of the byte counter
    ;; v[13] ^= t1 (high 64 bits) — always 0 for our message sizes
    (when last?
      (aset v 14 (bit-xor (aget v 14) (unchecked-long 0xffffffffffffffff))))
    (dotimes [r 12]
      (let [s (SIGMA r)]
        (mix! v 0 4 8  12 (aget m (s 0))  (aget m (s 1)))
        (mix! v 1 5 9  13 (aget m (s 2))  (aget m (s 3)))
        (mix! v 2 6 10 14 (aget m (s 4))  (aget m (s 5)))
        (mix! v 3 7 11 15 (aget m (s 6))  (aget m (s 7)))
        (mix! v 0 5 10 15 (aget m (s 8))  (aget m (s 9)))
        (mix! v 1 6 11 12 (aget m (s 10)) (aget m (s 11)))
        (mix! v 2 7 8  13 (aget m (s 12)) (aget m (s 13)))
        (mix! v 3 4 9  14 (aget m (s 14)) (aget m (s 15)))))
    (dotimes [i 8]
      (aset h i (bit-xor (aget h i) (bit-xor (aget v i) (aget v (+ i 8))))))))

(defn- load-le-words
  "Read 16 little-endian 64-bit words out of `bytes-in` starting at `off` (a BLOCKBYTES
  window, zero-padded past the input length `len`)."
  [^bytes bytes-in ^long off ^long len]
  (let [m (long-array 16)]
    (dotimes [w 16]
      (let [base (+ off (* w 8))]
        (aset m w
              (loop [j 0 acc 0]
                (if (< j 8)
                  (let [idx (+ base j)
                        b (if (< idx len) (bit-and (long (aget bytes-in idx)) 0xff) 0)]
                    (recur (inc j) (bit-or acc (bit-shift-left (long b) (* 8 j)))))
                  acc)))))
    m))

(defn digest-bytes
  "BLAKE2b of `data` (a byte-array / seq of bytes) → a byte-array of length `digest-size`
  (unkeyed, no salt/person — the shape shomei uses). 1 ≤ digest-size ≤ 64.

  Mirrors `hashlib.blake2b(data, digest_size=N).digest()` byte-for-byte."
  [data ^long digest-size]
  (let [^bytes input #?(:clj (if (bytes? data) data (byte-array data))
                        :cljs (if (instance? js/Uint8Array data) data (js/Uint8Array. (clj->js data))))
        len (long (alength input))
        h (long-array 8)]
    ;; h0 ← IV0 ⊕ 0x0101_kk_nn  (kk = keylen = 0, nn = digest length, fanout=depth=1)
    (dotimes [i 8] (aset h i (aget IV i)))
    (aset h 0 (bit-xor (aget h 0)
                       (unchecked-long (bit-or 0x01010000 (bit-and digest-size 0xff)))))
    (let [full (long (quot len BLOCKBYTES))
          ;; number of complete blocks that are NOT the final block:
          ;; if len is a positive multiple of 128 the last full block is the final one.
          n-mid (cond
                  (zero? len) 0
                  (zero? (mod len BLOCKBYTES)) (dec full)
                  :else full)]
      ;; all non-final complete blocks
      (loop [i 0 t 0]
        (when (< i n-mid)
          (let [t' (+ t BLOCKBYTES)
                m (load-le-words input (* i BLOCKBYTES) len)]
            (compress! h m t' false)
            (recur (inc i) t'))))
      ;; final block (possibly empty / partial)
      (let [off (* n-mid BLOCKBYTES)
            t-final len
            m (load-le-words input off len)]
        (compress! h m t-final true)))
    ;; serialize the first `digest-size` bytes, little-endian per word
    (let [out (byte-array digest-size)]
      (dotimes [i digest-size]
        (let [w (aget h (quot i 8))
              shift (* 8 (mod i 8))
              b (bit-and (unsigned-bit-shift-right w shift) 0xff)]
          (aset out i (#?(:clj unchecked-byte :cljs identity) b))))
      out)))

(defn- byte->hex [b]
  (let [v (bit-and (long b) 0xff)
        s (str (.toString (java.math.BigInteger/valueOf v) 16))]
    (if (< v 16) (str "0" s) s)))

(defn hexdigest
  "Lowercase hex of the digest — mirrors `hashlib.blake2b(data, digest_size=N).hexdigest()`."
  [data ^long digest-size]
  #?(:clj (str/join (map byte->hex (digest-bytes data digest-size)))
     :cljs (str/join (map (fn [b]
                            (let [v (bit-and b 0xff)
                                  s (.toString v 16)]
                              (if (< v 16) (str "0" s) s)))
                          (digest-bytes data digest-size)))))

#?(:clj
   (defn utf8-bytes [^String s] (.getBytes s "UTF-8")))
