#!/usr/bin/env python3
"""Small, reproducible balance model; not the Android combat implementation."""
import argparse
import json
import math
import random
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def clamp(value, low, high):
    return min(high, max(low, value))


def luck_chance(config, luck):
    return min(max(0, luck) * config["model"]["luck_proc_per_point"], config["model"]["luck_proc_max"])


def full_guard_chance(defense, luck, incoming_attack):
    defense, luck = max(0, defense), max(0, luck)
    return clamp(.02 + .12 * defense / (defense + max(1, incoming_attack) * 2)
                 + .10 * luck / (luck + 80), .02, .24)


def validate(config):
    expected = {
        "sword": {"attack": 1, "crit_damage": 1},
        "spear": {"attack": 1, "crit_damage": 1, "defense": -1, "crit_chance": -1},
        "hammer": {"attack": 1, "crit_damage": 1, "defense": -1, "crit_chance": -1, "agility": -1},
        "claw": {"attack": 1, "agility": 1},
        "whip": {"attack": 1, "agility": 1, "crit_damage": 1, "crit_chance": -1},
    }
    for weapon, signs in expected.items():
        for stat in ("attack", "defense", "agility", "crit_chance", "crit_damage"):
            value = config["weapons"][weapon][stat]
            sign = signs.get(stat, 0)
            if (sign == 0 and value != 0) or (sign and value * sign <= 0):
                raise ValueError(f"{weapon}.{stat} violates the requested modifier direction")
    w = config["weapons"]
    for stat in ("attack", "crit_damage"):
        if not w["hammer"][stat] > w["spear"][stat] > w["sword"][stat]:
            raise ValueError(f"Two-handed compensation missing for {stat}")
    if not abs(w["spear"]["defense"]) < abs(w["hammer"]["defense"]):
        raise ValueError("Spear defense penalty must be smaller than hammer")
    if not abs(w["spear"]["crit_chance"]) < abs(w["hammer"]["crit_chance"]):
        raise ValueError("Spear crit penalty must be smaller than hammer")
    for loadout in config["loadouts"].values():
        occupied = sum(w[name]["hands"] for name in loadout["weapons"]) + int(loadout["shield"])
        if occupied > 2:
            raise ValueError("Loadout exceeds two hand slots")


def player_stats(config, build, loadout, scale):
    stats = dict(build)
    m = config["model"]
    for stat in ("hp", "attack", "defense", "agility"):
        stats[stat] *= scale
    for weapon in loadout["weapons"]:
        for stat in ("attack", "defense", "agility", "crit_chance", "crit_damage"):
            stats[stat] += config["weapons"][weapon][stat] * (scale if stat in ("attack", "defense", "agility") else 1)
    if loadout["shield"]:
        stats["defense"] += config["shield"]["defense"] * scale
    stats["defense"] = max(0, stats["defense"])
    stats["agility"] = max(0, stats["agility"])
    stats["crit_chance"] = clamp(stats["crit_chance"], m["crit_chance_min"], m["crit_chance_max"])
    stats["crit_damage"] = clamp(stats["crit_damage"], m["crit_damage_min"], m["crit_damage_max"])
    return stats


def parameters(config, build, enemy, loadout, scale, attack_policy):
    player = player_stats(config, build, loadout, scale)
    m = config["model"]
    constant = m["defense_constant"] * scale
    attack_multiplier = m["strong_damage"] if attack_policy == "strong" else 1
    hit = m["strong_hit"] if attack_policy == "strong" else m["normal_hit"]
    outgoing = player["attack"] * attack_multiplier * constant / (constant + enemy["defense"] * scale)
    enemy_attack = enemy["attack"] * scale
    incoming = enemy_attack * enemy_attack / (enemy_attack + player["defense"])
    dodge = clamp(m["dodge_base"] + (player["agility"] / scale - enemy["precision"]) * m["dodge_per_agility_gap"], m["dodge_min"], m["dodge_max"])
    guard = m["guard_reduction"] + (config["shield"]["guard_bonus"] if loadout["shield"] else 0)
    # Choose the lower expected damage defense. Dodge variance remains in simulation.
    block = full_guard_chance(player["defense"], player.get("luck", 0), enemy_attack)
    expected_guard = 1 - (1 - guard) * (1 - block)
    defense = "dodge" if dodge > expected_guard else "guard"
    proc = luck_chance(config, player.get("luck", 0))
    boosted_crit = min(m["crit_chance_max"], player["crit_chance"] + m["luck_crit_bonus"])
    expected_crit = player["crit_chance"] + proc * (boosted_crit - player["crit_chance"])
    expected_out = outgoing * (1 + proc * m["luck_attack_bonus"]) * hit * (1 + expected_crit * (player["crit_damage"] - 1))
    expected_in = incoming * (1 + enemy["crit_chance"] * (enemy["crit_damage"] - 1)) * (1 - max(dodge, expected_guard))
    return player, outgoing, incoming, hit, dodge, guard, defense, expected_out / expected_in


