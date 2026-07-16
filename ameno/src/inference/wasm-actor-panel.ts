/**
 * WASM-actor UI panel — a framework-free DOM widget that runs a content-addressed
 * actor browser-local and renders its result. The ameno-side surface of the
 * "one Worker, many WASM actors" model; the yoro appview mounts this. Per
 * ADR-2606015200 (builds on the ADR-2606014600 loader).
 *
 * `mountActorPanel(el, { did })` resolves the actor DID → fetches its WASM via the
 * apex trustless gateway → CID-verifies → runs → renders. T2 (dag-pb) actors are
 * declined client-side with an explanatory message (they run on the mesh, not the
 * browser). `formatActorResult` is the pure (testable) renderer.
 */

import { loadActor, type WasmActorLoaderOpts } from "./wasm-actor-loader.ts";

export interface ActorResult {
  actor?: string;
  metric?: string;
  sourcing?: string;
  top?: Array<Record<string, unknown>>;
  [k: string]: unknown;
}

function esc(s: unknown): string {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/** Pure renderer: actor result JSON → an HTML string (no DOM, unit-testable). */
export function formatActorResult(r: ActorResult): string {
  const top = Array.isArray(r.top) ? r.top : [];
  // pick the first numeric field as the bar value; label = first string field.
  const valKey = top.length
    ? Object.keys(top[0]).find((k) => typeof top[0][k] === "number" && k !== "rank" && k !== "id")
    : undefined;
  const labKey = top.length
    ? Object.keys(top[0]).find((k) => typeof top[0][k] === "string")
    : undefined;
  const max = valKey ? Math.max(...top.map((t) => Number(t[valKey]) || 0)) : 1;
  const rows = top
    .map((t) => {
      const v = valKey ? Number(t[valKey]) || 0 : 0;
      const pct = max > 0 ? Math.round((v / max) * 100) : 0;
      return (
        `<tr><td>${esc(t.rank ?? "")}</td>` +
        `<td><b>${esc(labKey ? t[labKey] : "")}</b></td>` +
        `<td>${esc(valKey ? t[valKey] : "")}</td>` +
        `<td style="width:45%"><div style="height:.5rem;border-radius:1rem;opacity:.55;background:currentColor;width:${pct}%"></div></td></tr>`
      );
    })
    .join("");
  return (
    `<h3 style="margin:.6rem 0 .2rem">${esc(r.actor ?? "actor")} — ${esc(r.metric ?? "")}</h3>` +
    `<p style="opacity:.65;font-size:.85rem;margin:.1rem 0 .5rem">sourcing: ${esc(r.sourcing ?? "—")}${r.adjudication ? ` · adjudication: ${esc(r.adjudication)}` : ""}</p>` +
    `<table style="border-collapse:collapse;width:100%;font-size:.92rem"><tbody>${rows}</tbody></table>`
  );
}

export interface MountOpts extends WasmActorLoaderOpts {
  did: string;
}

/** Mount a live actor panel into `el`. Returns the parsed result (or throws). */
export async function mountActorPanel(
  el: HTMLElement,
  opts: MountOpts,
): Promise<ActorResult> {
  el.innerHTML = `<p style="opacity:.7">Resolving ${esc(opts.did)} → fetch → CID-verify → run…</p>`;
  try {
    const { ref, run } = await loadActor(opts.did, opts);
    const result = run() as ActorResult;
    el.innerHTML =
      formatActorResult(result) +
      `<p style="opacity:.55;font-size:.78rem;margin-top:.5rem">ran browser-local · ${esc(ref.uri)} · CID-verified, no server</p>`;
    return result;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    el.innerHTML = `<p style="opacity:.8">⚠ ${esc(msg)}</p>`;
    throw e;
  }
}
