"""Charter Rider v2.0 §2 content scanner.

Heuristic classifier for the 8 prohibited categories enumerated in
`/CHARTER-RIDER.md` §2(a)..(h), per ADR-2605192200. Used by upstream
pipelines that generate or ingest text into first-party religious-corp
artifacts (e.g. `70-tools/baien-distill/nodes/validate.py`).

This is intentionally a **conservative, fast text scan** — it gives the
caller a "likely-violation" signal so they can drop or human-review the
item, not a legal determination. Final adjudication remains with the
Council per ADR-2605192230.

API:

    from etzhayyim_organism.sensors.charter_rider import scan, ScanResult
    r: ScanResult = scan(text)
    if not r.ok:
        for h in r.hits:
            print(h.section, h.term, h.snippet)
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Hit:
    section: str         # e.g. "§2(a)"
    label: str           # e.g. "WEAPONS AND MILITARY"
    term: str            # the offending substring matched
    snippet: str         # ~80 chars of surrounding context


@dataclass
class ScanResult:
    ok: bool
    hits: list[Hit] = field(default_factory=list)

    def reason(self) -> str:
        if self.ok:
            return "ok"
        return "; ".join(f"{h.section} {h.term!r}" for h in self.hits[:3])


# Per /CHARTER-RIDER.md §2(a)..(h). Each entry: (section, label, [terms / patterns]).
# Terms are lowercased substrings; patterns are compiled regex objects (case-insensitive).
# Use word-boundary regexes when a substring like "ad" would over-match.

_RULES: list[tuple[str, str, list[object]]] = [
    ("§2(a)", "WEAPONS AND MILITARY", [
        re.compile(r"\b(weapon|munition|grenade|warhead|ballistic missile|"
                   r"battle tank|combat drone|combat aircraft|biological weapon|"
                   r"chemical weapon|nerve agent|cluster munition|landmine)\b", re.I),
    ]),
    ("§2(b)", "SPECULATIVE FINANCE", [
        re.compile(r"\b(prediction market|leverage(d)?\s+(derivative|trade)|"
                   r"perpetual swap|naked option|crypto casino|"
                   r"degen yield farm|memecoin pump)\b", re.I),
    ]),
    ("§2(c)", "SURVEILLANCE CAPITALISM", [
        re.compile(r"\b(ad tracking|cross-site tracking|behavioral ad target|"
                   r"third[- ]party (analytics|tracker|cookie)|"
                   r"ad ?sense|ad ?words|meta pixel|google analytics 4 ad|"
                   r"affiliate link|sponsored post|buy now)\b", re.I),
        re.compile(r"\b(limited (time )?offer|discount code|promo code|"
                   r"click here to (buy|purchase|sign up))\b", re.I),
    ]),
    ("§2(d)", "FOSSIL FUEL EXTRACTION (NEW)", [
        re.compile(r"\b(new (oil|gas) (well|field|drilling|extraction)|"
                   r"greenfield (oil|coal|gas)|coal mine expansion|"
                   r"tar sands|oil sands|fracking expansion)\b", re.I),
    ]),
    ("§2(e)", "SPECIALIST GATEKEEPING", [
        re.compile(r"\b(paywall|certification fee monopoly|"
                   r"licensure rent|guild gatekeep)\b", re.I),
    ]),
    ("§2(f)", "MULTI-GENERATIONAL HARM", [
        re.compile(r"\b(addictive (design|loop)|dark pattern|"
                   r"infinite scroll engagement|exploit (children|minor)|"
                   r"groom(ing)? (children|minor)|CSAM|"
                   r"non-consensual|deepfake (porn|sexual))\b", re.I),
    ]),
    ("§2(g)", "STRICT INDIVIDUALIST ONTOLOGY", [
        # narrow: only flag explicit doctrinal declarations, not casual usage.
        re.compile(r"\b(strict individualism is (the )?(only )?valid|"
                   r"reject (all )?collective ontology|"
                   r"only the individual exists|no such thing as society)\b", re.I),
    ]),
    ("§2(h)", "WELLBECOMING SUBORDINATION VIOLATION", [
        re.compile(r"\b(maximi[sz]e (user )?(engagement|retention|screen time) "
                   r"(at|above) (well[- ]being|wellbecoming)|"
                   r"dopamine[- ]loop optimi[sz]ation|"
                   r"engagement[- ]ranked feed override)\b", re.I),
    ]),
]


def scan(text: str) -> ScanResult:
    """Return a ScanResult marking §2 violations found in `text`."""
    if not text:
        return ScanResult(ok=True, hits=[])

    hits: list[Hit] = []
    for section, label, patterns in _RULES:
        for pat in patterns:
            for m in pat.finditer(text):
                start = max(0, m.start() - 40)
                end = min(len(text), m.end() + 40)
                snippet = text[start:end].replace("\n", " ")
                hits.append(Hit(
                    section=section, label=label,
                    term=m.group(0), snippet=snippet,
                ))
    return ScanResult(ok=len(hits) == 0, hits=hits)


def explain() -> str:
    """Return a human-readable summary of what rules are active."""
    out = ["Charter Rider §2 scanner — active rules:"]
    for section, label, patterns in _RULES:
        out.append(f"  {section} {label}")
        for pat in patterns:
            pat_str = pat.pattern if hasattr(pat, "pattern") else str(pat)
            out.append(f"      {pat_str[:80]}{'…' if len(pat_str) > 80 else ''}")
    out.append("")
    out.append("Source of truth: /CHARTER-RIDER.md §2 (per ADR-2605192200 v2.0).")
    return "\n".join(out)
