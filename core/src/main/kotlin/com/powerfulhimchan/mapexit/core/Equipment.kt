package com.powerfulhimchan.mapexit.core

import kotlin.math.max

object Equipment {
    fun equipped(state: State): List<Gear> =
        state.equipment.values.distinct().mapNotNull { id -> state.inventory.find { it.id == id } }

    fun charm(state: State): Charm =
        equipped(state).firstOrNull { it.kind == Kind.NECKLACE }?.charm ?: Charm.NONE

    fun charmRefinement(state: State): Double = (equipped(state).firstOrNull { it.kind == Kind.NECKLACE }?.refinements ?: 0) * .05

    fun stats(state: State): Stats {
        var result = Stats.zero()
        val gear = equipped(state)
        for (stat in Stat.entries) {
            val flat = gear.sumOf { it.flatValue(stat) }
            val percent = gear.sumOf { it.percent.value(stat) }
            var value = (state.base.value(stat) + flat) * (1 + percent)
            value =
                when (stat) {
                    Stat.HP,
                    Stat.ATTACK -> max(1.0, value)
                    Stat.CRIT ->
                        value.coerceIn(
                            GameBalance.model("crit_chance_min"),
                            GameBalance.model("crit_chance_max"),
                        )
                    Stat.CRIT_DAMAGE ->
                        value.coerceIn(
                            GameBalance.model("crit_damage_min"),
                            GameBalance.model("crit_damage_max"),
                        )
                    else -> max(0.0, value)
                }
            result = result.add(stat, value)
        }
        return result
    }

    fun allowedSlots(gear: Gear): List<Slot> =
        when (gear.kind) {
            Kind.SWORD,
            Kind.SPEAR,
            Kind.HAMMER,
            Kind.CLAW,
            Kind.WHIP,
            Kind.SHIELD -> listOf(Slot.LEFT, Slot.RIGHT)
            Kind.HEAD -> listOf(Slot.HEAD)
            Kind.BODY -> listOf(Slot.BODY)
            Kind.PANTS -> listOf(Slot.PANTS)
            Kind.BOOTS -> listOf(Slot.BOOTS)
            Kind.RING -> listOf(Slot.RING1, Slot.RING2)
            Kind.NECKLACE -> listOf(Slot.NECK)
            else -> emptyList()
        }

    data class Comparison(
        val slot: Slot,
        val current: Gear?,
        val removed: List<Gear>,
        val before: Stats,
        val after: Stats,
    )

    /** Preview a shop item with the exact same replacement rules as a real equip. */
    fun compare(state: State, item: Gear): List<Comparison> =
        allowedSlots(item).map { slot ->
            val preview =
                state.copy(
                    inventory = state.inventory.toMutableList(),
                    equipment = state.equipment.toMutableMap(),
                )
            if (preview.inventory.none { it.id == item.id }) preview.inventory.add(item)
            equip(preview, item.id, slot)
            Comparison(
                slot,
                state.inventory.find { it.id == state.equipment[slot] },
                equipped(state).filter { it.id !in preview.equipment.values },
                stats(state),
                stats(preview),
            )
        }

    fun quickSlot(state: State, item: Gear): Slot? {
        val slots = when {
            item.weapon -> listOf(Slot.RIGHT, Slot.LEFT)
            item.kind == Kind.SHIELD -> listOf(Slot.LEFT, Slot.RIGHT)
            else -> allowedSlots(item)
        }
        return slots.firstOrNull { state.equipment[it] == null } ?: slots.firstOrNull()
    }

