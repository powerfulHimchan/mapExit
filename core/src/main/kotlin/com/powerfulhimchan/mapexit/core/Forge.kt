package com.powerfulhimchan.mapexit.core

import kotlin.math.min

/** Upgrade one real instance, preserving its identity, inheritance and equipped slots. */
object Forge {
    data class Pairing(val base: Gear, val material: Gear)
    fun upgraded(g: Gear): Gear {
        require(g.equippable)
        fun improve(values: Stats, percent: Boolean): Stats {
            var next = values
            for (stat in Stat.entries) {
                val value = values.value(stat)
                if (value > 0) {
                    val bonus = if (percent) .02 else when (stat) {
                        Stat.CRIT -> .005
                        Stat.CRIT_DAMAGE -> .05
                        else -> value / g.tier.coerceAtLeast(1)
                    }
                    next = next.add(stat,bonus)
                }
            }
            return next
        }
        return g.copy(name=g.name.replace(Regex(" \\+\\d+$"),"")+" +${g.tier+1}",tier=g.tier+1,
            flat=improve(g.flat,false),percent=improve(g.percent,true),price=g.price+35,refinements=g.refinements+1)
    }
    private fun family(g: Gear) = listOf(g.kind,g.name.replace(Regex(" \\+\\d+$"),""),g.tier,g.variantId,g.charm,g.bossOriginFloor,g.affixes)
    fun pairs(s: State): List<Pairing> = s.inventory.filter { it.equippable }.groupBy(::family).values.mapNotNull { group ->
        val base = group.sortedWith(compareByDescending<Gear> { it.inherited }.thenByDescending { it.id in s.equipment.values }.thenByDescending { it.refinements }.thenBy { it.id }).first()
        val material = group.filter { it.id != base.id && !it.inherited }.sortedWith(compareBy<Gear> { it.id in s.equipment.values }.thenBy { it.id }).firstOrNull()
        material?.let { Pairing(base,it) }
    }
    fun ensure(s: State) {
        val m=s.dungeon ?: return
        if (s.challenge != null || s.floor<=0 || s.floor%20!=0 || m.cells.any { it.event==Event.FORGE }) return
        val index=m.cells.indices.lastOrNull { !m.cells[it].wall && m.cells[it].event==Event.NONE && it!=m.y*m.width+m.x } ?: return
        m.cells[index].event=Event.FORGE
    }
    fun failureChance(s: State) = Luck.bad(.10,Equipment.stats(s).luck,.02)
    fun atForge(s: State) = s.floor > 0 && s.floor % 20 == 0 && s.dungeon?.here()?.event == Event.FORGE && s.challenge == null
    fun combine(s: State, id: Long): Boolean {
        if (s.mode != Mode.MENU || s.menu?.type != "forgeConfirm" || s.menu?.argument != id || !atForge(s)) return false
        val pair = pairs(s).firstOrNull { it.base.id == id } ?: return false
        val failed = s.random() < failureChance(s)
        Equipment.unequip(s,pair.material.id); s.inventory.removeAll { it.id == pair.material.id }
        if (!failed) s.inventory[s.inventory.indexOfFirst { it.id==id }] = upgraded(pair.base)
        s.hp = min(s.hp,Equipment.stats(s).hp)
        s.quickEquipIds = s.quickEquipIds.filter { it != pair.material.id }
        s.menuStack.clear()
        s.menu = Menu("forge",returnMode=Mode.EXPLORE); s.selection=0
        s.note(if(failed) "합성 실패 · 재료 1개 소모 · ${pair.base.name} 유지" else "합성 성공 · ${s.inventory.first { it.id==id }.name}")
        return !failed
    }
    fun useScroll(s: State, scrollId: Long, targetId: Long): Boolean {
        if (s.mode != Mode.MENU || s.menu?.type != "upgradeTargets" || s.menu?.argument != scrollId) return false
        val scroll = s.inventory.firstOrNull { it.id==scrollId && it.kind==Kind.SCROLL } ?: return false
        val target = s.inventory.firstOrNull { it.id==targetId && it.equippable } ?: return false
        val next = upgraded(target)
        s.inventory[s.inventory.indexOf(target)] = next; s.inventory.remove(scroll)
        s.hp=min(s.hp,Equipment.stats(s).hp)
        s.note("강화 성공 · ${target.name} → ${next.name} · 강화서 1개 소모")
        s.menuStack.clear(); s.menu=Menu("inventory",returnMode=s.menu!!.returnMode); s.selection=0
        return true
    }
}
