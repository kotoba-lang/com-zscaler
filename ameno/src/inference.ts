/**
 * @etzhayyim/ameno/inference — Browser-side LLM runtime built on `@huggingface/transformers`.
 *
 * Authoritative for the transformers.js / ONNX kernel path in ameno
 * (svelte/src/lib/inference.ts is a re-export). The MediaPipe LiteRT kernel
 * path lives in the svelte appview at lib/mediapipe-runtime.ts so that the
 * `@mediapipe/tasks-genai` dependency doesn't reach this package.
 *
 * Authoritative ADRs:
 *   90-docs/adr/2605182312-local-bring-up-murakumo-gemma4.md
 *   90-docs/adr/2605190824-ameno-mediapipe-llm-browser-runtime.md
 */
import {
  AutoTokenizer,
  AutoModelForCausalLM,
  TextStreamer,
  type PreTrainedTokenizer,
  type PreTrainedModel,
} from "@huggingface/transformers";

/**
 * Inference device identifier passed to transformers.js.
 *
 * `'webnn'` is the NPU fast path added by ADR-2605252100 — the
 * browser maps it to CoreML / DirectML / NNAPI / QNN delegate
 * depending on the platform. Detection + backend routing live in
 * `./inference/webnn.ts`; this union extension is the only change to
 * the existing API surface. `'webgpu'` remains the universal
 * fallback (Safari has no WebNN signal as of 2026-05-25).
 */
export type InferenceDevice = "webnn" | "webgpu" | "wasm" | "cpu";

/** dtypes accepted by transformers.js v3. */
export type InferenceDtype =
  | "auto"
  | "fp32"
  | "fp16"
  | "q8"
  | "int8"
  | "uint8"
  | "q4"
  | "bnb4"
  | "q4f16";

export interface ModelMeta {
  id: string;
  displayName: string;
  /** HF repo id used by transformers.js (`onnx-community/...`). */
  hfModel: string;
  defaultDevice: InferenceDevice;
  /** Default dtype passed to `AutoModelForCausalLM.from_pretrained`. */
  dtype: InferenceDtype;
}

/** Engine model registry. The Worker-side MODEL_CATALOG mirrors these ids. */
export const MODELS: Record<string, ModelMeta> = {
  "gemma-4-e2b-it": {
    id: "gemma-4-e2b-it",
    displayName: "Gemma 4 E2B (Instruct)",
    hfModel: "onnx-community/gemma-4-E2B-it-ONNX",
    defaultDevice: "webgpu",
    dtype: "q4f16",
  },
  "gemma-4-e4b-it": {
    id: "gemma-4-e4b-it",
    displayName: "Gemma 4 E4B (Instruct)",
    hfModel: "onnx-community/gemma-4-E4B-it-ONNX",
    defaultDevice: "webgpu",
    dtype: "q4f16",
  },
  "baien-bitnet-2b": {
    id: "baien-bitnet-2b",
    displayName: "Baien (BitNet b1.58 2B)",
    hfModel: "onnx-community/bitnet-b1.58-2B-4T-bf16-ONNX",
    defaultDevice: "wasm",
    // The HF repo ships bf16; transformers.js v3 accepts "fp16" / "q4"
    // for ONNX-decoded BitNet at runtime. bf16-as-fp16 is the closest
    // truthful default. The ternary kernel path will diverge in a follow-up.
    dtype: "fp16",
  },
};

export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

export interface GenerationStats {
  durationMs: number;
  totalTokens: number;
  tokensPerSecond: number;
  /** True when a RAG context block was prepended for this generation. */
  ragActive: boolean;
}

export interface InferenceState {
  status: "idle" | "loading" | "ready" | "generating" | "error";
  progress: number;
  error: string | null;
  loadedModel: string | null;
  webgpuAvailable: boolean;
  /** Adapter ids that are conceptually active (UI-tracked; merge into
   *  weights is a separate large change — see ADR-2605190824). */
  activeAdapters: string[];
  actorDid: string | null;
}

interface LoadedSession {
  modelId: string;
  device: InferenceDevice;
  tokenizer: PreTrainedTokenizer;
  model: PreTrainedModel;
}

let session: LoadedSession | null = null;
let activeAdapters: string[] = [];

/** Detect WebGPU adapter availability. Safe to call multiple times. */
export async function checkWebGPU(): Promise<boolean> {
  const navAny = navigator as Navigator & { gpu?: GPU };
  if (!navAny.gpu) return false;
  try {
    const adapter = await navAny.gpu.requestAdapter();
    return adapter !== null;
  } catch {
    return false;
  }
}

