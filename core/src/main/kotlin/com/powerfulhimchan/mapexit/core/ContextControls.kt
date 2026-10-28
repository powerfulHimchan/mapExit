package com.powerfulhimchan.mapexit.core

data class ContextButton(
    val label: String,
    val choice: Choice? = null,
    val holdLabel: String? = null,
    val holdChoice: Choice? = null,
    val enabled: Boolean = true,
)

/** Transient navigation lives outside the save: reopening always shows actual available actions. */
class ContextControls(private val engine: GameEngine) {
    private var context = ""
    private var routes: List<Choice>? = null
    private var eventSkipped = false

    fun token(): String {
        val s = engine.state
        return "${s.mode}:${s.floor}:${s.dungeon?.x}:${s.dungeon?.y}:${s.dungeon?.direction}:${s.enemy?.hp}:${s.dungeon?.here()?.resolved}:${s.menu}:${s.gold}:${s.points}"
    }

    fun inputToken(): String {
        buttons()
        return if (engine.state.mode == Mode.MOVING) "MOVING"
        else token() + ":$routes:$eventSkipped"
    }

    fun reset() {
        routes = null
        eventSkipped = false
        context = ""
    }

    fun buttons(): List<ContextButton> {
        val s = engine.state
        val key = token()
        if (context != key) {
            routes = null
            eventSkipped = false
            context = key
        }
        fun b(label: String, action: String, arg: Long = 0) =
            ContextButton(label, Choice(label, action, arg))
        val inventory = Choice("가방 / 장비", "inventory")
        val help = Choice("미션 / 기록", "help")
        return when (s.mode) {
            Mode.COMBAT_ATTACK ->
                listOf(
                    b("일반 공격", "attack")
                        .copy(
                            holdLabel = "회복약 ${s.inventory.count { it.kind == Kind.POTION }}개",
                            holdChoice = Choice("회복약", "potion"),
                        ),
                    b("강공격", "strong").copy(holdLabel = "도주 시도", holdChoice = Choice("도주", "flee")),
                )
            Mode.COMBAT_DEFEND -> listOf(b("방어", "guard"), b("회피", "dodge"))
            Mode.MOVING -> listOf(ContextButton("이동 중", enabled = false), b("멈추기", "stop"))
            Mode.EXPLORE -> {
                val all = engine.choices()
                val event =
                    all.firstOrNull { it.action in setOf("chest", "heal", "shop", "npc", "exit") }
                if (event != null && !eventSkipped)
                    listOf(ContextButton(event.label, event), b("지나가기", "_skip"))
                else {
                    val m = s.dungeon
                    val available =
                        all.filter { it.action == "move" }
                            .sortedBy {
                                listOf(0, 2, 3, 1)
                                    .indexOf((it.argument.toInt() - (m?.direction ?: 0) + 4) % 4)
                            }
                    val moves = routes ?: available
                    val result =
                        when {
                            moves.isEmpty() ->
                                listOf(
                                    ContextButton("막힌 길", enabled = false),
                                    ContextButton("막힌 길", enabled = false),
                                )
                            moves.size == 1 ->
                                listOf(
                                    moveButton(moves[0]),
                                    ContextButton("다른 길 없음", enabled = false),
                                )
                            moves.size == 2 -> moves.map(::moveButton)
                            moves.size == 4 ->
                                listOf(b("앞 / 뒤 방향", "_pair", 0), b("좌 / 우 방향", "_pair", 1))
                            else -> listOf(moveButton(moves.first()), b("다른 방향 ›", "_routes"))
                        }
                    result.mapIndexed { i, button ->
                        if (i == 0) button.copy(holdLabel = "가방 / 장비", holdChoice = inventory)
                        else if (routes != null)
                            button.copy(holdLabel = "방향 처음으로", holdChoice = Choice("처음", "_root"))
                        else button.copy(holdLabel = "기록 / 능력치", holdChoice = help)
                    }
                }
            }
            Mode.FLOOR_CLEAR ->
                engine
                    .choices()
                    .filter { it.action in setOf("enter", "return", "ending") }
                    .map { ContextButton(it.label, it) }
                    .let {
                        if (it.size == 1) listOf(ContextButton("층 클리어", enabled = false), it[0])
                        else it
                    }
            Mode.TRAP -> {
                val t = s.trap
                if (t?.phase == TrapPhase.RESULT)
                    listOf(
                        ContextButton(if (t.success) "해제 성공" else "함정 결과", enabled = false),
                        b("계속", "trapDone"),
                    )
                else
                    (0..1).map { i ->
                        val label =
                            when {
                                t == null -> "대기"
                                t.phase in listOf(TrapPhase.PREPARE, TrapPhase.REVEAL) -> "준비 / 기억"
                                t.type in listOf(0, 3) -> "${if (i == 0) "A" else "B"} 입력"
                                t.pattern.firstOrNull() != i -> "입력 금지"
                                t.type == 1 -> "목표에서 누르기"
                                t.type == 2 -> "연타하기"
                                t.signal() -> "지금 놓기"
                                else -> "누르고 유지"
                            }
                        ContextButton(label)
                    }
            }
            Mode.NOTICE -> listOf(ContextButton("안내", enabled = false), b("계속", "notice"))
            else ->
                listOf(
                    ContextButton("준비 화면", enabled = false),
                    ContextButton("화면에서 선택", enabled = false),
                )
        }
    }

    private fun moveButton(choice: Choice): ContextButton {
        val m = engine.state.dungeon ?: return ContextButton("이동 불가", enabled = false)
        val label =
            listOf("↑ 앞으로 이동", "→ 오른쪽 길", "↓ 뒤로 이동", "← 왼쪽 길")[
                (choice.argument.toInt() - m.direction + 4) % 4]
        return ContextButton(label, choice)
    }

    fun press(button: Int, held: Boolean = false) {
        val buttons = buttons()
        val b = buttons.getOrNull(button) ?: return
        if (!b.enabled && !held) return
        val c = (if (held) b.holdChoice else b.choice) ?: return
        when (c.action) {
            "_skip" -> eventSkipped = true
            "_pair" -> {
                val all =
                    engine
                        .choices()
                        .filter { it.action == "move" }
                        .sortedBy {
                            listOf(0, 2, 3, 1)
                                .indexOf(
                                    (it.argument.toInt() - (engine.state.dungeon?.direction ?: 0) +
                                        4) % 4
                                )
                        }
                routes = all.chunked(2).getOrNull(c.argument.toInt())
            }
            "_routes" -> {
                val all =
                    routes
                        ?: engine
                            .choices()
                            .filter { it.action == "move" }
                            .sortedBy {
                                listOf(0, 2, 3, 1)
                                    .indexOf(
                                        (it.argument.toInt() -
                                            (engine.state.dungeon?.direction ?: 0) + 4) % 4
                                    )
                            }
                routes = all.drop(1)
            }
            "_root" -> routes = null
            else -> {
                engine.choose(c)
                routes = null
            }
        }
    }
}
