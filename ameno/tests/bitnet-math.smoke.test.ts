/**
 * Smoke tests for `bitnet-math` (ADR-2605263800 R1b commit 9).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-math.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  softmaxInPlace,
  argmaxF32,
  dotProductF32,
  scalarMatmulF32,
  addInPlaceF32,
  scaleInPlaceF32,
} from "../src/inference/bitnet-math.ts";

// ──────────────────────────────────────────────────────────────
// softmax
// ──────────────────────────────────────────────────────────────

test("softmaxInPlace — sum equals 1", () => {
  const x = new Float32Array([1, 2, 3, 4]);
  softmaxInPlace(x);
  let sum = 0;
  for (let i = 0; i < 4; i++) sum += x[i]!;
  assert.ok(Math.abs(sum - 1) < 1e-6, `sum=${sum}`);
});

test("softmaxInPlace — uniform input → uniform output", () => {
  const x = new Float32Array([1, 1, 1, 1]);
  softmaxInPlace(x);
  for (let i = 0; i < 4; i++) {
    assert.ok(Math.abs(x[i]! - 0.25) < 1e-6, `x[${i}]=${x[i]} ≈ 0.25`);
  }
});

test("softmaxInPlace — large values don't overflow (max-subtract)", () => {
  const x = new Float32Array([700, 700, 700]);
  softmaxInPlace(x);
  // Without max-subtract, exp(700) = Infinity → NaN. With max-subtract,
  // exp(0) = 1 for all three, normalized to 1/3.
  for (let i = 0; i < 3; i++) {
    assert.ok(Math.abs(x[i]! - 1 / 3) < 1e-6);
  }
});

test("softmaxInPlace — partial length", () => {
  const x = new Float32Array([1, 2, 3, 999]); // last value should be ignored
  softmaxInPlace(x, 3);
  let sum = 0;
  for (let i = 0; i < 3; i++) sum += x[i]!;
  assert.ok(Math.abs(sum - 1) < 1e-6);
  // Index 3 unchanged.
  assert.equal(x[3], 999);
});

test("softmaxInPlace — throws on length > x.length", () => {
  const x = new Float32Array(3);
  assert.throws(() => softmaxInPlace(x, 5), /length=5 > x\.length=3/);
});

test("softmaxInPlace — empty array no-op", () => {
  const x = new Float32Array(0);
  // Should not throw.
  softmaxInPlace(x);
  assert.equal(x.length, 0);
});

// ──────────────────────────────────────────────────────────────
// argmax
// ──────────────────────────────────────────────────────────────

test("argmaxF32 — strictly increasing array", () => {
  const x = new Float32Array([1, 2, 3, 4, 5]);
  assert.equal(argmaxF32(x), 4);
});

test("argmaxF32 — first occurrence wins on ties", () => {
  const x = new Float32Array([5, 5, 5]);
  // Implementation uses strict >; the first index keeps the max.
  assert.equal(argmaxF32(x), 0);
});

test("argmaxF32 — single negative max", () => {
  const x = new Float32Array([-3, -1, -7, -2]);
  assert.equal(argmaxF32(x), 1);
});

test("argmaxF32 — empty array returns 0", () => {
  const x = new Float32Array(0);
  assert.equal(argmaxF32(x), 0);
});

// ──────────────────────────────────────────────────────────────
// dot product
// ──────────────────────────────────────────────────────────────

test("dotProductF32 — known result", () => {
  const a = new Float32Array([1, 2, 3]);
  const b = new Float32Array([4, 5, 6]);
  // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32.
  assert.equal(dotProductF32(a, b), 32);
});

test("dotProductF32 — orthogonal vectors → 0", () => {
  const a = new Float32Array([1, 0, 0]);
  const b = new Float32Array([0, 1, 0]);
  assert.equal(dotProductF32(a, b), 0);
});

test("dotProductF32 — throws on length mismatch", () => {
  const a = new Float32Array(4);
  const b = new Float32Array(3);
  assert.throws(() => dotProductF32(a, b), /a\.length=4 != b\.length=3/);
});

// ──────────────────────────────────────────────────────────────
// scalar matmul
// ──────────────────────────────────────────────────────────────

test("scalarMatmulF32 — 2×3 × 3×2 = 2×2 known result", () => {
  // A = [[1,2,3],[4,5,6]]  (2×3)
  // B = [[7,8],[9,10],[11,12]]  (3×2)
  // A × B = [[58, 64], [139, 154]]
  const A = new Float32Array([1, 2, 3, 4, 5, 6]);
  const B = new Float32Array([7, 8, 9, 10, 11, 12]);
  const out = new Float32Array(4);
  scalarMatmulF32(A, B, 2, 3, 2, out);
  assert.deepEqual(Array.from(out), [58, 64, 139, 154]);
});

test("scalarMatmulF32 — identity × A = A", () => {
  // I_3 × A_3x2 = A_3x2.
  const I = new Float32Array([1, 0, 0, 0, 1, 0, 0, 0, 1]);
  const A = new Float32Array([1, 2, 3, 4, 5, 6]);
  const out = new Float32Array(6);
  scalarMatmulF32(I, A, 3, 3, 2, out);
  assert.deepEqual(Array.from(out), [1, 2, 3, 4, 5, 6]);
});

test("scalarMatmulF32 — 1×K × K×1 = dot product", () => {
  const A = new Float32Array([1, 2, 3]); // 1×3
  const B = new Float32Array([4, 5, 6]); // 3×1
  const out = new Float32Array(1);
  scalarMatmulF32(A, B, 1, 3, 1, out);
  assert.equal(out[0], 32);
});

test("scalarMatmulF32 — throws on shape mismatch", () => {
  const A = new Float32Array(5);
  const B = new Float32Array(6);
  const out = new Float32Array(4);
  assert.throws(
    () => scalarMatmulF32(A, B, 2, 3, 2, out),
    /A\.length=5 != M×K=6/,
  );
});

// ──────────────────────────────────────────────────────────────
// addInPlace + scaleInPlace
// ──────────────────────────────────────────────────────────────

test("addInPlaceF32 — element-wise add", () => {
  const a = new Float32Array([1, 2, 3]);
  const b = new Float32Array([10, 20, 30]);
  addInPlaceF32(a, b);
  assert.deepEqual(Array.from(a), [11, 22, 33]);
});

test("addInPlaceF32 — throws on shape mismatch", () => {
  const a = new Float32Array(3);
  const b = new Float32Array(4);
  assert.throws(() => addInPlaceF32(a, b), /a\.length=3 != b\.length=4/);
});

test("scaleInPlaceF32 — multiply by scalar", () => {
  const a = new Float32Array([1, 2, 3]);
  scaleInPlaceF32(a, 0.5);
  assert.deepEqual(Array.from(a), [0.5, 1.0, 1.5]);
});

test("scaleInPlaceF32 — 1/sqrt(head_dim) attention scaling factor", () => {
  // BitNet 2B head_dim = 64 → 1/sqrt(64) = 0.125
  const a = new Float32Array([8, 16, 24]);
  scaleInPlaceF32(a, 1 / Math.sqrt(64));
  assert.deepEqual(Array.from(a), [1, 2, 3]);
});
