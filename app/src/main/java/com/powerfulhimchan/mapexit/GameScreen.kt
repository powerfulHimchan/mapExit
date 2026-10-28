package com.powerfulhimchan.mapexit

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val ink = Color(0xFF090F18)
internal val panel = Color(0xFF121E2A)
internal val line = Color(0xFF2B4250)
internal val mint = Color(0xFF91DCC1)
internal val amber = Color(0xFFEAC685)
internal val pale = Color(0xFFE2E9E1)
internal val muted = Color(0xFF91A4AF)
internal val red = Color(0xFFF29491)

@Composable
fun MapExitScreen(vm: GameModel) {
    vm.revision
    val s = vm.engine.state
    var clock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vm) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L && now - previous >= 33_000_000) {
                    if (vm.active) {
                        vm.tick(((now - previous) / 1_000_000).coerceAtMost(100))
                        clock = SystemClock.uptimeMillis()
                    }
                    previous = now
                } else if (previous == 0L) previous = now
            }
        }
    }
    BackHandler(s.mode == Mode.MENU) { vm.back() }
    MaterialTheme(
        colorScheme =
            darkColorScheme(background = ink, surface = panel, onSurface = pale, primary = mint)
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(ink)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MAP / EXIT",
                    color = mint,
                    fontSize = 15.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (s.debug) "TEST"
                    else "${s.cycle}회차 · ${if (s.floor == 0) "지상" else "B${s.floor}"}",
                    color = amber,
                    fontSize = 12.sp,
                )
            }
            if (vm.loadError != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(vm.loadError!!, color = red)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.up(1) }) { Text("원본을 보존하고 새로 시작") }
                }
            } else if (s.mode in listOf(Mode.SURFACE, Mode.MENU, Mode.INHERIT)) {
                PreparationScreen(vm, Modifier.weight(1f))
            } else {
                DungeonScreen(vm, clock, Modifier.weight(1f))
            }
            vm.saveError?.let { Text(it, color = red, fontSize = 11.sp) }
        }
    }
}

