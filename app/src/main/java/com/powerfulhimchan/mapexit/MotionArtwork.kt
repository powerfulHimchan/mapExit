package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.*

/** Painted reference-matched assets keep their native detail through the existing battle rig. */
internal object MotionArtwork {
    private data class Frame(val image: ImageBitmap, val offset: IntOffset, val size: IntSize, val nativeScale: Float = 4f, val logicalX: Float = 0f)
    private data class Assets(val hero: ImageBitmap, val monsters: ImageBitmap, val gear: Bitmap, val arena: ImageBitmap, val arms: ImageBitmap)
    @Volatile private var assets: Assets? = null
    val loaded get() = assets != null
    private val heroIndex = MotionAtlas.heroPoses.withIndex().associate { it.value to it.index }
    private val monsterIndex = MotionAtlas.monsters.withIndex().associate { it.value to it.index }
    private val monsterPoseIndex = MotionAtlas.monsterPoses.withIndex().associate { it.value to it.index }
    private val gearIndex = MotionAtlas.gear.withIndex().associate { it.value to it.index }
    private val gearCache = object : LinkedHashMap<String, Frame>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Frame>?) = size > 128
    }
    private val dressedCache=object : LinkedHashMap<String,Frame>(32,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Frame>?)=size>32
    }

    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false }))
        assets = Assets(decode(R.drawable.combat_hero_motion).asImageBitmap(), decode(R.drawable.combat_monster_motion).asImageBitmap(), decode(R.drawable.combat_equipment_motion), decode(R.drawable.combat_arena_refined).asImageBitmap(), decode(R.drawable.combat_arms_046).asImageBitmap())
        ObstacleArtwork.load(resources)
        NarrativeArtwork.load(resources)
        PreparationArtwork.load(resources)
        PresentationArtwork.load(resources)
    }

    fun has(type: String, pose: String = "idle_0"): Boolean = when {
        type == "heroBack" -> pose in heroIndex
        monsterKey(type) in monsterIndex -> pose in monsterPoseIndex
        else -> base(type) in gearIndex
    }
    private fun base(type: String) = if (type.startsWith("gear:")) type.split(':')[1] else type
    private fun monsterKey(type: String) = if (type.startsWith("monster_")) type else "monster_0_$type"
    private fun tile(image: ImageBitmap, index: Int, columns: Int, cellWidth: Int, cellHeight: Int,
        height: Int = cellHeight, nativeScale: Float = 4f, logicalX: Float = 0f) =
        Frame(image, IntOffset(index % columns * cellWidth, index / columns * cellHeight), IntSize(cellWidth, height), nativeScale, logicalX)

    private fun frame(type: String, pose: String): Frame? {
        val a = assets ?: return null
        if (type == "heroBack") return heroIndex[pose]?.let { tile(a.hero, it, 12, 192, 192, logicalX = -8f) }
        monsterIndex[monsterKey(type)]?.let { index ->
            val phase = monsterPoseIndex[pose] ?: return null
            return tile(a.monsters, index * MotionAtlas.monsterPoses.size + phase, 24, 144, 144, MotionAtlas.monsterHeights[index], 3f, -8f)
        }
        return gear(type, badge = false)
    }

    @Synchronized private fun gear(type: String, badge: Boolean): Frame? {
        val a = assets ?: return null
        val index = gearIndex[base(type)] ?: return null
        val height = MotionAtlas.gearHeights[index]
        if (!type.startsWith("gear:")) return tile(a.gear.asImageBitmap(), index, 10, 128, 192, height)
        val parts = type.split(':')
        val tier = (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 7)
        val prefix = if (badge) parts.getOrNull(3)?.toIntOrNull() ?: -1 else -1
        val key = "${base(type)}:$tier:$prefix"
        return gearCache.getOrPut(key) {
            val pixels = IntArray(128 * height)
            a.gear.getPixels(pixels, 0, 128, index % 10 * 128, index / 10 * 192, 128, height)
            val palette = MotionAtlas.palette
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val hues = floatArrayOf(0f, 135f, 205f, 275f, 320f, 25f, 45f)
            val hsv = FloatArray(3)
            if (tier > 1) for (i in pixels.indices) {
                val c = pixels[i]
                android.graphics.Color.colorToHSV(c, hsv)
                // Recolour steel planes while preserving the painted light, leather and gold hardware.
                if (c ushr 24 > 0 && hsv[1] < .28f && hsv[2] > .14f) {
                    hsv[0] = hues[tier - 1]
                    hsv[1] = .30f + tier * .025f
                    pixels[i] = android.graphics.Color.HSVToColor(c ushr 24, hsv)
                }
            }
            if (prefix >= 0) {
                val glyphs = listOf(listOf("020", "222", "020"), listOf("002", "020", "200"), listOf("222", "202", "222"), listOf("202", "020", "202"), listOf("222", "020", "020"))
                val color = palette[alphabet.indexOf(listOf('F', 't', 'p', 'B', 'w')[prefix % 5])]
                val x0 = 102; val y0 = height - 32
                for (y in 0..27) for (x in 0..23) pixels[(y0 + y) * 128 + x0 + x] = palette[1]
                glyphs[prefix % 5].forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '0') for (yy in 0..3) for (xx in 0..3) pixels[(y0 + 8 + y * 4 + yy) * 128 + x0 + 4 + x * 4 + xx] = color } }
            }
            Frame(Bitmap.createBitmap(pixels, 128, height, Bitmap.Config.ARGB_8888).asImageBitmap(), IntOffset.Zero, IntSize(128, height))
        }
    }

    private fun DrawScope.paint(frame: Frame, x: Float, y: Float, width: Float, height: Float, flash: Color?, opacity: Float) {
        drawImage(frame.image, srcOffset = frame.offset, srcSize = frame.size,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None, alpha = opacity.coerceIn(0f, 1f), colorFilter = flash?.let { ColorFilter.tint(it, BlendMode.SrcAtop) })
    }
    fun DrawScope.paintSprite(type: String, pose: String, x: Float, y: Float, unit: Float, flash: Color?, opacity: Float): Boolean {
        val f = frame(type, pose) ?: return false
        paint(f, x + f.logicalX * unit, y, f.size.width / f.nativeScale * unit, f.size.height / f.nativeScale * unit, flash, opacity)
        return true
    }
    @Synchronized fun DrawScope.paintDressedBody(item: Gear?,pose: String,x: Float,y: Float,unit: Float,flash: Color?,headless: Boolean=false): Boolean {
        val original=frame("heroBack",pose) ?: return false
        val key="${item?.variantId}:${item?.tier}:$pose:$headless"
        val dressed=dressedCache[key] ?: run {
            val body=Bitmap.createBitmap(original.image.asAndroidBitmap(),original.offset.x,original.offset.y,original.size.width,original.size.height)
            var fitted=if(item!=null)PreparationArtwork.dressedBody(item,body,pose) ?: return false else body.copy(Bitmap.Config.ARGB_8888,true)
            if(headless) {
                val raw=IntArray(body.width*body.height);val pixels=IntArray(raw.size)
                body.getPixels(raw,0,body.width,0,0,body.width,body.height)
                fitted.getPixels(pixels,0,body.width,0,0,body.width,body.height)
                NativeEquipment057.clearHead(pixels,raw,body.width,body.height,NativeEquipment057.battleHead(pose))
                fitted.recycle();fitted=Bitmap.createBitmap(pixels,body.width,body.height,Bitmap.Config.ARGB_8888)
            }
            body.recycle()
            original.copy(image=fitted.asImageBitmap(),offset=IntOffset.Zero).also { dressedCache[key]=it }
        }
        paint(dressed,x+dressed.logicalX*unit,y,dressed.size.width/dressed.nativeScale*unit,dressed.size.height/dressed.nativeScale*unit,flash,1f)
        return true
    }
    fun DrawScope.paintWorn(item: Gear, x: Float, y: Float, width: Float, height: Float, flash: Color?, angle: Float = 0f, back: Boolean = false): Boolean {
        if (back && with(PreparationArtwork) { paintWorn(item,x,y,width,height,flash,angle) }) return true
        val type = if(item.kind == Kind.NECKLACE) "necklace" else gearSprite(item)
        val index = gearIndex[base(type)] ?: return false
        val f = gear(type, badge = false) ?: return false
        val b = MotionAtlas.gearBounds[index]
        withTransform({ rotate(angle, Offset(x + width / 2, y + height / 2)) }) {
            paint(f.copy(offset = f.offset + IntOffset(b[0], b[1]), size = IntSize(b[2] - b[0], b[3] - b[1])), x, y, width, height, flash, 1f)
        }
        return true
    }

    // Socket centres on the painted sleeve caps. The old crouch/attack landmarks
    // lay outside the body silhouette and left the upper sleeve detached in motion.
    private val rightSockets=listOf(Offset(123f,90f),Offset(116f,93f),Offset(123f,99f),Offset(123f,111f),
        Offset(112f,115f),Offset(112f,76f),Offset(91f,128f),Offset(130f,90f))
    private val leftSockets=listOf(Offset(80f,88f),Offset(80f,89f),Offset(88f,85f),Offset(86f,99f),
        Offset(92f,101f),Offset(65f,72f),Offset(57f,130f),Offset(87f,88f))
    private fun socket(points: List<Offset>,pose: String): Offset {
        val p=points[EquipmentFit.cell(pose)]
        return Offset((p.x-32f)*.25f,(p.y+EquipmentFit.bob(pose))*.25f)
    }
    fun shoulder(pose: String): Offset = socket(rightSockets,pose)
    fun leftShoulder(pose: String): Offset = socket(leftSockets,pose)
    fun bodyScale(pose: String) = if (pose.startsWith("death_")) .55f else 1f
    data class WornPlacement(val area: List<Float>, val angle: Float)
    fun wornPlacement(kind: Kind, pose: String): WornPlacement? {
        val part = when (kind) { Kind.HEAD -> 0; Kind.BODY -> 1; Kind.PANTS -> 2; Kind.BOOTS -> 3; else -> return null }
        val cell = MotionAtlas.heroSourceCells[heroIndex[pose] ?: 0]
        val b = MotionAtlas.worn[cell][part]
        return WornPlacement(listOf((b[0] - 32) * .25f, b[1] * .25f, b[2] * .25f, b[3] * .25f), b[4].toFloat())
    }
    fun monsterHand(type: String, pose: String): Offset? {
        val monster = monsterIndex[monsterKey(type)] ?: return null
        val phase = monsterPoseIndex[pose] ?: return null
        return MotionAtlas.monsterHands[monster * MotionAtlas.monsterPoses.size + phase].let { Offset(it[0] / 3f, it[1] / 3f) }
    }
    fun weaponGrip(type: String): Offset? = gearIndex[base(type)]?.let { MotionAtlas.gearGrips[it] }?.let { Offset(it[0] * .25f, it[1] * .25f) }
    fun DrawScope.paintArena(clock: Long, floor: Int): Boolean {
        val a = assets ?: return false
        paint(Frame(a.arena, IntOffset.Zero, IntSize(a.arena.width, a.arena.height)), 0f, 0f, size.width, size.height, null, 1f)
        if (floor > 10) drawRect(Color(DungeonTheme.forFloor(floor).wall).copy(alpha = .16f))
        val pulse = (.022f + .012f * sin(clock * .004).toFloat()).coerceAtLeast(0f)
        for (x in listOf(.045f, .955f)) drawCircle(Color(0xFFFFB957).copy(alpha = pulse), size.width * .12f, Offset(size.width * x, size.height * .21f))
        return true
    }

    private fun armPose(a: Offset, b: Offset, unit: Float): ArmKinematics.Pose =
        ArmKinematics.solve(ArmKinematics.Point(a.x,a.y),ArmKinematics.Point(b.x,b.y),unit*6.5f,unit*6.5f)

    fun DrawScope.arm(a: Offset, b: Offset, unit: Float, opacity: Float, metal: Boolean = false, forearmOnly: Boolean = false): Boolean =
        paintArm(armPose(a,b,unit),unit,opacity,metal,forearmOnly)

    /** Render the supplied joints directly; do not solve a rear arm a second time. */
    fun DrawScope.paintArm(pose: ArmKinematics.Pose, unit: Float, opacity: Float = 1f,
        metal: Boolean = false, forearmOnly: Boolean = false, upperOnly: Boolean = false,
        rear: Boolean = false, mirror: Boolean = false): Boolean {
        val image=assets?.arms ?: return false
        val shoulder=Offset(pose.shoulder.x,pose.shoulder.y)
        val elbow=Offset(pose.elbow.x,pose.elbow.y);val wrist=Offset(pose.wrist.x,pose.wrist.y)
        fun segment(start: Offset,end: Offset,index: Int,width: Float) {
            val delta=end-start
            withTransform({
                rotate(atan2(delta.y,delta.x)*180f/PI.toFloat()-90f,start)
                if(mirror)scale(-1f,1f,start)
            }) {
                // Rear upper sleeves sit below the native shoulder cap. The cuff ends
                // at the actual wrist, leaving the glove's cuff to overlap that joint.
                paint(Frame(image,IntOffset(index*32,0),IntSize(32,64)),start.x-width/2,start.y-unit*.4f,
                    width,hypot(delta.x,delta.y)+unit*.8f,null,opacity)
            }
        }
        val base=if(metal)3 else 0
        if (!forearmOnly) segment(shoulder,elbow,base,unit*(if(rear)3.8f else 4.5f))
        if (!upperOnly) segment(elbow,wrist,base+1,unit*(if(rear)3.2f else 3.5f))
        return true
    }
    fun DrawScope.grip(hand: Offset, unit: Float, angle: Float, opacity: Float, metal: Boolean = false, mirror: Boolean = false) {
        val image=assets?.arms ?: return
        withTransform({ rotate(angle,hand);if(mirror)scale(-1f,1f,hand) }) {
            // The source cuff is below the compact fist; its centre coincides with the weapon handle.
            paint(Frame(image,IntOffset((if(metal)5 else 2)*32,0),IntSize(32,32)),
                hand.x-unit*1.8f,hand.y-unit*1.7f,unit*3.6f,unit*3.6f,null,opacity)
        }
    }
}
