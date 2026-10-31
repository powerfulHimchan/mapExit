package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.powerfulhimchan.mapexit.core.Dungeon
import com.powerfulhimchan.mapexit.core.ChallengeStages
import com.powerfulhimchan.mapexit.core.MonsterCatalog
import com.powerfulhimchan.mapexit.core.DungeonFixtures
import com.powerfulhimchan.mapexit.core.Event
import com.powerfulhimchan.mapexit.core.DungeonTheme
import com.powerfulhimchan.mapexit.core.DungeonViewPose
import kotlin.math.*

/** World-space textures and a continuous camera keep courses aligned across cell boundaries. */
internal class DungeonRaster(private val width: Int, private val height: Int) {
    private class Texture(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels =
            IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }

        fun sample(u: Float, v: Float): Int = pixels[(v * height).toInt().coerceIn(0,height-1)*width + (u*width).toInt().coerceIn(0,width-1)]
        fun at(u: Float, v: Float): Int {
            val x = ((u - floor(u)) * width).toInt().coerceIn(0, width - 1)
            val y = ((v - floor(v)) * height).toInt().coerceIn(0, height - 1)
            return pixels[y * width + x]
        }
    }

    private data class Materials(val wall: Texture, val ceiling: Texture, val floor: Texture)
    private val materials = mutableMapOf<DungeonTheme, Materials>()
    private var lastTheme: DungeonTheme? = null
    private val torch = Texture(FineSprites.all.getValue("torch").bitmap.asAndroidBitmap())
    private val pixels = IntArray(width * height)
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val image = bitmap.asImageBitmap()
    private val assets = FineSprites.all.mapValues { Texture(it.value.bitmap.asAndroidBitmap()) }.toMutableMap().apply {
        val plate=Bitmap.createBitmap(32,12,Bitmap.Config.ARGB_8888)
        for(y in 2..10) for(x in 1..30) {
            val edge=x in listOf(1,30) || y in listOf(2,10)
            plate.setPixel(x,y,if(edge)0xff243b49.toInt() else if((x+y)%6==0)0xffa69b77.toInt() else 0xff63737a.toInt())
        }
        put("trap",Texture(plate))
    }
    private val wallDepth = FloatArray(width)
    private val rayX = FloatArray(width)
    private val rayY = FloatArray(width)
    private var lastMap: Dungeon? = null
    private var lastCamera: DungeonViewPose? = null
    private var lastGeometry = 0
    private var lastVisibility = 12f

    private fun shade(color: Int, light: Int): Int =
        (0xff shl 24) or
            ((((color ushr 16) and 255) * light / 256) shl 16) or
            ((((color ushr 8) and 255) * light / 256) shl 8) or
            ((color and 255) * light / 256)

    fun render(map: Dungeon, camera: DungeonViewPose, theme: DungeonTheme = DungeonTheme.MOSS, visibility: Float = 12f): ImageBitmap {
        fun fog(light: Int, distance: Float): Int = if (visibility >= 12f) light else (light * (1f - distance / visibility).coerceIn(0f, 1f)).toInt()
        val textures = materials.getOrPut(theme) {
            Materials(Texture(PixelMaterials.wall(Color(theme.wall), theme = theme)),
                Texture(PixelMaterials.wall(Color(theme.ceiling), 8, theme)),
                Texture(PixelMaterials.floor(theme)))
        }
        val geometry = map.cells.fold(1) { hash, cell -> hash * 31 + (if (cell.wall) 1 else 0) + cell.event.ordinal * 3 + cell.challengeMark * 67 + cell.npcRole.ordinal * 43 + (cell.monsterId?.hashCode() ?: 0) + if (cell.resolved) 37 else 0 }
        if (lastMap === map && lastCamera == camera && lastGeometry == geometry && lastTheme == theme && lastVisibility == visibility) return image
        val dx = cos(camera.angle)
        val dy = sin(camera.angle)
        val focal = DungeonFixtures.focal(map,width,height)
        val plane = width / (2f * focal)
        val horizon = height * .48f
        for (x in 0 until width) {
            val side = (2f * (x + .5f) / width - 1f) * plane
            rayX[x] = dx - dy * side
            rayY[x] = dy + dx * side
        }
        // Floor casting uses the same rays as the walls. The pattern never restarts per step.
        for (y in 0 until height) {
            val below = y + .5f > horizon
            val distance =
                (.5f * focal / abs(y + .5f - horizon).coerceAtLeast(.25f)).coerceAtMost(100f)
            val light =
                ((if (below) 225f else 170f) / (1f + distance * .14f)).toInt().coerceAtLeast(48)
            val material = if (below) textures.floor else textures.ceiling
            for (x in 0 until width) {
                val worldX = camera.x + rayX[x] * distance
                val worldY = camera.y + rayY[x] * distance
                pixels[y * width + x] = shade(material.at(worldX / 2, worldY / 2), fog(light, distance))
            }
        }
        fun solid(x: Int, y: Int): Boolean =
            x !in 0 until map.width || y !in 0 until map.height || map.cells[y * map.width + x].wall
        for (x in 0 until width) {
            val rx = rayX[x]
            val ry = rayY[x]
            var mx = floor(camera.x).toInt()
            var my = floor(camera.y).toInt()
            val deltaX = if (abs(rx) < .00001f) 100000f else abs(1f / rx)
            val deltaY = if (abs(ry) < .00001f) 100000f else abs(1f / ry)
            val stepX = if (rx < 0) -1 else 1
            val stepY = if (ry < 0) -1 else 1
            var sideX = (if (rx < 0) camera.x - mx else mx + 1f - camera.x) * deltaX
            var sideY = (if (ry < 0) camera.y - my else my + 1f - camera.y) * deltaY
            var side = 0
            var distance = 1f
            for (step in 0..96) {
                if (sideX < sideY) {
                    distance = sideX
                    sideX += deltaX
                    mx += stepX
                    side = 0
                } else {
                    distance = sideY
                    sideY += deltaY
                    my += stepY
                    side = 1
                }
                if (solid(mx, my)) break
            }
            distance = distance.coerceAtLeast(.03f)
            wallDepth[x] = distance
            val span = focal / distance
            val top = horizon - span * .5f
            val bottom = horizon + span * .5f
            val start = ceil(top).toInt().coerceAtLeast(0)
            val end = floor(bottom).toInt().coerceAtMost(height - 1)
            val hit = if (side == 0) camera.y + ry * distance else camera.x + rx * distance
            val u = hit - floor(hit)
            val light =
                ((if (side == 0) 232f else 208f) / (1f + distance * .10f)).toInt().coerceAtLeast(65)
            val isDoor = DungeonFixtures.doorOnFace(map, mx, my, if (side == 0) mx - stepX else mx, if (side == 1) my - stepY else my)
            val hasTorch = ((mx * 31 + my * 17) and 7) == 0
            for (y in start..end) {
                val v = (y + .5f - top) / span
                var color = textures.wall.at(u, v)
                if (hasTorch && u >= .38f && u <= .62f && v >= .24f && v <= .60f) {
                    val flame = torch.at((u - .38f) / .24f, (v - .24f) / .36f)
                    if ((flame ushr 24) > 0) {
                        val r = (flame ushr 16) and 255
                        val g = (flame ushr 8) and 255
                        color = if (r > g && g > 60) shade(theme.light, (r + 80).coerceAtMost(256)) else flame
                    }
                }
                if (isDoor && u in .17f.. .83f && v in .08f..1f) {
                    val door = assets.getValue("door").sample((u-.17f)/.66f,(v-.08f)/.92f)
                    if (door ushr 24 != 0) color = door
                }
                // One contact edge at the actual floor, rather than a projecting lip per tile.
                val edge = if (bottom - y < 2f || y - top < 1f) light * 2 / 3 else light
                pixels[y * width + x] = shade(color, fog(edge, distance))
            }
        }
        // Back-to-front world billboards share wall depth and the floor contact plane.
        DungeonFixtures.visible(map,camera).forEach { fixture ->
            val name = when(fixture.event) {
                Event.HEAL -> "heal"; Event.CHEST -> "chest"; Event.MERCHANT -> "merchant"
                Event.NPC -> map.cell(fixture.x,fixture.y).npcRole.sprite
                Event.MONSTER -> MonsterCatalog.find(map.cell(fixture.x,fixture.y).monsterId)?.sprite
                    ?: MonsterCatalog.forFloor(theme.ordinal * 10 + 1)[(fixture.x + fixture.y) % 6].sprite
                Event.ELITE -> MonsterCatalog.special(theme.ordinal * 10 + 1, false).sprite
                Event.PORTAL -> "challenge_portal"
                Event.CHALLENGE -> ChallengeStages.featureSprite(map.cell(fixture.x, fixture.y))
                Event.TRAP -> "trap"
                else -> null
            } ?: return@forEach
            val texture=assets.getValue(name)
            val depth=fixture.depth
            if (visibility < 12f && depth >= visibility) return@forEach
            val span=DungeonFixtures.spriteSpan(fixture.event,focal,depth,width,height,texture.width.toFloat()/texture.height)
            val spriteWidth=span*texture.width/texture.height
            val center=width/2f+fixture.lateral*focal/depth
            val left=center-spriteWidth/2f
            val base=horizon+.5f*focal/depth
            val top=base-span
            val light=(240f/(1+depth*.10f)).toInt().coerceAtLeast(65)
            val shadowW=spriteWidth*.65f
            val shadowH=(span*.045f).coerceAtLeast(1f)
            for (yy in max(0,(base-shadowH/2).toInt())..min(height-1,(base+shadowH/2).toInt())) {
                for (xx in max(0,(center-shadowW/2).toInt())..min(width-1,(center+shadowW/2).toInt())) {
                    val sx=(xx+.5f-center)/(shadowW/2);val sy=(yy+.5f-base)/(shadowH/2)
                    if(sx*sx+sy*sy<1 && depth<wallDepth[xx]) pixels[yy*width+xx]=shade(pixels[yy*width+xx],145)
                }
            }
            for(yy in max(0,ceil(top).toInt())..min(height-1,floor(base).toInt())) {
                for(xx in max(0,ceil(left).toInt())..min(width-1,floor(left+spriteWidth).toInt())) {
                    if(depth>=wallDepth[xx]) continue
                    val color=texture.sample((xx+.5f-left)/spriteWidth,(yy+.5f-top)/span)
                    if(color ushr 24 !=0) pixels[yy*width+xx]=shade(color,fog(light,depth))
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        lastTheme = theme
        lastMap = map
        lastCamera = camera
        lastGeometry = geometry
        lastVisibility = visibility
        return image
    }
}
