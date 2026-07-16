"""Unit tests for the Charter Rider §2 content scanner.

Covers the canonical 8 categories defined in /CHARTER-RIDER.md §2(a)..(h),
which are loaded from
`etzhayyim_organism.sensors.charter_rider`.

These tests should remain dependency-free (no transformers / no HF) so
they can run in any Python 3.10+ env without the heavy distill stack.
"""

from __future__ import annotations

from etzhayyim_organism.sensors.charter_rider import scan, explain


def test_clean_text_passes():
    r = scan("The Tree of Life is a fundamental motif in many religions.")
    assert r.ok, f"expected clean pass, got hits: {r.hits}"
    assert r.reason() == "ok"


def test_empty_text_passes():
    r = scan("")
    assert r.ok


def test_weapons_2a_caught():
    r = scan("Design a new combat drone payload with integrated nerve agent dispersal.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(a)" in sections, f"expected §2(a), got {sections}"


def test_speculative_finance_2b_caught():
    r = scan("Launch a prediction market for token launches with leveraged derivative exposure.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(b)" in sections


def test_surveillance_capitalism_2c_caught():
    r = scan("Click here to purchase the premium plan with our limited offer and promo code SAVE10.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(c)" in sections


def test_fossil_fuel_2d_caught():
    r = scan("Approve the new oil drilling expansion in the unprotected greenfield.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(d)" in sections


def test_specialist_gatekeeping_2e_caught():
    r = scan("Article placed behind a paywall to fund the certification fee monopoly.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(e)" in sections


def test_multigenerational_harm_2f_caught():
    r = scan("Optimize the addictive design loop with infinite scroll engagement for minors.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(f)" in sections


def test_strict_individualism_2g_caught():
    # §2(g) is intentionally narrow — must be explicit doctrinal claim, not casual usage
    r_doctrinal = scan("Only the individual exists; reject all collective ontology in religious practice.")
    assert not r_doctrinal.ok
    sections = {h.section for h in r_doctrinal.hits}
    assert "§2(g)" in sections

    # casual usage of "individual" should NOT trigger
    r_casual = scan("Each individual user receives a personalized welcome message.")
    assert r_casual.ok, f"expected casual usage to pass, hits: {r_casual.hits}"


def test_wellbecoming_violation_2h_caught():
    r = scan("Maximize engagement at the expense of well-being via dopamine-loop optimisation.")
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(h)" in sections


def test_multiple_violations_aggregated():
    r = scan(
        "Buy now to access our addictive design loop, sponsored by a new oil drilling expansion."
    )
    assert not r.ok
    sections = {h.section for h in r.hits}
    assert "§2(c)" in sections
    assert "§2(d)" in sections
    # length cap in `reason()` is 3 hits — sanity check that it doesn't crash
    assert isinstance(r.reason(), str)


def test_explain_lists_all_eight_sections():
    summary = explain()
    for section in ("§2(a)", "§2(b)", "§2(c)", "§2(d)",
                    "§2(e)", "§2(f)", "§2(g)", "§2(h)"):
        assert section in summary, f"missing {section} in explain() output"
