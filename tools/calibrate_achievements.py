#!/usr/bin/env python3
"""Summarize anonymous vanilla-stat snapshots and propose reviewable achievement thresholds.

This is an offline design aid, not a prediction of when a player first unlocked a
achievement. Online time includes AFK time. Candidate cohorts and quantiles are explicit
design assumptions; a missing cohort never silently falls back to another cohort.
Only aggregate statistics are emitted. No player identifiers are required.
"""

from __future__ import annotations

import argparse
from decimal import Decimal, ROUND_HALF_UP
import json
import math
from pathlib import Path
import sys
from typing import Any


INT_MAX = 2_147_483_647
LONG_MAX = 9_223_372_036_854_775_807
TICKS_PER_HOUR = 72_000
TIERS = ("bronze", "silver", "gold")
# Configuration key, source column, source units per configured unit, source cap.
METRICS = (
    ("mining", "mining", 1, LONG_MAX),
    ("crafting", "crafting", 1, LONG_MAX),
    ("elytra", "elytra_cm", 100_000, INT_MAX),
    ("fishing", "fishing", 1, INT_MAX),
    ("trading", "trading", 1, INT_MAX),
    ("walking", "walking_cm", 100_000, INT_MAX),
)
GROUPS = (
    ("under_1h", 0, 1),
    ("1_to_10h", 1, 10),
    ("10_to_50h", 10, 50),
    ("50_to_200h", 50, 200),
    ("200_to_500h", 200, 500),
    ("500h_and_above", 500, math.inf),
)
QUANTILES = (("p10", .10), ("p25", .25), ("p50", .50),
             ("p75", .75), ("p90", .90), ("p95", .95))
# A longer total playtime need not imply greater involvement in a particular hobby.
# These uniform floors preserve milestones reached by dedicated shorter-time players.
CROSS_COHORT_GUARDS = {"silver": (10., 50., .75), "gold": (50., math.inf, .90)}


def load_rows(text: str) -> list[dict[str, int]]:
    stripped = text.lstrip()
    if not stripped:
        return []
    try:
        if stripped.startswith("["):
            incoming = json.loads(stripped)
        else:
            incoming = [json.loads(line) for line in text.splitlines() if line.strip()]
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid JSON near line {error.lineno}: {error.msg}") from None
    rows: list[dict[str, int]] = []
    fields = (("played_ticks", INT_MAX),) + tuple((m[1], m[3]) for m in METRICS)
    for index, source in enumerate(incoming, 1):
        if not isinstance(source, dict):
            raise ValueError(f"Row {index} must be an object")
        row = {}
        for field, maximum in fields:
            value = source.get(field)
            if type(value) is not int or value < 0 or value > maximum:
                raise ValueError(f"Row {index}: {field} must be an integer in 0..{maximum}")
            row[field] = value
        rows.append(row)
    return rows


def percentile(values: list[int], fraction: float) -> Decimal | None:
    """Linear interpolation at (n - 1) * p; Decimal avoids integer overflow/loss."""
    if not values:
        return None
    ordered = sorted(values)
    position = Decimal(len(ordered) - 1) * Decimal(str(fraction))
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    weight = position - lower
    return Decimal(ordered[lower]) * (1 - weight) + Decimal(ordered[upper]) * weight


def json_number(value: Decimal | None, scale: int = 1) -> int | float | None:
    if value is None:
        return None
    converted = value / scale
    return int(converted) if converted == int(converted) else float(converted)


def ratio(count: int, total: int) -> float | None:
    return round(count / total, 6) if total else None


def cohort(rows: list[dict[str, int]], lower: float, upper: float) -> list[dict[str, int]]:
    return [row for row in rows
            if lower * TICKS_PER_HOUR <= row["played_ticks"] < upper * TICKS_PER_HOUR]


def interval(lower: float, upper: float) -> dict[str, float | None]:
    return {"hours_min_inclusive": lower,
            "hours_max_exclusive": upper if math.isfinite(upper) else None}


def distribution(values: list[int], scale: int) -> dict[str, Any]:
    return {
        "n": len(values),
        "min": json_number(Decimal(min(values)), scale) if values else None,
        "max": json_number(Decimal(max(values)), scale) if values else None,
        "quantiles": {key: json_number(percentile(values, p), scale) for key, p in QUANTILES},
    }


