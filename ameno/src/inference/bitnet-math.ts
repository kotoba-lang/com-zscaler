/**
 * @etzhayyim/ameno/inference/bitnet-math — small numerical helpers for
 * the BitNet forward path (ADR-2605263800 R1b commit 9).
 *
 * - `softmaxInPlace(x, length?)` — numerically-stable max-shifted softmax.
 * - `argmaxF32(x)` — index of the maximum element (greedy sampling).
 * - `dotProductF32(a, b)` — inner product of two equal-length vectors.
 * - `scalarMatmulF32(A, B, M, K, N, out)` — O(M·K·N) row-major matmul.
 *
 * These are reference implementations:
 *   - The scalar matmul backs the fp16-fallback dispatch path AND
 *     unit tests cross-check the BitLinear WGSL/WASM kernels against it.
 *   - dot product + softmax sit on the attention hot path; SIMD via
 *     `Float32Array.reduce` style fusion lands at R1c.
 *
 * All buffers are caller-allocated `Float32Array`s; no heap traffic
 * in the hot loop.
 */

/**
 * Compute softmax over `x[0..length]` in place. `length` defaults to
 * the full array. Uses the standard max-subtract trick to avoid
 * overflow of `exp(x_i)`.
 *
 * Post-condition: `sum(x[0..length]) ≈ 1` (within fp32 epsilon).
 *
 * Throws if `length` exceeds `x.length`.
 */
export function softmaxInPlace(x: Float32Array, length?: number): void {
  const n = length ?? x.length;
  if (n > x.length) {
    throw new Error(
      `softmaxInPlace: length=${String(n)} > x.length=${String(x.length)}`,
    );
  }
  if (n === 0) return;

  let max = x[0]!;
  for (let i = 1; i < n; i++) {
    if (x[i]! > max) max = x[i]!;
  }
  let sum = 0;
  for (let i = 0; i < n; i++) {
    const e = Math.exp(x[i]! - max);
    x[i] = e;
    sum += e;
  }
  const invSum = 1.0 / sum;
  for (let i = 0; i < n; i++) {
    x[i] = x[i]! * invSum;
  }
}

/**
 * Index of the maximum element. Returns 0 for empty arrays (caller's
 * responsibility to handle the degenerate case).
 *
 * Used by greedy decoding (temperature = 0). Top-k / top-p sampling
 * is a future R-step concern.
 */
export function argmaxF32(x: Float32Array): number {
  if (x.length === 0) return 0;
  let maxVal = x[0]!;
  let maxIdx = 0;
  for (let i = 1; i < x.length; i++) {
    if (x[i]! > maxVal) {
      maxVal = x[i]!;
      maxIdx = i;
    }
  }
  return maxIdx;
}

/**
 * Inner product `a · b`. Both arrays must be the same length.
 *
 * Used in the attention scores computation: for each (head, position),
 * dotProduct(Q_head, K_position_head) → one scalar score.
 */
export function dotProductF32(a: Float32Array, b: Float32Array): number {
  if (a.length !== b.length) {
    throw new Error(
      `dotProductF32: a.length=${String(a.length)} != b.length=${String(b.length)}`,
    );
  }
  let s = 0;
  for (let i = 0; i < a.length; i++) {
    s += a[i]! * b[i]!;
  }
  return s;
}

/**
 * O(M·K·N) reference matmul: `out[m, n] = Σ_k A[m, k] × B[k, n]`.
 *
 * Layout:
 *   - A is `[M × K]` row-major (length M·K).
 *   - B is `[K × N]` row-major (length K·N).
 *   - out is `[M × N]` row-major (length M·N).
 *
 * `out === A` or `out === B` is NOT supported (output overlaps inputs
 * mid-loop); caller must allocate a separate buffer.
 *
 * This is **not** for the BitLinear hot path — that goes through
 * the wgpu/wasm dispatch. This function is the reference test
 * oracle + the fp16-fallback dispatch implementation.
 */
export function scalarMatmulF32(
  A: Float32Array,
  B: Float32Array,
  M: number,
  K: number,
  N: number,
  out: Float32Array,
): void {
  if (A.length !== M * K) {
    throw new Error(
      `scalarMatmulF32: A.length=${String(A.length)} != M×K=${String(M * K)}`,
    );
  }
  if (B.length !== K * N) {
    throw new Error(
      `scalarMatmulF32: B.length=${String(B.length)} != K×N=${String(K * N)}`,
    );
  }
  if (out.length !== M * N) {
    throw new Error(
      `scalarMatmulF32: out.length=${String(out.length)} != M×N=${String(M * N)}`,
    );
  }
  for (let m = 0; m < M; m++) {
    for (let n = 0; n < N; n++) {
      let acc = 0;
      for (let k = 0; k < K; k++) {
        acc += A[m * K + k]! * B[k * N + n]!;
      }
      out[m * N + n] = acc;
    }
  }
}

/**
 * Add `b[i]` into `a[i]` in place. Used by the residual-connection
 * step in transformer blocks: `x = x + attn_out` and `x = x + ffn_out`.
 *
 * Both arrays must be the same length.
 */
export function addInPlaceF32(a: Float32Array, b: Float32Array): void {
  if (a.length !== b.length) {
    throw new Error(
      `addInPlaceF32: a.length=${String(a.length)} != b.length=${String(b.length)}`,
    );
  }
  for (let i = 0; i < a.length; i++) {
    a[i] = a[i]! + b[i]!;
  }
}

/**
 * Scale all elements by a constant in place: `a[i] *= s`. Used for
 * the `1/sqrt(head_dim)` attention scaling factor.
 */
export function scaleInPlaceF32(a: Float32Array, s: number): void {
  for (let i = 0; i < a.length; i++) {
    a[i] = a[i]! * s;
  }
}
