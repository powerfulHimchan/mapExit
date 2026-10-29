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
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var confirmRetreat by remember { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(0L) }
    val now = clock.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
    val s = vm.presentationState(now)
    val canRetreat = s.dungeon != null && s.mode in listOf(Mode.EXPLORE, Mode.MOVING)
    LaunchedEffect(vm) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L && now - previous >= 33_000_000) {
                    if (vm.active) {
                        vm.tick(((now - previous) / 1_000_000).coerceAtMost(100))
                    }
                    val uptime = SystemClock.uptimeMillis()
                    if (
                        vm.active ||
                            vm.battleVisual?.let {
                                uptime - it.startedAt <= BattleAnimation.DURATION_MS + 100
                            } == true
                    )
                        clock = uptime
                    previous = now
                } else if (previous == 0L) previous = now
            }
        }
    }
    LaunchedEffect(vm.currentImpact?.serial) {
        val event = vm.currentImpact ?: return@LaunchedEffect
        delay(2600)
        vm.clearImpact(event.serial)
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
                Modifier.fillMaxWidth().height(if (canRetreat) 48.dp else 34.dp),
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
                if (canRetreat)
                    TextButton(
                        onClick = {
                            if (s.mode == Mode.MOVING) vm.choose(Choice("멈추기", "stop"))
                            confirmRetreat = true
                        }
                    ) {
                        Text("지상으로", color = red, fontSize = 12.sp)
                    }
            }
            if (vm.loadError != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(vm.loadError!!, color = red)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.up(1) }) { Text("원본을 보존하고 새로 시작") }
                }
            } else if (s.mode == Mode.RUN_RESULT) {
                RunResultScreen(vm, Modifier.weight(1f))
            } else if (s.mode in listOf(Mode.SURFACE, Mode.MENU, Mode.INHERIT)) {
                if (s.mode == Mode.SURFACE && vm.currentImpact?.target == ImpactTarget.PLAYER) {
                    Text("받은 피해 ${vm.currentImpact!!.label()}", color = red, fontSize = 16.sp)
                }
                PreparationScreen(vm, Modifier.weight(1f))
            } else {
                DungeonScreen(vm, clock, Modifier.weight(1f))
            }
            vm.saveError?.let { Text(it, color = red, fontSize = 11.sp) }
        }
        if (confirmRetreat && canRetreat)
            AlertDialog(
                onDismissRequest = { confirmRetreat = false },
                title = { Text("탐험을 포기하고 지상으로 갈까요?") },
                text = { Text("사망과 동일하게 장비·아이템·소지 골드를 모두 잃습니다. 성장, 보관 골드와 이동 장치는 유지됩니다.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRetreat = false
                            vm.choose(Choice("탐험 포기", "retreat"))
                        }
                    ) {
                        Text("포기하고 귀환", color = red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRetreat = false }) { Text("계속 탐험") }
                },
            )
    }
}

