import {
  asAgentTool,
  createWorkerExport,
  withCapabilityTags,
  withOCELEvent,
  type ComAtprotoSyncSubscribeReposCommit,
  type HostSDK,
  nowISO,
  encodeJson,
  decodeJson,
  str,
  createKyselyDb,
  genID,
} from "@etzhayyim/kotodama-host-sdk";

let appId = "";

// --- Zero-cost Data Source Design ---
//
// All sources below are free/self-hosted. No paid external APIs.
//
// CF Workers (HTTP only, no cost):
//   IANA RDAP bootstrap → authoritative RIR RDAP (public, no auth)
//   RIR delegation files (FTP-over-HTTPS, public, no auth)
//   Cloudflare DoH (PTR lookup, free, no auth)
//   Common Crawl CDX + Internet Archive CDX (public, no auth)
//
// Linode linode-intel (self-hosted, fixed monthly cost):
//   MaxMind GeoLite2 service — free mmdb (GeoLite2-City + GeoLite2-ASN + GeoLite2-Anonymous-IP)
//     GET {SS_LINODE_GEOIP_URL}/json/{ip} → {country,city,lat,lng,isp,asn,isProxy,isDatacenter,...}
//     Auth: Bearer SS_LINODE_GEOIP_TOKEN
//     mmdb updated monthly via MaxMind free account license key
//     Data also written to s3://etzhayyim-intel/geoip/{ip}-{YYYYMMDD}.json
//   ZMap/Masscan scheduler (daily, full /8 block sweeps)
//     Results → s3://etzhayyim-intel/scans/{date}/{cidr}.ndjson → batch push to ingestScanResult
//   BGP feed processor (RouteViews + RIPE RIS bgpdump)
//     Results → POST /xrpc/com.etzhayyim.apps.ipaddress.collectRirDelegations
//
// Cloudflare Container (short-lived TCP scan, cost-efficient):
//   Polls getScanJobs?status=queued every 60s
//   Runs ZMap/Masscan for specific CIDR → banner grab → TLS extract
//   Results → POST /xrpc/com.etzhayyim.apps.ipaddress.ingestScanResult
//   Status → PATCH scanJob to "completed"
//
// S3/R2 raw storage (s3://etzhayyim-intel/):
//   scans/{YYYY-MM-DD}/{cidr}.ndjson     — port scan results
//   geoip/{ip}-{YYYYMMDD}.json           — GeoIP snapshots
//   rir/{rir}-{YYYYMMDD}.txt             — RIR delegation files
//   blockchain/{chain}/addr/{addr}.json  — blockchain address data
// 4. All scan results → graph.etzhayyim.com as ScanResult nodes linked to IPAddress

// --- RIR RDAP endpoints ---
const RIR_RDAP: Record<string, string> = {
  apnic: "https://rdap.apnic.net",
  ripe: "https://rdap.db.ripe.net",
  arin: "https://rdap.arin.net/registry",
  lacnic: "https://rdap.lacnic.net/rdap",
  afrinic: "https://rdap.afrinic.net/rdap",
};

// RIR delegation file URLs (text format: type|CC|type|start|value|date|status)
const RIR_DELEGATION_URLS: Record<string, string> = {
  apnic: "https://ftp.apnic.net/stats/apnic/delegated-apnic-latest",
  ripe: "https://ftp.ripe.net/pub/stats/ripencc/delegated-ripencc-latest",
  arin: "https://ftp.arin.net/pub/stats/arin/delegated-arin-extended-latest",
  lacnic: "https://ftp.lacnic.net/pub/stats/lacnic/delegated-lacnic-latest",
  afrinic: "https://ftp.afrinic.net/pub/stats/afrinic/delegated-afrinic-latest",
};

// --- Helpers ---

type AnyRow = Record<string, unknown>;
type KyselyDb = ReturnType<typeof createKyselyDb>;

let db: KyselyDb | null = null;

function getDb(): KyselyDb {
  if (!db) db = createKyselyDb();
  return db;
}

function parseProps(props: unknown): Record<string, unknown> {
  if (typeof props !== "string" || props.length === 0) return {};
  try {
    const parsed = JSON.parse(props) as unknown;
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  } catch {
    // ignore malformed props payloads
  }
  return {};
}

function normalizeTypedRow(row: AnyRow | null | undefined): AnyRow {
  if (!row) return {};
  const props = parseProps(row.props);
  return {
    ...props,
    ...row,
    nodeId: row.node_id ?? props.nodeId ?? row.rkey ?? row.vertex_id,
    resultId: row.result_id ?? props.resultId ?? row.rkey,
    jobId: row.job_id ?? props.jobId,
    asnNumber: row.asn_number ?? props.asnNumber,
    country: row.country ?? row.country_code ?? props.country,
    createdAt: row.created_at ?? props.createdAt,
    updatedAt: row.updated_at ?? props.updatedAt,
    firstSeen: row.first_seen ?? props.firstSeen,
    lastSeen: row.last_seen ?? props.lastSeen,
    scannedAt: row.scanned_at ?? props.scannedAt,
  };
}

