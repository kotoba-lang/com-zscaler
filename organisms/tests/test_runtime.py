#!/usr/bin/env python3
"""organisms — runtime + environment tests (coverage loop iteration 3).

The actor had a runnable ecosystem simulation (runtime.py 133 + environment.py
100 lines) with zero tests — the last real gap in 20-actors besides the
intentionally-disabled kotodama Council-gated cell stubs. Pure stdlib.

Run: PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest tests/ -q
"""
import pathlib
import sys

ACTOR_DIR = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ACTOR_DIR))

from runtime import Koke, Kabi, Hoshi, OrganismRuntime  # noqa: E402
from environment import LocalEcosystemPDS  # noqa: E402

ENV_DID = "did:web:environment.etzhayyim.local"


# ── lexicon-shaped outputs ───────────────────────────────────────────────────

def test_profile_matches_organism_lexicon_shape():
    koke = Koke("did:web:koke.test", ENV_DID)
    p = koke.get_profile()
    assert p["$type"] == "com.etzhayyim.ecosystem.organism"
    assert p["motif"] == "koke"
    assert p["environment"] == ENV_DID
    assert p["genome"].startswith("genome-hash-koke")


def test_tick_signal_shape_and_monotonic_tick():
    kabi = Kabi("did:web:kabi.test", ENV_DID)
    s1, s2 = kabi.tick(), kabi.tick()
    for s in (s1, s2):
        assert s["$type"] == "com.etzhayyim.ecosystem.signal"
        assert isinstance(s["mass"], int)
    assert (s1["tick"], s2["tick"]) == (1, 2)
    # base runtime stays idle with no target
    base = OrganismRuntime("saikin", "did:web:base.test", ENV_DID)
    s = base.tick()
    assert s["action"] == "idle" and "targetUri" not in s


# ── motif metabolism invariants ──────────────────────────────────────────────

def test_koke_accrues_then_grows_and_pays_mass():
    koke = Koke("did:web:koke.test", ENV_DID)  # mass 2, +1/tick, grow when >10
    actions = []
    for _ in range(12):
        before = koke.mass
        sig = koke.tick()
        actions.append(sig["action"])
        if sig["action"] == "grow":
            assert sig["mass"] == before - 1  # +1 photosynthesis, -2 growth cost
    assert actions[:8] == ["idle"] * 8
    assert actions[8] == "grow"  # mass 2 → first exceeds 10 on tick 9
    assert all(a in ("idle", "grow") for a in actions)


def test_kabi_consumes_with_target_until_sporing():
    kabi = Kabi("did:web:kabi.test", ENV_DID)  # mass 5, +3/tick, spore when >15
    sigs = [kabi.tick() for _ in range(4)]
    assert [s["action"] for s in sigs] == ["consume", "consume", "consume", "spore"]
    for s in sigs[:3]:
        assert s["targetUri"].startswith("at://")
    assert "targetUri" not in sigs[3]  # spores broadcast, not targeted
    assert sigs[3]["mass"] == 12  # 5+3*4 = 17 > 15 → -5


def test_hoshi_decays_to_death_with_default_mass():
    hoshi = Hoshi("did:web:hoshi.test", ENV_DID)  # mass 3, -1/tick
    sigs = [hoshi.tick() for _ in range(3)]
    assert [s["action"] for s in sigs] == ["idle", "idle", "decay"]
    assert sigs[2]["mass"] == 0
    assert hoshi.mass == 0  # never negative


def test_hoshi_germinates_when_it_survives_past_two_ticks():
    hoshi = Hoshi("did:web:hoshi.test", ENV_DID)
    hoshi.mass = 10  # enough reserve to outlive the dormancy window
    sigs = [hoshi.tick() for _ in range(4)]
    assert [s["action"] for s in sigs[:2]] == ["idle", "idle"]
    assert sigs[2]["action"] == "consume"
    assert sigs[2]["targetUri"].endswith("/ambient-moisture")
    assert ENV_DID in sigs[2]["targetUri"]


# ── environment (mock PDS) population dynamics ───────────────────────────────

def test_register_organism_uri_shape_and_record():
    pds = LocalEcosystemPDS(ENV_DID)
    koke = Koke("did:web:koke-base.etzhayyim.local", ENV_DID)
    uri = pds.register_organism(koke)
    assert uri.startswith(f"at://{ENV_DID}/com.etzhayyim.ecosystem.organism/koke-")
    assert pds.records[uri]["$type"] == "com.etzhayyim.ecosystem.organism"


def test_spore_spawns_hoshi_and_grow_spawns_koke():
    pds = LocalEcosystemPDS(ENV_DID)
    pds.register_organism(Kabi("did:web:kabi-base.local", ENV_DID))
    for _ in range(4):  # kabi spores on its 4th tick
        pds.tick()
    motifs = sorted(o.motif for o in pds.organisms.values())
    assert motifs == ["hoshi", "kabi"], motifs

    pds2 = LocalEcosystemPDS(ENV_DID)
    pds2.register_organism(Koke("did:web:koke-base.local", ENV_DID))
    for _ in range(9):  # koke grows on its 9th tick
        pds2.tick()
    assert sorted(o.motif for o in pds2.organisms.values()) == ["koke", "koke"]


def test_decayed_organism_is_removed_from_pds():
    pds = LocalEcosystemPDS(ENV_DID)
    uri = pds.register_organism(Hoshi("did:web:hoshi-base.local", ENV_DID))
    for _ in range(3):
        pds.tick()
    assert uri not in pds.organisms
    assert uri not in pds.records
    assert len(pds.organisms) == 0


def test_consuming_a_registered_target_removes_it():
    class Predator(OrganismRuntime):
        def __init__(self, target_uri):
            super().__init__("kabi", "did:web:predator.local", ENV_DID, initial_mass=5)
            self._target = target_uri

        def metabolize(self):
            return "consume", self._target

    pds = LocalEcosystemPDS(ENV_DID)
    prey_uri = pds.register_organism(Koke("did:web:prey.local", ENV_DID))
    pds.register_organism(Predator(prey_uri))
    pds.tick()
    assert prey_uri not in pds.organisms
    assert len(pds.organisms) == 1  # predator remains