@Composable
internal fun PixelCanvas(
    s: State,
    clock: Long,
    modifier: Modifier = Modifier,
    effect: String = "",
    effectAt: Long = 0L,
    camera: DungeonViewPose? = null,
    battle: BattleVisual? = null,
) {
    BoxWithConstraints(
        modifier.background(ink).border(1.dp, line).semantics { contentDescription = "메인 장면" }
    ) {
        val logicalH =
            (480f * maxHeight.value / maxWidth.value.coerceAtLeast(1f))
                .roundToInt()
                .coerceIn(160, 960)
        val bitmap = remember(logicalH) { ImageBitmap(480, logicalH) }
        val scope = remember { CanvasDrawScope() }
        Canvas(Modifier.fillMaxSize()) {
            scope.draw(
                Density(1f),
                layoutDirection,
                ImageCanvas(bitmap),
                Size(480f, logicalH.toFloat()),
            ) {
                drawScene(s, clock, effect, effectAt, camera, battle)
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
    vm.revision
    val s = vm.presentationState(clock.takeIf { it > 0 } ?: SystemClock.uptimeMillis())
    val p = Equipment.stats(s)
    val motion =
        vm.battleVisual?.takeIf {
            it.active(clock.takeIf { time -> time > 0 } ?: SystemClock.uptimeMillis())
        }
    val buttons =
        vm.engine.controls.buttons().map {
            if (vm.battleBusy()) it.copy(enabled = false, holdChoice = null, holdLabel = null)
            else it
        }
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 560.dp
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {
            ScenePresentation(vm, clock, Modifier.fillMaxWidth().weight(1f))
            Text(
                if (motion != null)
                    "${if (motion.impact.target == ImpactTarget.ENEMY) "공격 중" else "적 반격"} · ${s.message}"
                else sceneMessage(s),
                color = if (s.mode == Mode.COMBAT_DEFEND) amber else pale,
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = if (compact) 30.dp else 34.dp)
                        .background(panel, RoundedCornerShape(6.dp))
                        .border(1.dp, amber.copy(alpha = .6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp)
                        .semantics { contentDescription = "현재 상황" },
            )
            Row(
                Modifier.fillMaxWidth().height(if (compact) 54.dp else 68.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Canvas(
                    Modifier.width(if (compact) 64.dp else 80.dp)
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
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${s.gold} G", color = amber, fontSize = 12.sp, lineHeight = 16.sp)
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
                        lineHeight = 14.sp,
                    )
                    if (!compact)
                        Text(
                            s.quest?.let { "의뢰 ${it.progress}/${it.target} · ${it.title}" }
                                ?: "${directionHint(s)} · 길게 누르기 기능은 버튼 아래 표시",
                            color = muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(if (compact) 62.dp else 76.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                buttons.forEachIndexed { i, b -> ActionButton(vm, i, b, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(2.dp))
        }
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
                            var released = false
                            try {
                                released = tryAwaitRelease()
                            } finally {
                                job.cancel()
                                held = false
                                longReady = false
                                vm.up(
                                    index,
                                    released,
                                    held =
                                        !trap &&
                                            b.holdChoice != null &&
                                            SystemClock.uptimeMillis() - start >= 550,
                                )
                            }
                        }
                    )
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (longReady) b.holdLabel ?: b.label else b.label,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = if (b.enabled) pale else muted,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        b.holdLabel?.let { hint ->
            Text("길게: $hint", fontSize = 10.sp, lineHeight = 14.sp, color = mint)
        }
    }
}

@Composable
private fun PreparationScreen(vm: GameModel, modifier: Modifier) {
    vm.revision
    val e = vm.engine
    val s = e.state
    val surface = s.mode == Mode.SURFACE
    val menu = s.menu?.type
    val menuChoices = e.choices()
    val ownedItems = s.inventory.toList()
    val shopItems = s.shop.toList()
    val wornSlots = s.equipment.toMap()
    val listState = rememberLazyListState()
    LaunchedEffect(s.mode, s.menu?.type, s.menu?.argument) { listState.scrollToItem(0) }
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
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!surface) MenuArt(menu ?: "help", Modifier.padding(end = 10.dp).size(34.dp))
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
            if (surface || s.mode == Mode.INHERIT)
                item { PixelCanvas(s, 0, Modifier.fillMaxWidth().height(142.dp)) }
            if (surface) item { PreparationMenuGrid(vm) }
            if (menu == "equipment")
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("장비를 누르면 상세 정보, 빈 부위를 누르면 가방이 열립니다.", color = muted, fontSize = 12.sp)
                        EquipmentOverview(vm)
                    }
                }
            if (menu !in listOf("growth", "shop", "sell", "quantity", "help")) item { Summary(e) }
            if (surface || s.mode == Mode.INHERIT)
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
                item { EquipmentOverview(vm) }
                items(e.choices().filter { it.action == "enter" && it.argument != 1L }) { c ->
                    ExpeditionButton(c.label, "이동 장치로 출발", "enter") { vm.choose(c) }
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
                if (menu == "growth") growthCards(vm)
                if (menu in listOf("shop", "sell", "quantity")) tradeCards(vm, menu!!)
                if (menu == "item") item { ItemComparison(e) }
                if (menu == "help") item { ExplorationRecord(s) }
                items(
                    menuChoices.filterNot {
                        (menu == "help") ||
                            (menu == "equipment" && it.action == "slotItem") ||
                            (menu == "growth" && it.action == "stat") ||
                            (menu in listOf("growth", "shop", "sell", "quantity") &&
                                it.action == "back") ||
                            (menu in listOf("shop", "sell", "quantity") &&
                                it.action in listOf("buy", "sell", "buyQuantity", "sellMenu"))
                    },
                    key = { "${it.action}:${it.argument}" },
                ) { c ->
                    val gear =
                        when (menu) {
                            "shop" -> shopItems.find { it.id == c.argument }
                            "inventory",
                            "sell" -> ownedItems.find { it.id == c.argument }
                            "equipment" ->
                                ownedItems.find {
                                    it.id == wornSlots[Slot.entries[c.argument.toInt()]]
                                }
                            "quantity" -> shopItems.find { it.id == s.menu?.argument }
                            else ->
                                if (c.action == "inherit") ownedItems.find { it.id == c.argument }
                                else null
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
                    Row(
                        Modifier.fillMaxWidth()
                            .background(panel, RoundedCornerShape(8.dp))
                            .border(1.dp, if (enabled) line else ink, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled, role = Role.Button) { vm.choose(c) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gear?.let { ItemArt(it.kind, Modifier.size(48.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.label,
                                color = if (enabled) pale else muted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (menu == "sell" && gear != null && gear.id in wornSlots.values) {
                                Text(
                                    "장착 중 · 판매하면 장착이 해제됩니다",
                                    color = mint,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
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
        }
        if (surface) {
            ExpeditionButton(
                "던전 진입",
                "지하 1층부터 탐험",
                "enter",
                Modifier.padding(top = 8.dp, bottom = 4.dp),
            ) {
                e.choices()
                    .firstOrNull { it.action == "enter" && it.argument == 1L }
                    ?.let(vm::choose)
            }
        }
    }
}

@Composable
private fun Summary(e: GameEngine) {
    StatusSummary(e)
}

@Composable
private fun ItemComparison(e: GameEngine) {
    val s = e.state
    val gear = s.inventory.find { it.id == s.menu?.argument } ?: return
    Column(
        Modifier.fillMaxWidth().background(panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ItemArt(gear.kind, Modifier.size(60.dp))
            Text(gear.name, color = amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
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
        s.mode == Mode.TRAP_GUIDE -> s.trap?.cue() ?: "곧 시작"
        s.mode == Mode.VICTORY -> "전투 종료 · 보상을 확인하고 계속하세요"
        s.mode == Mode.TRAP -> s.trap?.prompt() ?: "함정"
        s.enemy != null ->
            "${if (s.mode == Mode.COMBAT_ATTACK) "공격 차례" else "방어 차례"} · ${s.message}"
        else -> s.message
    }
