(ns noroshi.methods.mt19937
  "CPython-identical Mersenne Twister (MT19937) + `random.Random(seed).gauss(mu,sigma)`.

  isac_sim's `_add_noise` uses `random.Random(seed)` then `rng.gauss(0,σ)` per cell. To
  reproduce the seeded CA-CFAR detections byte-for-byte against python3 we replicate:
    - MT19937 with CPython's `init_by_array` integer seeding (seed → 32-bit key words),
    - `genrand_res53()` 53-bit double = (a>>5)·2^26 + (b>>6)) / 2^53  (CPython random_random),
    - `gauss()` with its `gauss_next` one-value cache (Box-Muller via cos/sin).

  CPython seeds `Random(int)` via `init_by_array(key)` where key = the seed's abs value split
  into little-endian 32-bit words (a single word for seeds < 2^32). 32-bit arithmetic is done
  on JVM longs masked to 0xFFFFFFFF. Stateful object via an atom; pure consumers fold over it.
  Portable .cljc (JVM longs)."
  (:refer-clojure :exclude []))

(def ^:private N 624)
(def ^:private M 397)
(def ^:private MATRIX-A 0x9908b0df)
(def ^:private UPPER 0x80000000)
(def ^:private LOWER 0x7fffffff)
(def ^:private MASK32 0xffffffff)

(defn- u32 [x] (bit-and (long x) MASK32))

(defn- init-genrand
  "CPython init_genrand(s): seed the mt[] array from a single 32-bit value."
  [s]
  (let [mt (long-array N)]
    (aset mt 0 (u32 s))
    (loop [i 1]
      (when (< i N)
        (let [prev (aget mt (dec i))
              v (u32 (+ (* 1812433253 (bit-xor prev (unsigned-bit-shift-right prev 30))) i))]
          (aset mt i v))
        (recur (inc i))))
    mt))

(defn- init-by-array
  "CPython init_by_array(init_key): the seeding used by Random(int)."
  [init-key]
  (let [mt (init-genrand 19650218)
        klen (count init-key)
        ka (long-array init-key)]
    ;; first loop — returns the index `i` reached, which the second loop continues from
    (let [i-after
          (loop [i 1, j 0, k (max N klen)]
            (if (pos? k)
              (let [prev (aget mt (dec i))
                    v (u32 (+ (bit-xor (aget mt i)
                                       (u32 (* (bit-xor prev (unsigned-bit-shift-right prev 30)) 1664525)))
                              (aget ka j) j))]
                (aset mt i v)
                (let [i' (inc i) j' (inc j)
                      ni (if (>= i' N) (do (aset mt 0 (aget mt (dec N))) 1) i')
                      nj (if (>= j' klen) 0 j')]
                  (recur ni nj (dec k))))
              i))]
      ;; second loop — continues from i-after (CPython does NOT reset i)
      (loop [i i-after, k (dec N)]
        (when (pos? k)
          (let [prev (aget mt (dec i))
                v (u32 (- (bit-xor (aget mt i)
                                   (u32 (* (bit-xor prev (unsigned-bit-shift-right prev 30)) 1566083941)))
                          i))]
            (aset mt i v)
            (let [i' (inc i)
                  ni (if (>= i' N) (do (aset mt 0 (aget mt (dec N))) 1) i')]
              (recur ni (dec k)))))))
    (aset mt 0 0x80000000)
    mt))

(defn- seed->key
  "CPython Random(int) splits |seed| into little-endian 32-bit words (≥1 word)."
  [seed]
  (let [s (long (Math/abs (long seed)))]
    (loop [x s, words []]
      (if (and (zero? x) (seq words))
        words
        (recur (unsigned-bit-shift-right x 32)
               (conj words (bit-and x MASK32)))))))

(defn make
  "Construct a MT19937 state seeded as CPython `random.Random(seed)`."
  [seed]
  (atom {:mt (init-by-array (seed->key seed)) :mti N :gauss-next nil}))

(defn- generate-block!
  "Regenerate the mt[] array (CPython genrand_int32 reload branch)."
  [^longs mt]
  (let [mag01 (long-array [0 MATRIX-A])]
    (loop [kk 0]
      (when (< kk (- N M))
        (let [y (u32 (bit-or (bit-and (aget mt kk) UPPER) (bit-and (aget mt (inc kk)) LOWER)))]
          (aset mt kk (u32 (bit-xor (aget mt (+ kk M))
                                    (unsigned-bit-shift-right y 1)
                                    (aget mag01 (bit-and y 1))))))
        (recur (inc kk))))
    (loop [kk (- N M)]
      (when (< kk (dec N))
        (let [y (u32 (bit-or (bit-and (aget mt kk) UPPER) (bit-and (aget mt (inc kk)) LOWER)))]
          (aset mt kk (u32 (bit-xor (aget mt (+ kk (- M N)))
                                    (unsigned-bit-shift-right y 1)
                                    (aget mag01 (bit-and y 1))))))
        (recur (inc kk))))
    (let [y (u32 (bit-or (bit-and (aget mt (dec N)) UPPER) (bit-and (aget mt 0) LOWER)))]
      (aset mt (dec N) (u32 (bit-xor (aget mt (dec M))
                                     (unsigned-bit-shift-right y 1)
                                     (aget mag01 (bit-and y 1))))))))

(defn- genrand-int32!
  "CPython genrand_uint32 — one tempered 32-bit word, advancing the state."
  [state]
  (let [s @state
        ^longs mt (:mt s)]
    (when (>= (:mti s) N)
      (generate-block! mt)
      (swap! state assoc :mti 0))
    (let [mti (:mti @state)
          y0 (aget mt mti)
          _ (swap! state assoc :mti (inc mti))
          y1 (bit-xor y0 (unsigned-bit-shift-right y0 11))
          y2 (bit-xor y1 (bit-and (bit-shift-left y1 7) 0x9d2c5680))
          y3 (bit-xor y2 (bit-and (bit-shift-left y2 15) 0xefc60000))
          y4 (bit-xor y3 (unsigned-bit-shift-right y3 18))]
      (u32 y4))))

(defn random!
  "CPython random_random / genrand_res53: a 53-bit float in [0,1)."
  [state]
  (let [a (unsigned-bit-shift-right (genrand-int32! state) 5)   ; 27 bits
        b (unsigned-bit-shift-right (genrand-int32! state) 6)]  ; 26 bits
    (/ (+ (* (double a) 67108864.0) (double b)) 9007199254740992.0)))

(def ^:private TWO-PI (* 2.0 Math/PI))

(defn gauss!
  "CPython random.gauss(mu, sigma) with its gauss_next one-value cache."
  [state mu sigma]
  (let [z (:gauss-next @state)]
    (swap! state assoc :gauss-next nil)
    (if (nil? z)
      (let [x2pi (* (random! state) TWO-PI)
            g2rad (Math/sqrt (* -2.0 (Math/log (- 1.0 (random! state)))))
            z (* (Math/cos x2pi) g2rad)]
        (swap! state assoc :gauss-next (* (Math/sin x2pi) g2rad))
        (+ mu (* z sigma)))
      (+ mu (* z sigma)))))