function tableForCollection(collection: string): string {
  switch (collection) {
    case "com.etzhayyim.apps.ipaddress.ipAddress":
      return "vertex_ip_address";
    case "com.etzhayyim.apps.ipaddress.ipRange":
      return "vertex_ipaddress_range";
    case "com.etzhayyim.apps.ipaddress.asn":
      return "vertex_ipaddress_asn";
    case "com.etzhayyim.apps.ipaddress.scanJob":
      return "vertex_ipaddress_scan_job";
    case "com.etzhayyim.apps.ipaddress.scanResult":
      return "vertex_scan_result";
    default:
      throw new Error(`No typed table for collection ${collection}`);
  }
}

async function listByCollection(
  collection: string,
  build?: (query: any) => any,
): Promise<AnyRow[]> {
  let query: any = getDb()
    .selectFrom(tableForCollection(collection) as any)
    .selectAll();
  if (build) query = build(query);
  const rows = await query.execute();
  return rows.map((row: AnyRow) => normalizeTypedRow(row));
}

async function getFirstByCollection(
  collection: string,
  build?: (query: any) => any,
): Promise<AnyRow | null> {
  const rows = await listByCollection(collection, (query) => {
    const next = build ? build(query) : query;
    return next.limit(1);
  });
  return rows[0] ?? null;
}

function write(sdk: HostSDK, collection: string, rec: Record<string, unknown>): void {
  const nsid = `com.etzhayyim.apps.ipaddress.${collection}`;
  const pds = sdk.pds as unknown as {
    createRecord?: (collection: string, record: Record<string, unknown>) => unknown;
    dispatch?: (msg: { type: string; payload: unknown }) => unknown;
  };
  if (typeof pds.createRecord === "function") { pds.createRecord(nsid, rec); return; }
  pds.dispatch?.({ type: "com.atproto.repo.createRecord", payload: { collection: nsid, recordJson: JSON.stringify(rec) } });
}

function post(sdk: HostSDK, text: string): void {
  const pds = sdk.pds as unknown as { dispatch?: (m: Record<string, unknown>) => unknown };
  pds.dispatch?.({ type: "app.bsky.feed.post", text: text.slice(0, 300), createdAt: nowISO() });
}

