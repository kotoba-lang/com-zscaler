/**
 * @etzhayyim/ameno/inference/kernels — Barrel for the BitLinear
 * per-kernel inference path (ADR-2605263300).
 *
 * Public surface:
 *   - WGSL_BITLINEAR_FORWARD / WGSL_BITNET_PACKED_DEQUANT — shader sources
 *   - BitLinearForwardParams / BitnetPackedDequantParams  — Params layouts
 *   - dispatchBitLinearForward / dispatchPackedDequant    — R1a-throws wrappers
 *   - I2S_WEIGHTS_PER_BYTE / I2S_BITS_PER_WEIGHT / I2S_WEIGHTS_PER_U32 — layout constants (gate G3)
 *   - BitnetBackend / BitnetBackendProbe / ProbeOptions   — selection types
 *   - probeBitnetBackend / hasWasmSimd                    — runtime probes
 */

export {
  WGSL_BITLINEAR_FORWARD,
  I2S_WEIGHTS_PER_BYTE,
  I2S_BITS_PER_WEIGHT,
  I2S_WEIGHTS_PER_U32,
  dispatchBitLinearForward,
} from "./bitlinear-forward";
export type { BitLinearForwardParams } from "./bitlinear-forward";

export {
  WGSL_BITNET_PACKED_DEQUANT,
  dispatchPackedDequant,
} from "./bitnet-packed-dequant";
export type { BitnetPackedDequantParams } from "./bitnet-packed-dequant";

export {
  probeBitnetBackend,
  hasWasmSimd,
} from "./dispatch";
export type {
  BitnetBackend,
  BitnetBackendProbe,
  ProbeOptions,
} from "./dispatch";
