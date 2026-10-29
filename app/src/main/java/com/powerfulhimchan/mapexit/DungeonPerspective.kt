package com.powerfulhimchan.mapexit

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.PI

private var rasterSize = 0 to 0
private var dungeonRaster: DungeonRaster? = null

/** Solid map cells expose their real side faces; camera movement shares one projection. */
internal fun DrawScope.drawDungeonPerspective(
    s: State,
    clock: Long,
    camera: DungeonViewPose? = null,
) {
    val m = s.dungeon ?: return
    val w = size.width
    val h = size.height
    val dimensions = w.toInt() to h.toInt()
    if (dungeonRaster == null || rasterSize != dimensions) {
        dungeonRaster = DungeonRaster(dimensions.first, dimensions.second)
        rasterSize = dimensions
    }
    val pose =
        camera ?: DungeonViewPose(m.x + .5f, m.y + .5f, (-PI / 2 + m.direction * PI / 2).toFloat())
    drawImage(dungeonRaster!!.render(m, pose), filterQuality = FilterQuality.None)
    val cell = m.here()
    if (!cell.resolved) {
        val type =
            when (cell.event) {
                Event.CHEST -> "chest"
                Event.HEAL -> "heal"
                Event.MERCHANT -> "merchant"
                Event.NPC -> "npc"
                Event.EXIT -> "door"
                else -> null
            }
        if (type != null)
            sprite(
                type,
                w * .34f,
                h * .89f - spriteHeight(type, minOf(w * .26f, h * .22f)),
                minOf(w * .26f, h * .22f),
                if (cell.event == Event.HEAL) mint else amber,
            )
    }
    pixelRect(amber, w * .48f, h * .94f, w * .04f, 2f)
}