function fireAndForgetAnalyzeIp(sdk: HostSDK, ip: string, context: string): void {
  void cmdAnalyzeIp(sdk, encodeJson({ ip })).catch((error: unknown) => {
    console.warn(`${context} analyzeIp failed for ${ip}: ${String(error)}`);
  });
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T | null> {
  try {
    const res = await fetch(url, { ...init, headers: { Accept: "application/json", "User-Agent": "etzhayyim-ipaddress/1.0", ...(init?.headers ?? {}) } });
    if (!res.ok) return null;
    return await res.json() as T;
  } catch {
    return null;
  }
}

// IP → hex encoding for DID paths
function ipv4ToHex(ip: string): string {
  const octets = ip.split(".");
  if (octets.length !== 4) return "";
  return octets.map(o => Number.parseInt(o, 10).toString(16).padStart(2, "0")).join("");
}

function ipv4ToDid(ip: string): string {
  const hex = ipv4ToHex(ip);
  return hex ? `did:web:ipaddress.etzhayyim.com:v4:ip${hex}` : "";
}

function asnToDid(asn: number): string {
  return `did:web:ipaddress.etzhayyim.com:asn:as${asn}`;
}

function cidrToNodeId(cidr: string): string {
  const [base, mask] = cidr.split("/");
  const hex = ipv4ToHex(base);
  return hex ? `p${hex}m${mask}` : cidr.replace(/[./]/g, "_");
}

// --- RDAP IP lookup (determines authoritative RIR then queries) ---

interface RdapIp {
  handle?: string;
  startAddress?: string;
  endAddress?: string;
  ipVersion?: string;
  name?: string;
  type?: string;
  country?: string;
  parentHandle?: string;
  entities?: Array<{ roles?: string[]; handle?: string; vcardArray?: unknown[] }>;
  events?: Array<{ eventAction?: string; eventDate?: string }>;
  remarks?: Array<{ description?: string[] }>;
  cidr0_cidrs?: Array<{ v4prefix?: string; length?: number }>;
}

async function rdapIpLookup(ip: string): Promise<{ rir: string; data: RdapIp } | null> {
  // Try IANA bootstrap first, fallback to each RIR
  const bootstrap = await fetchJson<{ services: Array<[string[], string[]]> }>(
    "https://data.iana.org/rdap/ipv4.json"
  );

  let rdapBase = "";
  if (bootstrap?.services) {
    // Find matching RIR for the IP
    for (const [prefixes, urls] of bootstrap.services) {
      for (const prefix of prefixes) {
        if (ipInCidr(ip, prefix)) { rdapBase = urls[0] ?? ""; break; }
      }
      if (rdapBase) break;
    }
  }

  if (rdapBase) {
    const data = await fetchJson<RdapIp>(`${rdapBase.replace(/\/$/, "")}/ip/${ip}`,
      { headers: { Accept: "application/rdap+json" } });
    if (data) {
      const rir = Object.entries(RIR_RDAP).find(([, base]) => rdapBase.includes(base.replace("https://", "").split("/")[0]))?.[0] ?? "unknown";
      return { rir, data };
    }
  }

  // Fallback: try each RIR directly
  for (const [rir, base] of Object.entries(RIR_RDAP)) {
    const data = await fetchJson<RdapIp>(`${base}/ip/${ip}`,
      { headers: { Accept: "application/rdap+json" } });
    if (data?.startAddress) return { rir, data };
  }
  return null;
}

function ipInCidr(ip: string, cidr: string): boolean {
  try {
    const [base, maskStr] = cidr.split("/");
    const mask = Number.parseInt(maskStr, 10);
    const ipNum = ip.split(".").reduce((a, b) => (a << 8) | Number.parseInt(b, 10), 0) >>> 0;
    const baseNum = base.split(".").reduce((a, b) => (a << 8) | Number.parseInt(b, 10), 0) >>> 0;
    const maskNum = mask === 0 ? 0 : (~0 << (32 - mask)) >>> 0;
    return (ipNum & maskNum) === (baseNum & maskNum);
  } catch { return false; }
}

// --- GeoIP via self-hosted MaxMind GeoLite2 service (linode-intel) ---
// Free mmdb database (MaxMind free account, updated monthly)
// linode-intel runs a Go HTTP service wrapping geoip2-golang library:
//   GET {baseUrl}/json/{ip} → JSON response
//   Databases: GeoLite2-City.mmdb + GeoLite2-ASN.mmdb + GeoLite2-Anonymous-IP.mmdb
// Also caches results to s3://etzhayyim-intel/geoip/{ip}-{YYYYMMDD}.json

interface LinodeGeoIpResult {
  ip?: string;
  country?: string;         // "Australia"
  countryCode?: string;     // "AU"
  region?: string;          // "QLD"
  city?: string;            // "Brisbane"
  lat?: number;
  lon?: number;
  timezone?: string;        // "Australia/Brisbane"
  isp?: string;
  org?: string;
  asn?: string;             // "AS13335"
  asnOrg?: string;          // "Cloudflare, Inc."
  isProxy?: boolean;        // GeoLite2-Anonymous-IP: isAnonymousProxy
  isDatacenter?: boolean;   // GeoLite2-Anonymous-IP: isHostingProvider
  isMobile?: boolean;       // not in GeoLite2 — always false unless extended mmdb
  isAnonymousVpn?: boolean;
  isTorExitNode?: boolean;
}

async function linodeGeoipLookup(baseUrl: string, token: string, ip: string): Promise<LinodeGeoIpResult | null> {
  if (!baseUrl) return null;
  return fetchJson<LinodeGeoIpResult>(
    `${baseUrl.replace(/\/$/, "")}/json/${encodeURIComponent(ip)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
}

// --- Reverse DNS via Cloudflare DoH ---

async function reverseDnsLookup(ip: string): Promise<string[]> {
  const octets = ip.split(".").reverse().join(".");
  const arpa = `${octets}.in-addr.arpa`;
  const result = await fetchJson<{ Answer?: Array<{ data?: string }> }>(
    `https://cloudflare-dns.com/dns-query?name=${arpa}&type=PTR`,
    { headers: { Accept: "application/dns-json" } },
  );
  return (result?.Answer ?? []).map(a => str(a.data ?? "").replace(/\.$/, ""));
}

// --- RIR delegation file parsing ---

interface DelegationRecord {
  rir: string;
  cc: string;
  type: string; // "ipv4" | "ipv6" | "asn"
  start: string;
  value: string; // host count for IPv4, prefix length for IPv6
  date: string;
  status: string; // "allocated" | "assigned" | "reserved"
}

async function fetchRirDelegations(rir: string, maxLines = 2000): Promise<DelegationRecord[]> {
  const url = RIR_DELEGATION_URLS[rir];
  if (!url) return [];
  try {
    const res = await fetch(url);
    if (!res.ok) return [];
    const text = await res.text();
    const lines = text.split("\n").filter(l => !l.startsWith("#") && l.trim());
    const records: DelegationRecord[] = [];
    for (const line of lines.slice(0, maxLines)) {
      const parts = line.split("|");
      if (parts.length < 7) continue;
      const [rirName, cc, type, start, value, date, status] = parts;
      if (type !== "ipv4" && type !== "ipv6" && type !== "asn") continue;
      if (status === "summary") continue;
      records.push({ rir: rirName ?? rir, cc: cc ?? "", type, start: start ?? "", value: value ?? "", date: date ?? "", status: status?.split(" ")[0] ?? "" });
    }
    return records;
  } catch { return []; }
}

// --- Commands ---

async function cmdAnalyzeIp(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ ip?: string }>(payload, {});
  const ip = str(req.ip ?? "").trim();
  if (!ip) return { error: "ip required" };

  const env = sdk as unknown as Record<string, unknown>;
  const geoipUrl = str(env["SS_LINODE_GEOIP_URL"] ?? "");
  const geoipToken = str(env["SS_LINODE_GEOIP_TOKEN"] ?? "");
  const ts = nowISO();
  const ipHex = ipv4ToHex(ip);
  const ipDid = ipv4ToDid(ip);

  // Run all lookups in parallel (RDAP + MaxMind GeoLite2 + DoH PTR)
  const [rdapResult, geo, ptrs] = await Promise.all([
    rdapIpLookup(ip),
    linodeGeoipLookup(geoipUrl, geoipToken, ip),
    reverseDnsLookup(ip),
  ]);

  // Extract ASN number from MaxMind GeoLite2 response "AS13335"
  const asnMatch = (geo?.asn ?? "").match(/^AS(\d+)/i);
  const asnNumber = asnMatch ? Number.parseInt(asnMatch[1], 10) : 0;
  const asnDid = asnNumber ? asnToDid(asnNumber) : "";

  // Write IPAddress node
  write(sdk, "ipAddress", {
    nodeId: `ip:${ip}`, address: ip, version: "v4",
    did: ipDid, asnNumber, asnDid,
    country: geo?.countryCode ?? "", city: geo?.city ?? "",
    isp: geo?.isp ?? "", org: geo?.org ?? "",
    isProxy: geo?.isProxy ?? false,
    isDatacenter: geo?.isDatacenter ?? false,
    isMobile: geo?.isMobile ?? false,
    isAnonymousVpn: geo?.isAnonymousVpn ?? false,
    isTorExitNode: geo?.isTorExitNode ?? false,
    reverseDns: JSON.stringify(ptrs),
    firstSeen: ts, lastSeen: ts,
    orgId: "anon", userId: "anon", actorId: appId,
  });

  // Write Geolocation node (MaxMind GeoLite2-City source)
  write(sdk, "geolocation", {
    nodeId: `geo:${ip}`, ip,
    country: geo?.country ?? "", countryCode: geo?.countryCode ?? "",
    region: geo?.region ?? "", city: geo?.city ?? "",
    lat: geo?.lat ?? 0, lng: geo?.lon ?? 0,
    timezone: geo?.timezone ?? "",
    isp: geo?.isp ?? "", org: geo?.org ?? "",
    isProxy: geo?.isProxy ?? false,
    isDatacenter: geo?.isDatacenter ?? false,
    isTorExitNode: geo?.isTorExitNode ?? false,
    source: "maxmind-geolite2",
    observedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
  });

  // Write WhoisSnapshot from RIR RDAP
  if (rdapResult?.data) {
    const d = rdapResult.data;
    const registrationDate = d.events?.find(e => e.eventAction === "registration")?.eventDate ?? "";
    const lastChangedDate = d.events?.find(e => e.eventAction === "last changed")?.eventDate ?? "";
    write(sdk, "whoisSnapshot", {
      nodeId: `whois:${ip}:${ts}`, ip,
      handle: d.handle ?? "", name: d.name ?? "",
      startAddress: d.startAddress ?? "", endAddress: d.endAddress ?? "",
      type: d.type ?? "", country: d.country ?? "",
      rir: rdapResult.rir, registrationDate, lastChangedDate,
      observedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
    });
  }

  // Write ASN node (MaxMind GeoLite2-ASN source)
  if (asnNumber) {
    write(sdk, "asn", {
      nodeId: `asn:${asnNumber}`,
      number: asnNumber, did: asnDid,
      name: geo?.asnOrg ?? "",
      country: geo?.countryCode ?? "",
      isp: geo?.isp ?? "",
      source: "maxmind-geolite2",
      updatedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
    });
  }

  // Consolidated IpAnalysis record (consumed by yabai Follow)
  write(sdk, "ipAnalysis", {
    nodeId: `analysis:${ip}:${ts}`, ip,
    country: geo?.countryCode ?? "", city: geo?.city ?? "",
    isp: geo?.isp ?? "", asnNumber,
    isProxy: geo?.isProxy ?? false,
    isDatacenter: geo?.isDatacenter ?? false,
    isTorExitNode: geo?.isTorExitNode ?? false,
    isAnonymousVpn: geo?.isAnonymousVpn ?? false,
    reverseDnsHost: ptrs[0] ?? "",
    rir: rdapResult?.rir ?? "",
    geoSource: "maxmind-geolite2",
    analysedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
  });

  return {
    ip, did: ipDid, country: geo?.countryCode, city: geo?.city,
    isp: geo?.isp, asnNumber, asnOrg: geo?.asnOrg,
    isProxy: geo?.isProxy, isDatacenter: geo?.isDatacenter,
    isTorExitNode: geo?.isTorExitNode,
    reverseDns: ptrs, rir: rdapResult?.rir,
  };
}