def simulate(config, build, enemy, loadout, scale, policy, seed, trials):
    player, outgoing, incoming, hit, dodge, guard, defense, efficiency = parameters(config, build, enemy, loadout, scale, policy)
    wins = 0
    total_rounds = total_health = 0
    for trial in range(trials):
        rng = random.Random(seed + trial * 1009)
        health, target = player["hp"], enemy["hp"] * scale
        for turn in range(1, config["model"]["max_turns"] + 1):
            # Fixed random draws per turn allow loadouts to share comparable streams.
            r_hit, r_critical, r_evade, r_enemy_critical, r_block, r_luck_attack, r_luck_critical = (rng.random() for _ in range(7))
            if r_hit < hit:
                proc = luck_chance(config, player.get("luck", 0))
                multiplier = 1 + config["model"]["luck_attack_bonus"] if r_luck_attack < proc else 1
                crit = min(config["model"]["crit_chance_max"], player["crit_chance"] +
                           (config["model"]["luck_crit_bonus"] if r_luck_critical < proc else 0))
                target -= outgoing * multiplier * (player["crit_damage"] if r_critical < crit else 1)
            if target <= 0:
                wins += 1
                break
            damage = incoming * (enemy["crit_damage"] if r_enemy_critical < enemy["crit_chance"] else 1)
            if defense == "guard":
                damage *= 1 - guard
                if r_block < full_guard_chance(player["defense"], player.get("luck", 0), enemy["attack"] * scale):
                    damage = 0
            elif r_evade < dodge:
                damage = 0
            health -= damage
            if health <= 0:
                break
        total_rounds += turn
        total_health += max(0, health) / player["hp"]
    return {"win_rate": wins / trials, "mean_turns": total_rounds / trials, "remaining_hp_fraction": total_health / trials, "efficiency": efficiency, "defense": defense}


def analyze(config, trials, seed, quick=False):
    rows = []
    scenario = 0
    for build_id, build in config["builds"].items():
        for enemy_id, enemy in config["enemies"].items():
            for scale in config["stage_scales"]:
                for policy in ("normal", "strong"):
                    scenario += 1
                    for loadout_id, loadout in config["loadouts"].items():
                        result = {"efficiency": parameters(config, build, enemy, loadout, scale, policy)[-1]} if quick else simulate(config, build, enemy, loadout, scale, policy, seed + scenario * 104729, trials)
                        rows.append({"build": build_id, "enemy": enemy_id, "scale": scale, "policy": policy, "loadout": loadout_id, **result})
    groups = defaultdict(list)
    for row in rows:
        groups[(row["build"], row["enemy"], row["scale"], row["policy"])].append(row)
    normalized = defaultdict(list)
    first = defaultdict(float)
    for group in groups.values():
        mean = sum(r["efficiency"] for r in group) / len(group)
        best = max(r["efficiency"] for r in group)
        leaders = [r for r in group if math.isclose(r["efficiency"], best, rel_tol=1e-9)]
        for row in group:
            normalized[row["loadout"]].append(row["efficiency"] / mean)
        for row in leaders:
            first[row["loadout"]] += 1 / len(leaders)
    summary = {}
    for key in config["loadouts"]:
        subset = [r for r in rows if r["loadout"] == key]
        summary[key] = {"mean_relative_efficiency": sum(normalized[key]) / len(normalized[key]), "top_scenario_share": first[key] / len(groups)}
        if not quick:
            summary[key].update({metric: sum(r[metric] for r in subset) / len(subset) for metric in ("win_rate", "mean_turns", "remaining_hp_fraction")})
    values = [r["mean_relative_efficiency"] for r in summary.values()]
    spread = max(values) / min(values) - 1
    universal = [key for key in summary if all(next(r for r in g if r["loadout"] == key)["efficiency"] >= max(r["efficiency"] for r in g) - 1e-9 for g in groups.values())]
    dominated = []
    for candidate in summary:
        for rival in summary:
            if candidate == rival:
                continue
            differences = [next(r for r in g if r["loadout"] == rival)["efficiency"] - next(r for r in g if r["loadout"] == candidate)["efficiency"] for g in groups.values()]
            if all(d >= -1e-9 for d in differences) and any(d > 1e-9 for d in differences):
                dominated.append({"loadout": candidate, "dominated_by": rival})
    checks = {"mean_efficiency_spread": spread <= config["analysis"]["mean_efficiency_spread_limit"], "no_universal_winner": not universal, "no_excessive_top_share": max(r["top_scenario_share"] for r in summary.values()) <= config["analysis"]["dominant_top_share_limit"], "no_strictly_dominated_loadout": not dominated}
    win_spread = None
    if not quick:
        win_spread = max(r["win_rate"] for r in summary.values()) - min(r["win_rate"] for r in summary.values())
        checks["mean_win_rate_spread"] = win_spread <= config["analysis"]["mean_win_rate_spread_limit"]
    return {"model_version": config["version"], "seed": seed, "trials_per_scenario": 0 if quick else trials, "scenario_count": len(groups), "duel_count": 0 if quick else len(rows) * trials, "efficiency_spread": spread, "win_rate_spread": win_spread, "dominated_loadouts": dominated, "checks": checks, "summary": summary, "scenarios": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=ROOT / "balance/weapons.json")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--trials", type=int)
    parser.add_argument("--seed", type=int)
    parser.add_argument("--quick", action="store_true", help="Expected-value analysis without random battles")
    parser.add_argument("--check", action="store_true", help="Exit nonzero when prototype balance gates fail")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    validate(config)
    trials = args.trials if args.trials is not None else config["analysis"]["trials"]
    if trials < 1:
        parser.error("--trials must be positive")
    seed = args.seed if args.seed is not None else config["analysis"]["seed"]
    report = analyze(config, trials, seed, args.quick)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "scenarios"}, ensure_ascii=False, indent=2))
    if args.check and not all(report["checks"].values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
