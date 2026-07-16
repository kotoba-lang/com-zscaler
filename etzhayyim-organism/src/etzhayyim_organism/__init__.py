"""etzhayyim-organism — religious-corp artificial-organism daemon.

The constitution (ADR-2605192100 §1) is the prior; `_observations/*-cycle-NN.md`
is the variational posterior. Each tick reads repo state, scores 10 axes against
the prior, picks the lowest-score × highest-leverage gap, and emits one action.

Non-eschatological active inference (ADR-2605192100 §1.15): there is no target
total. The trajectory is the wellbecoming.
"""

__version__ = "0.1.0"