def cohort_summary(rows: list[dict[str, int]]) -> dict[str, Any]:
    metrics = {}
    for name, column, scale, _ in METRICS:
        values = [row[column] for row in rows]
        positive = [value for value in values if value > 0]
        metrics[name] = {
            "unit": "km" if scale > 1 else "count",
            "positive_participants": len(positive),
            "participation_rate": ratio(len(positive), len(values)),
            "all_players": distribution(values, scale),
            "positive_participants_only": distribution(positive, scale),
        }
    return {
        "players": len(rows),
        "played_hours": distribution([row["played_ticks"] for row in rows], TICKS_PER_HOUR),
        "metrics": metrics,
    }


def readable_threshold(raw: Decimal, scale: int) -> int:
    """Two significant digits, rounded to whole config units, minimum one unit."""
    value = raw / scale
    quantum = Decimal(1).scaleb(max(0, value.adjusted() - 1))
    return max(1, int(value.quantize(quantum, rounding=ROUND_HALF_UP)))


def source_quantile(rows: list[dict[str, int]], column: str, scale: int,
                    lower: float, upper: float, fraction: float,
                    min_samples: int) -> tuple[Decimal | None, dict[str, Any]]:
    subset = cohort(rows, lower, upper)
    positive = [row[column] for row in subset if row[column] > 0]
    raw = percentile(positive, fraction) if len(positive) >= min_samples else None
    return raw, {
        **interval(lower, upper),
        "positive_participant_quantile": fraction,
        "cohort_players": len(subset),
        "positive_samples": len(positive),
        "participation_rate": ratio(len(positive), len(subset)),
        "unrounded_quantile": json_number(raw, scale),
        "status": "available" if raw is not None else "insufficient-data",
    }


def achievement(rows: list[dict[str, int]], column: str, raw_threshold: int) -> dict[str, Any]:
    achieved = sum(row[column] >= raw_threshold for row in rows)
    participants = sum(row[column] > 0 for row in rows)
    return {
        "players": len(rows), "positive_participants": participants, "achieved": achieved,
        "all_player_rate": ratio(achieved, len(rows)),
        "positive_participant_rate": ratio(achieved, participants),
    }


def per_group_achievement(rows: list[dict[str, int]], column: str, raw_threshold: int) -> dict[str, Any]:
    return {
        "all_players": achievement(rows, column, raw_threshold),
        "playtime_groups": {
            name: achievement(cohort(rows, lower, upper), column, raw_threshold)
            for name, lower, upper in GROUPS
        },
    }


def combined_achievement(rows: list[dict[str, int]], thresholds: dict[str, dict[str, int]]) -> dict[str, Any]:
    """All six categories are necessary for an honest all-achievements comparison."""
    at_least_one = sum(any(row[column] >= thresholds[name]["bronze"] * scale
                          for name, column, scale, _ in METRICS) for row in rows)
    all_gold = sum(all(row[column] >= thresholds[name]["gold"] * scale
                       for name, column, scale, _ in METRICS) for row in rows)
    return {
        "players": len(rows),
        "at_least_one_bronze": {"count": at_least_one, "rate": ratio(at_least_one, len(rows))},
        "all_six_gold": {"count": all_gold, "rate": ratio(all_gold, len(rows))},
    }


def validate_thresholds(thresholds: Any) -> dict[str, dict[str, int]]:
    if not isinstance(thresholds, dict):
        raise ValueError("Threshold configuration must be an object")
    if "achievement-thresholds" in thresholds:
        thresholds = thresholds["achievement-thresholds"]
    if not isinstance(thresholds, dict):
        raise ValueError("achievement-thresholds must be an object")
    result = {}
    for name, _, scale, maximum in METRICS:
        entry = thresholds.get(name)
        if not isinstance(entry, dict):
            raise ValueError(f"Threshold configuration is missing {name}")
        values = [entry.get(tier) for tier in TIERS]
        if any(type(value) is not int or value <= 0 or value * scale > maximum for value in values):
            raise ValueError(f"{name} thresholds must be positive whole units within the vanilla/stat sum range")
        if not values[0] < values[1] < values[2]:
            raise ValueError(f"{name} thresholds must satisfy bronze < silver < gold")
        result[name] = dict(zip(TIERS, values))
    return result


def evaluate(rows: list[dict[str, int]], thresholds: dict[str, dict[str, int]]) -> dict[str, Any]:
    return {
        "thresholds": thresholds,
        "metrics": {
            name: {tier: per_group_achievement(rows, column, thresholds[name][tier] * scale)
                   for tier in TIERS} for name, column, scale, _ in METRICS
        },
        "combined": {
            "all_players": combined_achievement(rows, thresholds),
            "playtime_groups": {name: combined_achievement(cohort(rows, lower, upper), thresholds)
                                for name, lower, upper in GROUPS},
        },
    }