/** Human-readable GPU info string for the landing card. */
export async function getGPUInfo(): Promise<string> {
  const navAny = navigator as Navigator & { gpu?: GPU };
  if (!navAny.gpu) return "WebGPU unavailable";
  try {
    const adapter = await navAny.gpu.requestAdapter();
    if (!adapter) return "No WebGPU adapter";
    const info = (adapter as GPUAdapter & { info?: GPUAdapterInfo }).info;
    const vendor = info?.vendor ?? "unknown";
    const arch = info?.architecture ?? info?.device ?? "";
    return arch ? `${vendor} · ${arch}` : vendor;
  } catch (e) {
    return e instanceof Error ? e.message : "GPU info unavailable";
  }
}

/**
 * Load a model into a long-lived `session`. Reuses the session when the same
 * (modelId, device) tuple is requested. Progress is reported coarsely because
 * transformers.js's progress callback fires per-file and we want a
 * monotonically-increasing 0..100 for the UI.
 */
export async function loadModel(
  onProgress: (pct: number) => void,
  modelId: string,
  deviceOverride?: InferenceDevice | null,
): Promise<void> {
  const meta = MODELS[modelId];
  if (!meta) throw new Error(`Unknown model: ${modelId}`);
  const device = deviceOverride ?? meta.defaultDevice;
  if (device === "webnn") {
    // ADR-2605252100 R0: the WebNN inference path is contract-only
    // until R1 wires the ONNX Runtime Web WebNN EP. Detection
    // (`detectWebnnSupport`) + backend selection (`selectInferenceBackend`)
    // are usable today; actual dispatch is R1. Mirrors the
    // train/kernels.ts dispatch-wrapper pattern.
    throw new Error(
      "loadModel: device='webnn' is R0 contract-only. Wire `onnxruntime-web/webnn` EP in R1 (see ADR-2605252100 §3). Use 'webgpu' for now.",
    );
  }
  if (session?.modelId === modelId && session.device === device) {
    onProgress(100);
    return;
  }

  let lastPct = 0;
  const reportProgress = (info: unknown): void => {
    const o = info as { status?: string; progress?: number; loaded?: number; total?: number };
    if (o?.status === "progress" && typeof o.progress === "number") {
      const pct = Math.min(99, Math.max(lastPct, Math.round(o.progress)));
      lastPct = pct;
      onProgress(pct);
    } else if (o?.status === "ready") {
      onProgress(100);
    }
  };

  onProgress(1);
  const tokenizer = await AutoTokenizer.from_pretrained(meta.hfModel, {
    progress_callback: reportProgress,
  });
  const model = await AutoModelForCausalLM.from_pretrained(meta.hfModel, {
    dtype: meta.dtype,
    device,
    progress_callback: reportProgress,
  });

  session = { modelId, device, tokenizer, model };
  onProgress(100);
}

/** Replace the conceptually-active LoRA adapter id list (UI state only). */
export function setLoraAdapters(ids: string[]): void {
  activeAdapters = [...ids];
}

export function getActiveAdapterIds(): string[] {
  return [...activeAdapters];
}

/**
 * Streaming chat generation. The token callback fires once per decoded chunk
 * emitted by `TextStreamer` — chunks may be multi-character on q4 models, but
 * the consumer just concatenates so granularity is invisible.
 */
export async function generate(
  messages: ChatMessage[],
  onToken: (token: string) => void,
): Promise<GenerationStats> {
  if (!session) throw new Error("model not loaded");
  const { tokenizer, model } = session;

  const inputs = tokenizer.apply_chat_template(messages, {
    add_generation_prompt: true,
    return_dict: true,
  }) as { input_ids: unknown; attention_mask?: unknown };

  let tokenCount = 0;
  const streamer = new TextStreamer(tokenizer, {
    skip_prompt: true,
    skip_special_tokens: true,
    callback_function: (text: string) => {
      if (text) {
        tokenCount++;
        onToken(text);
      }
    },
  });

  const started = performance.now();
  await (model as unknown as {
    generate: (opts: Record<string, unknown>) => Promise<unknown>;
  }).generate({
    ...inputs,
    max_new_tokens: 1024,
    do_sample: true,
    top_k: 50,
    temperature: 0.7,
    streamer,
  });
  const durationMs = performance.now() - started;

  const tokensPerSecond = tokenCount > 0 ? (tokenCount * 1000) / durationMs : 0;
  return {
    durationMs,
    totalTokens: tokenCount,
    tokensPerSecond,
    ragActive: false,
  };
}
