import copy
import gzip
import json
import runpy
import tempfile
import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch


converter = runpy.run_path(str(Path(__file__).resolve().parents[3] / "tools/convert_rewards.py"))
convert = converter["convert"]


class ShopQuotaMigrationTests(unittest.TestCase):
    def test_output_publication_is_complete_or_absent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            artifacts = {"overlay/account.json": {"balances": {"tongbao": 12345}},
                         "luckperms.json.gz": {"users": {}, "groups": {}}}
            report = {"mode": "test"}
            failures = [patch.object(converter["os"], "fsync", side_effect=[None, OSError("disk full")]),
                        patch.object(Path, "rename", side_effect=OSError("rename failed"))]
            for failure in failures:
                with failure, self.assertRaises(OSError):
                    converter["write_result"](output, artifacts.copy(), report)
                self.assertFalse(output.exists())
                self.assertEqual(list(root.iterdir()), [])
            converter["write_result"](output, artifacts.copy(), report)
            expected = {**artifacts, "report.json": report}
            files = {p.relative_to(output).as_posix(): p.read_bytes()
                     for p in output.rglob("*") if p.is_file()}
            self.assertEqual(set(files), set(expected))
            for name, data in files.items():
                self.assertEqual(json.loads(gzip.decompress(data) if name.endswith(".gz") else data), expected[name])
            with self.assertRaises(FileExistsError):
                converter["write_result"](output, {"replacement.json": {}}, report)
            self.assertEqual(files, {p.relative_to(output).as_posix(): p.read_bytes()
                                     for p in output.rglob("*") if p.is_file()})
            self.assertEqual(list(root.iterdir()), [output])

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


class SignTicketMigrationTests(unittest.TestCase):
    @staticmethod
    def write_sign(path, records):
        import struct

        def name(value):
            data = value.encode()
            return struct.pack(">H", len(data)) + data

        data = b"\x0a\x00\x00\x0a" + name("data") + b"\x0a" + name("players")
        for owner, claimed in records.items():
            data += b"\x0a" + name(owner) + b"\x0b" + name("serverClaimed")
            data += struct.pack(">i", len(claimed)) + b"".join(struct.pack(">i", day) for day in claimed)
            data += b"\x03" + name("serverTotal") + struct.pack(">i", 99) + b"\x00"
        path.write_bytes(gzip.compress(data + b"\x00\x00\x00"))

    def test_compensation_composes_with_quotas_and_survives_delivery(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            snapshot = root / "snapshot"
            accounts = snapshot / "config/smp3/accounts"
            accounts.mkdir(parents=True)
            owner, missing, ineligible = [f"00000000-0000-0000-0000-{i:012d}" for i in range(1, 4)]
            legacy = root / "sign-mod.dat"
            self.write_sign(legacy, {owner: [1, 5], missing: [5], ineligible: [1, 3]})
            original = {"balances": {"tongbao": 12345, "coffee_stamp": 500}, "rewardStates": {
                "sign": {"serverClaimed": [1, 5], "pending": []}, "milestones": {"tree.all": []},
                "questsAndPacks": {"sequence": 7, "inventory": [{"id": "minecraft:stone", "count": 64}],
                                   "pending": [{"id": "minecraft:diamond", "count": 2}],
                                   "effects": [], "shop": {"date": "2026-11-20", "used": {"stone": 12}}}}}
            (accounts / f"{owner}.json").write_text(json.dumps(original))
            (accounts / f"{ineligible}.json").write_text(json.dumps({"balances": {}, "rewardStates": {
                "sign": {"serverClaimed": [5]}}}))
            before = {p.name: p.read_bytes() for p in accounts.iterdir()}
            output = root / "output"
            report = convert(snapshot, None, output, None, migrate_shop_quotas=True,
                             as_of=date(2026, 11, 20), legacy_sign=legacy)
            self.assertEqual(report["signTickets"]["queued"], [owner, missing])
            self.assertEqual(report["signTickets"]["eligible"], 2)
            self.assertEqual(before, {p.name: p.read_bytes() for p in accounts.iterdir()})
            results = output / "overlay/config/smp3/accounts"
            self.assertEqual({p.stem for p in results.iterdir()}, {owner, missing})
            account = json.loads((results / f"{owner}.json").read_text())
            expected = copy.deepcopy(original)
            expected["rewardStates"]["questsAndPacks"]["shop"] = {
                "stone": {"end": "2026-11-21", "weekly": False, "count": 12}}
            expected["rewardStates"]["questsAndPacks"]["pending"].append({"id": "smp3:ticket_ten", "count": 1})
            expected["rewardStates"]["migrations"] = {"legacySignTicketV1": True}
            self.assertEqual(account, expected)
            for p in results.iterdir():
                account = json.loads(p.read_text())
                account["rewardStates"]["questsAndPacks"]["pending"] = []
                (accounts / p.name).write_text(json.dumps(account))
            report = convert(snapshot, None, root / "repeat", None, legacy_sign=legacy)
            self.assertEqual(report["signTickets"]["queued"], [])
            self.assertEqual(report["signTickets"]["alreadyQueued"], [owner, missing])
            self.assertEqual(list((root / "repeat").iterdir()), [root / "repeat/report.json"])

    def test_invalid_recipient_does_not_publish_partial_compensation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "snapshot/config/smp3/accounts").mkdir(parents=True)
            legacy = root / "sign-mod.dat"
            self.write_sign(legacy, {"00000000-0000-0000-0000-000000000001": [5], "invalid": [5]})
            with self.assertRaisesRegex(ValueError, "UUID"):
                convert(root / "snapshot", None, root / "output", None, legacy_sign=legacy)
            self.assertFalse((root / "output").exists())