async function cmdCollectRirDelegations(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ rir?: string; maxLines?: number }>(payload, {});
  const rir = str(req.rir ?? "").toLowerCase();
  const rirs = rir && RIR_DELEGATION_URLS[rir] ? [rir] : Object.keys(RIR_DELEGATION_URLS);
  const maxLines = req.maxLines ?? 2000;
  const ts = nowISO();

  let totalIpv4 = 0, totalIpv6 = 0, totalAsn = 0;

  for (const r of rirs) {
    const delegations = await fetchRirDelegations(r, maxLines);

    for (const d of delegations) {
      if (d.type === "ipv4") {
        const hosts = Number.parseInt(d.value, 10);
        const cidr = hostsToCidr(d.start, hosts);
        const nodeId = `iprange:${cidrToNodeId(cidr)}`;
        write(sdk, "ipRange", {
          nodeId, cidr, start: d.start,
          country: d.cc, rir: d.rir,
          allocationDate: d.date,
          status: d.status,
          hostCount: hosts,
          updatedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
        });
        totalIpv4++;
      } else if (d.type === "asn") {
        const asnNumber = Number.parseInt(d.start, 10);
        write(sdk, "asn", {
          nodeId: `asn:${asnNumber}`,
          number: asnNumber, did: asnToDid(asnNumber),
          country: d.cc, rir: d.rir,
          allocationDate: d.date, status: d.status,
          updatedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
        });
        totalAsn++;
      } else if (d.type === "ipv6") {
        const cidr = `${d.start}/${d.value}`;
        write(sdk, "ipRange", {
          nodeId: `iprange6:${d.start.replace(/:/g, "_")}_${d.value}`,
          cidr, start: d.start, version: "v6",
          country: d.cc, rir: d.rir,
          allocationDate: d.date, status: d.status,
          updatedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
        });
        totalIpv6++;
      }
    }
  }

  post(sdk, `RIR delegation update: ${rirs.join(",")} — IPv4 ranges: ${totalIpv4}, IPv6: ${totalIpv6}, ASNs: ${totalAsn}`);

  return { rirs, totalIpv4, totalIpv6, totalAsn, updatedAt: ts };
}

