package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** Authored narrative pictures and chest props use the same pixel filtering as combat. */
internal object NarrativeArtwork {
    val storyKeys = listOf("lantern", "contract", "record", "flame", "ember", "fragment", "chains",
        "guardian", "possessed", "door", "footprints", "stairs", "names", "graves", "water", "ledger",
        "corridor", "lanterns", "letter", "prayer", "sealed_door")
    private data class Region(val offset: IntOffset, val size: IntSize)
    private data class Assets(val story: ImageBitmap, val chest: ImageBitmap, val chests: List<Region>)
    @Volatile private var assets: Assets? = null

    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id,
            BitmapFactory.Options().apply { inScaled = false }))
        val story = decode(R.drawable.story_scenes_047)
        val chest = decode(R.drawable.chest_047)
        val regions = (0..2).map { index ->
            val left = chest.width * index / 3
            val right = chest.width * (index + 1) / 3
            var x0 = right; var y0 = chest.height; var x1 = left; var y1 = 0
            for (y in 0 until chest.height) for (x in left until right) {
                if (chest.getPixel(x,y) ushr 24 > 24) {
                    x0 = minOf(x0,x); y0 = minOf(y0,y); x1 = maxOf(x1,x+1); y1 = maxOf(y1,y+1)
                }
            }
            require(x1 > x0 && y1 > y0)
            Region(IntOffset(x0,y0),IntSize(x1-x0,y1-y0))
        }
        assets = Assets(story.asImageBitmap(),chest.asImageBitmap(),regions)
    }

    fun DrawScope.paintStory(key: String): Boolean {
        val a = assets ?: return false
        val index = storyKeys.indexOf(key)
        if (index < 0) return false
        val col = index % 3; val row = index / 3
        val x = col * a.story.width / 3 + 2
        val y = row * a.story.height / 7 + 2
        val width = (col + 1) * a.story.width / 3 - x - 2
        val height = (row + 1) * a.story.height / 7 - y - 2
        val scale = min(size.width / width, size.height / height)
        val dst = IntSize((width*scale).roundToInt().coerceAtLeast(1),(height*scale).roundToInt().coerceAtLeast(1))
        drawRect(Color(0xFF101B28))
        drawImage(a.story,srcOffset=IntOffset(x,y),srcSize=IntSize(width,height),
            dstOffset=IntOffset(((size.width-dst.width)/2).roundToInt(),((size.height-dst.height)/2).roundToInt()),
            dstSize=dst,filterQuality=FilterQuality.None)
        return true
    }

    fun DrawScope.paintChest(open: Boolean, x: Float, baseline: Float, width: Float) {
        val a = assets ?: return
        val region = a.chests[if (open) 1 else 0]
        val height = width * region.size.height / region.size.width
        drawImage(a.chest,srcOffset=region.offset,srcSize=region.size,
            dstOffset=IntOffset(x.roundToInt(),(baseline-height).roundToInt()),
            dstSize=IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),
            filterQuality=FilterQuality.None)
    }
}
