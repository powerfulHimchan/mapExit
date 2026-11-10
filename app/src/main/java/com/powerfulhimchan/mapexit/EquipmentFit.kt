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
        listOf(68f,58f,136f,61f,139f,139f,68f,139f),
        listOf(57f,67f,119f,56f,147f,137f,82f,147f),
        listOf(79f,60f,143f,76f,120f,148f,49f,126f),
        listOf(75f,78f,136f,94f,118f,155f,44f,137f),
        listOf(52f,86f,121f,103f,101f,165f,20f,143f),
        listOf(50f,65f,110f,45f,146f,125f,80f,149f),
        listOf(33f,111f,83f,97f,126f,151f,72f,178f),
        listOf(75f,58f,143f,61f,146f,139f,75f,139f),
    )
    // Full clothing contours, including the native collar, shoulder cap and coat hem.
    // Armor is composited into these original body pixels; its icon silhouette is never
    // pasted on top of a second coat. Coordinates remain in the native 192px cell.
    private val coats = listOf(
        listOf(82f,60f,112f,60f,124f,69f,134f,82f,136f,92f,128f,102f,128f,112f,140f,133f,110f,140f,72f,137f,68f,130f,78f,111f,78f,100f,70f,87f,72f,79f,80f,73f),
        listOf(86f,65f,111f,59f,123f,68f,131f,92f,130f,104f,134f,116f,148f,135f,117f,145f,82f,145f,74f,137f,84f,114f,78f,101f,64f,95f,59f,84f,66f,75f),
        listOf(99f,63f,119f,66f,136f,80f,140f,99f,129f,111f,121f,122f,127f,139f,106f,148f,72f,138f,49f,128f,59f,119f,77f,107f,82f,94f,79f,87f,76f,82f,84f,75f,95f,72f),
        listOf(102f,79f,122f,82f,134f,98f,136f,116f,122f,124f,118f,134f,122f,149f,103f,155f,68f,148f,46f,137f,58f,123f,72f,114f,74f,103f,70f,98f,76f,91f,95f,87f),
        listOf(96f,91f,113f,99f,123f,111f,121f,127f,108f,137f,104f,151f,91f,163f,61f,158f,22f,143f,30f,133f,51f,122f,58f,112f,54f,104f,48f,100f,58f,94f,82f,91f),
        listOf(84f,53f,103f,48f,116f,57f,133f,79f,134f,94f,138f,104f,149f,119f,132f,131f,99f,145f,78f,147f,67f,137f,75f,115f,70f,103f,54f,95f,51f,83f,59f,70f,73f,61f),
        listOf(60f,119f,74f,108f,93f,103f,105f,113f,109f,127f,122f,132f,131f,145f,119f,158f,96f,176f,73f,178f,65f,167f,68f,150f,54f,141f,42f,137f,35f,128f,45f,123f),
        listOf(89f,60f,119f,60f,131f,69f,141f,82f,143f,92f,135f,102f,135f,112f,147f,133f,117f,140f,79f,137f,75f,130f,85f,111f,85f,100f,77f,87f,79f,79f,87f,73f),
    )
    private val belts = listOf(
        listOf(78f,103f,125f,102f,129f,116f,79f,118f),
        listOf(84f,104f,128f,99f,132f,112f,85f,120f),
        listOf(76f,103f,124f,111f,122f,126f,72f,117f),
        listOf(68f,114f,122f,120f,119f,134f,63f,128f),
        listOf(53f,117f,108f,128f,104f,141f,48f,132f),
        listOf(75f,92f,128f,80f,132f,94f,80f,107f),
        listOf(87f,133f,111f,124f,119f,137f,94f,147f),
        listOf(85f,103f,132f,102f,136f,116f,86f,118f),
    )
    fun coat(pose: String) = coats[cell(pose)]
    fun belt(pose: String) = belts[cell(pose)]
    fun torso(pose: String) = torsos[cell(pose)]
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
    private val boots = listOf(
        listOf(65f,153f,91f,153f,94f,184f,61f,184f, 110f,153f,134f,153f,147f,184f,109f,184f),
        listOf(65f,153f,87f,164f,75f,184f,49f,184f, 121f,151f,142f,151f,151f,184f,120f,184f),
        listOf(48f,151f,68f,162f,58f,183f,33f,183f, 111f,153f,134f,153f,149f,184f,110f,184f),
        listOf(45f,156f,65f,166f,53f,184f,29f,184f, 112f,159f,134f,159f,146f,184f,111f,184f),
        listOf(28f,166f,47f,172f,30f,187f,4f,187f, 126f,165f,146f,165f,165f,187f,126f,187f),
        listOf(50f,154f,72f,166f,59f,184f,31f,184f, 119f,150f,138f,150f,154f,181f,123f,184f),
        listOf(117f,144f,137f,136f,163f,155f,148f,172f, 135f,160f,151f,148f,181f,161f,170f,179f),
        listOf(72f,153f,98f,153f,101f,184f,68f,184f, 117f,153f,141f,153f,154f,184f,116f,184f),
    )
    fun cell(pose: String): Int = MotionAtlas.heroSourceCells[MotionAtlas.heroPoses.indexOf(pose).coerceAtLeast(0)]
    fun bob(pose: String): Float {
        val phase=pose.substringAfterLast('_').toIntOrNull() ?: 0
        return if(!pose.startsWith("idle") && phase in listOf(2,4)) if(phase==2)-1f else 1f else 0f
    }
    fun patches(kind: Kind, pose: String): List<Patch>? {
        val cell = cell(pose)
        return when (kind) {
            Kind.BODY -> listOf(Patch(whole, torsos[cell]))
            Kind.PANTS -> legs[cell].let { p -> listOf(
                Patch(listOf(0f,0f,1f,.32f),p.subList(0,8)),
                Patch(listOf(0f,.28f,.50f,1f),p.subList(8,16)),
                Patch(listOf(.50f,.28f,1f,1f),p.subList(16,24)),
            ) }
            Kind.BOOTS -> boots[cell].let { p -> listOf(
                Patch(listOf(0f,0f,.50f,1f),p.subList(0,8)),
                Patch(listOf(.50f,0f,1f,1f),p.subList(8,16)),
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
        val bob = bob(pose)
        return Offset(x+(p[index]-32f)*unit*.25f,y+(p[index+1]+bob)*unit*.25f)
    }
}