function hostsToCidr(start: string, hosts: number): string {
  const bits = 32 - Math.log2(hosts);
  return `${start}/${Math.round(bits)}`;
}

async function cmdCollectScan(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  // Creates a scan job in SQL. CF Container / Linode polls for "queued" jobs
  // via GET /xrpc/com.etzhayyim.apps.ipaddress.getScanJobs?status=queued
  const req = decodeJson<{
    cidr?: string;        // e.g. "103.21.244.0/22"
    ports?: number[];     // e.g. [80, 443, 22, 25]
    scanType?: string;    // "port" | "tls" | "banner" | "full"
    priority?: string;    // "high" | "normal" | "low"
    scanner?: string;     // "cf-container" | "linode" — hint only
  }>(payload, {});

  if (!req.cidr) return { error: "cidr required" };

  const jobId = genID("sjob");
  const ts = nowISO();

  write(sdk, "scanJob", {
    jobId, cidr: req.cidr,
    ports: JSON.stringify(req.ports ?? [80, 443, 22, 25, 8080, 8443]),
    scanType: str(req.scanType ?? "port"),
    priority: str(req.priority ?? "normal"),
    preferredScanner: str(req.scanner ?? "cf-container"),
    status: "queued",
    createdAt: ts, orgId: "anon", userId: "anon", actorId: appId,
  });

  return { jobId, cidr: req.cidr, status: "queued" };
}

async function cmdGetScanJobs(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ status?: string; limit?: number }>(payload, {});
  const limit = Math.min(req.limit ?? 50, 100);
  const rows = await listByCollection("com.etzhayyim.apps.ipaddress.scanJob", (query) => {
    let next = query;
    if (req.status) next = next.where("status", "=", req.status);
    return next.orderBy("created_at", "desc").limit(limit);
  });
  return { jobs: rows, total: rows.length };
}

