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
            val rows=base.rows.map { row -> row.map { pixel -> when (pixel) {
                '3' -> ramp[0]; '4' -> ramp[1]; '5' -> ramp[2]; else -> pixel
            } }.joinToString("").toCharArray() }.toMutableList()
            val prefix=parts.getOrNull(3)?.toIntOrNull() ?: -1
            if(prefix>=0) {
                val glyphs=listOf(listOf("020","222","020"),listOf("002","020","200"),listOf("222","202","222"),listOf("202","020","202"),listOf("222","020","020"))
                val color=listOf('F','t','p','B','w')[prefix%5]
                val x=base.width-7;val y=base.height-8
                for(yy in 0..6) for(xx in 0..5) rows[y+yy][x+xx]='1'
                glyphs[prefix%5].forEachIndexed { yy,row -> row.forEachIndexed { xx,c -> if(c!='0') rows[y+yy+2][x+xx+1]=color } }
            }
            FineSprites.Art(rows.map { String(it) })
        }
    }
}

internal fun gearSprite(gear: Gear): String = GearAppearance.key(gear)