    /** Compare complete raw loadouts, including penalties and displaced two-handed gear. */
    fun isUpgrade(state: State, item: Gear, targetSlot: Slot? = null): Boolean {
        if (!item.equippable || item.id in state.equipment.values) return false
        val slots = targetSlot?.let { listOf(it) } ?: allowedSlots(item)
        fun raw(s: State, stat: Stat): Double {
            val gear = equipped(s)
            return (s.base.value(stat) + gear.sumOf { it.flatValue(stat) }) *
                (1 + gear.sumOf { it.percent.value(stat) })
        }
        return slots.filter { it in allowedSlots(item) }.any { slot ->
            val worn = state.inventory.find { it.id == state.equipment[slot] }
            if (worn == null || worn.kind != item.kind || worn.charm != item.charm) false
            else {
                val preview = state.copy(inventory = state.inventory.toMutableList(), equipment = state.equipment.toMutableMap())
                if (preview.inventory.none { it.id == item.id }) preview.inventory.add(item)
                equip(preview, item.id, slot)
                val delta = Stat.entries.map { raw(preview, it) - raw(state, it) }
                delta.all { it >= -1e-7 } && delta.any { it > 1e-7 }
            }
        }
    }

    fun equip(state: State, id: Long, slot: Slot): Boolean {
        val item = state.inventory.find { it.id == id } ?: return false
        if (slot !in allowedSlots(item)) return false
        val next = state.equipment.toMutableMap()
        next.entries.removeAll { it.value == id }
        if (slot == Slot.LEFT || slot == Slot.RIGHT) {
            val other = if (slot == Slot.LEFT) Slot.RIGHT else Slot.LEFT
            val opposite = state.inventory.find { it.id == next[other] }
            if (item.twoHanded || opposite?.twoHanded == true) {
                next.remove(Slot.LEFT)
                next.remove(Slot.RIGHT)
            } else if (opposite != null) {
                val legal =
                    (item.kind == Kind.CLAW && opposite.kind == Kind.CLAW) ||
                        (item.weapon && opposite.kind == Kind.SHIELD) ||
                        (item.kind == Kind.SHIELD && opposite.weapon)
                if (!legal) next.remove(other)
            }
            next[slot] = id
            if (item.twoHanded) next[other] = id
        } else next[slot] = id
        state.equipment = next
        state.hp = state.hp.coerceAtMost(stats(state).hp)
        return true
    }

    fun unequip(state: State, id: Long) {
        state.equipment.entries.removeAll { it.value == id }
        state.hp = state.hp.coerceAtMost(stats(state).hp)
    }

    fun buyPrice(state: State, item: Gear): Int =
        max(1, (item.price * (if (charm(state) == Charm.MERCHANT) (.8-charmRefinement(state)).coerceAtLeast(.40) else 1.0)).toInt())

    fun sellPrice(item: Gear): Int = max(1, item.price / 3)

    fun equippedLabel(state: State, id: Long): String {
        val slots = Slot.entries.filter { state.equipment[it] == id }
        return if (slots.isEmpty()) "" else " [착용중] · ${slots.joinToString("·") { it.label }}"
    }
}

object ItemFactory {
    fun scroll(s: State) = Gear(s.nextId++,Kind.SCROLL,"강화서",price=180)
    fun rareScroll(s: State) {
        if (s.shop.none { it.kind == Kind.SCROLL } && s.random() < Luck.good(.025,Equipment.stats(s).luck,.06)) s.shop.add(scroll(s))
    }

    fun potion(s: State, grade: PotionGrade): Gear = Gear(
        s.nextId++, Kind.POTION, grade.label, price = grade.price, potionGrade = grade,
    )

