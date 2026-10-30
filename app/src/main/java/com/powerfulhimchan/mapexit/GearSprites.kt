package com.powerfulhimchan.mapexit

import com.powerfulhimchan.mapexit.core.*

internal object GearSprites {
    private val cache = mutableMapOf<String, FineSprites.Art>()
    fun get(key: String): FineSprites.Art? {
        if (!key.startsWith("gear:")) return FineSprites.all[key]
        return cache.getOrPut(key) {
            val parts = key.split(':')
            val base = FineSprites.all[parts[1]] ?: FineSprites.all.getValue("sword")
            val tier = parts[2].toInt().coerceIn(1, 7)
            val ramps = listOf("345", "cde", "BCE", "vwx", "myz", "yz6", "DEF")
            val ramp = ramps[tier - 1]
            FineSprites.Art(base.rows.map { row -> row.map { pixel -> when (pixel) {
                '3' -> ramp[0]; '4' -> ramp[1]; '5' -> ramp[2]; else -> pixel
            } }.joinToString("") })
        }
    }
}

internal fun gearSprite(gear: Gear): String = GearAppearance.key(gear)
