import json
from typing import Dict, List, Any
from runtime import Koke, Kabi, Hoshi, OrganismRuntime

class LocalEcosystemPDS:
    """Mock Local AT Protocol PDS/Environment for Organism Runtimes."""
    
    def __init__(self, environment_did: str):
        self.environment_did = environment_did
        self.organisms: Dict[str, OrganismRuntime] = {}
        self.records: Dict[str, Any] = {}
        self.tick_count = 0

    def register_organism(self, org: OrganismRuntime):
        """Registers an organism with the local PDS."""
        uri = f"at://{self.environment_did}/com.etzhayyim.ecosystem.organism/{org.motif}-{org.did.split(':')[-1]}"
        self.organisms[uri] = org
        self.records[uri] = org.get_profile()
        print(f"[PDS] Registered {org.motif} at {uri}")
        return uri

    def tick(self):
        """Advances the ecosystem by one tick."""
        self.tick_count += 1
        print(f"\n--- Ecosystem Tick {self.tick_count:02d} ---")
        
        signals = []
        new_organisms = []
        dead_uris = []
        
        # Collect signals from all live organisms
        for uri, org in self.organisms.items():
            signal = org.tick()
            signals.append((uri, signal))
            
            action = signal["action"]
            target_uri = signal.get("targetUri")
            
            # Print signal emission
            print(f"[{org.motif.upper()}] {uri.split('/')[-1]} | Action: {action:<7} | Mass: {signal['mass']}")
            
            if action == "spore":
                # Kabi released spores -> spawn new Hoshi
                new_did = f"did:web:hoshi-{self.tick_count}.etzhayyim.local"
                new_org = Hoshi(new_did, self.environment_did)
                new_organisms.append(new_org)
                print(f"  └─> Spawning new Hoshi: {new_did}")
                
            elif action == "consume" and target_uri:
                # Needs to consume target
                # If target is mock, we try to find a real target or just let it pass
                if target_uri in self.organisms:
                    target_org = self.organisms[target_uri]
                    print(f"  └─> Consumed {target_org.motif} at {target_uri.split('/')[-1]}")
                    dead_uris.append(target_uri)
                else:
                    # In a real environment, it would fail or use ambient resources
                    print(f"  └─> Consumed ambient/mock resource: {target_uri.split('/')[-1]}")

            elif action == "decay" and signal["mass"] <= 0:
                print(f"  └─> {org.motif} at {uri.split('/')[-1]} has decayed and died.")
                dead_uris.append(uri)
                
            elif action == "grow":
                # Koke grows and splits/spreads
                new_did = f"did:web:koke-{self.tick_count}.etzhayyim.local"
                new_org = Koke(new_did, self.environment_did)
                new_organisms.append(new_org)
                print(f"  └─> Koke spread to new location: {new_did}")

        # Remove dead organisms
        for uri in dead_uris:
            if uri in self.organisms:
                del self.organisms[uri]
                del self.records[uri]
                
        # Register new organisms
        for org in new_organisms:
            self.register_organism(org)
            
        print(f"--- Ecosystem Stats: {len(self.organisms)} organisms alive ---")

if __name__ == "__main__":
    pds = LocalEcosystemPDS("did:web:environment.etzhayyim.local")
    
    # Bootstrap initial population
    koke_base = Koke("did:web:koke-base.etzhayyim.local", pds.environment_did)
    kabi_base = Kabi("did:web:kabi-base.etzhayyim.local", pds.environment_did)
    hoshi_base = Hoshi("did:web:hoshi-base.etzhayyim.local", pds.environment_did)
    
    # We tweak Kabi to actually target Koke instead of a mock URI
    # This requires modifying the Kabi instance's target for simulation purposes
    # For now, Kabi returns a mock URI in runtime.py. We will just run the simulation.
    
    pds.register_organism(koke_base)
    pds.register_organism(kabi_base)
    pds.register_organism(hoshi_base)
    
    for _ in range(12):
        pds.tick()
