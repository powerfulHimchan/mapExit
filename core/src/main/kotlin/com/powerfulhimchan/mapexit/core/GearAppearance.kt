package com.powerfulhimchan.mapexit.core

/** Item identity drives the same authored silhouette in menus and in combat. */
object GearAppearance {
    fun base(gear: Gear): String {
        if (gear.weapon) {
            val variant = WeaponCatalog.of(gear.kind).firstOrNull { it.id == gear.variantId }
                ?: WeaponCatalog.of(gear.kind).firstOrNull { gear.name.startsWith(it.name) }
                ?: WeaponCatalog.of(gear.kind).first()
            return "weapon_${variant.id}"
        }
        if(gear.kind==Kind.CONSUMABLE) return "consumable_${gear.consumableId}"
        if(gear.kind==Kind.RING && gear.variantId=="abyss-seal") return "ring_abyss_seal"
        if(ArmorCatalog.all.any { it.id==gear.variantId && it.kind==gear.kind }) return if(gear.variantId==gear.kind.name.lowercase()) gear.variantId!! else "armor_${gear.variantId}"
        if (gear.kind == Kind.POTION && gear.potionGrade == PotionGrade.GREATER) return "potion_greater"
        if (gear.kind == Kind.RING && gear.variantId == "exit-seal") return "ring_exit_seal"
        if (gear.kind == Kind.RING) {
            val names = listOf("전사의", "바람의", "광기의", "사냥꾼의", "행운의", "수호의", "광전사의", "암살자의", "철벽의", "생명의", "방랑자의", "추적자의")
            return "ring_${names.indexOfFirst { gear.name.startsWith(it) }.coerceAtLeast(0)}"
        }
        if (gear.kind == Kind.NECKLACE) return "necklace_${gear.charm.name.lowercase()}"
        return gear.kind.name.lowercase()
    }
    fun key(gear: Gear):String {
        val prefix=PrefixCatalog.byKind[gear.kind]?.indexOfFirst { it.id==gear.prefixId } ?: -1
        return "gear:${base(gear)}:${gear.tier.coerceIn(1,7)}:$prefix"
    }
    fun heldScale(kind: Kind): Float = when (kind) { Kind.CLAW -> .52f; Kind.SPEAR -> .8f; else -> 1f }
}
