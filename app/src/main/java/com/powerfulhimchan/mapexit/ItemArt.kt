package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.powerfulhimchan.mapexit.core.Kind
import com.powerfulhimchan.mapexit.core.Gear
import kotlin.math.min

/** The same fine artwork is used in the player's hands, equipment lists and item details. */
@Composable
internal fun ItemArt(kind: Kind, modifier: Modifier = Modifier) {
    val art = remember(kind) { FineSprites.all.getValue(gearSprite(kind)) }
    ItemArtImage(art, modifier)
}

@Composable
internal fun ItemArt(gear: Gear, modifier: Modifier = Modifier) {
    val key = gearSprite(gear)
    val art = remember(key) { requireNotNull(GearSprites.get(key)) }
    ItemArtImage(art, modifier)
}

@Composable
private fun ItemArtImage(art: FineSprites.Art, modifier: Modifier) {
    Canvas(modifier.background(ink).border(1.dp, line).clearAndSetSemantics {}) {
        val scale =
            min((size.width - 8) / art.width, (size.height - 8) / art.height)
                .coerceAtLeast(.1f)
        val w = art.width * scale
        val h = art.height * scale
        drawImage(
            art.bitmap,
            srcSize = IntSize(art.width, art.height),
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None,
        )
    }
}
