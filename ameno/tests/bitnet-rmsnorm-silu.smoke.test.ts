/**
 * Smoke tests for `bitnet-rmsnorm` + `bitnet-silu` (ADR-2605263800
 * R1b commit 8).
 *
 * Run:
 *   node --experimental-strip-types --test tests/bitnet-rmsnorm-silu.smoke.test.ts
 */

import { test } from "node:test";
import assert from "node:assert/strict";

import {
  applyRmsNorm,
  applyRmsNormInPlace,
} from "../src/inference/bitnet-rmsnorm.ts";
import {
  sigmoid,
  silu,
  applySiluElementwise,
  applySwiGluCombine,
} from "../src/inference/bitnet-silu.ts";

// ──────────────────────────────────────────────────────────────
// RMSNorm tests
// ──────────────────────────────────────────────────────────────

test("applyRmsNorm — known result on unit-weight identity input", () => {
  // x = [3, 4] (RMS = sqrt((9+16)/2) = sqrt(12.5) ≈ 3.5355)
  // w = [1, 1] → y = x / RMS
  // Expected: [3/3.5355, 4/3.5355] ≈ [0.8485, 1.1314]
  const x = new Float32Array([3, 4]);
  const w = new Float32Array([1, 1]);
  const out = new Float32Array(2);
  applyRmsNorm(x, w, 0 /* eps */, out);
  const rms = Math.sqrt((9 + 16) / 2);
  assert.ok(Math.abs(out[0]! - 3 / rms) < 1e-5);
  assert.ok(Math.abs(out[1]! - 4 / rms) < 1e-5);
});

test("applyRmsNorm — per-dim weight scales output", () => {
  // x = [2, 2], w = [3, 5], eps = 0.
  // RMS = sqrt(mean(4+4)) = sqrt(4) = 2.
  // y_i = x_i × w_i / RMS = (2*3/2, 2*5/2) = (3, 5).
  const x = new Float32Array([2, 2]);
  const w = new Float32Array([3, 5]);
  const out = new Float32Array(2);
  applyRmsNorm(x, w, 0, out);
  assert.ok(Math.abs(out[0]! - 3) < 1e-5);
  assert.ok(Math.abs(out[1]! - 5) < 1e-5);
});

test("applyRmsNorm — eps prevents div by zero on all-zero input", () => {
  const x = new Float32Array([0, 0, 0, 0]);
  const w = new Float32Array([1, 1, 1, 1]);
  const out = new Float32Array(4);
  applyRmsNorm(x, w, 1e-5, out);
  // sqrt(0 + 1e-5) ≈ 3.16e-3, so y = 0/anything = 0.
  for (let i = 0; i < 4; i++) {
    assert.equal(out[i], 0);
  }
});

test("applyRmsNorm — throws on shape mismatch", () => {
  const x = new Float32Array(4);
  const w = new Float32Array(3); // wrong
  const out = new Float32Array(4);
  assert.throws(() => applyRmsNorm(x, w, 1e-5, out), /x\.length=4 != w\.length=3/);
});

test("applyRmsNormInPlace — mutates the input buffer", () => {
  const x = new Float32Array([3, 4]);
  const w = new Float32Array([1, 1]);
  applyRmsNormInPlace(x, w, 0);
  const rms = Math.sqrt((9 + 16) / 2);
  assert.ok(Math.abs(x[0]! - 3 / rms) < 1e-5);
  assert.ok(Math.abs(x[1]! - 4 / rms) < 1e-5);
});

test("applyRmsNorm — BitNet 2B sized buffer (2048-dim) completes <1ms", () => {
  const d = 2048;
  const x = new Float32Array(d);
  const w = new Float32Array(d);
  const out = new Float32Array(d);
  for (let i = 0; i < d; i++) {
    x[i] = Math.sin(i * 0.001);
    w[i] = 1.0;
  }
  const start = performance.now();
  applyRmsNorm(x, w, 1e-5, out);
  const elapsed = performance.now() - start;
  assert.ok(elapsed < 1, `2048-dim RMSNorm took ${elapsed}ms (expected <1)`);
  // After normalization: per-element |y_i| ~= |sin(i*0.001)| / RMS.
  // We just check that the output is non-zero somewhere.
  let nonZero = 0;
  for (let i = 0; i < d; i++) if (out[i] !== 0) nonZero++;
  assert.ok(nonZero > d / 2);
});

