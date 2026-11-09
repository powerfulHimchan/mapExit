package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import com.powerfulhimchan.mapexit.core.Kind

/** Attachment points measured on the eight painted 192px body cells, not inventory icons. */
internal object EquipmentFit {
    data class Patch(val source: List<Float>, val corners: List<Float>)
    private fun patch(source: List<Float>, vararg points: Float) = Patch(source, points.toList())
    private val whole = listOf(0f, 0f, 1f, 1f)
    // Clockwise: neck/shoulder line, then waist/hem. The source already has its own silhouette.
    private val torsos = listOf(
        listOf(70f,76f,133f,76f,138f,139f,70f,139f),
        listOf(63f,81f,121f,70f,143f,135f,85f,143f),
        listOf(76f,87f,132f,99f,110f,145f,49f,130f),
        listOf(73f,102f,127f,111f,111f,151f,48f,138f),
        listOf(52f,120f,112f,128f,90f,161f,27f,148f),
        listOf(58f,79f,116f,64f,141f,127f,83f,146f),
        listOf(48f,112f,76f,99f,112f,149f,69f,173f),
        listOf(76f,77f,139f,77f,145f,140f,77f,140f),
    )
    // Hips and each trouser leg attach separately, preserving the opening in a wide stance.
    private val legs = listOf(
        listOf(79f,135f,133f,135f,131f,146f,76f,146f, 78f,143f,101f,145f,94f,171f,71f,170f, 109f,144f,131f,143f,134f,171f,110f,171f),
        listOf(83f,132f,137f,132f,142f,146f,76f,145f, 80f,142f,103f,147f,78f,172f,57f,165f, 115f,143f,137f,139f,147f,170f,125f,173f),
        listOf(56f,132f,111f,139f,121f,151f,51f,148f, 59f,142f,80f,150f,61f,171f,41f,167f, 96f,143f,119f,141f,136f,170f,111f,173f),
        listOf(54f,141f,112f,146f,121f,158f,48f,155f, 54f,149f,76f,155f,55f,173f,36f,169f, 99f,150f,120f,147f,135f,174f,112f,176f),
        listOf(39f,146f,107f,150f,115f,161f,32f,159f, 38f,154f,62f,159f,35f,180f,13f,178f, 94f,154f,115f,151f,148f,178f,124f,181f),
        listOf(63f,133f,135f,131f,140f,145f,57f,149f, 64f,143f,87f,148f,65f,172f,43f,165f, 113f,140f,134f,137f,145f,167f,124f,173f),
        listOf(92f,130f,117f,126f,137f,142f,110f,153f, 108f,143f,126f,135f,150f,153f,132f,165f, 112f,151f,133f,143f,155f,169f,134f,178f),
        listOf(86f,135f,139f,135f,137f,146f,83f,146f, 85f,143f,108f,145f,101f,172f,78f,171f, 116f,144f,138f,143f,141f,172f,117f,172f),
    )
    fun cell(pose: String): Int = MotionAtlas.heroSourceCells[MotionAtlas.heroPoses.indexOf(pose).coerceAtLeast(0)]
    fun patches(kind: Kind, pose: String): List<Patch>? {
        val cell = cell(pose)
        return when (kind) {
            Kind.BODY -> listOf(Patch(whole, torsos[cell]))
            Kind.PANTS -> legs[cell].let { p -> listOf(
                Patch(listOf(0f,0f,1f,.32f),p.subList(0,8)),
                Patch(listOf(0f,.28f,.50f,1f),p.subList(8,16)),
                Patch(listOf(.50f,.28f,1f,1f),p.subList(16,24)),
            ) }
            // Helmets use the head silhouette dimensions.
            Kind.HEAD -> {
                val b = MotionAtlas.worn[cell][0]
                listOf(patch(whole,b[0].toFloat(),b[1].toFloat(),(b[0]+b[2]).toFloat(),b[1].toFloat(),
                    (b[0]+b[2]).toFloat(),(b[1]+b[3]).toFloat(),b[0].toFloat(),(b[1]+b[3]).toFloat()))
            }
            else -> null
        }
    }
    fun point(p: List<Float>, index: Int, x: Float, y: Float, unit: Float, pose: String): Offset {
        val phase = pose.substringAfterLast('_').toIntOrNull() ?: 0
        val bob = if (!pose.startsWith("idle") && phase in listOf(2,4)) if(phase==2)-1f else 1f else 0f
        return Offset(x+(p[index]-32f)*unit*.25f,y+(p[index+1]+bob)*unit*.25f)
    }
}
