/**
 * mst-projector firehose subscriber.
 *
 * Polling-based PDS firehose consumer. This is a Phase 3 reference implementation
 * suitable for tests and low-volume actors. Production deployments should replace
 * the poll loop with a WebSocket client to com.atproto.sync.subscribeRepos for
 * sub-second latency. The onCommit interface remains identical.
 *
 * Per ADR-2605212000.
 */

import type { Etzhayyim } from "@etzhayyim/sdk";

/**
 * Configuration for polling-based firehose subscription.
 */
export interface FirehoseConfig {
  actorDid: string;
  collections: string[];
  pollIntervalMs?: number;
}

/**
 * Represents a single commit event (record create/update/delete).
 */
export interface CommitEvent {
  collection: string;
  rkey: string;
  value?: Record<string, unknown>;
  previousValue?: Record<string, unknown>;
}

/**
 * Polling-based firehose subscriber.
 */
export class PollingFirehose {
  private snapshots = new Map<string, Map<string, Record<string, unknown>>>();
  private intervalHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private e: Etzhayyim,
    private config: FirehoseConfig,
    private onCommit: (ev: CommitEvent) => Promise<void>,
  ) {}

  async start(): Promise<void> {
    if (this.intervalHandle) return;
    await this.pollOnce();
    const ms = this.config.pollIntervalMs ?? 5000;
    this.intervalHandle = setInterval(() => {
      this.pollOnce().catch(() => undefined);
    }, ms);
  }

  stop(): void {
    if (this.intervalHandle) {
      clearInterval(this.intervalHandle);
      this.intervalHandle = null;
    }
  }

  async pollOnce(): Promise<void> {
    for (const col of this.config.collections) {
      await this.pollCollection(col);
    }
  }

  private async pollCollection(collection: string): Promise<void> {
    const snapshot = this.snapshots.get(collection) ?? new Map<string, Record<string, unknown>>();
    const current = new Map<string, Record<string, unknown>>();

    let cursor: string | undefined;

    do {
      const page = await this.e
        .read<Record<string, unknown>>({ collection, cursor, limit: 100 })
        .catch(() => ({ records: [], cursor: undefined as string | undefined }));

      for (const r of page.records) {
        const rkey = r.uri.split("/").pop() ?? "";
        current.set(rkey, r.value);
      }

      cursor = page.cursor;
    } while (cursor);

    for (const [rkey, val] of current) {
      const prev = snapshot.get(rkey);
      if (!prev || JSON.stringify(prev) !== JSON.stringify(val)) {
        await this.onCommit({
          collection,
          rkey,
          value: val,
          previousValue: prev,
        });
      }
    }

    for (const [rkey, prev] of snapshot) {
      if (!current.has(rkey)) {
        await this.onCommit({
          collection,
          rkey,
          value: undefined,
          previousValue: prev,
        });
      }
    }

    this.snapshots.set(collection, current);
  }
}
