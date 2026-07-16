(ns noroshi.methods.complex
  "noroshi 烽 — minimal complex-arithmetic helper (ADR-2606051600). Stdlib only (Math/).

  The JVM/Clojure has no built-in complex type, so isac_sim.cljc reproduces exactly the
  cmath / complex operations the Python ISAC simulator uses, byte-for-byte against CPython:

    cmath.exp(1j·θ)  = (cos θ, sin θ)        → `cis`
    complex +,−,*                            → `add` `sub` `mul`
    complex(re, im)                          → `c`
    abs(z)           = math.hypot(im, re)    → `cabs`  (CPython hypot order: |re|,|im|)
    phase(z)         = math.atan2(im, re)    → `phase` (unused by isac_sim, provided for parity)
    sum([z…])        = left-fold add from 0j → `csum`

  A complex value is a 2-vector `[re im]` of doubles. Every op routes through Math/ at
  last-ULP (Math/cos Math/sin Math/exp Math/hypot Math/atan2), matching CPython's libm-backed
  C complex semantics. CPython's `abs(complex)` is `hypot(z.real, z.imag)`; we call
  Math/hypot in the same argument order. Pure fns; portable .cljc."
  (:refer-clojure :exclude [+]))

(defn c
  "complex(re, im) → [re im] (doubles)."
  [re im] [(double re) (double im)])

(def zero "0j" [0.0 0.0])

(defn cis
  "cmath.exp(1j·θ) = (cos θ, sin θ)."
  [theta]
  [(Math/cos (double theta)) (Math/sin (double theta))])

(defn add
  "Complex addition."
  [[ar ai] [br bi]]
  [(clojure.core/+ ar br) (clojure.core/+ ai bi)])

(defn sub
  "Complex subtraction."
  [[ar ai] [br bi]]
  [(clojure.core/- ar br) (clojure.core/- ai bi)])

(defn mul
  "Complex multiplication: (a+bi)(c+di) = (ac−bd) + (ad+bc)i, in CPython's evaluation order."
  [[ar ai] [br bi]]
  [(clojure.core/- (clojure.core/* ar br) (clojure.core/* ai bi))
   (clojure.core/+ (clojure.core/* ar bi) (clojure.core/* ai br))])

(defn cdiv
  "Complex division a/b (CPython _Py_c_quot smith's algorithm)."
  [[ar ai] [br bi]]
  (let [abs-br (Math/abs (double br))
        abs-bi (Math/abs (double bi))]
    (if (>= abs-br abs-bi)
      (if (zero? abs-br)
        [(/ ar br) (/ ai br)]
        (let [ratio (/ bi br)
              denom (clojure.core/+ br (clojure.core/* bi ratio))]
          [(/ (clojure.core/+ ar (clojure.core/* ai ratio)) denom)
           (/ (clojure.core/- ai (clojure.core/* ar ratio)) denom)]))
      (let [ratio (/ br bi)
            denom (clojure.core/+ (clojure.core/* br ratio) bi)]
        [(/ (clojure.core/+ (clojure.core/* ar ratio) ai) denom)
         (/ (clojure.core/- (clojure.core/* ai ratio) ar) denom)]))))

(defn cabs
  "abs(complex) = math.hypot(re, im) — CPython c_abs uses hypot(real, imag)."
  [[re im]]
  (Math/hypot (double re) (double im)))

(defn phase
  "cmath.phase(z) = math.atan2(im, re)."
  [[re im]]
  (Math/atan2 (double im) (double re)))

(defn csum
  "sum(zs) — left fold from 0j, preserving CPython's left-to-right addition order."
  [zs]
  (reduce add zero zs))
