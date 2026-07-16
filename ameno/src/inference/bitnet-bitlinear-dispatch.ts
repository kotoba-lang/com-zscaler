/**
 * @etzhayyim/ameno/inference/bitnet-bitlinear-dispatch — BitLinear
 * fp32 fallback dispatch (ADR-2605263800 R1b commit 10).
 *
 * Computes `y = x · dequant(W_packed, W_scale)` by dequantizing the
 * i2_s weight one row at a time and accumulating in fp32. This is
 * the **fp16-fallback path** referenced by the R0 backend selection
 * ladder — used until R1c wires the wgpu/wasm dispatch through the
 * BitLinear kernel.
 *
 * ## Algorithm
 *
 * BitNet's `nn.Linear` weight is stored as `[out_features × in_features]`.
 * For a 1D input `x` of length `in_features`, the output is:
 *
 *     y[n] = (Σ_k x[k] × W_ternary[n, k]) × W_scale[n]
 *
 * with `W_ternary[n, k] ∈ {-1, 0, +1}` unpacked from `W_packed`. The
 * packed format is 4 weights per byte, little-endian within byte
 * (`00 → 0`, `01 → +1`, `10/11 → -1`); matches `bitnet-graph-patcher`'s
 * `BITNET_TRUNK_PROJ_PATTERN` convention and the WGSL shader's
 * `unpack_i2s` function (gate G3).
 *
 * ## When to use which dispatch
 *
 *   - **`applyBitLinearFp32Fallback`** (this module): pure-TS reference.
 *     Used by R1b commits 10-14 until the layer-replacement bridge
 *     is wired. Also the test oracle for the wgpu/wasm kernels.
 *   - **`dispatchBitLinearForward`** (`kernels/bitlinear-forward.ts`):
 *     WGSL kernel via wgpu adapter; R1c+ when the wgpu autograd
 *     bridge can hand off Q8 activation tensors.
 *   - **`BitnetWasmModule.ggmlBitnetMulMatTaskCompute`**: WASM SIMD
 *     scalar/SIMD path; R1c+ when the wasm-bindgen pointer
 *     marshalling lands.
 *
 * All three impls share the same numerical contract (gate G4: ±1 ULP
 * fp16). The fp16 quantization step happens at the caller, not in this
 * module — we emit fp32 and let the caller down-cast.
 */

import type { BitLinearWeightPack } from "./bitnet-weight-pack.ts";

/** Convert fp16 bit pattern (uint16) to fp32. Mirrors `bitnet-kv-cache`. */
function f16BitsToF32(bits: number): number {
  const sign = (bits & 0x8000) << 16;
  const expRaw = (bits >>> 10) & 0x1f;
  const mantRaw = bits & 0x3ff;
  let bits32: number;
  if (expRaw === 0) {
    if (mantRaw === 0) {
      bits32 = sign;
    } else {
      let exp = -14;
      let mant = mantRaw;
      while ((mant & 0x400) === 0) {
        mant <<= 1;
        exp--;
      }
      mant &= 0x3ff;
      bits32 = sign | ((exp + 127) << 23) | (mant << 13);
    }
  } else if (expRaw === 0x1f) {
    bits32 = sign | 0x7f800000 | (mantRaw << 13);
  } else {
    bits32 = sign | ((expRaw - 15 + 127) << 23) | (mantRaw << 13);
  }
  const buf = new ArrayBuffer(4);
  new Uint32Array(buf)[0] = bits32;
  return new Float32Array(buf)[0]!;
}

/**
 * Unpack one i2_s weight from a byte. `slot ∈ [0, 4)`. Returns
 * `-1`, `0`, or `+1` as a signed integer.
 *
 * Inline helper; the inner loop calls this `inFeatures` times per
 * output row, so we keep it small and branch-free.
 */
function unpackI2s(byte: number, slot: number): number {
  const bits = (byte >>> (slot * 2)) & 0b11;
  // 00 → 0, 01 → +1, 10/11 → -1.
  return bits === 0 ? 0 : bits === 1 ? 1 : -1;
}

/**
 * Apply a BitLinear projection: `y = x · dequant(W_packed, W_scale)`.
 *
 * - `x`: input activation, length `pack.dims[1]` (= `in_features`).
 * - `pack`: weight pack from `extractBitLinearWeightPack`. The pack's
 *   `dims = [out_features, in_features]`.
 * - `out`: output buffer, length `pack.dims[0]` (= `out_features`).
 *   Caller-allocated; may be the same buffer as `x` if and only if
 *   `out_features === in_features` AND the caller is OK with
 *   in-place mutation (the inner loop writes `out[n]` only after
 *   reading all `x[k]` — but since `x === out` aliases the read,
 *   the read must be cached before write; we DO cache via the
 *   inner accumulator, so aliasing IS safe).
 *
 * Throws on shape mismatch.
 */
export function applyBitLinearFp32Fallback(
  x: Float32Array,
  pack: BitLinearWeightPack,
  out: Float32Array,
): void {
  const [outFeatures, inFeatures] = pack.dims;
  if (x.length !== inFeatures) {
    throw new Error(
      `applyBitLinearFp32Fallback: x.length=${String(x.length)} != in_features=${String(inFeatures)} (${pack.origName})`,
    );
  }
  if (out.length !== outFeatures) {
    throw new Error(
      `applyBitLinearFp32Fallback: out.length=${String(out.length)} != out_features=${String(outFeatures)} (${pack.origName})`,
    );
  }
  // Snapshot `x` if `out === x` so the in-place case doesn't read
  // freshly-written output bytes. Cheap: one Float32Array copy per
  // call. For the non-aliased case (the typical path), we skip
  // the copy.
  let xRead: Float32Array;
  if (out === x) {
    xRead = new Float32Array(x);
  } else {
    xRead = x;
  }

  const packedColsBytes = Math.ceil(inFeatures / 4);
  const scaleDv = new DataView(
    pack.scale.buffer,
    pack.scale.byteOffset,
    pack.scale.byteLength,
  );

  for (let n = 0; n < outFeatures; n++) {
    const rowScale = f16BitsToF32(scaleDv.getUint16(n * 2, true));
    let acc = 0;
    const rowBase = n * packedColsBytes;
    for (let k = 0; k < inFeatures; k++) {
      const byteIdx = rowBase + (k >>> 2);
      const slot = k & 0b11;
      const w = unpackI2s(pack.packed[byteIdx]!, slot);
      // `w === 0` is the common case for sparse ternary weights;
      // a branch lets the JIT skip the multiply.
      if (w !== 0) {
        acc += xRead[k]! * w;
      }
    }
    out[n] = acc * rowScale;
  }
}
