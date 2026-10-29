package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.powerfulhimchan.mapexit.core.Stat
import kotlin.math.floor

/** Small authored glyphs retain crisp edges and remain supplementary to the text labels. */
@Composable
internal fun StatArt(stat: Stat, modifier: Modifier = Modifier) {
    val rows =
        when (stat) {
            Stat.HP ->
                listOf(
                    ".##..##.",
                    "########",
                    "########",
                    "########",
                    ".######.",
                    "..####..",
                    "...##...",
                    "........",
                )
            Stat.ATTACK ->
                listOf(
                    "......##",
                    ".....###",
                    "....###.",
                    "#..###..",
                    ".####...",
                    "..##....",
                    ".#.##...",
                    "#...#...",
                )
            Stat.DEFENSE ->
                listOf(
                    ".######.",
                    "########",
                    "##.##.##",
                    "##.##.##",
                    ".######.",
                    ".######.",
                    "..####..",
                    "...##...",
                )
            Stat.AGILITY ->
                listOf(
                    "...##...",
                    "..####..",
                    "######..",
                    ".#####..",
                    "..####..",
                    "..######",
                    ".#######",
                    "........",
                )
            Stat.CRIT ->
                listOf(
                    "...##...",
                    ".######.",
                    ".##..##.",
                    "##.##.##",
                    "##.##.##",
                    ".##..##.",
                    ".######.",
                    "...##...",
                )
            Stat.CRIT_DAMAGE ->
                listOf(
                    "#..##..#",
                    ".#.##.#.",
                    "..####..",
                    "########",
                    "########",
                    "..####..",
                    ".#.##.#.",
                    "#..##..#",
                )
            Stat.LUCK ->
                listOf(
                    ".##..##.",
                    "########",
                    ".######.",
                    "..####..",
                    ".######.",
                    "########",
                    ".##..##.",
                    "....#...",
                )
        }
    val color =
        when (stat) {
            Stat.HP -> red
            Stat.ATTACK,
            Stat.CRIT_DAMAGE -> amber
            Stat.DEFENSE,
            Stat.LUCK -> mint
            else -> Color(0xffa2c9eb)
        }
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = floor(minOf(size.width, size.height) / 10).coerceAtLeast(1f)
        val ox = (size.width - 8 * unit) / 2
        val oy = (size.height - 8 * unit) / 2
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '#') {
                    drawRect(
                        color.copy(alpha = .25f),
                        Offset(ox + x * unit, oy + (y + 1) * unit),
                        Size(unit, unit),
                    )
                    drawRect(
                        if (y < 3) color else color.copy(alpha = .8f),
                        Offset(ox + x * unit, oy + y * unit),
                        Size(unit, unit),
                    )
                }
            }
        }
    }
}
