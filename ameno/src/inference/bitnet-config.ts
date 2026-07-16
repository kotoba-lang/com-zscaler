/**
 * @etzhayyim/ameno/inference/bitnet-config — HF config.json reader +
 * typed BitNet model configuration (ADR-2605263800 R1b commit 5 in the
 * pivoted chain).
 *
 * The forward-override runtime needs to know the model's architecture
 * parameters at load time:
 *   - hidden dim / num heads / num KV heads → buffer sizes
 *   - num layers → loop count
 *   - intermediate size → FFN dim
 *   - vocab size → embedding + lm_head shapes
 *   - rope_theta + max position → RoPE precompute
 *   - rms_norm_eps → numerical stability
 *
 * The HF transformers BitNet model ships a standard config.json with
 * these fields. This module:
 *
 *   1. Parses the raw JSON.
 *   2. Validates required fields exist + have the right type.
 *   3. Returns a frozen `BitNetConfig` object.
 *   4. Optionally asserts the values match the canonical BitNet 2B
 *      4T constants (so we catch model-swap bugs early).
 *
 * This is pure-TS validation; no HF transformers.js dependency.
 * Tests inject a fixture JSON string.
 */

/**
 * Typed BitNet model config. All fields required (no optional;
 * the validator throws on missing fields instead of producing
 * a partial config).
 *
 * Field names match the HF transformers convention (snake_case).
 * `headDim` is the derived `hidden_size / num_attention_heads`;
 * it's not directly in config.json but is computed here.
 */
export interface BitNetConfig {
  /** Embedding + attention output dimension. BitNet 2B = 2048. */
  readonly hidden_size: number;
  /** Number of transformer blocks. BitNet 2B = 30. */
  readonly num_hidden_layers: number;
  /** Number of attention heads. BitNet 2B = 32. */
  readonly num_attention_heads: number;
  /**
   * Number of key/value heads (GQA — grouped-query attention).
   * BitNet 2B uses 8 KV heads with 32 query heads → 4-to-1 sharing.
   */
  readonly num_key_value_heads: number;
  /** Per-head dimension. Derived: `hidden_size / num_attention_heads`. */
  readonly head_dim: number;
  /** FFN inner dimension. BitNet 2B = 5632 (or similar; let's not hard-pin). */
  readonly intermediate_size: number;
  /** Tokenizer vocab size. BitNet 2B = 128_256 (LLaMA-3 tokenizer). */
  readonly vocab_size: number;
  /** Max sequence length the model was trained on. BitNet 2B = 4096. */
  readonly max_position_embeddings: number;
  /** RoPE base frequency. BitNet 2B = 500_000 (LLaMA-3 convention). */
  readonly rope_theta: number;
  /** RMSNorm epsilon. BitNet 2B = 1e-5. */
  readonly rms_norm_eps: number;
  /** Whether the lm_head weight is tied to the embedding weight. */
  readonly tie_word_embeddings: boolean;
}

/**
 * Canonical BitNet 2B 4T constants — used by `assertCanonicalBitNet2b`
 * to sanity-check that we loaded the right model. Numbers reflect
 * `microsoft/bitnet-b1.58-2B-4T-bf16-ONNX` HuggingFace metadata as of
 * 2026-05-26. Future BitNet variants override via a different
 * canonical block.
 */
export const BITNET_2B_4T_CANONICAL = {
  hidden_size: 2048,
  num_hidden_layers: 30,
  num_attention_heads: 32,
  num_key_value_heads: 8,
  intermediate_size: 5632,
  vocab_size: 128_256,
  max_position_embeddings: 4096,
  rope_theta: 500_000,
  rms_norm_eps: 1e-5,
  tie_word_embeddings: false,
} as const;

/** Subset of fields we type-check. */
const REQUIRED_NUMERIC_FIELDS = [
  "hidden_size",
  "num_hidden_layers",
  "num_attention_heads",
  "num_key_value_heads",
  "intermediate_size",
  "vocab_size",
  "max_position_embeddings",
  "rope_theta",
  "rms_norm_eps",
] as const;

const REQUIRED_BOOL_FIELDS = ["tie_word_embeddings"] as const;

/**
 * Parse + validate an HF config.json. Accepts either a JSON string
 * or a pre-parsed object (the latter is what `transformers.js` exposes
 * via `model.config`).
 *
 * Throws on:
 *   - Invalid JSON.
 *   - Missing required field.
 *   - Wrong field type.
 *   - `hidden_size % num_attention_heads !== 0` (head_dim must be
 *     an integer).
 *
 * Returns a frozen `BitNetConfig` with the derived `head_dim`.
 */
