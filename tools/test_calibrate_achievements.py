"""Offline checks for the five-tier calibration design and evaluation contract."""
import copy
import unittest

import calibrate_achievements as c


class CalibrationTest(unittest.TestCase):
    def row(self, hours, value):
        return {"played_ticks": hours * c.TICKS_PER_HOUR,
                **{column: value * scale for _, column, scale, _ in c.METRICS}}

    def thresholds(self):
        return {name: dict(zip(c.TIERS, (1, 2, 3, 4, 5))) for name, *_ in c.METRICS}

    def test_all_five_boundaries_and_gold_distribution(self):
        rows = [self.row(10, value) for value in range(7)]
        result = c.evaluate(rows, self.thresholds())
        combined = result["combined"]["all_players"]
        self.assertEqual([6, 5, 4, 3, 2], [combined["at_least_one_by_tier"][t]["count"] for t in c.TIERS])
        self.assertEqual(2, combined["gold_count_distribution"]["6"])
        self.assertEqual(5, combined["gold_count_distribution"]["0"])
        for metric in result["metrics"].values():
            self.assertEqual([6, 5, 4, 3, 2], [metric[t]["all_players"]["achieved"] for t in c.TIERS])

    def test_old_schema_missing_tier_and_non_increasing_rejected(self):
        original = self.thresholds()
        for bad in ({"bronze": 1, "silver": 2, "gold": 3},
                    {"wood": 1, "iron": 2, "copper": 3, "diamond": 3, "gold": 5},
                    {"wood": 1, "iron": 2, "copper": 3, "diamond": 4, "gold": True}):
            thresholds = copy.deepcopy(original)
            thresholds["mining"] = bad
            with self.assertRaises(ValueError):
                c.validate_thresholds(thresholds)

    def test_distance_cap_and_integer_input(self):
        thresholds = self.thresholds()
        thresholds["walking"]["gold"] = 21475
        with self.assertRaises(ValueError):
            c.validate_thresholds(thresholds)
        with self.assertRaises(ValueError):
            c.load_rows('[{"played_ticks":true}]')

    def test_sparse_data_never_invents_candidates_or_uses_entry_floor(self):
        result = c.summarize([self.row(5, 1)] * 19, c.parser().parse_args(["-"]))
        for metric in result["candidates"].values():
            self.assertEqual("insufficient-data", metric["tiers"]["wood"]["status"])
            self.assertIsNone(metric["tiers"]["wood"]["threshold"])
        self.assertNotIn("thresholds", result["complete_candidate_evaluation"])

    def test_entry_floor_is_explicit_and_guard_requires_samples(self):
        rows = [self.row(5, 1)] * 20 + [self.row(100, 2000)] * 20
        result = c.summarize(rows, c.parser().parse_args(["-"]))
        wood = result["candidates"]["mining"]["tiers"]["wood"]
        self.assertEqual(500, wood["threshold"])
        self.assertEqual(1, wood["rounded_before_entry_floor"])
        self.assertEqual(500, wood["entry_floor"])
        copper = result["candidates"]["mining"]["tiers"]["copper"]
        self.assertEqual(["guard"], copper["insufficient_sources"])
        self.assertIsNone(copper["threshold"])

    def test_empty_denominator_is_unknown_and_zeroes_are_included(self):
        result = c.evaluate([], self.thresholds())["combined"]["all_players"]
        self.assertIsNone(result["at_least_one_by_tier"]["wood"]["rate"])
        result = c.achievement([self.row(1, 0), self.row(1, 1)], "fishing", 1)
        self.assertEqual(.5, result["all_player_rate"])
        self.assertEqual(1, result["positive_participant_rate"])

    def test_percentile_and_rounding_are_reproducible(self):
        self.assertEqual(c.Decimal("19.0"), c.percentile([10, 20, 30], .45))
        self.assertEqual(110, c.readable_threshold(c.Decimal("10502132"), 100000))
        self.assertEqual(19, c.readable_threshold(c.Decimal("18.8"), 1))


if __name__ == "__main__":
    unittest.main()