// ──────────────────────────────────────────────────────────────
// SiLU tests
// ──────────────────────────────────────────────────────────────

test("sigmoid — known values", () => {
  assert.ok(Math.abs(sigmoid(0) - 0.5) < 1e-6);
  assert.ok(Math.abs(sigmoid(1) - 0.7310585786) < 1e-5);
  assert.ok(Math.abs(sigmoid(-1) - 0.2689414214) < 1e-5);
});

test("sigmoid — extreme values don't overflow", () => {
  // Large positive: σ(100) ≈ 1.
  assert.ok(Math.abs(sigmoid(100) - 1) < 1e-12);
  // Large negative: σ(-100) ≈ 0.
  assert.ok(sigmoid(-100) < 1e-30);
  // Both branches finite.
  assert.ok(Number.isFinite(sigmoid(1000)));
  assert.ok(Number.isFinite(sigmoid(-1000)));
});

test("silu — known values", () => {
  // silu(0) = 0 × σ(0) = 0 × 0.5 = 0.
  assert.equal(silu(0), 0);
  // silu(1) = 1 × σ(1) ≈ 0.7311.
  assert.ok(Math.abs(silu(1) - 0.7310585786) < 1e-5);
  // silu(-1) = -1 × σ(-1) ≈ -0.2689.
  assert.ok(Math.abs(silu(-1) + 0.2689414214) < 1e-5);
});

test("silu — minimum near z = -1.278", () => {
  // The SiLU minimum is at z ≈ -1.2785, silu ≈ -0.2785.
  // We check that silu(-1.2785) is close to that.
  const minVal = silu(-1.2785);
  assert.ok(
    Math.abs(minVal - -0.2784645) < 1e-3,
    `silu(-1.2785) = ${minVal} ≈ -0.2785`,
  );
});

test("silu — asymptotes to z for large positive", () => {
  // silu(z) → z as z → +∞.
  const z = 20;
  assert.ok(Math.abs(silu(z) - z) < 1e-6);
});

test("applySiluElementwise — produces correct values", () => {
  const x = new Float32Array([-2, -1, 0, 1, 2]);
  const out = new Float32Array(5);
  applySiluElementwise(x, out);
  for (let i = 0; i < 5; i++) {
    const expected = silu(x[i]!);
    assert.ok(
      Math.abs(out[i]! - expected) < 1e-5,
      `i=${i}: silu(${x[i]}) = ${out[i]}, expected ${expected}`,
    );
  }
});

test("applySiluElementwise — in-place (out === x) OK", () => {
  const x = new Float32Array([1, 2, 3]);
  applySiluElementwise(x, x);
  for (let i = 0; i < 3; i++) {
    // Each x[i] is now silu of original x[i] = (i+1).
    const expected = silu(i + 1);
    assert.ok(
      Math.abs(x[i]! - expected) < 1e-5,
      `i=${i}: ${x[i]} ≈ silu(${i + 1}) = ${expected}`,
    );
  }
});

test("applySiluElementwise — throws on shape mismatch", () => {
  const x = new Float32Array(4);
  const out = new Float32Array(3);
  assert.throws(() => applySiluElementwise(x, out), /shapes disagree|x\.length/);
});

test("applySwiGluCombine — silu(gate) × up", () => {
  // SwiGLU(gate, up) = silu(gate) ⊙ up
  const gate = new Float32Array([1, -1, 2, 0]);
  const up = new Float32Array([10, 10, 10, 10]);
  const out = new Float32Array(4);
  applySwiGluCombine(gate, up, out);
  for (let i = 0; i < 4; i++) {
    const expected = silu(gate[i]!) * up[i]!;
    assert.ok(
      Math.abs(out[i]! - expected) < 1e-5,
      `i=${i}: ${out[i]} vs ${expected}`,
    );
  }
});

test("applySwiGluCombine — throws on shape disagreement", () => {
  const gate = new Float32Array(4);
  const up = new Float32Array(3);
  const out = new Float32Array(4);
  assert.throws(() => applySwiGluCombine(gate, up, out), /shapes disagree/);
});

test("applySwiGluCombine — gate == 0 → out = 0", () => {
  // silu(0) = 0, so silu(0) × anything = 0.
  const gate = new Float32Array([0, 0, 0]);
  const up = new Float32Array([100, 100, 100]);
  const out = new Float32Array(3);
  applySwiGluCombine(gate, up, out);
  for (let i = 0; i < 3; i++) {
    assert.equal(out[i], 0);
  }
});
