/**
 * etzhayyim-esign — religious-corp document signing actor (Phase 0 scaffold).
 *
 * Per ADR-2605231230. This Worker terminates the three procedure XRPCs:
 *
 *   POST /xrpc/com.etzhayyim.esign.requestEnvelope
 *   POST /xrpc/com.etzhayyim.esign.signEnvelope
 *   POST /xrpc/com.etzhayyim.esign.declineEnvelope
 *
 * In Phase 0, every procedure returns 501 NotYetImplemented. Phase 1 wires
 * the requestEnvelope path through @etzhayyim/sdk (PDS write + ipfs-pinner)
 * for the Bootstrap Council Seats 2–5 RFP minutes (deadline 2026-06-19).
 *
 * Substrate boundary (CRITICAL): this Worker MUST NOT import @atproto/api
 * or viem directly. All AT Protocol writes go through @etzhayyim/sdk.
 * All on-chain anchoring is delegated to anchor-cron via a `completedEvent`
 * record; this Worker never holds a wallet key.
 */

const PROCEDURE_NSIDS = [
  "com.etzhayyim.esign.requestEnvelope",
  "com.etzhayyim.esign.signEnvelope",
  "com.etzhayyim.esign.declineEnvelope",
] as const;

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body, null, 2) + "\n", {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "x-content-type-options": "nosniff",
    },
  });
}

function notYetImplemented(nsid: string): Response {
  return jsonResponse(501, {
    error: "NotYetImplemented",
    message: `${nsid} is scaffolded but not yet wired. See ADR-2605231230 Phase 1.`,
    nsid,
    phase: 0,
    adr: "2605231230-etzhayyim-esign-actor-did-bound-mst-anchored",
  });
}

export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return jsonResponse(200, {
        ok: true,
        actor: "etzhayyim-esign",
        did: "did:web:esign.etzhayyim.com",
        phase: 0,
        adr: "2605231230",
      });
    }

    if (url.pathname === "/" || url.pathname === "/about") {
      return jsonResponse(200, {
        actor: "etzhayyim-esign",
        did: "did:web:esign.etzhayyim.com",
        description:
          "DID-bound, MST-recorded, L2-anchored document signing for the religious-corp substrate. Phase 0 scaffold — procedures return 501.",
        adr: "2605231230",
        procedures: PROCEDURE_NSIDS,
        license: "Apache-2.0 + Charter Rider v2.0 (see NOTICE)",
      });
    }

    if (url.pathname.startsWith("/xrpc/")) {
      const nsid = url.pathname.slice("/xrpc/".length);
      if (!PROCEDURE_NSIDS.includes(nsid as (typeof PROCEDURE_NSIDS)[number])) {
        return jsonResponse(404, {
          error: "MethodNotFound",
          message: `Unknown NSID: ${nsid}`,
          supported: PROCEDURE_NSIDS,
        });
      }
      if (request.method !== "POST") {
        return jsonResponse(405, {
          error: "MethodNotAllowed",
          message: `${nsid} accepts POST only`,
        });
      }
      return notYetImplemented(nsid);
    }

    return jsonResponse(404, { error: "NotFound", path: url.pathname });
  },
} satisfies ExportedHandler;