    fun create(
        s: State,
        kind: Kind? = null,
        floor: Int = max(1, s.floor),
        variantId: String? = null,
    ): Gear {
        if (kind == Kind.SCROLL) return scroll(s)
        val regular = Kind.entries.filter { it != Kind.SCROLL }
        val k = kind ?: regular[s.nextInt(regular.size)]
        val tier = (1 + (floor - 1) / 10 + if (s.random() < 1-Luck.good(.5,Equipment.stats(s).luck,.80)) 0 else 1).coerceAtMost(7)
        if (k == Kind.POTION && kind == null && floor >= 11 && s.random() < Luck.good(.25,Equipment.stats(s).luck,.55))
            return potion(s, PotionGrade.GREATER)
        val t = tier.toDouble()
        var f = Stats.zero()
        var p = Stats.zero()
        var charm = Charm.NONE
        var title = k.label
        var variant: WeaponVariant? = null
        when (k) {
            Kind.SWORD,
            Kind.SPEAR,
            Kind.HAMMER,
            Kind.CLAW,
            Kind.WHIP -> {
                val variants = WeaponCatalog.of(k)
                variant =
                    if (variantId == null) variants[s.nextInt(variants.size)]
                    else variants.first { it.id == variantId }
                f = variant.stats(tier)
                title = variant.name
            }
            Kind.SHIELD -> f = f.copy(defense = GameBalance.shield("defense") * t)

            Kind.HEAD,
            Kind.BODY,
            Kind.PANTS -> f = f.copy(defense = 4 * t, hp = 15 * t)
            Kind.BOOTS -> f = f.copy(defense = 2 * t, agility = 3 * t)
            Kind.SCROLL -> error("Scroll is created separately")
            Kind.POTION -> title = "회복약"
            Kind.RING ->
                when (s.nextInt(6)) {
                    0 -> {
                        title = "전사의 반지"
                        p = p.copy(attack = .20 + .02 * t, defense = -.15)
                    }
                    1 -> {
                        title = "바람의 반지"
                        p = p.copy(agility = .30 + .02 * t, hp = -.15)
                    }
                    2 -> {
                        title = "광기의 반지"
                        f = f.copy(critDamage = .5 + .05 * t, crit = -.06)
                    }
                    3 -> {
                        title = "사냥꾼의 반지"
                        f = f.copy(crit = .08 + .005 * t)
                        p = p.copy(hp = -.15)
                    }
                    4 -> {
                        title = "행운의 반지"
                        f = f.copy(luck = 12 * t)
                        p = p.copy(attack = -.10)
                    }
                    else -> {
                        title = "수호의 반지"
                        p = p.copy(hp = .25, defense = .15, agility = -.20)
                    }
                }
            Kind.NECKLACE -> {
                charm = Charm.entries[1 + s.nextInt(Charm.entries.size - 1)]
                title =
                    when (charm) {
                        Charm.MAP -> "탐색의 목걸이"
                        Charm.CHEST -> "보물의 목걸이"
                        Charm.MERCHANT -> "상인의 목걸이"
                        Charm.TRAP -> "함정의 목걸이"
                        else -> "의뢰의 목걸이"
                    }
            }
        }
        return Gear(
            s.nextId++,
            k,
            if (k == Kind.POTION) title else "$title +$tier",
            tier,
            f,
            p,
            charm,
            price = if (k == Kind.POTION) 25 else 35 * tier,
            variantId = variant?.id,
        )
    }

    /** A guaranteed weapon with preserved base stats and distinct, additive rolled affixes. */
    fun bossWeapon(s: State, floor: Int = max(1, s.floor)): Gear {
        val variant = WeaponCatalog.all[s.nextInt(WeaponCatalog.all.size)]
        val normal = create(s, variant.kind, floor, variant.id)
        val pool = Stat.entries.toMutableList()
        val count = (2 + (floor.coerceIn(1, 50) - 1) / 20).coerceAtMost(4)
        val options =
            List(count) {
                val stat = pool.removeAt(s.nextInt(pool.size))
                val roll = .8 + s.random() * .4
                val t = normal.tier.toDouble()
                val amount =
                    when (stat) {
                        Stat.HP -> kotlin.math.round(18 * t * roll)
                        Stat.ATTACK -> kotlin.math.round(5 * t * roll)
                        Stat.DEFENSE -> kotlin.math.round(4 * t * roll)
                        Stat.AGILITY -> kotlin.math.round(3 * t * roll)
                        Stat.LUCK -> kotlin.math.round(3 * t * roll)
                        Stat.CRIT -> kotlin.math.round((.015 + .003 * t) * roll * 1000) / 1000
                        Stat.CRIT_DAMAGE -> kotlin.math.round((.08 + .012 * t) * roll * 1000) / 1000
                    }
                WeaponAffix(stat, amount)
            }
        return normal.copy(
            name = "수문장의 ${normal.name}",
            bossOriginFloor = floor,
            affixes = options,
            price = normal.price * 2,
        )
    }
}
