/**
 * @etzhayyim/ameno/train/kernels — WGSL shader sources for the LoRA
 * forward, LoRA backward, and bias-corrected Adam-step kernels
 * (ADR-2605242630 §7 R1 implementation task list).
 *
 * R1a ships:
 *   - `WGSL_LORA_FORWARD`  — workgroup-tiled fp16 LoRA forward
 *   - `WGSL_LORA_BACKWARD` — two entry points (`main_dA`, `main_dB`)
 *   - `WGSL_ADAM_STEP`     — bias-corrected Adam with numerically-stable
 *                            `exp2(t * log2(beta))` instead of `pow(beta, t)`
 *                            (mobile fp16 pow has notorious precision issues).
 *
 * R1b plugs in:
 *   - `dispatchLoraForward`  — bind groups, layouts, GPU pipeline setup
 *   - `dispatchLoraBackward` — chain through transformers.js layer
 *                              replacement OR a tfjs-webgpu autograd
 *                              bridge so the trunk's forward graph is
 *                              reachable from our LoRA backward.
 *   - `dispatchAdamStep`     — momentum + parameter binding glue.
 *
 * Until R1b lands, the dispatch wrappers throw with a clear marker
 * pointing to the autograd bridge work. R1a code (selection,
 * orchestration, eval, OPFS, scanner, shard) is callable today.
 */

/**
 * LoRA forward pass: y = x · (A · B) · (α / r) + residual
 *
 * Bindings:
 *   @group(0) @binding(0)  storage<read>     X            — input activations  [M × K]
 *   @group(0) @binding(1)  storage<read>     A            — LoRA down-projection [K × R]
 *   @group(0) @binding(2)  storage<read>     B            — LoRA up-projection   [R × N]
 *   @group(0) @binding(3)  storage<read_write> Y          — output [M × N] (accumulates residual)
 *   @group(0) @binding(4)  uniform           Params       — { M, K, N, R, alphaOverR }
 *
 * Workgroup size: 16×16 — fits comfortably under Apple A14 + Adreno 650
 * subgroup ceilings. The kernel computes one output tile per workgroup
 * via a two-stage matmul (X · A → tmp[R], then tmp · B → Y).
 */
export const WGSL_LORA_FORWARD = /* wgsl */ `
struct Params {
  M: u32,
  K: u32,
  N: u32,
  R: u32,
  alphaOverR: f32,
};

@group(0) @binding(0) var<storage, read>          X     : array<f32>;
@group(0) @binding(1) var<storage, read>          A     : array<f32>;
@group(0) @binding(2) var<storage, read>          B     : array<f32>;
@group(0) @binding(3) var<storage, read_write>    Y     : array<f32>;
@group(0) @binding(4) var<uniform>                P     : Params;

@compute @workgroup_size(16, 16, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let m = gid.x;
  let n = gid.y;
  if (m >= P.M || n >= P.N) {
    return;
  }
  // Two-stage matmul. R is small (typically 16 for the rank-16 default).
  var acc: f32 = 0.0;
  for (var r: u32 = 0u; r < P.R; r = r + 1u) {
    var tmp: f32 = 0.0;
    for (var k: u32 = 0u; k < P.K; k = k + 1u) {
      tmp = tmp + X[m * P.K + k] * A[k * P.R + r];
    }
    acc = acc + tmp * B[r * P.N + n];
  }
  // Residual is already in Y; the LoRA delta is scaled by α/r.
  Y[m * P.N + n] = Y[m * P.N + n] + acc * P.alphaOverR;
}
`;