@Composable
internal fun PixelCanvas(
    s: State,
    clock: Long,
    modifier: Modifier = Modifier,
    effect: String = "",
    effectAt: Long = 0L,
) {
    BoxWithConstraints(
        modifier.background(ink).border(1.dp, line).semantics { contentDescription = "메인 장면" }
    ) {
        val logicalH =
            (240f * maxHeight.value / maxWidth.value.coerceAtLeast(1f))
                .roundToInt()
                .coerceIn(120, 420)
        val bitmap = remember(logicalH) { ImageBitmap(240, logicalH) }
        val scope = remember { CanvasDrawScope() }
        Canvas(Modifier.fillMaxSize()) {
            scope.draw(
                Density(1f),
                layoutDirection,
                ImageCanvas(bitmap),
                Size(240f, logicalH.toFloat()),
            ) {
                drawScene(s, clock, effect, effectAt)
            }
            drawImage(
                bitmap,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun DungeonScreen(vm: GameModel, clock: Long, modifier: Modifier) {
    val s = vm.engine.state
    val p = vm.engine.stats()
    val buttons = vm.engine.controls.buttons()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelCanvas(
            s,
            clock,
            Modifier.fillMaxWidth().weight(1f),
            vm.visualEffect,
            vm.visualEffectAt,
        )
        Text(
            sceneMessage(s),
            color = if (s.mode == Mode.COMBAT_DEFEND) amber else pale,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                Modifier.width(88.dp)
                    .fillMaxHeight()
                    .background(panel)
                    .border(1.dp, line)
                    .semantics { contentDescription = "주변 미니맵" }
            ) {
                drawMap(s)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "LV.${s.level}   HP ${s.hp.toInt()} / ${p.hp.toInt()}",
                        color = mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${s.gold} G", color = amber, fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().height(5.dp).background(line)) {
                    Box(
                        Modifier.fillMaxWidth((s.hp / p.hp).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(if (s.hp < p.hp * .3) red else mint)
                    )
                }
                Text(
                    "공격 ${p.attack.toInt()} · 방어 ${p.defense.toInt()} · 회복약 ${s.inventory.count { it.kind == Kind.POTION }}",
                    color = muted,
                    fontSize = 10.sp,
                )
                Text(
                    s.quest?.let { "의뢰 ${it.progress}/${it.target} · ${it.title}" }
                        ?: "${directionHint(s)} · 길게 누르기 기능은 버튼 아래 표시",
                    color = muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            buttons.forEachIndexed { i, b -> ActionButton(vm, i, b, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ActionButton(vm: GameModel, index: Int, b: ContextButton, modifier: Modifier) {
    var held by remember { mutableStateOf(false) }
    var longReady by remember { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()
    val trap = vm.engine.state.mode == Mode.TRAP
    val inputKey = vm.engine.controls.inputToken()
    Column(
        modifier
            .fillMaxHeight()
            .background(if (held) Color(0xFF294C49) else panel, RoundedCornerShape(10.dp))
            .border(
                if (held) 2.dp else 1.dp,
                if (b.enabled) mint else line,
                RoundedCornerShape(10.dp),
            )
            .semantics {
                role = Role.Button
                contentDescription = "${if (index == 0) "A" else "B"} ${b.label}"
                if (!b.enabled && b.holdChoice == null) disabled()
                if (b.enabled)
                    onClick {
                        vm.down(index)
                        vm.up(index)
                        true
                    }
                if (b.holdChoice != null)
                    onLongClick(label = b.holdLabel) {
                        vm.down(index)
                        vm.up(index, held = true)
                        true
                    }
            }
            .pointerInput(vm, index, if (trap) vm.engine.state.trap else inputKey, b.enabled) {
                if (b.enabled || b.holdChoice != null)
                    detectTapGestures(
                        onPress = {
                            held = true
                            longReady = false
                            val start = SystemClock.uptimeMillis()
                            vm.down(index)
                            val job =
                                coroutine.launch {
                                    delay(550)
                                    longReady = true
                                }
                            val released =
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    job.cancel()
                                }
                            held = false
                            vm.up(
                                index,
                                released,
                                held =
                                    !trap &&
                                        b.holdChoice != null &&
                                        SystemClock.uptimeMillis() - start >= 550,
                            )
                            longReady = false
                        }
                    )
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (longReady) b.holdLabel ?: b.label else b.label,
            fontSize = 16.sp,
            color = if (b.enabled) pale else muted,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            "${if (index == 0) "A" else "B"}${b.holdLabel?.let { "  ·  길게: $it" } ?: "  ·  바로 실행"}",
            fontSize = 10.sp,
            color = if (b.enabled) mint else muted,
        )
    }
}

@Composable
private fun PreparationScreen(vm: GameModel, modifier: Modifier) {
    val e = vm.engine
    val s = e.state
    val surface = s.mode == Mode.SURFACE
    val menu = s.menu?.type
    val title =
        if (surface) "출정 준비"
        else if (s.mode == Mode.INHERIT) "다음 여정에 남길 장비"
        else
            when (menu) {
                "equipment" -> "장비 장착"
                "inventory" -> "가방"
                "shop" -> "상점"
                "sell" -> "아이템 판매"
                "quantity" -> "소모품 구매 수량"
                "growth" -> "능력치 성장"
                "bank" -> "골드 보관소"
                "help" -> "탐험 기록"
                "item" -> "장비 / 아이템 상세"
                else -> "${if (s.debug) "개발용 테스트" else "준비 메뉴"}"
            }
    Column(modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "준비 메뉴" },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (surface) "BASE CAMP / PREPARATION" else "EQUIPMENT / RECORD",
                            color = muted,
                            fontSize = 9.sp,
                            letterSpacing = 2.sp,
                        )
                        Text(title, color = pale, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    if (!surface) TextButton(onClick = { vm.back() }) { Text("돌아가기", color = mint) }
                }
            }
            if (surface) item { PixelCanvas(s, 0, Modifier.fillMaxWidth().height(142.dp)) }
            item { Summary(e) }
            item {
                Text(
                    s.message,
                    color = amber,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().background(panel).padding(12.dp),
                )
            }
            if (surface) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("장비" to "equipment", "가방" to "inventory", "상점" to "shop").forEach {
                            (label, action) ->
                            OutlinedButton(
                                onClick = {
                                    e.choices().firstOrNull { it.action == action }?.let(vm::choose)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(8.dp),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("성장 ${s.points}P" to "growth", "보관소" to "bank", "기록" to "help")
                            .forEach { (label, action) ->
                                OutlinedButton(
                                    onClick = {
                                        e.choices()
                                            .firstOrNull { it.action == action }
                                            ?.let(vm::choose)
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(8.dp),
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                    }
                }
                item { Text("장착 현황", color = mint, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                item {
                    Column(
                        Modifier.fillMaxWidth().background(panel).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Slot.entries.chunked(2).forEach { slots ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                slots.forEach { slot ->
                                    val gear = s.inventory.find { it.id == s.equipment[slot] }
                                    Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                                        Text(slot.label, color = muted, fontSize = 10.sp)
                                        Text(
                                            gear?.name ?: "비어 있음",
                                            color = if (gear == null) muted else pale,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (slots.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                items(e.choices().filter { it.action == "enter" && it.argument != 1L }) { c ->
                    Button(
                        onClick = { vm.choose(c) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(
                            if (c.argument == 1L) "던전 진입 · 지하 1층" else c.label,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (BuildConfig.DEBUG)
                    item {
                        TextButton(
                            onClick = {
                                e.choices().firstOrNull { it.action == "debug" }?.let(vm::choose)
                            }
                        ) {
                            Text("개발용 테스트 · 별도 저장", color = muted, fontSize = 11.sp)
                        }
                    }
            } else {
                if (menu == "item") item { ItemComparison(e) }
                if (menu == "help")
                    item {
                        Text(
                            s.log.takeLast(12).reversed().joinToString("\n\n"),
                            color = muted,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                        )
                    }
                if (menu == "equipment")
                    item {
                        Text(
                            "슬롯을 눌러 장착 중인 장비를 확인하세요. 빈 슬롯은 가방에서 장착할 수 있습니다.",
                            color = muted,
                            fontSize = 12.sp,
                        )
                    }
                items(e.choices(), key = { "${it.action}:${it.argument}:${it.label}" }) { c ->
                    val gear =
                        when (menu) {
                            "shop" -> s.shop.find { it.id == c.argument }
                            "inventory",
                            "sell" -> s.inventory.find { it.id == c.argument }
                            else -> null
                        }
                    val enabled =
                        when (c.action) {
                            "buy" -> gear != null && s.gold >= Equipment.buyPrice(s, gear)
                            "buyQuantity" ->
                                s.shop
                                    .firstOrNull { it.id == s.menu?.argument }
                                    ?.let {
                                        s.gold >= Equipment.buyPrice(s, it) * c.argument.toInt()
                                    } ?: false
                            "stat" -> s.points > 0
                            "slotItem" -> s.equipment[Slot.entries[c.argument.toInt()]] != null
                            else -> true
                        }
                    Column(
                        Modifier.fillMaxWidth()
                            .background(panel, RoundedCornerShape(8.dp))
                            .border(1.dp, if (enabled) line else ink, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled, role = Role.Button) { vm.choose(c) }
                            .padding(14.dp)
                    ) {
                        Text(
                            c.label,
                            color = if (enabled) pale else muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        gear?.let {
                            Text(
                                it.summary(),
                                color = muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        if (c.action == "buy" && !enabled)
                            Text("골드가 부족합니다", color = red, fontSize = 10.sp)
                    }
                }
            }
        }
        if (surface) {
            Button(
                onClick = {
                    e.choices()
                        .firstOrNull { it.action == "enter" && it.argument == 1L }
                        ?.let(vm::choose)
                },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .heightIn(min = 54.dp)
                        .semantics { contentDescription = "던전 진입" },
            ) {
                Text("던전 진입 · 지하 1층", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Summary(e: GameEngine) {
    val s = e.state
    val p = e.stats()
    Column(
        Modifier.fillMaxWidth().background(panel).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "LV.${s.level}    HP ${s.hp.toInt()} / ${p.hp.toInt()}    ${s.gold} G",
            color = mint,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "공격 ${p.attack.toInt()}  ·  방어 ${p.defense.toInt()}  ·  민첩 ${p.agility.toInt()}",
            color = pale,
            fontSize = 12.sp,
        )
        Text(
            "치명 ${(p.crit * 100).toInt()}%  ·  치명 피해 ${(p.critDamage * 100).toInt()}%  ·  행운 ${p.luck.toInt()}",
            color = muted,
            fontSize = 11.sp,
        )
        Text(
            "회복약 ${s.inventory.count { it.kind == Kind.POTION }}개  ·  보관 ${s.bank}G  ·  성장 ${s.points}P",
            color = amber,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ItemComparison(e: GameEngine) {
    val s = e.state
    val gear = s.inventory.find { it.id == s.menu?.argument } ?: return
    Column(
        Modifier.fillMaxWidth().background(panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(gear.name, color = amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(gear.summary(), color = pale, fontSize = 12.sp)
        Equipment.allowedSlots(gear).forEach { slot ->
            val preview = s.copy(equipment = s.equipment.toMutableMap())
            Equipment.equip(preview, gear.id, slot)
            val current = e.stats()
            val next = Equipment.stats(preview)
            val changes =
                Stat.entries.mapNotNull { stat ->
                    val delta = next.value(stat) - current.value(stat)
                    if (kotlin.math.abs(delta) < .0001) null
                    else {
                        val percent = stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE)
                        "${stat.label} ${if (delta > 0) "+" else ""}${if (percent) (delta * 100).roundToInt() else delta.roundToInt()}${if (percent) "%p" else ""}"
                    }
                }
            val previous = s.inventory.find { it.id == s.equipment[slot] }?.name ?: "비어 있음"
            Text(
                "${slot.label} · $previous → ${gear.name}\n${changes.joinToString(" / ").ifEmpty { "능력치 변화 없음" }}",
                color = mint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

private fun directionHint(s: State): String =
    s.dungeon?.let { "${listOf("북", "동", "남", "서")[it.direction]}쪽을 보는 중" } ?: "탐험"

private fun sceneMessage(s: State): String =
    when {
        s.mode == Mode.TRAP -> s.trap?.prompt() ?: "함정"
        s.enemy != null ->
            "${s.enemy!!.name}  ${s.enemy!!.hp.toInt()}/${s.enemy!!.maxHp.toInt()} HP · ${if (s.mode == Mode.COMBAT_ATTACK) "공격 차례" else "방어 차례"}\n${s.message}"
        else -> s.message
    }
