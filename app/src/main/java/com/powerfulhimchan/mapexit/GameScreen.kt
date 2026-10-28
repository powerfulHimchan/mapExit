package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

private val ink = Color(0xFF0A1018)
private val panel = Color(0xFF101D29)
private val line = Color(0xFF294355)
private val mint = Color(0xFF83D9BE)
private val amber = Color(0xFFEBCB7A)
private val pale = Color(0xFFDDE8E3)
private val muted = Color(0xFF8BA4AF)
private val red = Color(0xFFEC8991)

@Composable
fun MapExitScreen(vm: GameModel) {
    vm.revision
    val s = vm.engine.state
    LaunchedEffect(vm) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val ms = (now - previous) / 1_000_000
                    if (ms >= 32) {
                        vm.tick(ms.coerceAtMost(100))
                        previous = now
                    }
                } else previous = now
            }
        }
    }
    MaterialTheme(
        colorScheme =
            darkColorScheme(background = ink, surface = panel, onSurface = pale, primary = mint)
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(ink)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MAP EXIT",
                    color = mint,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (s.debug) "TEST MODE"
                    else "${s.cycle}회차 · ${if(s.floor==0)"지상" else "B${s.floor}"}",
                    color = if (s.debug) red else amber,
                    fontSize = 12.sp,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Panel("01 / ${sceneTitle(s)}", Modifier.weight(1f)) {
                    Canvas(Modifier.fillMaxWidth().weight(1f).background(ink)) { drawScene(s) }
                    val detail = selectedDetail(vm.engine)
                    Text(
                        vm.loadError
                            ?: vm.saveError
                            ?: detail.ifEmpty { s.trap?.prompt() ?: s.message },
                        color = if (vm.loadError != null || vm.saveError != null) red else pale,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(6.dp),
                    )
                }
                Panel("02 / MINI MAP", Modifier.weight(1f)) {
                    Canvas(Modifier.fillMaxWidth().weight(1f).background(ink)) { drawMap(s) }
                    Text(
                        "▲ 나  ● 적  □ 상자\n+ 회복  $ 상인  ? 의뢰\n▼ 출구  × 함정",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = muted,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Panel("03 / CONTROL", Modifier.weight(1f)) { Controls(vm, Modifier.weight(1f)) }
                Panel("04 / STATUS", Modifier.weight(1f)) { Status(s, vm.engine.stats()) }
            }
        }
    }
}

@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxHeight().background(panel).border(1.dp, line)) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = mint,
            modifier =
                Modifier.fillMaxWidth()
                    .background(Color(0xFF172A37))
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            maxLines = 1,
        )
        content()
    }
}