def summarize(rows: list[dict[str, int]], args: argparse.Namespace) -> dict[str, Any]:
    design = {
        tier: {**interval(*getattr(args, f"{tier}_hours")),
               "positive_participant_quantile": getattr(args, f"{tier}_quantile")}
        for tier in TIERS
    }
    result = {
        "schema_version": 2,
        "status": "offline_candidates_require_review",
        "input_rows": len(rows),
        "assumptions": {
            "tier_design": design,
            "minimum_positive_samples_per_metric_per_tier": args.min_samples,
            "cross_cohort_guards": {
                "enabled": args.cross_cohort_guards,
                "selection": "max(base, guard) before rounding; both sources must have enough positive samples",
                "design": {tier: {**interval(lower, upper), "positive_participant_quantile": fraction}
                           for tier, (lower, upper, fraction) in CROSS_COHORT_GUARDS.items()},
            },
            "quantile_method": "linear interpolation at (n - 1) * p",
            "rounding": "two significant digits; whole counts/km; minimum 1",
            "zero_handling": "Zero values remain in overall rates; candidate quantiles use positive participants only.",
            "time_handling": "Vanilla play ticks / 72000; includes AFK and is not time to first achievement.",
            "deduplication": "Input rows must already represent unique players from the authoritative stats directory.",
            "limitations": [
                "Cross-sectional cohorts are not longitudinal progress curves or unlock-time forecasts.",
                "Automatic farms and different play styles may produce very different counts at the same playtime.",
                "Candidate time windows and quantiles are design choices, not facts inferred from data.",
                "Insufficient samples and non-increasing candidates require review; no extrapolation or silent fallback.",
                "Current cumulative records cannot distinguish AFK, manual play, and automated activity.",
                "Fixed three-tier lifetime achievements will eventually be completed by sufficiently active players.",
            ],
        },
        "all_players": cohort_summary(rows),
        "playtime_groups": {
            name: {**interval(lower, upper), **cohort_summary(cohort(rows, lower, upper))}
            for name, lower, upper in GROUPS
        },
        "candidates": {},
    }
    complete = {}
    for name, column, scale, maximum in METRICS:
        tiers = {}
        proposed = {}
        for tier in TIERS:
            raw, base = source_quantile(rows, column, scale, *getattr(args, f"{tier}_hours"),
                                        getattr(args, f"{tier}_quantile"), args.min_samples)
            entry = {
                "cohort_players": base["cohort_players"], "positive_samples": base["positive_samples"],
                "participation_rate": base["participation_rate"],
                "base": base, "guard": None, "selected_source": None,
                "status": "insufficient-data", "threshold": None,
            }
            insufficient_sources = [] if raw is not None else ["base"]
            selected_source = "base"
            if args.cross_cohort_guards and tier in CROSS_COHORT_GUARDS:
                guard_raw, guard = source_quantile(rows, column, scale, *CROSS_COHORT_GUARDS[tier],
                                                   args.min_samples)
                entry["guard"] = guard
                if guard_raw is None:
                    insufficient_sources.append("guard")
                elif raw is not None and guard_raw > raw:
                    raw, selected_source = guard_raw, "guard"
            if insufficient_sources:
                entry["insufficient_sources"] = insufficient_sources
            else:
                threshold = readable_threshold(raw, scale)
                entry.update({"unrounded_quantile": json_number(raw, scale),
                              "threshold": threshold, "status": "candidate",
                              "selected_source": selected_source})
                if threshold * scale > maximum:
                    entry["status"] = "exceeds_statistic_range"
                else:
                    proposed[tier] = threshold
                    entry["achievement"] = per_group_achievement(rows, column, threshold * scale)
            tiers[tier] = entry
        status = "insufficient_or_invalid_tier"
        if len(proposed) == 3:
            if proposed["bronze"] < proposed["silver"] < proposed["gold"]:
                status = "candidate_requires_review"
                complete[name] = proposed
            else:
                status = "non_increasing_candidates_require_review"
        result["candidates"][name] = {"unit": "km" if scale > 1 else "count",
                                      "status": status, "tiers": tiers}
    if len(complete) == len(METRICS):
        result["complete_candidate_evaluation"] = evaluate(rows, complete)
    else:
        result["complete_candidate_evaluation"] = {
            "status": "unavailable_incomplete_or_non_increasing_candidates",
            "missing_metrics": [m[0] for m in METRICS if m[0] not in complete],
        }
    if all(tier["status"] == "insufficient-data"
           for metric in result["candidates"].values() for tier in metric["tiers"].values()):
        result["status"] = "insufficient-data"
    if args.evaluate_thresholds:
        with args.evaluate_thresholds.open(encoding="utf-8") as source:
            configured = validate_thresholds(json.load(source))
        result["supplied_threshold_evaluation"] = evaluate(rows, configured)
    return result


