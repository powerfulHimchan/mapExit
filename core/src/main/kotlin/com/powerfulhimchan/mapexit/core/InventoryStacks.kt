package com.powerfulhimchan.mapexit.core

/** Display groups retain real item IDs: consuming/selling one never changes its siblings. */
object InventoryStacks {
    data class Stack(val items: List<Gear>, val representative: Gear, val equippedCount: Int) {
        val count: Int get() = items.size
        val displayName: String get() = representative.name + if (count > 1) " ×$count" else ""
    }

    fun groups(items: List<Gear>, equippedIds: Collection<Long> = emptyList()): List<Stack> {
        val worn = equippedIds.toSet()
        return items.groupBy { item ->
            // Inheritance, real rolled stats, variants and potion grade are significant differences.
            item.copy(id = 0, tier = if (item.kind == Kind.POTION) 1 else item.tier,
                affixes = item.affixes.sortedWith(compareBy<WeaponAffix> { it.stat.ordinal }.thenBy { it.amount }))
        }.values.map { copies ->
            Stack(copies.toList(), copies.firstOrNull { it.id !in worn } ?: copies.first(), copies.count { it.id in worn })
        }
    }

    fun inventoryLabel(stack: Stack): String {
        val status = when {
            stack.equippedCount == 0 -> ""
            stack.count == 1 -> " [착용중]"
            else -> " [착용중 ${stack.equippedCount}개]"
        }
        return (if (stack.representative.inherited) "◆ " else "") + stack.displayName + status
    }

    fun saleLabel(state: State, stack: Stack): String {
        val item = stack.representative
        val label = if (stack.count == 1) item.name + Equipment.equippedLabel(state, item.id)
            else inventoryLabel(stack)
        return "$label 판매 · ${Equipment.sellPrice(item)}G"
    }
}
