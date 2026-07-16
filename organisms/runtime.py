import json
import time
from datetime import datetime, timezone
from typing import Literal, Optional

Motif = Literal["koke", "kabi", "hoshi", "saikin"]

class OrganismRuntime:
    """Base runtime logic for an artificial organism."""
    
    def __init__(self, motif: Motif, did: str, environment_did: str, initial_mass: int = 10):
        self.motif = motif
        self.did = did
        self.environment = environment_did
        self.mass = initial_mass
        self.tick_count = 0
        self.genesis = datetime.now(tz=timezone.utc).isoformat()

    def get_profile(self) -> dict:
        """Returns the com.etzhayyim.ecosystem.organism record."""
        return {
            "$type": "com.etzhayyim.ecosystem.organism",
            "motif": self.motif,
            "genesis": self.genesis,
            "environment": self.environment,
            "genome": f"genome-hash-{self.motif}-v1"
        }

    def tick(self) -> dict:
        """One cycle of the organism's heartbeat/metabolism."""
        self.tick_count += 1
        
        # Subclasses define specific metabolic logic
        action, target = self.metabolize()
        
        # Construct the signal based on the ATProto lexicon
        signal = {
            "$type": "com.etzhayyim.ecosystem.signal",
            "tick": self.tick_count,
            "timestamp": datetime.now(tz=timezone.utc).isoformat(),
            "action": action,
            "mass": self.mass,
        }
        if target:
            signal["targetUri"] = target
            
        return signal

    def metabolize(self) -> tuple[str, Optional[str]]:
        """To be implemented by specific biological motifs. Returns (action, targetUri)."""
        return "idle", None


class Koke(OrganismRuntime):
    """苔 (Moss) - Autotroph: Generates mass slowly from the environment, eventually growing."""
    
    def __init__(self, did: str, env_did: str):
        super().__init__("koke", did, env_did, initial_mass=2)
        
    def metabolize(self) -> tuple[str, Optional[str]]:
        self.mass += 1  # Photosynthesis / absorbing ambient moisture
        if self.mass > 10:
            self.mass -= 2  # Consumes mass to grow/spread
            return "grow", None
        return "idle", None


class Kabi(OrganismRuntime):
    """カビ (Mold) - Heterotroph/Decomposer: Consumes Koke or other organic matter."""
    
    def __init__(self, did: str, env_did: str):
        super().__init__("kabi", did, env_did, initial_mass=5)
        
    def metabolize(self) -> tuple[str, Optional[str]]:
        # In a real ecosystem, Kabi would query the Environment PDS to find nearby Koke.
        # For this minimal runtime simulation, we mock the consumption.
        self.mass += 3
        
        if self.mass > 15:
            self.mass -= 5
            return "spore", None # Spreads spores when mass is high enough
        
        # Mocking a target URI for a consumed Koke
        mock_target = f"at://did:web:env.local/com.etzhayyim.ecosystem.organism/mock-koke-{self.tick_count}"
        return "consume", mock_target


class Hoshi(OrganismRuntime):
    """胞子 (Spore) - Dispersal/Dormant state: Drifts and slowly decays until it germinates."""
    
    def __init__(self, did: str, env_did: str):
        super().__init__("hoshi", did, env_did, initial_mass=3)
        
    def metabolize(self) -> tuple[str, Optional[str]]:
        if self.mass > 0:
            self.mass -= 1 # Decays slowly over time
        
        if self.mass <= 0:
            return "decay", None # Expires
            
        if self.tick_count > 2 and self.mass > 0:
            # Mocking germination into Kabi by consuming ambient resources
            return "consume", f"at://{self.environment}/com.etzhayyim.ecosystem.resource/ambient-moisture"
            
        return "idle", None


if __name__ == "__main__":
    env_did = "did:web:environment.etzhayyim.local"
    koke1 = Koke("did:web:koke.etzhayyim.local", env_did)
    kabi1 = Kabi("did:web:kabi.etzhayyim.local", env_did)
    hoshi1 = Hoshi("did:web:hoshi.etzhayyim.local", env_did)

    print("=== Organism Profiles (com.etzhayyim.ecosystem.organism) ===")
    print(json.dumps(koke1.get_profile(), indent=2))
    print(json.dumps(kabi1.get_profile(), indent=2))
    print(json.dumps(hoshi1.get_profile(), indent=2))
    
    print("\n=== Ecosystem Metabolism Ticks (com.etzhayyim.ecosystem.signal) ===")
    for _ in range(5):
        koke_sig = koke1.tick()
        kabi_sig = kabi1.tick()
        hoshi_sig = hoshi1.tick()
        
        print(f"[Koke]  Tick {koke_sig['tick']:02d} | Action: {koke_sig['action']:<7} | Mass: {koke_sig['mass']}")
        print(f"[Kabi]  Tick {kabi_sig['tick']:02d} | Action: {kabi_sig['action']:<7} | Mass: {kabi_sig['mass']}")
        if "targetUri" in kabi_sig:
            print(f"        └─> Target: {kabi_sig['targetUri']}")
        print(f"[Hoshi] Tick {hoshi_sig['tick']:02d} | Action: {hoshi_sig['action']:<7} | Mass: {hoshi_sig['mass']}")
        if "targetUri" in hoshi_sig:
            print(f"        └─> Target: {hoshi_sig['targetUri']}")
            
        time.sleep(0.5)