async function cmdIngestScanResult(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  // CF Container / Linode pushes scan results here
  const req = decodeJson<{
    jobId?: string;
    ip?: string;
    port?: number;
    protocol?: string;
    state?: string;
    service?: string;
    software?: string;
    version?: string;
    banner?: string;
    tlsVersion?: string;
    tlsCipher?: string;
    certSubject?: string;
    certIssuer?: string;
    certExpires?: string;
    osGuess?: string;
    scannerHost?: string;
    scannedAt?: string;
  }>(payload, {});

  if (!req.ip || !req.port) return { error: "ip and port required" };

  const resultId = genID("scan");
  const ts = req.scannedAt ?? nowISO();

  write(sdk, "scanResult", {
    resultId, jobId: str(req.jobId ?? ""),
    nodeId: `scan:${req.ip}:${req.port}:${ts}`,
    ip: req.ip, port: req.port,
    protocol: str(req.protocol ?? "tcp"),
    state: str(req.state ?? "open"),
    service: str(req.service ?? ""),
    software: str(req.software ?? ""),
    version: str(req.version ?? ""),
    banner: str(req.banner ?? "").slice(0, 512),
    tlsVersion: str(req.tlsVersion ?? ""),
    tlsCipher: str(req.tlsCipher ?? ""),
    certSubject: str(req.certSubject ?? ""),
    certIssuer: str(req.certIssuer ?? ""),
    certExpires: str(req.certExpires ?? ""),
    osGuess: str(req.osGuess ?? ""),
    scannerHost: str(req.scannerHost ?? "unknown"),
    scannedAt: ts, orgId: "anon", userId: "anon", actorId: appId,
  });

  // Ensure IPAddress node exists for the scanned host
  const existing = await getFirstByCollection("com.etzhayyim.apps.ipaddress.ipAddress", (query) =>
    query.where("node_id", "=", `ip:${req.ip}`),
  );
  if (!existing) {
    write(sdk, "ipAddress", {
      nodeId: `ip:${req.ip}`, address: req.ip, version: "v4",
      did: ipv4ToDid(req.ip),
      firstSeen: ts, lastSeen: ts,
      orgId: "anon", userId: "anon", actorId: appId,
    });
  }

  return { resultId, ip: req.ip, port: req.port, state: req.state };
}

async function cmdGetScanResults(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ ip?: string; port?: number; limit?: number; offset?: number }>(payload, {});
  const limit = Math.min(req.limit ?? 50, 100);
  const offset = req.offset ?? 0;
  const rows = await listByCollection("com.etzhayyim.apps.ipaddress.scanResult", (query) => {
    let next = query;
    if (req.ip) next = next.where("ip", "=", req.ip);
    if (req.port) next = next.where("port", "=", req.port);
    return next.orderBy("created_at", "desc").offset(offset).limit(limit);
  });
  return { results: rows, total: rows.length, offset, limit };
}

async function cmdGetAsn(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ asn?: number; name?: string }>(payload, {});
  if (!req.asn && !req.name) return { error: "asn or name required" };
  const rows = req.asn
    ? await listByCollection("com.etzhayyim.apps.ipaddress.asn", (query) =>
        query.where("number", "=", req.asn).limit(1),
      )
    : await listByCollection("com.etzhayyim.apps.ipaddress.asn", (query) =>
        query.where("name", "ilike", `%${str(req.name ?? "").trim()}%`).limit(10),
      );
  return { asns: rows };
}

async function cmdListAsns(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ rir?: string; country?: string; limit?: number; offset?: number }>(payload, {});
  const limit = Math.min(req.limit ?? 50, 100);
  const offset = req.offset ?? 0;
  const rows = await listByCollection("com.etzhayyim.apps.ipaddress.asn", (query) => {
    let next = query;
    if (req.rir) next = next.where("rir", "=", req.rir);
    if (req.country) next = next.where("country", "=", req.country);
    return next.orderBy("number", "asc").offset(offset).limit(limit);
  });
  return { asns: rows, total: rows.length, offset, limit };
}

async function cmdSeedAsns(sdk: HostSDK, _payload: Uint8Array): Promise<unknown> {
  // Seed well-known ASNs for major providers
  const seeds = [
    { number: 13335, name: "Cloudflare, Inc.", country: "US", isp: "Cloudflare" },
    { number: 15169, name: "Google LLC", country: "US", isp: "Google" },
    { number: 16509, name: "Amazon.com, Inc.", country: "US", isp: "AWS" },
    { number: 8075, name: "Microsoft Corporation", country: "US", isp: "Azure" },
    { number: 32934, name: "Facebook, Inc.", country: "US", isp: "Meta" },
    { number: 2497, name: "Internet Initiative Japan Inc.", country: "JP", isp: "IIJ" },
    { number: 2516, name: "KDDI CORPORATION", country: "JP", isp: "KDDI" },
    { number: 7679, name: "NTT Communications Corporation", country: "JP", isp: "NTT" },
    { number: 17676, name: "SoftBank Corp.", country: "JP", isp: "SoftBank" },
    { number: 9370, name: "Sakura Internet Inc.", country: "JP", isp: "Sakura" },
  ];
  const ts = nowISO();
  for (const s of seeds) {
    write(sdk, "asn", {
      nodeId: `asn:${s.number}`, number: s.number, did: asnToDid(s.number),
      name: s.name, country: s.country, isp: s.isp,
      status: "seeded", updatedAt: ts,
      orgId: "anon", userId: "anon", actorId: appId,
    });
  }
  return { seeded: seeds.length };
}

async function cmdReverseDns(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  const req = decodeJson<{ ip?: string }>(payload, {});
  const ip = str(req.ip ?? "").trim();
  if (!ip) return { error: "ip required" };
  const ptrs = await reverseDnsLookup(ip);
  write(sdk, "reverseDns", {
    nodeId: `ptr:${ip}`, ip,
    ptrRecords: JSON.stringify(ptrs),
    verified: ptrs.length > 0,
    observedAt: nowISO(), orgId: "anon", userId: "anon", actorId: appId,
  });
  return { ip, ptrRecords: ptrs };
}