/**
 * LoRA backward pass — two entry points.
 *
 * Entry `main_dA` computes:
 *   dA = X^T · (dY · B^T) · (α / r)
 *
 * Entry `main_dB` computes:
 *   dB = (X · A)^T · dY · (α / r)
 *
 * Backward through the trunk is NOT computed — that's R1b's
 * responsibility (the trunk is frozen per G1; chaining dY into the
 * LoRA layer is enough). The shared bindings cover both entries; the
 * orchestration layer binds the right output buffer per entry.
 *
 * Bindings:
 *   @group(0) @binding(0)  X       — input activations  [M × K]
 *   @group(0) @binding(1)  A       — LoRA down-projection [K × R]
 *   @group(0) @binding(2)  B       — LoRA up-projection   [R × N]
 *   @group(0) @binding(3)  dY      — upstream gradient   [M × N]
 *   @group(0) @binding(4)  dA_out  — output dA           [K × R]  (read_write — only written by main_dA)
 *   @group(0) @binding(5)  dB_out  — output dB           [R × N]  (read_write — only written by main_dB)
 *   @group(0) @binding(6)  Params  — { M, K, N, R, alphaOverR }
 */
export const WGSL_LORA_BACKWARD = /* wgsl */ `
struct Params {
  M: u32,
  K: u32,
  N: u32,
  R: u32,
  alphaOverR: f32,
};

@group(0) @binding(0) var<storage, read>          X      : array<f32>;
@group(0) @binding(1) var<storage, read>          A      : array<f32>;
@group(0) @binding(2) var<storage, read>          B      : array<f32>;
@group(0) @binding(3) var<storage, read>          dY     : array<f32>;
@group(0) @binding(4) var<storage, read_write>    dA_out : array<f32>;
@group(0) @binding(5) var<storage, read_write>    dB_out : array<f32>;
@group(0) @binding(6) var<uniform>                P      : Params;

// dA[k, r] = sum_{m, n} X[m, k] · dY[m, n] · B[r, n] · (alpha / r)
@compute @workgroup_size(16, 16, 1)
fn main_dA(@builtin(global_invocation_id) gid: vec3<u32>) {
  let k = gid.x;
  let r = gid.y;
  if (k >= P.K || r >= P.R) {
    return;
  }
  var acc: f32 = 0.0;
  for (var m: u32 = 0u; m < P.M; m = m + 1u) {
    var inner: f32 = 0.0;
    for (var n: u32 = 0u; n < P.N; n = n + 1u) {
      inner = inner + dY[m * P.N + n] * B[r * P.N + n];
    }
    acc = acc + X[m * P.K + k] * inner;
  }
  dA_out[k * P.R + r] = acc * P.alphaOverR;
}

// dB[r, n] = sum_{m, k} X[m, k] · A[k, r] · dY[m, n] · (alpha / r)
@compute @workgroup_size(16, 16, 1)
fn main_dB(@builtin(global_invocation_id) gid: vec3<u32>) {
  let r = gid.x;
  let n = gid.y;
  if (r >= P.R || n >= P.N) {
    return;
  }
  var acc: f32 = 0.0;
  for (var m: u32 = 0u; m < P.M; m = m + 1u) {
    var inner: f32 = 0.0;
    for (var k: u32 = 0u; k < P.K; k = k + 1u) {
      inner = inner + X[m * P.K + k] * A[k * P.R + r];
    }
    acc = acc + inner * dY[m * P.N + n];
  }
  dB_out[r * P.N + n] = acc * P.alphaOverR;
}
`;

/**
 * Bias-corrected Adam step.
 *
 *   m_t   = β1 · m_{t-1} + (1 - β1) · g
 *   v_t   = β2 · v_{t-1} + (1 - β2) · g²
 *   m_hat = m_t / (1 - β1^t)
 *   v_hat = v_t / (1 - β2^t)
 *   θ_t   = θ_{t-1} - lr · m_hat / (sqrt(v_hat) + eps)
 *
 * The bias-correction powers (`β1^t`, `β2^t`) are computed via
 *   `exp2(t * log2(β))`
 * to avoid the precision loss `pow(β, t)` suffers on mobile fp16 GPUs.
 * For β ∈ (0, 1) and t ≥ 1 we have log2(β) < 0, and `exp2` is one of
 * the most precise mobile intrinsics — Apple A14 + Adreno 650 both
 * advertise IEEE-754 fp32 conformance for `exp2`.
 *
 * Bindings:
 *   @group(0) @binding(0)  Θ      — parameters         [P]
 *   @group(0) @binding(1)  G      — gradients          [P]
 *   @group(0) @binding(2)  M      — first moments      [P]
 *   @group(0) @binding(3)  V      — second moments     [P]
 *   @group(0) @binding(4)  Hp     — Adam hyperparams { lr, beta1, beta2, eps, t, paramCount }
 */
