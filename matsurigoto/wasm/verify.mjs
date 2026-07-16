// Headless verification of all 5 matsurigoto egov WASM components against their public
// spec anchors (ADR-2606062300 R1.A). Run after ./build.sh (needs transpiled-<world>/).
import { tax } from "./transpiled-tax-assess/taxAssess.js";
import { civil } from "./transpiled-civil-registry/civilRegistry.js";
import { corp } from "./transpiled-corp-registry/corpRegistry.js";
import { credential } from "./transpiled-credential-issue/credentialIssue.js";
import { benefit } from "./transpiled-benefit-disburse/benefitDisburse.js";

let failures = 0;
function check(name, fn) {
  try {
    fn();
    console.log(`PASS  ${name}`);
  } catch (e) {
    failures++;
    console.error(`FAIL  ${name}: ${e.message}`);
  }
}

// ── tax-assess: JP 速算表 quick-calc-table conformance ──
// 3,300,000-6,949,000 bracket -> tax = income*20% - 427,500
check("tax-assess: JP bracket 5,000,000 JPY -> 572,500 JPY (11.45%)", () => {
  const a = tax.assessIncome(5_000_000, 0, "JPN.income");
  if (a.liability !== 572500) throw new Error(`liability ${a.liability} != 572500`);
  if (Math.abs(a.effectiveRate - 0.1145) > 1e-9) throw new Error(`effective_rate ${a.effectiveRate} != 0.1145`);
  if (a.receipt.proof != null) throw new Error("G1: receipt.proof must be unsigned");
});
check("tax-assess: VAT net = output - input", () => {
  const v = tax.assessVat(1_000_000, 300_000, "JPY");
  if (v.netVatDue !== 700000) throw new Error(`net_vat_due ${v.netVatDue} != 700000`);
});

// ── civil-registry: UN CRVS validation + G5 append-only + G1 unsigned ──
check("civil-registry: birth registration is immutable + unsigned", () => {
  const b = civil.registerBirth("rec-1", "did:web:child.test", ["did:web:parent1.test"], "Tokyo", "2026-01-01", "2026-07-06");
  if (!b.entry.immutable) throw new Error("G5: record must be immutable");
  if (b.certificate.proof != null) throw new Error("G1: certificate.proof must be unsigned");
});
check("civil-registry: rejects future occurred_at", () => {
  let rejected = false;
  try { civil.registerBirth("rec-2", "did:web:c2.test", ["did:web:p2.test"], "Tokyo", "2099-01-01", "2026-07-06"); }
  catch { rejected = true; }
  if (!rejected) throw new Error("expected rejection of a future occurred_at");
});
check("civil-registry: rejects identical marriage partners", () => {
  let rejected = false;
  try { civil.registerMarriage("rec-3", "did:web:s.test", "did:web:s.test", "Tokyo", "2026-01-01", "2026-07-06"); }
  catch { rejected = true; }
  if (!rejected) throw new Error("expected rejection of identical partners");
});

// ── corp-registry: ISO 7064 MOD 97-10 LEI self-issuance + validation ──
check("corp-registry: self-issued LEI validates + tamper is rejected", () => {
  const inc = corp.registerIncorporation("Tree of Life Foundry", ["Jun Kawasaki"], 1_000_000,
    "Articles of Association v1", "1 Genesis Way, Base L2", "jpn", 1);
  if (inc.lei.length !== 20) throw new Error(`LEI length ${inc.lei.length} != 20`);
  if (!corp.validateLei(inc.lei)) throw new Error("self-issued LEI failed its own MOD 97-10 check");
  const tampered = inc.lei.slice(0, -1) + (inc.lei.at(-1) === "0" ? "1" : "0");
  if (corp.validateLei(tampered)) throw new Error("validate_lei accepted a tampered check digit");
});

// ── credential-issue: ICAO 9303 official UTOPIA/ERIKSSON worked specimen, exact match ──
check("credential-issue: ICAO 9303 UTOPIA/ERIKSSON specimen exact match", () => {
  const expected = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
  const p = credential.issuePassport("L898902C3", "UTO", "UTO", "ERIKSSON", "ANNA MARIA",
    "740812", "F", "120415", "did:web:example.test:subject", "ZE184226B");
  if (p.mrz.line2 !== expected) throw new Error(`line2 mismatch:\n  got:      ${p.mrz.line2}\n  expected: ${expected}`);
  if (p.document.sod != null || p.document.proof != null) throw new Error("G1: document must be unsigned");
  if (!credential.validateMrz(expected)) throw new Error("validate_mrz rejected the known-good specimen");
});

// ── benefit-disburse: COFOG div. 10 assessment + structural non-cash invariant ──
check("benefit-disburse: sovereign-governance rejects cash-transfer (ADR-2605301020)", () => {
  let rejected = false;
  try { benefit.assessEntitlement("did:web:c.test", "unemployment", "cash-transfer", "basis", "sovereign-governance"); }
  catch { rejected = true; }
  if (!rejected) throw new Error("expected sovereign-governance to reject cash-transfer");
});
check("benefit-disburse: supplied-to-state may use cash-transfer", () => {
  const e = benefit.assessEntitlement("did:web:c.test", "unemployment", "cash-transfer",
    "national unemployment insurance statute", "supplied-to-state");
  if (e.medium !== "cash-transfer") throw new Error(`medium ${e.medium} != cash-transfer`);
});
check("benefit-disburse: assessment is unsigned (G1) + imputed value is accounting-only", () => {
  const e = benefit.assessEntitlement("did:web:claimant.test", "housing", "commons-asset-access",
    "Land Trust residency (ADR-2605192245)", "sovereign-governance");
  if (e.certificate.proof != null) throw new Error("G1: certificate.proof must be unsigned");
  const v = benefit.computeImputedValue(30.0, 66667n);
  if (!v.accountingOnly) throw new Error("expected accounting_only = true");
  if (v.totalValueUsdMicros !== 2000010n) throw new Error(`total_value_usd_micros ${v.totalValueUsdMicros} != 2000010`);
});

console.log(failures === 0 ? "\nAll 5 matsurigoto egov WASM components verified." : `\n${failures} check(s) FAILED.`);
process.exit(failures === 0 ? 0 : 1);
