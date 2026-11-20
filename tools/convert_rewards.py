#!/usr/bin/env python3
"""Prepare a reviewed maintenance overlay from an offline SMP3 snapshot (stdlib only)."""

import argparse
import gzip
import hashlib
import json
import os
import struct
import tempfile
import zipfile
from collections import Counter
from datetime import date, timedelta
from pathlib import Path
from uuid import UUID


TITLE_IDS = {
    "advancement_minecraft": "001_Adv_Story",
    "advancement_adventure": "002_Adv_Adventure",
    "advancement_nether": "003_Adv_Nether",
    "advancement_end": "004_Adv_End",
    "advancement_farming": "005_Adv_Husbandry",
    "advancement_all": "006_Adv_All",
    "signin_t1": "007_SignIn_1",
    "signin_t2": "008_SignIn_2",
    "signin_t3": "009_SignIn_3",
    "signin_t4": "010_SignIn_4",
    "signin_t5": "011_SignIn_5",
    "wealth_t1": "021_Wealth_1",
    "wealth_t2": "022_Wealth_2",
    "wealth_t3": "023_Wealth_3",
    "wealth_t4": "024_Wealth_4",
    "founding_player": "027_Sign_In_1",
    "nether_builder": "031_Nether_Townhall",
}
CANONICAL = {**TITLE_IDS, **{value.lower(): value for value in TITLE_IDS.values()}}
TITLE_PREFIX = "fuji.title.obtain."
CONFIGS = ("sign.json", "milestone-rewards.json", "shops.json", "dailyquests.json",
           "gacha.json", "server.json", "watchdog.json")


def title_id(value):
    return CANONICAL.get(value.lower(), value) if value else value


def permission_nodes(nodes):
    result, titles = [], {}
    for source in nodes:
        node = source.copy()
        key = node["key"]
        if key.lower().startswith(TITLE_PREFIX):
            suffix = key[len(TITLE_PREFIX):]
            if suffix.lower() in CANONICAL:
                node["key"] = TITLE_PREFIX + title_id(suffix).lower()
            identity = json.dumps({k: v for k, v in node.items() if k != "value"}, sort_keys=True)
            if identity in titles:
                if titles[identity] != node["value"]:
                    raise ValueError(f"Conflicting title permission: {node['key']}")
                continue
            titles[identity] = node["value"]
        result.append(node)
    return result


def title_grants(value):
    if isinstance(value, dict):
        if value.get("type") == "title":
            value["id"] = title_id(value["id"])
        if isinstance(value.get("title"), str):
            value["title"] = title_id(value["title"])
        for child in value.values():
            title_grants(child)
    elif isinstance(value, list):
        for child in value:
            title_grants(child)


def menu_commands(value):
    if isinstance(value, dict):
        return {k: menu_commands(v) for k, v in value.items()}
    if isinstance(value, list):
        return [menu_commands(v) for v in value]
    return {
        "run as player %player:name% sign": "run as player %player:name% smp3 sign",
        "shop %player:name%": "smp3 shop %player:name%",
    }.get(value, value)


def read_sign(path):
    """Read the legacy NBT without rewriting the world's saved data."""
    data, offset = gzip.decompress(path.read_bytes()), 0

    def number(fmt):
        nonlocal offset
        value = struct.unpack_from(">" + fmt, data, offset)[0]
        offset += struct.calcsize(">" + fmt)
        return value

    def string():
        nonlocal offset
        size = number("H")
        value = data[offset:offset + size].decode("utf-8")
        offset += size
        return value

    def payload(kind):
        if 1 <= kind <= 6:
            return number("bhiqfd"[kind - 1])
        if kind == 8:
            return string()
        if kind == 9:
            element, count = number("B"), number("i")
            return [payload(element) for _ in range(count)]
        if kind == 10:
            result = {}
            while (element := number("B")):
                name = string()
                result[name] = payload(element)
            return result
        if kind in (7, 11, 12):
            return [number({7: "b", 11: "i", 12: "q"}[kind]) for _ in range(number("i"))]
        raise ValueError(f"Unsupported NBT type: {kind}")

    if number("B") != 10:
        raise ValueError("Expected a compound NBT root")
    string()
    return payload(10)["data"]["players"]