export const WGSL_ADAM_STEP = /* wgsl */ `
struct AdamHp {
  lr      : f32,
  beta1   : f32,
  beta2   : f32,
  eps     : f32,
  t       : f32,
  paramCount : u32,
};

@group(0) @binding(0) var<storage, read_write>  THETA : array<f32>;
@group(0) @binding(1) var<storage, read>        G     : array<f32>;
@group(0) @binding(2) var<storage, read_write>  M     : array<f32>;
@group(0) @binding(3) var<storage, read_write>  V     : array<f32>;
@group(0) @binding(4) var<uniform>              Hp    : AdamHp;

@compute @workgroup_size(64, 1, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= Hp.paramCount) {
    return;
  }
  let g = G[i];

  let m_t = Hp.beta1 * M[i] + (1.0 - Hp.beta1) * g;
  let v_t = Hp.beta2 * V[i] + (1.0 - Hp.beta2) * g * g;

  // Bias correction via exp2/log2 (stable on mobile fp16).
  let bc1 = 1.0 - exp2(Hp.t * log2(Hp.beta1));
  let bc2 = 1.0 - exp2(Hp.t * log2(Hp.beta2));

  let m_hat = m_t / bc1;
  let v_hat = v_t / bc2;

  THETA[i] = THETA[i] - Hp.lr * m_hat / (sqrt(v_hat) + Hp.eps);

  M[i] = m_t;
  V[i] = v_t;
}
`;

// ── Dispatch wrappers (R1b — see top-of-file note) ────────────────

/**
 * Dispatch the LoRA forward kernel. Sets up bind groups, encodes the
 * compute pass, and submits to the device queue.
 *
 * R1b: requires transformers.js layer-replacement OR tfjs-webgpu
 * autograd bridge — until then the residual + trunk activations are
 * not reachable from this dispatch (the trunk owns the activations).
 */
export async function dispatchLoraForward(
  ..._args: unknown[]
): Promise<void> {
  throw new Error(
    "dispatchLoraForward: R1b — requires transformers.js layer-replacement OR tfjs-webgpu autograd bridge. WGSL_LORA_FORWARD is ready; only the bind-group + pipeline setup is missing.",
  );
}

/**
 * Dispatch the LoRA backward kernel (both `main_dA` and `main_dB`
 * entry points). Returns the L2 norm of the produced delta.
 *
 * R1b: requires transformers.js layer-replacement OR tfjs-webgpu
 * autograd bridge — the upstream `dY` from the trunk's backward graph
 * has to chain into this kernel's input binding.
 */
export async function dispatchLoraBackward(
  ..._args: unknown[]
): Promise<void> {
  throw new Error(
    "dispatchLoraBackward: R1b — requires transformers.js layer-replacement OR tfjs-webgpu autograd bridge so the trunk's backward graph can feed dY into this kernel. WGSL_LORA_BACKWARD is ready.",
  );
}

/**
 * Dispatch the bias-corrected Adam step. Updates `THETA`, `M`, `V`
 * in-place.
 *
 * R1b: requires transformers.js layer-replacement OR tfjs-webgpu
 * autograd bridge — the parameter buffer (THETA) has to be the same
 * buffer the LoRA forward kernel reads from on the next iteration.
 */
export async function dispatchAdamStep(
  ..._args: unknown[]
): Promise<void> {
  throw new Error(
    "dispatchAdamStep: R1b — requires transformers.js layer-replacement OR tfjs-webgpu autograd bridge so THETA shares storage with the LoRA forward kernel's A/B bindings. WGSL_ADAM_STEP is ready.",
  );
}
