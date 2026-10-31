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
    val view = DungeonFixtures.camera(m, pose)
    val challenge = s.challenge
    val visibility = if (challenge?.kind == ChallengeKind.TORCH) 1.5f + (challenge.torchMs / 1000).toInt() * .25f else 12f
    drawImage(dungeonRaster!!.render(m, view, DungeonTheme.forFloor(s.floor), visibility), filterQuality = FilterQuality.None)
}
