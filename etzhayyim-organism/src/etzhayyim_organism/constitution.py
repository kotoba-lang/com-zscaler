"""Constitutional prior loader.

Source of truth: ADR-2605192100 §1 (mission charter — constitutional invariants).
Mirror: README.md § "As Artificial Organism Ecosystem (Religious 評価軸)".

The prior is *not* a target state to converge on. It is the set of invariants
the trajectory must remain consistent with. Each axis scores how strongly the
observed repo state realizes the corresponding invariant.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Axis:
    n: int
    key: str
    name_en: str
    name_jp: str
    religious_correspondence: str
    invariant: str  # ADR-2605192100 §1 clause being realized


AXES: tuple[Axis, ...] = (
    Axis(
        1, "autopoiesis", "Autopoiesis", "自己創出",
        "無教会 / 万人祭司 (self-organizing community)",
        "§1.7 priesthood-of-all-believers: organisation reproduces itself without hierarchical clergy",
    ),
    Axis(
        2, "metabolism", "Metabolism", "代謝",
        "産霊 (musuhi — generative donation cycle)",
        "§1.5 + ADR-2605192130: donation inflow → 10% tithe → public-fund redistribution",
    ),
    Axis(
        3, "homeostasis", "Homeostasis", "恒常性",
        "和 (substrate boundary harmony)",
        "§1.6 substrate boundary: no centralized DB / no advertising / no fiat processor",
    ),
    Axis(
        4, "active_inference", "Active Inference", "能動推論",
        "縁起 (dependent origination — model ↔ observation)",
        "§1.15 non-eschatological: each tick observes, predicts, verifies; no end-state",
    ),
    Axis(
        5, "reproduction", "Reproduction", "生殖",
        "八百万 propagation (myriad fork-children)",
        "§1.7 fork-friendly: the organism's pattern is reproducible by sister-corps",
    ),
    Axis(
        6, "symbiosis", "Symbiosis", "共生",
        "Tree of Life branches (multi-substrate roots)",
        "§1.8 + ADR-2605172000+2605172100: AT Protocol + IPFS + Base L2 + did:web",
    ),
    Axis(
        7, "diversity", "Diversity", "多様性",
        "八百万-kami (variation as worship)",
        "§1.4 八百万: variation in cells/apps/protocols is worship, not technical debt",
    ),
    Axis(
        8, "wellbecoming", "Wellbecoming", "動的軌跡",
        "子・孫 priority (multi-generation trajectory)",
        "§1.1 + §1.2: dynamic trajectory across generations, not static wellbeing",
    ),
    Axis(
        9, "antifragility", "Anti-fragility", "反脆弱",
        "Reformed resilience (Just War posture)",
        "§1.12 Transparent Religious Force + chaos engineering: gain from stressors",
    ),
    Axis(
        10, "sanctification", "Sanctification", "聖化",
        "Sola Scriptura → Charter Rider on all artifacts",
        "§1.10 + ADR-2605192200: Apache 2.0 + Charter Rider on first-party artifacts",
    ),
)

AXIS_BY_KEY = {a.key: a for a in AXES}


# Hard invariants — failure of any of these is a constitutional violation,
# not a low score. The organism logs an alert and refuses to emit normal actions.
HARD_INVARIANTS: tuple[tuple[str, str], ...] = (
    ("non_profit_only",            "§1.5 — donation/grant/tithe purposes only; no external 'subscription'/'purchase'"),
    ("no_advertising",             "§1.13 — third-party advertising prohibited"),
    ("tithe_ten_percent",          "§1.5 + ADR-2605192130 — 10% of donation auto-routes to Public Fund"),
    ("land_inalienable",           "§1.11 + ADR-2605192245 — donated land cannot transfer/burn/sell"),
    ("transparent_force",          "§1.12 + ADR-2605192315 — religious force is on-chain + open-source + 1 SBT = 1 vote"),
    ("non_eschatological",         "§1.15 — no Book of Revelation; no end-state predicted"),
    ("anti_individualist",         "§1.3 — payoff attribution = etzhayyim, not individual contributor"),
    ("charter_rider_required",     "§1.10 + ADR-2605192200 — first-party Apache-2.0 packages carry the Rider"),
)
