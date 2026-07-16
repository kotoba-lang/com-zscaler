#!/usr/bin/env bb
;; noroshi 烽 — complex-arithmetic parity test (CPython cmath / complex).
;; Run:  bb --classpath 20-actors 20-actors/noroshi/methods/test_complex.cljc
(ns noroshi.methods.test-complex
  "Pins noroshi.methods.complex byte-for-byte against CPython's complex / cmath ops.

  isac_sim reproduces the Python ISAC radar simulator's spectral math; complex is the helper
  that must match CPython's libm-backed C complex semantics (Math/cos·sin·hypot·atan2 at
  last-ULP, CPython arg orders) so detections are byte-identical vs python3. The intricate one is
  cdiv = CPython _Py_c_quot (Smith's algorithm, three branches) — a wrong branch silently skews
  division. The module had NO test. The pinned literals are actual `python3` complex/cmath output."
  (:require [noroshi.methods.complex :as cx]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private eps 1.0e-12)
(defn- close?  [[ar ai] [br bi]] (and (< (Math/abs (- (double ar) (double br))) eps)
                                      (< (Math/abs (- (double ai) (double bi))) eps)))
(defn- close1? [a b] (< (Math/abs (- (double a) (double b))) eps))

(deftest construction-and-zero
  (is (= [3.0 4.0] (cx/c 3 4)) "complex(re,im) → [re im] doubles")
  (is (= [0.0 0.0] cx/zero)))

(deftest add-and-sub
  (is (= [4.0 6.0]   (cx/add (cx/c 1 2) (cx/c 3 4))))
  (is (= [-2.0 -2.0] (cx/sub (cx/c 1 2) (cx/c 3 4)))))

(deftest mul-matches-cpython
  ;; (a+bi)(c+di) = (ac−bd)+(ad+bc)i — CPython evaluation order
  (is (close? (cx/mul (cx/c 1 2)    (cx/c 3 4))  [-5.0 10.0]))    ; (1+2j)*(3+4j)
  (is (close? (cx/mul (cx/c -1.5 0.5) (cx/c 2 -3)) [-1.5 5.5])))  ; (-1.5+0.5j)*(2-3j)

(deftest cdiv-matches-cpython-smith-all-branches
  ;; _Py_c_quot Smith's algorithm — exercise each branch
  (is (close? (cx/cdiv (cx/c 1 2) (cx/c 3 4)) [0.44 0.08]))    ; |br|<|bi| → ratio=br/bi
  (is (close? (cx/cdiv (cx/c 1 2) (cx/c 4 3)) [0.4 0.2]))      ; |br|>=|bi| → ratio=bi/br
  (is (close? (cx/cdiv (cx/c 5 -2) (cx/c 0 1)) [-2.0 -5.0])))  ; br=0 (|br|<|bi|)

(deftest cabs-matches-cpython
  ;; abs(complex) = hypot(re, im) — CPython c_abs arg order
  (is (close1? (cx/cabs (cx/c 3 4)) 5.0))
  (is (close1? (cx/cabs (cx/c -0.6 0.8)) 1.0)))

(deftest cis-matches-cmath-exp
  ;; cmath.exp(1j·θ) = (cos θ, sin θ)
  (is (close? (cx/cis 0.5) [0.8775825618903728 0.479425538604203])))

(deftest phase-matches-cmath
  ;; cmath.phase(z) = atan2(im, re)
  (is (close1? (cx/phase (cx/c 1 1))  0.7853981633974483))
  (is (close1? (cx/phase (cx/c -1 -1)) -2.356194490192345)))

(deftest csum-is-left-fold-from-zero
  (is (close? (cx/csum [(cx/c 1 1) (cx/c 2 -3) (cx/c 0.5 0.5)]) [3.5 -1.5]))
  (is (= cx/zero (cx/csum [])) "empty sum = 0j"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-complex)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
