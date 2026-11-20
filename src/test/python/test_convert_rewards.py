import copy
import json
import runpy
import tempfile
import unittest
from datetime import date
from pathlib import Path


convert = runpy.run_path(str(Path(__file__).resolve().parents[3] / "tools/convert_rewards.py"))["convert"]


class ShopQuotaMigrationTests(unittest.TestCase):
    def test_expiry_preservation_and_repeat_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            snapshot = root / "snapshot"
            accounts = snapshot / "config/smp3/accounts"
            accounts.mkdir(parents=True)
            modern = {"stone": {"end": "2026-11-23", "weekly": True, "count": 9}}
            quotas = {
                "expired": {"date": "2026-11-19", "used": {"stone": 64}},
                "today": {"date": "2026-11-20", "used": {"stone": 12}},
                "future": {"date": "2026-11-21", "used": {"stone": 7}},
                "modern": modern,
                "unused": None,
            }
            originals = {}
            for owner, quota in quotas.items():
                account = {"balances": {"tongbao": 12345, "coffee_stamp": 678},
                           "rewardStates": {"milestones": {"tree.all": []}, "sign": {"days": 5},
                                            "questsAndPacks": {"sequence": 3, "pending": [], "quests": {"day": 1}}}}
                if quota is not None:
                    account["rewardStates"]["questsAndPacks"]["shop"] = quota
                originals[owner] = account
                (accounts / f"{owner}.json").write_text(json.dumps(account))
            before = {p.name: p.read_bytes() for p in accounts.iterdir()}
            output = root / "output"
            report = convert(snapshot, None, output, None, migrate_shop_quotas=True, as_of=date(2026, 11, 20))
            self.assertEqual(report["shopQuotas"]["cleared"], ["expired"])
            self.assertEqual(report["shopQuotas"]["converted"], ["future", "today"])
            self.assertEqual(before, {p.name: p.read_bytes() for p in accounts.iterdir()})
            results = output / "overlay/config/smp3/accounts"
            self.assertEqual({p.stem for p in results.iterdir()}, {"expired", "today", "future"})
            for owner, expected_shop in {
                "expired": {},
                "today": {"stone": {"end": "2026-11-21", "weekly": False, "count": 12}},
                "future": {"stone": {"end": "2026-11-22", "weekly": False, "count": 7}},
            }.items():
                expected = copy.deepcopy(originals[owner])
                expected["rewardStates"]["questsAndPacks"]["shop"] = expected_shop
                result = results / f"{owner}.json"
                self.assertEqual(json.loads(result.read_text()), expected)
                (accounts / result.name).write_bytes(result.read_bytes())
            repeat = root / "repeat"
            report = convert(snapshot, None, repeat, None, migrate_shop_quotas=True, as_of=date(2026, 11, 20))
            self.assertEqual(report["shopQuotas"]["cleared"], [])
            self.assertEqual(report["shopQuotas"]["converted"], [])
            self.assertEqual(list(repeat.iterdir()), [repeat / "report.json"])

    def test_invalid_usage_stops_before_writing_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            accounts = root / "snapshot/config/smp3/accounts"
            accounts.mkdir(parents=True)
            for owner, count in [("a-valid", 4), ("b-invalid", -1)]:
                account = {"balances": {}, "rewardStates": {"questsAndPacks": {
                    "shop": {"date": "2026-11-19", "used": {"stone": count}}}}}
                (accounts / f"{owner}.json").write_text(json.dumps(account))
            with self.assertRaisesRegex(ValueError, "b-invalid.json"):
                convert(root / "snapshot", None, root / "output", None,
                        migrate_shop_quotas=True, as_of=date(2026, 11, 20))
            self.assertFalse((root / "output").exists())