def advancement_requirements(rules, minecraft_jar):
    with zipfile.ZipFile(minecraft_jar) as jar:
        prefix = "data/minecraft/advancement/"
        entries = {"minecraft:" + name[len(prefix):-5]: json.loads(jar.read(name))
                   for name in jar.namelist() if name.startswith(prefix) and name.endswith(".json")}
    children = {}
    for key, value in entries.items():
        children.setdefault(value.get("parent"), []).append(key)

    def tree(root):
        if root not in entries:
            raise ValueError(f"Unknown advancement: {root}")
        return {root}.union(*(tree(child) for child in children.get(root, [])))

    result = {}
    for rule in rules:
        trigger = rule["trigger"]
        if trigger["type"] == "advancement":
            if trigger["id"] not in entries:
                raise ValueError(f"Unknown advancement: {trigger['id']}")
            result[rule["id"]] = {trigger["id"]}
        elif trigger["type"] == "advancement_tree":
            roots = trigger["root"]
            roots = roots if isinstance(roots, list) else [roots]
            result[rule["id"]] = set().union(*(tree(root) for root in roots)) - set(trigger.get("exclude", []))
    return result


def shop_quotas(snapshot, read, artifacts, as_of):
    directory = snapshot / "config/smp3/accounts"
    if not directory.is_dir():
        raise ValueError("Shop quota migration requires config/smp3/accounts in the snapshot")
    report = {"asOf": as_of.isoformat(), "accountsScanned": 0, "cleared": [], "converted": []}
    for path in sorted(directory.glob("*.json")):
        relative = path.relative_to(snapshot).as_posix()
        account = read(relative)
        report["accountsScanned"] += 1
        state = account["rewardStates"].get("questsAndPacks", {})
        quota = state.get("shop", {})
        if "date" not in quota or isinstance(quota["date"], dict):
            continue
        try:
            if set(quota) != {"date", "used"} or not isinstance(quota["used"], dict):
                raise ValueError("expected legacy date/used quota")
            day = date.fromisoformat(quota["date"])
            if any(type(count) is not int or not 0 <= count <= 2**63 - 1 for count in quota["used"].values()):
                raise ValueError("used counts must be nonnegative integers")
            expired = day < as_of
            state["shop"] = {} if expired else {
                item: {"end": (day + timedelta(days=1)).isoformat(), "weekly": False, "count": count}
                for item, count in quota["used"].items()
            }
        except (ValueError, TypeError, OverflowError) as error:
            raise ValueError(f"{relative}: {error}") from error
        artifacts["overlay/" + relative] = account
        report["cleared" if expired else "converted"].append(path.stem)
    return report


