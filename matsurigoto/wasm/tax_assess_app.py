"""matsurigoto 政 `tax-assess` actor — WASI component (componentize-py, ADR-2606062300 R1.A).

Mirrors `methods/modules/tax_assess.cljc` (progressive marginal-bracket income/corporate
tax + VAT). G1: no signing authority here — every receipt returns unsigned; the governing
organ (Council 5-of-7 for principal A / the adopting state's own key for principal B) signs
externally. G2: brackets are the reference JP 速算表 (:representative figures anchored to
public tax law); the bracket table is the localized parameter, not the algorithm.

componentize-py binds this module's `Tax` class to the WIT `tax` interface export.
"""

from decimal import Decimal, ROUND_HALF_EVEN

from wit_world.exports.tax import Assessment, BracketLine, VatAssessment
from wit_world.imports.types import UnsignedArtifact
from componentize_py_types import Err

SERVER_HELD_AUTHORITY = False  # G1

# Reference marginal-bracket rate tables (the localized G2 parameter). Ascending
# [lower-bound-inclusive, marginal-rate]; the last bracket extends to +inf.
RATE_TABLES = {
    "JPN.income": {
        "currency": "JPY",
        "source": "所得税法 / 国税庁 速算表 (:representative)",
        "brackets": [
            (0, 0.05), (1_950_000, 0.10), (3_300_000, 0.20), (6_950_000, 0.23),
            (9_000_000, 0.33), (18_000_000, 0.40), (40_000_000, 0.45),
        ],
    },
    "FLAT20.income": {
        "currency": "XXX",
        "source": "illustrative flat 20% (:representative)",
        "brackets": [(0, 0.20)],
    },
}


def _pyround(x: float, n: int) -> float:
    """Python round(x, n): banker's rounding (HALF_EVEN) to n decimal places."""
    q = Decimal(1).scaleb(-n)
    return float(Decimal(str(x)).quantize(q, rounding=ROUND_HALF_EVEN))


def _assess_income_tax(taxable_income: float, brackets):
    if taxable_income < 0:
        raise Err("taxable_income must be >= 0")
    if not brackets:
        raise Err("brackets must be non-empty")
    n = len(brackets)
    lines = []
    for i in range(n):
        lower, rate = brackets[i]
        upper = float(brackets[i + 1][0]) if i + 1 < n else float("inf")
        if taxable_income > lower:
            amount = min(float(taxable_income), upper) - lower
            tax = amount * rate
            lines.append({"lower": lower, "upper": upper, "rate": rate,
                          "taxable_in_bracket": amount, "tax_in_bracket": tax})
    total = sum(ln["tax_in_bracket"] for ln in lines)
    effective_rate = _pyround(total / taxable_income, 6) if taxable_income else 0.0
    return {
        "taxable_income": taxable_income,
        "liability": _pyround(total, 2),
        "effective_rate": effective_rate,
        "lines": [{"lower": ln["lower"], "rate": ln["rate"],
                   "tax_in_bracket": _pyround(ln["tax_in_bracket"], 2)} for ln in lines],
    }


def _unsigned_receipt(amount: float, currency: str) -> UnsignedArtifact:
    return UnsignedArtifact(
        kind="tax-receipt",
        subject=currency,
        record_id="",
        proof=None,                              # G1 — this module signs nothing
        status="assessed-unsigned",
    )


class Tax:
    def assess_income(self, gross: float, deductions: float, table_key: str) -> Assessment:
        table = RATE_TABLES.get(table_key)
        if table is None:
            raise Err(f"unknown rate table {table_key!r}")
        taxable = max(0.0, gross - deductions)
        out = _assess_income_tax(taxable, table["brackets"])
        return Assessment(
            taxable_income=out["taxable_income"],
            liability=out["liability"],
            effective_rate=out["effective_rate"],
            currency=table["currency"],
            rate_table=table_key,
            brackets=[BracketLine(lower=ln["lower"], rate=ln["rate"],
                                  tax_in_bracket=ln["tax_in_bracket"]) for ln in out["lines"]],
            receipt=_unsigned_receipt(out["liability"], table["currency"]),
        )

    def assess_vat(self, output_vat: float, input_vat: float, currency: str) -> VatAssessment:
        net = _pyround(output_vat - input_vat, 2)
        net_due = net if net > 0 else 0.0
        refund_due = -net if net < 0 else 0.0
        return VatAssessment(
            net_vat_due=net_due,
            refund_due=refund_due,
            currency=currency,
            receipt=_unsigned_receipt(net_due, currency),
        )
