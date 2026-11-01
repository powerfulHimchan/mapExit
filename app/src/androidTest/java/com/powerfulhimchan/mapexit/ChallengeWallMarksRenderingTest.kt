package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class ChallengeWallMarksRenderingTest {
    private fun Bitmap.pixels(): List<Int> = IntArray(width*height).also { getPixels(it,0,width,0,0,width,height) }.toList()

    @Test fun actualWallArtRespectsCluesOcclusionDoorsAndChallengeCache() {
        val pictures = (0..5).map { ChallengeWallMarks.bitmap(it,1).pixels() }
        assertEquals(6,pictures.distinct().size)
        assertTrue(pictures.all { pixels -> pixels.any { it ushr 24 == 0 } && pixels.any { it ushr 24 == 255 } })
        assertEquals(17,(1..17).map { ChallengeWallMarks.bitmap(0,it).pixels() }.distinct().size)
        val room = Dungeon(9,9,MutableList(81) { Cell() },4,4,0)
        val facingRaster = DungeonRaster(480,640)
        val masks = (0..3).map { d ->
            room.cells.forEach { it.wall = true }; room.here().wall = false
            val behind = (d+2)%4
            room.cell(4+MapGenerator.dx[behind],4+MapGenerator.dy[behind]).wall = false
            facingRaster.render(room,DungeonViewPose(4.5f,4.5f,(-PI/2+d*PI/2).toFloat()),wallMarks=true)
                .asAndroidBitmap().pixels().map { color ->
                    val red = (color ushr 16) and 255; val green = (color ushr 8) and 255; val blue = color and 255
                    red > 90 && red > green && green > blue
                }
        }
        assertTrue(masks[0].any { it })
        for (mask in masks.drop(1)) assertEquals("Pictures and numbers must stay readable from all four facings",masks[0],mask)
        val m = Dungeon(9,9,MutableList(81) { Cell() },4,6,0)
        for (y in 2..7) m.cell(4,y).wall = false
        val r = DungeonRaster(480,640)
        val view = DungeonViewPose(4.5f,6.5f,(-PI/2).toFloat())
        fun render(marks: Boolean) = r.render(m,view,wallMarks=marks).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
        val ordinary = render(false); val marked = render(true)
        assertFalse(ordinary.sameAs(marked))
        assertTrue("Leaving a challenge must invalidate the mark cache",ordinary.sameAs(render(false)))
        assertTrue("Marks must return at the same physical locations",marked.sameAs(render(true)))
        m.cell(4,5).wall = true
        val blocked = render(true)
        m.cell(4,2).wall = true; m.cell(3,2).wall = false
        assertTrue("Changes behind a wall cannot change its visible marks",blocked.sameAs(render(true)))
        m.cell(4,5).wall = false; m.cell(4,2).wall = false; m.cell(3,2).wall = true
        m.cell(4,2).event = Event.EXIT
        val doorView = DungeonViewPose(4.5f,2.5f,(-PI/2).toFloat())
        val door = r.render(m,doorView,wallMarks=false).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
        val doorMarked = r.render(m,doorView,wallMarks=true).asAndroidBitmap()
        for (y in 30 until 600) for (x in 120 until 360)
            assertEquals("The exit door must not be painted over",door.getPixel(x,y),doorMarked.getPixel(x,y))
    }
}
