package com.powerfulhimchan.mapexit.core

data class ContextButton(val label: String, val choice: Choice? = null, val enabled: Boolean = true)

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

    fun skipCurrentEvent() {
        context = token()
        routes = null
        eventSkipped = true
    }

    fun directionSelectionActive(): Boolean {
        buttons()
        return routes != null
    }

    fun resetDirections() {
        routes = null
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
        return when (s.mode) {
            Mode.COMBAT_ATTACK -> listOf(b("일반 공격", "attack"), b("강공격", "strong"))
            Mode.VICTORY ->
                listOf(ContextButton("전투 승리", enabled = false), b("결과 확인 · 계속", "battleContinue"))
            Mode.RUN_RESULT ->
                listOf(ContextButton("탐험 종료", enabled = false), b("준비 화면으로", "runContinue"))
            Mode.ENCOUNTER ->
                engine
                    .choices()
                    .map { ContextButton(it.label, it,
                        enabled = it.action !in listOf("npcDonate", "npcHeal", "npcPotion") || NpcInteractions.blockedReason(s) == null) }
                    .let {
                        if (it.size == 1) listOf(ContextButton("대화 중", enabled = false), it[0])
                        else it
                    }
            Mode.TRAP_GUIDE -> List(2) { ContextButton("곧 시작", enabled = false) }
            Mode.COMBAT_DEFEND -> listOf(b("방어", "guard"), b("회피", "dodge"))
            Mode.MOVING -> listOf(ContextButton("이동 중", enabled = false), b("멈추기", "stop"))
            Mode.EXPLORE -> {
                val all = engine.choices()
                val event = all.firstOrNull { it.action in setOf("encounter", "heal", "exit") }
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
                            moves.size == 1 -> {
                                val relative=(moves[0].argument.toInt()-(m?.direction ?: 0)+4)%4
                                val turn=b("뒤로 돌기", "_turnBack")
                                if(relative in listOf(1,2)) listOf(turn,moveButton(moves[0]))
                                else listOf(moveButton(moves[0]),turn)
                            }
                            moves.size == 2 -> moves.sortedBy {
                                listOf(3,0,2,1).indexOf((it.argument.toInt()-(m?.direction ?: 0)+4)%4)
                            }.map(::moveButton)
                            moves.size == 4 ->
                                listOf(b("앞 / 뒤 방향", "_pair", 0), b("좌 / 우 방향", "_pair", 1))
                            else -> listOf(moveButton(moves.first()), b("다른 방향 ›", "_routes"))
                        }
                    result
                }
            }
            Mode.FLOOR_CLEAR ->
                engine
                    .choices()
                    .filter { it.action in setOf("enter", "return", "ending") }
                    .map { ContextButton(it.label, it,
                        enabled = it.action !in listOf("npcDonate", "npcHeal", "npcPotion") || NpcInteractions.blockedReason(s) == null) }
                    .let {
                        if (it.size == 1) listOf(ContextButton("층 클리어", enabled = false), it[0])
                        else it
                    }
            Mode.TRAP -> {
                val t = s.trap
                if (t?.phase == TrapPhase.RESULT)
                    listOf(
                        ContextButton(if (t.success) "해제 성공" else "함정 결과", enabled = false),
                        ContextButton("자동 복귀", enabled = false),
                    )
                else
                    (0..1).map { i ->
                        val label =
                            if (t?.physicalAction == true) {
                                if (i == 0) "점프" else "몸 숙이기"
                            } else if (i == 0) "왼쪽 동작" else "오른쪽 동작"
                        ContextButton(label, enabled = t?.phase == TrapPhase.ACTIVE)
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

    fun press(button: Int) {
        val buttons = buttons()
        val b = buttons.getOrNull(button) ?: return
        if (!b.enabled) return
        val c = b.choice ?: return
        when (c.action) {
            "_turnBack" -> { engine.turnBack(); routes=null }
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