@Composable
private fun Controls(vm: GameModel, modifier: Modifier) {
    val s = vm.engine.state
    val options = vm.engine.choices()
    val selected = s.selection.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    Column(
        modifier.fillMaxWidth().padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            if (vm.loadError != null) "저장 복구" else modeLabel(s),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = amber,
            maxLines = 2,
        )
        if (s.mode == Mode.TRAP && s.trap?.phase != TrapPhase.RESULT) {
            Text(
                s.trap?.instruction() ?: "",
                fontSize = 12.sp,
                color = pale,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val start = (selected - 1).coerceAtLeast(0)
                options.drop(start).take(2).forEachIndexed { i, choice ->
                    val active = start + i == selected
                    Text(
                        (if (active) "▶ " else "  ") + choice.label,
                        color = if (active) mint else muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier.fillMaxWidth()
                                .background(if (active) Color(0xFF1D3A40) else Color.Transparent)
                                .padding(4.dp),
                    )
                }
            }
            Text(
                if (options.isEmpty()) "" else "${selected+1} / ${options.size}",
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = muted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(75.dp),
        ) {
            InputButton("A", if (s.mode == Mode.TRAP) "입력" else "선택", 0, vm, Modifier.weight(1f))
            InputButton("B", if (s.mode == Mode.TRAP) "입력" else "실행", 1, vm, Modifier.weight(1f))
        }
        Text(
            "A 선택  /  B 실행",
            color = muted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun InputButton(
    label: String,
    subtitle: String,
    button: Int,
    vm: GameModel,
    modifier: Modifier,
) {
    var held by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxHeight()
            .background(if (held) Color(0xFF3F6E68) else Color(0xFF233A48))
            .border(2.dp, if (held) amber else mint)
            .semantics {
                role = Role.Button
                contentDescription = "$label $subtitle"
                onClick {
                    vm.down(button)
                    vm.up(button)
                    true
                }
            }
            .pointerInput(vm, button) {
                detectTapGestures(
                    onPress = {
                        held = true
                        vm.down(button)
                        val released = tryAwaitRelease()
                        held = false
                        vm.up(button, released)
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = if (held) amber else mint,
        )
        Text(subtitle, fontSize = 10.sp, color = pale)
    }
}

@Composable
private fun Status(s: State, p: Stats) {
    Column(
        Modifier.fillMaxWidth().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "LV.${s.level}  /  ${s.points} POINT",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = amber,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "HP ${s.hp.toInt()} / ${p.hp.toInt()}",
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = mint,
        )
        Box(Modifier.fillMaxWidth().height(6.dp).background(ink)) {
            Box(
                Modifier.fillMaxWidth((s.hp / p.hp).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (s.hp < p.hp * .3) red else mint)
            )
        }
        val rows =
            listOf(
                "공격력" to p.attack.toInt().toString(),
                "방어력" to p.defense.toInt().toString(),
                "민첩" to p.agility.toInt().toString(),
                "치명타 확률" to "${(p.crit*100).toInt()}%",
                "치명타 공격력" to "${(p.critDamage*100).toInt()}%",
                "행운" to p.luck.toInt().toString(),
            )
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = pale,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("소지 ${s.gold}G\n보관 ${s.bank}G", fontSize = 11.sp, lineHeight = 14.sp, color = amber)
        Text(
            "EXP ${s.experience} / ${s.level*80}\n가방 ${s.inventory.size} · 계승 ${s.inventory.count { it.inherited }}\n최고 B${s.highest} · 클리어 ${s.clears}",
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = muted,
        )
        Text(
            s.quest?.let { "의뢰 ${it.progress}/${it.target}\n${it.title}" } ?: "진행 중인 의뢰 없음",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = if (s.quest?.complete == true) mint else muted,
            maxLines = 2,
        )
    }
}

private fun sceneTitle(s: State): String =
    when {
        s.mode == Mode.TRAP -> "TRAP"
        s.enemy != null -> "BATTLE"
        s.mode == Mode.INHERIT -> "LEGACY"
        s.floor == 0 -> "SURFACE"
        else -> "DUNGEON"
    }

private fun modeLabel(s: State): String =
    when (s.mode) {
        Mode.COMBAT_ATTACK -> "공격 차례"
        Mode.COMBAT_DEFEND -> "몬스터 공격 / 방어 선택"
        Mode.MOVING -> "통로 자동 이동"
        Mode.TRAP -> s.trap?.title ?: "함정"
        Mode.FLOOR_CLEAR -> "층 클리어"
        Mode.INHERIT -> "계승할 새 장비 선택"
        Mode.MENU ->
            when (s.menu?.type) {
                "growth" -> "능력치 배분"
                "shop" -> "상점 · 구매"
                "sell" -> "상점 · 판매"
                "inventory" -> "가방"
                "equipment" -> "장착 현황"
                "bank" -> "지상 보관소"
                "debug" -> "개발용 / 별도 저장"
                else -> "선택 메뉴"
            }
        else -> "탐험 준비"
    }

private fun selectedDetail(e: GameEngine): String {
    val s = e.state
    if (s.mode == Mode.TRAP) return s.trap?.prompt() ?: ""
    s.enemy?.let {
        return "${it.name} · HP ${it.hp.toInt()}/${it.maxHp.toInt()}\n${s.message}"
    }
    if (s.mode == Mode.MENU) {
        val selected = e.choices().getOrNull(s.selection)
        val item =
            if (s.menu?.type == "item") s.inventory.find { it.id == s.menu?.argument }
            else if (s.menu?.type in listOf("inventory", "sell", "shop"))
                (s.inventory + s.shop).find { it.id == selected?.argument }
            else null
        if (item != null) return item.summary()
        if (s.menu?.type == "help") return s.log.takeLast(3).joinToString("\n")
    }
    if (s.mode == Mode.INHERIT) {
        val item = s.inventory.find { it.id == e.choices().getOrNull(s.selection)?.argument }
        return "기존 ${s.inventory.count { it.inherited }}개 자동 계승\n${item?.summary() ?: ""}"
    }
    return ""
}

private fun DrawScope.pixelRect(color: Color, x: Float, y: Float, w: Float, h: Float) {
    drawRect(color, Offset(x, y), Size(w, h))
}

private fun DrawScope.poly(color: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
    val pts = listOf(a, b, c, d)
    val p = Path()
    p.moveTo(a.x, a.y)
    pts.drop(1).forEach { p.lineTo(it.x, it.y) }
    p.close()
    drawPath(p, color)
    drawPath(p, line, style = Stroke(1f))
}

private fun DrawScope.drawScene(s: State) {
    drawRect(ink)
    if (s.mode == Mode.TRAP) {
        drawTrap(s.trap ?: return)
        return
    }
    if (s.enemy != null) {
        drawBattle(s)
        return
    }
    if (s.floor == 0 || s.mode == Mode.INHERIT) {
        val w = size.width
        val h = size.height
        pixelRect(Color(0xFF142838), 0f, h * .58f, w, h * .42f)
        for (i in 0..9) {
            val x = i * w / 9
            pixelRect(Color(0xFF213746), x, h * .15f + (i % 3) * h * .04f, w * .04f, h * .46f)
        }
        pixelRect(line, w * .18f, h * .31f, w * .64f, h * .33f)
        pixelRect(ink, w * .28f, h * .39f, w * .44f, h * .25f)
        pixelRect(amber, w * .21f, h * .25f, w * .58f, h * .035f)
        sprite("hero", w * .40f, h * .61f, w * .23f, mint)
        if (s.mode == Mode.INHERIT) {
            for (i in 0..2) pixelRect(amber, w * (.25f + i * .22f), h * .20f, w * .09f, h * .06f)
        }
        return
    }
    val m = s.dungeon ?: return
    val w = size.width
    val h = size.height
    pixelRect(Color(0xFF192530), 0f, h * .5f, w, h * .5f)
    for (depth in 4 downTo 0) {
        val near = (.5f - (.48f / (depth + 1)))
        val far = (.5f - (.48f / (depth + 2)))
        val nx = w * near
        val ny = h * near
        val fx = w * far
        val fy = h * far
        val xx = m.x + MapGenerator.dx[m.direction] * depth
        val yy = m.y + MapGenerator.dy[m.direction] * depth
        val left = (m.direction + 3) % 4
        val right = (m.direction + 1) % 4
        if (m.cell(xx + MapGenerator.dx[left], yy + MapGenerator.dy[left]).wall)
            poly(
                Color(0xFF29404F),
                Offset(nx, ny),
                Offset(fx, fy),
                Offset(fx, h - fy),
                Offset(nx, h - ny),
            )
        if (m.cell(xx + MapGenerator.dx[right], yy + MapGenerator.dy[right]).wall)
            poly(
                Color(0xFF223642),
                Offset(w - nx, ny),
                Offset(w - fx, fy),
                Offset(w - fx, h - fy),
                Offset(w - nx, h - ny),
            )
        drawLine(line, Offset(fx, h - fy), Offset(w - fx, h - fy), 1f)
        val front = m.cell(xx + MapGenerator.dx[m.direction], yy + MapGenerator.dy[m.direction])
        if (front.wall) {
            pixelRect(Color(0xFF344D5C), fx, fy, w - 2 * fx, h - 2 * fy)
            drawRect(line, Offset(fx, fy), Size(w - 2 * fx, h - 2 * fy), style = Stroke(2f))
            for (k in 1..3) drawLine(
                line,
                Offset(fx, fy + (h - 2 * fy) * k / 4),
                Offset(w - fx, fy + (h - 2 * fy) * k / 4),
                1f,
            )
        }
    }
    val cell = m.here()
    if (!cell.resolved) {
        val type =
            when (cell.event) {
                Event.CHEST -> "chest"
                Event.MERCHANT,
                Event.NPC -> "npc"
                Event.HEAL -> "heal"
                Event.EXIT -> "door"
                else -> null
            }
        if (type != null)
            sprite(
                type,
                w * .31f,
                h * .47f,
                w * .38f,
                if (cell.event == Event.HEAL) mint else amber,
            )
    }
    pixelRect(amber, w * .48f, h * .91f, w * .04f, h * .02f)
}

private fun DrawScope.drawBattle(s: State) {
    val e = s.enemy ?: return
    val w = size.width
    val h = size.height
    pixelRect(Color(0xFF1B2935), 0f, h * .65f, w, h * .35f)
    for (i in 0..5) drawLine(
        line,
        Offset(0f, h * (.65f + i * .06f)),
        Offset(w, h * (.65f + i * .06f)),
        1f,
    )
    pixelRect(line, w * .1f, h * .08f, w * .8f, h * .045f)
    pixelRect(
        if (e.boss) amber else red,
        w * .1f,
        h * .08f,
        w * .8f * (e.hp / e.maxHp).toFloat(),
        h * .045f,
    )
    sprite("hero", w * .09f, h * .48f, w * .34f, mint)
    sprite(
        if (e.boss) "boss" else "slime",
        w * .53f,
        h * .46f,
        w * .40f,
        if (e.boss) red else Color(0xFF9ACB94),
    )
    pixelRect(
        if (s.mode == Mode.COMBAT_ATTACK) mint else red,
        w * .44f,
        h * .35f,
        w * .10f,
        h * .04f,
    )
}

private fun DrawScope.drawMap(s: State) {
    val m = s.dungeon
    if (m == null) {
        val w = size.width
        val h = size.height
        for (y in 0..8) for (x in 0..8) if ((x + y) % 3 == 0)
            pixelRect(line, w * .08f + x * w * .10f, h * .12f + y * h * .08f, w * .025f, w * .025f)
        return
    }
    val unit = min(size.width / (m.width + 1), size.height / (m.height + 1))
    val ox = (size.width - m.width * unit) / 2
    val oy = (size.height - m.height * unit) / 2
    for (y in 0 until m.height) for (x in 0 until m.width) {
        val c = m.cell(x, y)
        val charm = Equipment.charm(s) == Charm.MAP && MapGenerator.charmVisible(m, x, y)
        if (!c.seen && !charm) continue
        val px = ox + x * unit
        val py = oy + y * unit
        pixelRect(if (c.wall) line else Color(0xFF1A303A), px, py, unit - 1, unit - 1)
        if (!c.wall && !c.resolved) {
            val color =
                when (c.event) {
                    Event.MONSTER -> red
                    Event.CHEST -> amber
                    Event.HEAL -> mint
                    Event.MERCHANT -> Color(0xFFBA9DDA)
                    Event.NPC -> Color(0xFF9AC3E4)
                    Event.EXIT -> Color.White
                    Event.TRAP -> Color(0xFFE6A36A)
                    else -> null
                }
            if (color != null)
                pixelRect(color, px + unit * .25f, py + unit * .25f, unit * .5f, unit * .5f)
        }
    }
    val x = ox + (m.x + .5f) * unit
    val y = oy + (m.y + .5f) * unit
    drawCircle(mint, unit * .42f, Offset(x, y))
    drawLine(
        Color.White,
        Offset(x, y),
        Offset(
            x + MapGenerator.dx[m.direction] * unit * .8f,
            y + MapGenerator.dy[m.direction] * unit * .8f,
        ),
        2f,
    )
}

private fun DrawScope.drawTrap(t: TrapState) {
    val w = size.width
    val h = size.height
    if (t.phase == TrapPhase.RESULT) {
        val color = if (t.success) mint else red
        pixelRect(color, w * .25f, h * .30f, w * .5f, h * .35f)
        pixelRect(ink, w * .35f, h * .40f, w * .3f, h * .15f)
        return
    }
    if (t.phase == TrapPhase.PREPARE) {
        for (i in 0..2) pixelRect(
            if (t.elapsedMs > i * 400) amber else line,
            w * (.22f + i * .23f),
            h * .4f,
            w * .10f,
            h * .13f,
        )
        return
    }
    when (t.type) {
        0,
        3 -> {
            val reveal = t.type == 0 || t.phase == TrapPhase.REVEAL
            val n = t.pattern.size
            val box = w / (n + 2)
            t.pattern.forEachIndexed { i, b ->
                val x = box * (i + 1)
                val color = if (i < t.progress) mint else if (reveal && b == 1) amber else line
                pixelRect(color, x, h * .38f, box * .8f, box * 1.1f)
                if (reveal) {
                    if (b == 0) {
                        pixelRect(ink, x + box * .2f, h * .38f + box * .25f, box * .4f, box * .18f)
                    } else {
                        pixelRect(ink, x + box * .2f, h * .38f + box * .2f, box * .4f, box * .14f)
                        pixelRect(ink, x + box * .2f, h * .38f + box * .55f, box * .4f, box * .14f)
                    }
                }
            }
        }
        1 -> {
            pixelRect(line, w * .08f, h * .42f, w * .84f, h * .15f)
            pixelRect(
                mint,
                w * (.08f + .84f * (t.target - t.targetWidth / 2).toFloat()),
                h * .42f,
                w * .84f * t.targetWidth.toFloat(),
                h * .15f,
            )
            pixelRect(
                amber,
                w * (.08f + .84f * t.position().toFloat()),
                h * .37f,
                w * .025f,
                h * .25f,
            )
        }
        2 -> {
            for (i in 0 until t.required) {
                val x = w * .12f + (i % 5) * w * .16f
                val y = h * .20f + (i / 5) * h * .16f
                pixelRect(if (i < t.progress) mint else line, x, y, w * .11f, h * .1f)
            }
        }
        4 -> {
            val c = if (t.signal()) mint else amber
            pixelRect(c, w * .3f, h * .25f, w * .4f, h * .4f)
            pixelRect(ink, w * .4f, h * .35f, w * .2f, h * .2f)
        }
    }
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .1f, h * .85f, w * .8f, h * .025f)
        pixelRect(
            amber,
            w * .1f,
            h * .85f,
            w * .8f * (1 - t.elapsedMs.toFloat() / t.limitMs).coerceIn(0f, 1f),
            h * .025f,
        )
    }
}

private fun DrawScope.sprite(type: String, x: Float, y: Float, width: Float, color: Color) {
    val pattern =
        when (type) {
            "hero" ->
                listOf(
                    "000333000",
                    "003333300",
                    "003443300",
                    "000444000",
                    "002222200",
                    "022222220",
                    "012222210",
                    "010222010",
                    "000222000",
                    "000101000",
                    "001101100",
                )
            "slime" ->
                listOf(
                    "000000000",
                    "000111000",
                    "001111100",
                    "011111110",
                    "011010110",
                    "111111111",
                    "111111111",
                    "011111110",
                )
            "boss" ->
                listOf(
                    "100000001",
                    "110111011",
                    "011111110",
                    "012222210",
                    "011010110",
                    "001111100",
                    "001111100",
                    "011111110",
                    "111111111",
                    "011111110",
                    "010000010",
                )
            "chest" ->
                listOf(
                    "011111110",
                    "111111111",
                    "122222221",
                    "111111111",
                    "111313111",
                    "111313111",
                    "111111111",
                )
            "door" ->
                listOf(
                    "111111111",
                    "122222221",
                    "122222221",
                    "122222221",
                    "122222321",
                    "122222221",
                    "122222221",
                    "122222221",
                )
            "heal" ->
                listOf("000111000", "000111000", "011111110", "011111110", "000111000", "000111000")
            else ->
                listOf(
                    "000111000",
                    "001111100",
                    "000333000",
                    "001111100",
                    "011111110",
                    "011111110",
                    "001111100",
                    "001101100",
                )
        }
    val u = width / 9
    pattern.forEachIndexed { row, data ->
        data.forEachIndexed { col, c ->
            if (c != '0')
                pixelRect(
                    when (c) {
                        '2' -> Color(0xFF334E5C)
                        '3' -> amber
                        '4' -> Color(0xFFD8BBA4)
                        else -> color
                    },
                    x + col * u,
                    y + row * u,
                    u,
                    u,
                )
        }
    }
}
