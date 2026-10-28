package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared with the Python checker. Gradle embeds balance/weapons.json into this module. */
object GameBalance {
    private val root = Json.parseToJsonElement(BALANCE_JSON).jsonObject

    fun model(key: String): Double =
        root.getValue("model").jsonObject.getValue(key).jsonPrimitive.double

    fun shield(key: String): Double =
        root.getValue("shield").jsonObject.getValue(key).jsonPrimitive.double

    fun weapon(kind: Kind, tier: Double): Stats {
        val data = root.getValue("weapons").jsonObject.getValue(kind.name.lowercase()).jsonObject
        fun value(key: String) = data.getValue(key).jsonPrimitive.double
        return Stats.zero()
            .copy(
                attack = value("attack") * tier,
                defense = value("defense") * tier,
                agility = value("agility") * tier,
                crit = value("crit_chance"),
                critDamage = value("crit_damage"),
            )
    }
}