def convert(snapshot, configs, output, minecraft_jar, titles_only=False, migrate_shop_quotas=False, as_of=None):
    sources = [snapshot / "config", snapshot / "world"]
    if configs is not None:
        sources.append(configs)
    if output.exists() or any(output.resolve().is_relative_to(path.resolve()) for path in sources):
        raise ValueError("Choose a new output directory outside the source configurations and world")
    artifacts, hashes = {}, {}

    def read(relative):
        data = (snapshot / relative).read_bytes()
        hashes[relative] = hashlib.sha256(data).hexdigest()
        return json.loads(gzip.decompress(data) if relative.endswith(".gz") else data)

    if migrate_shop_quotas:
        if as_of is None:
            raise ValueError("Shop quota migration requires --as-of (server business date)")
        quotas = shop_quotas(snapshot, read, artifacts, as_of)
        if not titles_only:
            report = {"mode": "shop-quotas", "shopQuotas": quotas, "sourceSHA256": hashes,
                      "notes": ["Only accounts with legacy shop quotas are included in the overlay.",
                                "Balances and all other reward state remain unchanged. Deploy while stopped."]}
            write_result(output, artifacts, report)
            return report

    definitions = read("config/fuji/modules/title/config.json")
    definitions["default_active_title_id"] = title_id(definitions["default_active_title_id"])
    unique = {}
    for descriptor in definitions["title_descriptors"]:
        descriptor["id"] = title_id(descriptor["id"])
        key = descriptor["id"].lower()
        if key in unique and unique[key] != descriptor:
            raise ValueError(f"Conflicting title definitions: {key}")
        unique[key] = descriptor
    definitions["title_descriptors"] = list(unique.values())
    artifacts["overlay/config/fuji/modules/title/config.json"] = definitions
    preferences = read("config/fuji/modules/title/data.json")
    for preference in preferences["preferences"]:
        preference["active_title_id"] = title_id(preference.get("active_title_id"))
    artifacts["overlay/config/fuji/modules/title/data.json"] = preferences

    permissions = read("smp3-rewards.json.gz")
    for section in ("users", "groups"):
        for owner, value in permissions[section].items():
            try:
                value["nodes"] = permission_nodes(value["nodes"])
            except ValueError as error:
                raise ValueError(f"{section}/{owner}: {error}") from error
    artifacts["luckperms.json.gz"] = permissions

    if titles_only:
        report = {"mode": "titles-only", "titleMapping": TITLE_IDS,
                  "titleDefinitions": len(unique), "titlePreferences": len(preferences["preferences"]),
                  "permissionUsers": len(permissions["users"]), "permissionGroups": len(permissions["groups"]),
                  "sourceSHA256": hashes,
                  "notes": ["Only Fuji title definitions, equipped titles and LuckPerms title nodes are converted.",
                            "SMP3 accounts, balances, reward receipts and configurations remain unchanged.",
                            "Import the fresh complete LuckPerms export using replace semantics during maintenance."]}
        if migrate_shop_quotas:
            report["mode"] = "titles-and-shop-quotas"
            report["shopQuotas"] = quotas
            report["notes"][0] = "Fuji title definitions, equipped titles and LuckPerms title nodes are converted."
            report["notes"][1] = "Only legacy shop quotas change in SMP3 accounts; balances and other reward state are preserved."
        write_result(output, artifacts, report)
        return report

    if list((snapshot / "config/smp3/accounts").glob("*.json")):
        raise ValueError("Snapshot contains new account ledgers; reconcile those instead of replaying legacy balances")
    if any((snapshot / "config/gacha/players").glob("**/*")):
        raise ValueError("Legacy gacha player data needs review before conversion")
    legacy_gacha = read("config/gacha/config.json")
    if legacy_gacha.get("pools"):
        raise ValueError("Legacy gacha pools need review before conversion")
    for name in ("config.json", "economy-data.json"):
        if read("config/fuji/modules/economy/" + name).get("currencies"):
            raise ValueError("Nonempty Fuji economy requires a separate balance review")

    for name in CONFIGS:
        value = json.loads((configs / name).read_text())
        title_grants(value)
        artifacts["overlay/config/smp3/" + name] = value

    menus = read("config/fuji/modules/command_menu/menus.json")
    artifacts["overlay/config/fuji/modules/command_menu/menus.json"] = menu_commands(menus)

    balances = read("config/smp3/economy.json")["balances"]
    totals = Counter()
    for owner, currencies in balances.items():
        if str(UUID(owner)) != owner:
            raise ValueError(f"Noncanonical UUID: {owner}")
        for currency, amount in currencies.items():
            if currency not in ("tongbao", "coffee_stamp") or type(amount) is not int or not 0 <= amount <= 2**63 - 1:
                raise ValueError(f"Invalid balance: {owner}/{currency}")
        totals.update(currencies)
        artifacts[f"overlay/config/smp3/accounts/{owner}.json"] = {"balances": currencies, "rewardStates": {}}

    sign_path = "world/dimensions/minecraft/overworld/data/smp3/sign-mod.dat"
    hashes[sign_path] = hashlib.sha256((snapshot / sign_path).read_bytes()).hexdigest()
    signs = read_sign(snapshot / sign_path)
    rules = artifacts["overlay/config/smp3/milestone-rewards.json"]["rules"]
    requirements = advancement_requirements(rules, minecraft_jar)
    players = set(balances) | set(signs) | set(permissions["users"])
    for folder, extension in (("data", "dat"), ("advancements", "json"), ("stats", "json")):
        players.update(p.stem for p in (snapshot / "world/players" / folder).glob("*." + extension))
    backfill, credits, title_counts = {}, Counter(), Counter()
    for owner in sorted(players):
        advancement_file = f"world/players/advancements/{owner}.json"
        progress = read(advancement_file) if (snapshot / advancement_file).exists() else {}
        completed = {key for key, value in progress.items() if isinstance(value, dict) and value.get("done")}
        rewards, titles = Counter(), set()
        for rule in rules:
            if rule["id"] not in requirements or not requirements[rule["id"]] <= completed:
                continue
            for grant in rule["grants"]:
                if grant["type"] == "currency":
                    rewards[grant["id"].split(":")[1]] += grant["count"]
                else:
                    titles.add(grant["id"])
        for milestone in artifacts["overlay/config/smp3/sign.json"]["lifetime"]:
            if signs.get(owner, {}).get("serverTotal", 0) >= milestone["days"]:
                titles.update(g["id"] for g in milestone["grants"] if g["type"] == "title")
        for rule in rules:
            trigger = rule["trigger"]
            if trigger["type"] == "balance":
                currency = trigger["currency"].split(":")[1]
                if balances.get(owner, {}).get(currency, 0) + rewards[currency] * 100 >= trigger["minimum"] * 100:
                    titles.update(g["id"] for g in rule["grants"] if g["type"] == "title")
        credits.update(rewards)
        title_counts.update(sorted(titles))
        backfill[owner] = {"advancementCredits": dict(rewards), "eligibleTitles": sorted(titles)}

    report = {"titleMapping": TITLE_IDS, "players": len(players), "accounts": len(balances),
              "signRecords": len(signs), "balanceRawTotals": dict(totals), "titleDefinitions": len(unique),
              "titlePreferences": len(preferences["preferences"]), "advancementCredits": dict(credits),
              "playersWithAdvancementCredits": sum(any(p["advancementCredits"].values()) for p in backfill.values()),
              "eligibleTitleCounts": dict(title_counts), "backfillOnLogin": backfill, "sourceSHA256": hashes,
              "notes": ["Overlay only; keep the complete world and unrelated configurations unchanged.",
                        "Import luckperms.json.gz using replace semantics during maintenance; plain merge retains old nodes.",
                        "Credits are a forecast only. The overlay preserves balances and leaves reward ledgers unclaimed.",
                        "Historical daily coffee stamps are not backfilled. Existing title ownership is preserved.",
                        "Gacha pools remain empty until their actual locations and rewards are configured."]}
    write_result(output, artifacts, report)
    return report


