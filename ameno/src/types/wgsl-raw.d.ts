/**
 * Ambient module declaration for Vite-style `?raw` imports of `.wgsl`
 * files (ADR-2605263400 §2 / §5).
 *
 * Vite, esbuild (with the `?raw` query), SvelteKit, Nuxt, and Next all
 * understand this convention: a file imported with the `?raw` query
 * string is loaded verbatim as a string at build time. Without this
 * declaration, `tsc --noEmit` would error on the imports in
 * `kernels/bitlinear-forward.ts` etc.
 *
 * Single-source-of-truth (gate R1a-G1, ADR-2605263400 §8): the .wgsl
 * files under `40-engine/baien-wasm-ternary/shaders/` are the ONLY
 * source of the shader text. The `?raw` import is the load mechanism.
 *
 * Test-runner / Node fallback: when the consumer (e.g. node ts-node)
 * does not understand `?raw`, the caller is responsible for providing
 * a polyfill resolver — or, more typically, the consumer is a bundler
 * that DOES understand it. The Rust test harness (`wgpu_bitlinear_*.rs`)
 * sidesteps the issue entirely by using `include_str!` against the same
 * underlying .wgsl file.
 */
declare module "*.wgsl?raw" {
  const content: string;
  export default content;
}