async function cmdGetIpReputation(sdk: HostSDK, payload: Uint8Array): Promise<unknown> {
  // Cross-app: query yabai.etzhayyim.com for IP risk score
  const req = decodeJson<{ ip?: string }>(payload, {});
  const ip = str(req.ip ?? "").trim();
  if (!ip) return { error: "ip required" };

  try {
    const res = await fetch(`https://yabai.etzhayyim.com/xrpc/com.etzhayyim.apps.yabai.getIpRisk`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ip }),
    });
    if (!res.ok) return { ip, riskScore: 0, level: "unknown", source: "yabai", error: res.status };
    return { ...(await res.json() as Record<string, unknown>), source: "yabai" };
  } catch (e: unknown) {
    return { ip, riskScore: 0, level: "unknown", source: "yabai", error: String(e) };
  }
}

async function cmdGetRirStats(sdk: HostSDK, _payload: Uint8Array): Promise<unknown> {
  const [ipRanges, asns, ips, scans] = await Promise.all([
    listByCollection("com.etzhayyim.apps.ipaddress.ipRange"),
    listByCollection("com.etzhayyim.apps.ipaddress.asn"),
    listByCollection("com.etzhayyim.apps.ipaddress.ipAddress"),
    listByCollection("com.etzhayyim.apps.ipaddress.scanResult"),
  ]);

  const rangesByRir: Record<string, number> = {};
  for (const r of ipRanges) { rangesByRir[str(r.rir ?? "unknown")] = (rangesByRir[str(r.rir ?? "unknown")] ?? 0) + 1; }

  const asnsByRir: Record<string, number> = {};
  for (const a of asns) { asnsByRir[str(a.rir ?? "unknown")] = (asnsByRir[str(a.rir ?? "unknown")] ?? 0) + 1; }

  return {
    ipRangesByRir: rangesByRir,
    asnsByRir,
    totalIpAddresses: ips.length,
    totalScanResults: scans.length,
  };
}

async function cmdRegisterEntityProfiles(sdk: HostSDK, _payload: Uint8Array): Promise<unknown> {
  // Register governance DID hierarchy: treaty → charter → RIR
  const ts = nowISO();
  const entities = [
    { did: "did:web:ipaddress.etzhayyim.com:treaty:itu", role: "treaty", name: "International Telecommunication Union", scope: "global" },
    { did: "did:web:ipaddress.etzhayyim.com:charter:icann", role: "charter", name: "ICANN / IANA", scope: "global" },
    { did: "did:web:ipaddress.etzhayyim.com:rir:apnic", role: "rir", name: "APNIC", scope: "asia-pacific" },
    { did: "did:web:ipaddress.etzhayyim.com:rir:ripe", role: "rir", name: "RIPE NCC", scope: "europe-middle-east-central-asia" },
    { did: "did:web:ipaddress.etzhayyim.com:rir:arin", role: "rir", name: "ARIN", scope: "north-america" },
    { did: "did:web:ipaddress.etzhayyim.com:rir:lacnic", role: "rir", name: "LACNIC", scope: "latin-america-caribbean" },
    { did: "did:web:ipaddress.etzhayyim.com:rir:afrinic", role: "rir", name: "AFRINIC", scope: "africa" },
    { did: "did:web:ipaddress.etzhayyim.com:nir:jpnic", role: "nir", name: "JPNIC", scope: "japan", parentRir: "apnic" },
  ];

  for (const e of entities) {
    write(sdk, "governanceRule", {
      nodeId: `gov:${e.did}`,
      did: e.did, role: e.role, name: e.name, scope: e.scope,
      parentRir: (e as Record<string, unknown>).parentRir ?? "",
      registeredAt: ts, orgId: "anon", userId: "anon", actorId: appId,
    });
  }

  return { registered: entities.length };
}

// --- Reactive pipeline ---

export async function handleComAtprotoSyncSubscribeReposCommit(sdk: HostSDK, commit: ComAtprotoSyncSubscribeReposCommit): Promise<{ ok: boolean }> {
  if (commit.action !== "create") return { ok: true };

  // yabai Follow: when yabai creates an entity for an IP, auto-enrich with full analysis
  if (commit.collection === "com.etzhayyim.apps.yabai.entity") {
    try {
      const row = await getFirstByCollection("com.etzhayyim.apps.yabai.entity", (query) =>
        query.where("rkey", "=", commit.rkey ?? "").where("entity_type", "=", "IPAddress"),
      );
      if (row) {
        const ip = str(row.value ?? "");
        if (ip) fireAndForgetAnalyzeIp(sdk, ip, "yabai entity commit");
      }
    } catch (e: unknown) { console.warn(`yabai entity commit error: ${String(e)}`); }
  }

  // malak Follow: when malak creates infrastructure record, analyze the IP
  if (commit.collection === "com.etzhayyim.apps.malak.actorInfrastructure") {
    try {
      const row = await getFirstByCollection("com.etzhayyim.apps.malak.actorInfrastructure", (query) =>
        query.where("rkey", "=", commit.rkey ?? "").where("type", "=", "ip"),
      );
      if (row) {
        const ip = str(row.value ?? "");
        if (ip) fireAndForgetAnalyzeIp(sdk, ip, "malak infra commit");
      }
    } catch (e: unknown) { console.warn(`malak infra commit error: ${String(e)}`); }
  }

  return { ok: true };
}

