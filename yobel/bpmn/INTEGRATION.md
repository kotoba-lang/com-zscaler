# BPMN SDK Integration

Status: **Phase δ — partial.** The clj/bb `yobel.orchestrator` (ported off the
original Python `YobelOrchestrator`, ADR-2605201800; py→cljc prune complete,
no `.py` remains anywhere in this actor) drives execution (production path).
BPMN XML at `yobel-rite-lifecycle.bpmn` is consumed by `@etzhayyim/bpmn-sdk-importer`
for documentation, audit visualization, and validation that the cell call
sequence matches the documented process — but it does **not** own execution
at S2.

## Why two orchestrators

| Layer | Owner | What it does |
|---|---|---|
| **clj/bb `yobel.orchestrator`** | `20-actors/yobel/orchestrator.cljc` | Drives cell execution: declare → enroll fan-out → release → audit. Calls cell `build-graph` lazily, manages checkpointer + ports. The actual runtime. |
| **BPMN XML + bpmn-sdk** | `bpmn/yobel-rite-lifecycle.bpmn` + `@etzhayyim/bpmn-sdk-{importer,runtime}` | Process documentation, audit-trail visualization, contract for what the orchestrator MUST do. Compile-time consistency check. |

Both descriptions are kept in 1:1 alignment manually (commit-time review). When BPMN XML diverges from the orchestrator's behavior, that's a bug.

## Why not cross-runtime-from-BPMN at S2

The straightforward approach — let `@etzhayyim/bpmn-sdk-runtime` (TS) drive execution and dispatch service tasks to the yobel cells — would require:

1. **Cross-runtime IPC**: the TS runtime calls into the clj/bb cells. Options: subprocess + JSON stdin/stdout, HTTP localhost, gRPC. All add latency + failure modes.
2. **State synchronization**: BPMN process state (variables, instance ID, currentActivities) lives in the TS runtime; the langgraph-clj cell state lives in the clj/bb checkpointer. Keeping them in sync requires duplicate state machine logic.
3. **Test infrastructure**: a full integration test would need both clj/bb (`bb test` + langgraph-clj) and TS (jest/vitest + @etzhayyim/bpmn-sdk-*) test harnesses. The current `20-actors/yobel/` is clj/bb-only (fully ported off Python, no `.py` remains).

At S2, the cost/benefit doesn't favor cross-runtime orchestration. The clj/bb orchestrator is direct, debuggable, and matches the BPMN XML structure 1:1 by construction.

## When to add full bpmn-sdk integration

S3+ scenarios where the BPMN runtime is worth the cross-runtime overhead:

- Multi-actor processes — when a yobel rite involves coordination with non-yobel actors (kuni-umi land coordination during yobel_50yr, lawfirm court filings during political_amnesty), a shared BPMN runtime simplifies cross-actor message correlation
- Human task integration — `@etzhayyim/bpmn-sdk-human` provides the council deliberation human-task UI, which would otherwise need a separate implementation
- Form-driven rite declaration — `@etzhayyim/bpmn-sdk-form` for the rite-declaration UI

## How to wire it when needed

Reference pattern (S3 design sketch — not implemented; written when the cells were still
Python, so `CELL_DISPATCH`/`pythonCellInvoke` below describe a python3-subprocess dispatch
that's no longer accurate now that the cells are `cell.cljc` — revisit the dispatch mechanics
against whatever bb/clj subprocess or in-process FFI story `@etzhayyim/bpmn-sdk-runtime`
ends up supporting before actually building this):

```typescript
// 20-actors/yobel/orchestrator-bpmn-sdk.ts
import { importFromXml } from '@etzhayyim/bpmn-sdk-importer';
import { BpmnRuntime } from '@etzhayyim/bpmn-sdk-runtime';
import { readFileSync } from 'node:fs';
import { spawn } from 'node:child_process';

const BPMN_XML = readFileSync(__dirname + '/bpmn/yobel-rite-lifecycle.bpmn', 'utf-8');

// Map BPMN serviceTask implementation to the clj/bb cell namespace
const CELL_DISPATCH = {
  'cell:rite_declaration': 'yobel.cells.rite-declaration.cell',
  'cell:creditor_enrollment': 'yobel.cells.creditor-enrollment.cell',
  'cell:debtor_enrollment': 'yobel.cells.debtor-enrollment.cell',
  'cell:release_settlement': 'yobel.cells.release-settlement.cell',
  'cell:audit_witness': 'yobel.cells.audit-witness.cell',
};

export async function runYobelLifecycle(riteInput: any, creditors: any[], debtors: any[], releases: any[]) {
  const ir = await importFromXml(BPMN_XML);
  const runtime = new BpmnRuntime();

  runtime.onEvent(async (event) => {
    if (event.type !== 'activity.start') return;
    const taskId = event.activityId;  // e.g. 'Task_RiteDeclaration'
    // Look up serviceTask.implementation from BPMN IR
    const cellModule = lookupCellModule(ir, taskId);  // returns 'cell:rite_declaration' etc.
    const cellNs = CELL_DISPATCH[cellModule];
    if (!cellNs) return;
    // Subprocess the clj/bb cell with the event payload as stdin
    const result = await bbCellInvoke(cellNs, event.variables);
    // Inject result back into BPMN process variables
    // (placeholder — requires BpmnRuntime.setVariables API which is also placeholder at S2)
  });

  const processId = await runtime.deployProcess(ir);
  const context = await runtime.startInstance(processId, {
    variables: { riteInput, creditors, debtors, releases }
  });
  return context;
}

function bbCellInvoke(cellNs: string, vars: any): Promise<any> {
  return new Promise((resolve, reject) => {
    const proc = spawn('bb', ['-e', `
(require '[cheshire.core :as json] '${cellNs}]
;; ... actually dispatch build-graph on the resolved ns, invoke with the piped vars
(println (json/generate-string {:ok true}))  ; placeholder
`], { stdio: ['pipe', 'pipe', 'pipe'] });
    let out = '';
    proc.stdin.write(JSON.stringify(vars));
    proc.stdin.end();
    proc.stdout.on('data', (d) => { out += d; });
    proc.on('close', (code) => code === 0 ? resolve(JSON.parse(out)) : reject(new Error(`exit ${code}`)));
  });
}
```

Then a small test under `20-actors/yobel/tests_integration/test_bpmn_sdk_dispatch.test.ts` (vitest) deploying the BPMN against a stub runtime + verifying handler dispatch order matches `bpmn/yobel-rite-lifecycle.bpmn`.

## Current state — what bpmn-sdk DOES do for yobel at S2

- Importer parses `bpmn/yobel-rite-lifecycle.bpmn` → IR for documentation tools (e.g. yoro Protocol Canvas BPMN viewer)
- Validation: `@etzhayyim/bpmn-sdk-validation` could check that the BPMN XML is well-formed (not currently wired into yobel CI)
- Export: `@etzhayyim/bpmn-sdk-compiler` round-trip — verify the XML we hand-wrote parses + re-compiles equivalently (not currently wired either)

For audit / Council review, the BPMN XML is the source of truth that documents what the orchestrator does. The clj/bb orchestrator is the source of truth for actual behavior.

## See also

- `bpmn/yobel-rite-lifecycle.bpmn` — the BPMN XML
- `orchestrator.cljc` — the clj/bb runtime
- `20-actors/etzhayyim-bpmn-sdk/packages/runtime/` — the BPMN runtime API surface
- `20-actors/etzhayyim-bpmn-sdk/examples/e2e-minimal/` — reference for `deployAndStart` flow
