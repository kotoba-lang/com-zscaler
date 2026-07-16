"""noroshi (烽) verifiable method cores — stdlib only (ADR-2606051600).

Three deterministic, offline-testable cores under the photonic-electronic convergence actor:

  link_budget     — the chip face: optical/CPO link power budget + energy-per-bit.
  isac_sim        — the isac face: OFDM JCAS communication capacity + range-Doppler sensing.
  active_alignment — the packaging-robotics face: fiber↔grating active alignment search with a
                     laser-safety interlock (the safety-critical coded-cell core, like tazuna's
                     teleop_safety).

None of these touch hardware, a foundry, or a live laser (all outward-gated, G7). They are pure
arithmetic / DSP so the design can be unit-tested before any silicon exists.
"""
