import { test } from "node:test";
import assert from "node:assert/strict";
import { formatActorResult } from "../src/inference/wasm-actor-panel.ts";

test("formatActorResult renders a kanae result to HTML", () => {
  const html = formatActorResult({
    actor: "kanae",
    metric: "recipient-inflow-oku-jpy",
    sourcing: "representative",
    adjudication: "none",
    top: [
      { rank: 1, id: 8, node: "Prefectures", inflow_oku_jpy: 39400 },
      { rank: 2, id: 2, node: "MHLW", inflow_oku_jpy: 33800 },
    ],
  });
  assert.match(html, /kanae/);
  assert.match(html, /Prefectures/);
  assert.match(html, /adjudication: none/);
  assert.match(html, /39400/);
  // bar width: top row is the max → 100%
  assert.match(html, /width:100%/);
});

test("formatActorResult escapes + handles empty top", () => {
  const html = formatActorResult({ actor: "<x>", top: [] });
  assert.match(html, /&lt;x&gt;/);
  assert.doesNotMatch(html, /<x>/);
});
