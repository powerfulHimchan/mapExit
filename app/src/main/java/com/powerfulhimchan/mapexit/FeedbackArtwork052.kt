package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.roundToInt

/** Authored transparent glyphs; no platform font or panel is used for floating feedback. */
internal object FeedbackArtwork052 {
    private lateinit var digits: ImageBitmap
    private lateinit var words: ImageBitmap
    private var loaded = false
    @Synchronized fun load(resources: Resources) {
        if (loaded) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id,
            BitmapFactory.Options().apply { inScaled = false })).asImageBitmap()
        digits = decode(R.drawable.battle_digits_052)
        words = decode(R.drawable.battle_words_052)
        loaded = true
    }
    fun labels(impact: BattleImpact): List<String> = buildList {
        when (impact.outcome) {
            ImpactOutcome.BLOCK -> add("완전 방어")
            ImpactOutcome.DODGE -> add("회피")
            ImpactOutcome.MISS -> add("빗나감")
            ImpactOutcome.GUARD -> add("방어")
            else -> Unit
        }
        if (impact.critical) add("치명타!")
        for (word in listOf("방패 파괴", "빈틈 공격", "연속 공격", "빈틈 발생", "반격"))
            if (word in impact.tactic) add(word)
        if ("경감" in impact.tactic) add("공격 경감")
        if (impact.luckyAttack || impact.luckyCritical) add("행운")
    }.distinct()
    private fun digitBounds(impact: BattleImpact): List<IntArray> {
        val row = if (impact.target == ImpactTarget.PLAYER && impact.outcome != ImpactOutcome.BLOCK) 2
            else if (impact.critical) 1 else 0
        val text = impact.amount.coerceAtLeast(0).toString() + if (impact.critical) "!" else ""
        return text.map { FeedbackAtlas052.digits[row * 11 + if (it == '!') 10 else it - '0'] }
    }
    private fun DrawScope.glyph(image: ImageBitmap, b: IntArray, x: Float, y: Float, w: Float, h: Float) {
        drawImage(image, srcOffset = IntOffset(b[0], b[1]), srcSize = IntSize(b[2], b[3]),
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None)
    }
    fun DrawScope.numbers(impact: BattleImpact) {
        val glyphs = digitBounds(impact)
        val nativeWidth = glyphs.sumOf { it[2] } + (glyphs.size - 1) * 2
        val nativeHeight = glyphs.maxOf { it[3] }
        val scale = minOf(size.width / nativeWidth, size.height / nativeHeight)
        var x = (size.width - nativeWidth * scale) / 2
        for (b in glyphs) {
            glyph(digits, b, x, (size.height - b[3] * scale) / 2, b[2] * scale, b[3] * scale)
            x += (b[2] + 2) * scale
        }
    }
    @Composable fun Content(impact: BattleImpact, compact: Boolean) {
        for (word in labels(impact)) {
            val b = FeedbackAtlas052.labels[FeedbackAtlas052.words.indexOf(word)]
            val h = if (word == "치명타!") { if (compact) 17.dp else 21.dp }
                else if (compact) 12.dp else 15.dp
            Canvas(Modifier.width(h * b[2].toFloat() / b[3]).height(h)) {
                val scale = minOf(size.width / b[2], size.height / b[3])
                glyph(words, b, (size.width - b[2] * scale) / 2, 0f, b[2] * scale, b[3] * scale)
            }
        }
        if (impact.outcome !in listOf(ImpactOutcome.DODGE, ImpactOutcome.MISS)) {
            val bounds = digitBounds(impact)
            val h = if (compact) { if (impact.critical) 30.dp else 25.dp }
                else if (impact.critical) 43.dp else 34.dp
            val aspect = (bounds.sumOf { it[2] } + (bounds.size - 1) * 2).toFloat() / bounds.maxOf { it[3] }
            Canvas(Modifier.width(h * aspect).height(h)) { numbers(impact) }
        }
    }
}

/** Production encounter artwork is also used for the approaching world billboards. */
internal object ExplorationArtwork052 {
    val all = mutableMapOf<String, Bitmap>()
    @Synchronized fun load(resources: Resources) {
        if (all.isNotEmpty()) return
        val sheet = requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.exploration_props_052,
            BitmapFactory.Options().apply { inScaled = false }))
        for ((i, b) in FeedbackAtlas052.props.withIndex()) {
            val name = if (i < 5) "trap_$i" else if (i == 5) "heal" else "heal_used"
            all[name] = Bitmap.createBitmap(sheet, b[0], b[1], b[2], b[3])
        }
        sheet.recycle()
        for (name in MotionAtlas.monsters) MotionArtwork.explorationMonster(name)?.let { all[name] = it }
    }
}