export function parseBitNetConfig(input: string | object): BitNetConfig {
  let raw: Record<string, unknown>;
  if (typeof input === "string") {
    raw = JSON.parse(input) as Record<string, unknown>;
  } else {
    raw = input as Record<string, unknown>;
  }

  for (const field of REQUIRED_NUMERIC_FIELDS) {
    const v = raw[field];
    if (typeof v !== "number" || !Number.isFinite(v)) {
      throw new Error(
        `bitnet-config: missing or non-numeric field '${field}' (got ${typeof v}: ${JSON.stringify(v)})`,
      );
    }
  }
  for (const field of REQUIRED_BOOL_FIELDS) {
    const v = raw[field];
    if (typeof v !== "boolean") {
      throw new Error(
        `bitnet-config: missing or non-boolean field '${field}' (got ${typeof v}: ${JSON.stringify(v)})`,
      );
    }
  }

  const hidden_size = raw["hidden_size"] as number;
  const num_attention_heads = raw["num_attention_heads"] as number;
  if (hidden_size % num_attention_heads !== 0) {
    throw new Error(
      `bitnet-config: hidden_size (${String(hidden_size)}) not divisible by num_attention_heads (${String(num_attention_heads)})`,
    );
  }
  const head_dim = hidden_size / num_attention_heads;

  const config: BitNetConfig = {
    hidden_size,
    num_hidden_layers: raw["num_hidden_layers"] as number,
    num_attention_heads,
    num_key_value_heads: raw["num_key_value_heads"] as number,
    head_dim,
    intermediate_size: raw["intermediate_size"] as number,
    vocab_size: raw["vocab_size"] as number,
    max_position_embeddings: raw["max_position_embeddings"] as number,
    rope_theta: raw["rope_theta"] as number,
    rms_norm_eps: raw["rms_norm_eps"] as number,
    tie_word_embeddings: raw["tie_word_embeddings"] as boolean,
  };
  return Object.freeze(config);
}

/**
 * Assert that the parsed config matches the canonical BitNet 2B 4T
 * constants. Throws with a diff-style message on mismatch — useful as
 * a model-load-time sanity check (catches accidental model swaps).
 *
 * `tolerance` controls float comparisons (default 0 for exact match).
 */
export function assertCanonicalBitNet2b(
  config: BitNetConfig,
  options: { rmsNormEpsTolerance?: number; ropeThetaTolerance?: number } = {},
): void {
  const issues: string[] = [];
  const rmsTol = options.rmsNormEpsTolerance ?? 0;
  const ropeTol = options.ropeThetaTolerance ?? 0;

  for (const [key, canonical] of Object.entries(BITNET_2B_4T_CANONICAL) as [
    keyof typeof BITNET_2B_4T_CANONICAL,
    unknown,
  ][]) {
    const actual = config[key];
    if (key === "rms_norm_eps") {
      if (Math.abs((actual as number) - (canonical as number)) > rmsTol) {
        issues.push(`${key}: expected ${String(canonical)}, got ${String(actual)}`);
      }
      continue;
    }
    if (key === "rope_theta") {
      if (Math.abs((actual as number) - (canonical as number)) > ropeTol) {
        issues.push(`${key}: expected ${String(canonical)}, got ${String(actual)}`);
      }
      continue;
    }
    if (actual !== canonical) {
      issues.push(`${key}: expected ${JSON.stringify(canonical)}, got ${JSON.stringify(actual)}`);
    }
  }

  if (issues.length > 0) {
    throw new Error(
      `bitnet-config: config does not match BitNet 2B 4T canonical:\n  ${issues.join("\n  ")}`,
    );
  }
}

/**
 * Derived buffer sizes used by the forward-override runtime. Computed
 * once at load time so the per-token forward path can read these as
 * cached integers.
 */
export interface BitNetBufferSizes {
  /** Embedding lookup output size = hidden_size. */
  readonly embeddingDim: number;
  /** Q projection output = num_attention_heads × head_dim = hidden_size. */
  readonly qProjOut: number;
  /** K/V projection output = num_key_value_heads × head_dim. */
  readonly kvProjOut: number;
  /** FFN gate/up projection output = intermediate_size. */
  readonly ffnInner: number;
  /** lm_head output = vocab_size. */
  readonly lmHeadOut: number;
  /**
   * Group size for GQA: `num_attention_heads / num_key_value_heads`.
   * BitNet 2B = 4 (each KV head is shared by 4 query heads).
   */
  readonly gqaGroupSize: number;
}

export function computeBufferSizes(config: BitNetConfig): BitNetBufferSizes {
  return Object.freeze({
    embeddingDim: config.hidden_size,
    qProjOut: config.num_attention_heads * config.head_dim,
    kvProjOut: config.num_key_value_heads * config.head_dim,
    ffnInner: config.intermediate_size,
    lmHeadOut: config.vocab_size,
    gqaGroupSize: config.num_attention_heads / config.num_key_value_heads,
  });
}