def write_result(output, artifacts, report):
    artifacts["report.json"] = report
    output.parent.mkdir(parents=True, exist_ok=True)
    # Stage beside the destination so publishing the complete directory is one rename.
    with tempfile.TemporaryDirectory(prefix=f".{output.name}.staging-", dir=output.parent) as temporary:
        staging = Path(temporary)
        for relative, value in artifacts.items():
            path = staging / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            data = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()
            with path.open("wb") as stream:
                stream.write(gzip.compress(data, mtime=0) if relative.endswith(".gz") else data)
                stream.flush()
                os.fsync(stream.fileno())
        if output.exists() or output.is_symlink():
            raise FileExistsError(f"Output already exists: {output}")
        staging.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--titles-only", action="store_true", help="Convert Fuji titles and LuckPerms only; preserve upgraded accounts")
    parser.add_argument("--shop-quotas", action="store_true", help="Migrate legacy shop quotas; may be combined with --titles-only")
    parser.add_argument("--as-of", type=date.fromisoformat, help="Server business date YYYY-MM-DD for --shop-quotas (before daily reset, use the previous date)")
    parser.add_argument("--configs", type=Path, help="Reviewed JSON configuration directory (full migration)")
    parser.add_argument("--output", type=Path, required=True, help="New directory; never an existing server directory")
    parser.add_argument("--minecraft-jar", type=Path, help="26.3 common/server JAR with vanilla advancements (full migration)")
    args = parser.parse_args()
    if args.shop_quotas != (args.as_of is not None):
        parser.error("--shop-quotas and --as-of must be supplied together")
    if args.titles_only or args.shop_quotas:
        if args.configs or args.minecraft_jar:
            parser.error("partial migration does not use --configs or --minecraft-jar")
    elif not args.configs or not args.minecraft_jar:
        parser.error("full migration requires --configs and --minecraft-jar")
    report = convert(args.snapshot, args.configs, args.output, args.minecraft_jar,
                     args.titles_only, args.shop_quotas, args.as_of)
    keys = (("mode", "titleDefinitions", "titlePreferences", "permissionUsers", "permissionGroups")
            if args.titles_only else ("players", "accounts", "signRecords", "balanceRawTotals",
                                     "playersWithAdvancementCredits", "advancementCredits"))
    if args.shop_quotas:
        keys = (keys if args.titles_only else ("mode",)) + ("shopQuotas",)
    print(json.dumps({key: report[key] for key in keys}, indent=2))


if __name__ == "__main__":
    main()