def probability(value: str) -> float:
    parsed = float(value)
    if not math.isfinite(parsed) or not 0 <= parsed <= 1:
        raise argparse.ArgumentTypeError("quantile must be between 0 and 1")
    return parsed


def parser() -> argparse.ArgumentParser:
    command = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Input: a JSON array, or one JSON object per line. Required integer columns:
  played_ticks, mining, crafting, elytra_cm, fishing, trading, walking_cm
Missing/invalid columns fail validation; they are never replaced by zero.
Additional input fields are ignored and are not copied to the output.

Examples:
  python3 tools/calibrate_achievements.py /private/anonymous-stats.json --output /private/summary.json
  python3 tools/calibrate_achievements.py /private/anonymous-stats.json --gold-hours 200 inf --gold-quantile .9
  python3 tools/calibrate_achievements.py /private/anonymous-stats.json --evaluate-thresholds /private/thresholds.json
  python3 tools/calibrate_achievements.py /private/anonymous-stats.json --no-cross-cohort-guards --gold-hours 500 inf

Evaluation thresholds are a JSON object with six keys: mining, crafting, elytra,
fishing, trading, walking. Each contains integer bronze, silver, gold values.
Distances are whole kilometres; all other thresholds are counts. A top-level
"achievement-thresholds" wrapper is also accepted. Ranges must be strictly increasing.

The default candidate design was chosen for the 2026-10-30 server snapshot; new
inputs still require review and never change plugin configuration automatically.
Shipped bronze defaults were subsequently raised after owner feedback. Supply
the final defaults with --evaluate-thresholds to evaluate that tuned configuration:
bronze: positive-player Q35 at 1 <= hours < 10;
silver: max(Q50 at 50 <= hours < 200, Q75 at 10 <= hours < 50);
gold: max(Q80 at hours >= 300, Q90 at hours >= 50).
All quantiles use positive participants. At least 20 are required for EACH base
and guard source. Missing either source prevents a candidate; there is no fallback.
Use --no-cross-cohort-guards to disable the extra sources. To reproduce the first
unguarded proposal, also pass --gold-hours 500 inf.
Rates are fractions in [0, 1]; null means an empty denominator, not zero.
""")
    command.add_argument("input", help="anonymous JSON/JSONL file; '-' reads standard input")
    command.add_argument("--output", type=Path, help="summary JSON path; default: standard output")
    command.add_argument("--min-samples", type=int, default=20,
                         help="required positive participants per metric/tier (default: 20; minimum: 5)")
    command.add_argument("--evaluate-thresholds", type=Path,
                         help="also evaluate a complete threshold configuration JSON")
    command.add_argument("--no-cross-cohort-guards", dest="cross_cohort_guards", action="store_false",
                         default=True, help="disable silver/gold cross-cohort lower bounds (enabled by default)")
    defaults = {"bronze": ((1., 10.), .35), "silver": ((50., 200.), .50),
                "gold": ((300., math.inf), .80)}
    for tier, (hours, quantile) in defaults.items():
        command.add_argument(f"--{tier}-hours", type=float, nargs=2, default=hours,
                             metavar=("MIN", "MAX"), help=f"inclusive minimum/exclusive maximum hours for {tier}; MAX can be inf")
        command.add_argument(f"--{tier}-quantile", type=probability, default=quantile,
                             help=f"positive-participant quantile for {tier} (default: {quantile})")
    return command


def main(argv: list[str] | None = None) -> int:
    command = parser()
    args = command.parse_args(argv)
    if args.min_samples < 5:
        command.error("--min-samples must be at least 5; smaller cohorts are not sufficient for candidate calibration")
    previous_max = 0.
    for tier in TIERS:
        lower, upper = getattr(args, f"{tier}_hours")
        if not math.isfinite(lower) or math.isnan(upper) or lower < 0 or upper <= lower:
            command.error(f"--{tier}-hours requires 0 <= finite MIN < MAX")
        if lower < previous_max:
            command.error("tier time windows must be ordered and must not overlap")
        previous_max = upper
    try:
        text = sys.stdin.read() if args.input == "-" else Path(args.input).read_text(encoding="utf-8")
        summary = summarize(load_rows(text), args)
        output = json.dumps(summary, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        if args.output:
            with args.output.open("x", encoding="utf-8") as destination:
                destination.write(output)
        else:
            sys.stdout.write(output)
    except (OSError, ValueError) as error:
        command.exit(2, f"calibrate_achievements: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