// --- Heartbeat: daily RIR delegation refresh ---

export async function runHeartbeat(sdk: HostSDK): Promise<{ ok: boolean; actions: Array<Record<string, unknown>> }> {
  // Triggered daily via cron (see actor-manifest.jsonld pipeline)
  // Refreshes top-level RIR delegation data and seeds ASNs
  const result = await cmdCollectRirDelegations(sdk, encodeJson({ maxLines: 5000 }));
  return { ok: true, actions: [{ type: "rir_delegation_refresh", result }] };
}

// --- SDK factory ---

export default createWorkerExport((sdk) => {
  appId = sdk.pds.selfNanoid ?? "";
  sdk.app
    // IP analysis
    .command("com.etzhayyim.apps.ipaddress.analyzeIp", (_, b) => cmdAnalyzeIp(sdk, b),
      asAgentTool("Full IP analysis: GeoIP + WHOIS RDAP + PTR + ASN"), withCapabilityTags("ip", "analyze"), withOCELEvent("collector.run"))
    .command("com.etzhayyim.apps.ipaddress.lookupIp", (_, b) => cmdAnalyzeIp(sdk, b),
      asAgentTool("Lookup IP address with enrichment"), withCapabilityTags("ip", "lookup"))
    .command("com.etzhayyim.apps.ipaddress.reverseDns", (_, b) => cmdReverseDns(sdk, b),
      asAgentTool("Reverse DNS (PTR) lookup for IP"), withCapabilityTags("dns", "ptr"))
    .command("com.etzhayyim.apps.ipaddress.getIpReputation", (_, b) => cmdGetIpReputation(sdk, b),
      asAgentTool("Get IP reputation from yabai risk engine"), withCapabilityTags("ip", "reputation"))
    // RIR collection
    .command("com.etzhayyim.apps.ipaddress.collectRirDelegations", (_, b) => cmdCollectRirDelegations(sdk, b),
      asAgentTool("Collect RIR delegation files (APNIC/RIPE/ARIN/LACNIC/AFRINIC)"), withCapabilityTags("rir", "collect"), withOCELEvent("collector.run"))
    // ASN management
    .command("com.etzhayyim.apps.ipaddress.getAsn", (_, b) => cmdGetAsn(sdk, b),
      asAgentTool("Get ASN details"), withCapabilityTags("asn", "query"))
    .command("com.etzhayyim.apps.ipaddress.listAsns", (_, b) => cmdListAsns(sdk, b),
      asAgentTool("List ASNs with optional RIR/country filter"), withCapabilityTags("asn", "query"))
    .command("com.etzhayyim.apps.ipaddress.seedAsns", (_, b) => cmdSeedAsns(sdk, b),
      asAgentTool("Seed well-known ASNs (Cloudflare, Google, AWS, NTT, etc.)"), withCapabilityTags("asn", "seed"))
    // Scan orchestration (job queue for CF Container / Linode)
    .command("com.etzhayyim.apps.ipaddress.collectScan", (_, b) => cmdCollectScan(sdk, b),
      asAgentTool("Create scan job for CF Container or Linode (ZMap/Masscan)"), withCapabilityTags("scan", "queue"))
    .command("com.etzhayyim.apps.ipaddress.getScanJobs", (_, b) => cmdGetScanJobs(sdk, b),
      asAgentTool("List scan jobs (polled by CF Container / Linode)"), withCapabilityTags("scan", "query"))
    .command("com.etzhayyim.apps.ipaddress.ingestScanResult", (_, b) => cmdIngestScanResult(sdk, b),
      asAgentTool("Ingest port scan result from CF Container or Linode"), withCapabilityTags("scan", "ingest"))
    .command("com.etzhayyim.apps.ipaddress.getScanResults", (_, b) => cmdGetScanResults(sdk, b),
      asAgentTool("Query stored scan results"), withCapabilityTags("scan", "query"))
    // Stats
    .command("com.etzhayyim.apps.ipaddress.getRirStats", (_, b) => cmdGetRirStats(sdk, b),
      asAgentTool("Get RIR delegation and coverage statistics"), withCapabilityTags("rir", "stats"))
    .command("com.etzhayyim.apps.ipaddress.registerEntityProfiles", (_, b) => cmdRegisterEntityProfiles(sdk, b),
      asAgentTool("Register governance DID hierarchy (ITU → ICANN → RIR → NIR)"), withCapabilityTags("did", "governance"));
});
