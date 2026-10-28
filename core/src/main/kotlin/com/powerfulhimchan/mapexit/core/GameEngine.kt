package com.powerfulhimchan.mapexit.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class GameEngine(var state: State = State(), private val debugTools: Boolean = false) {
    private var debugBackup: String? = null
    val controls = ContextControls(this)

    init {
        if (!state.bootstrapped) {
            state.bootstrapped = true
            state.inventory.add(ItemFactory.create(state, Kind.SWORD, 1))
            state.inventory.add(ItemFactory.create(state, Kind.SHIELD, 1))
            repeat(2) { state.inventory.add(ItemFactory.create(state, Kind.POTION, 1)) }
            Equipment.equip(state, state.inventory[0].id, Slot.RIGHT)
            Equipment.equip(state, state.inventory[1].id, Slot.LEFT)
            state.shop = MutableList(6) { ItemFactory.create(state, floor = 1) }
            state.shop.add(ItemFactory.create(state, Kind.POTION, 1))
            state.note("출정 준비: 장비와 소모품을 확인한 뒤 던전에 진입하세요")
        }
        if (state.mode == Mode.TRAP_GUIDE) state.mode = Mode.TRAP
        if (state.mode == Mode.MENU && state.menu?.type == "npc") {
            state.menu = null
            state.menuStack.clear()
            beginEncounter(Event.NPC)
        }
    }

    fun stats() = Equipment.stats(state)

    private fun beginEncounter(event: Event) {
        val s = state
        if (event !in listOf(Event.CHEST, Event.NPC, Event.MERCHANT)) return
        s.encounter = event
        s.mode = Mode.ENCOUNTER
        s.selection = 0
        if (event == Event.NPC) {
            s.dungeon?.here()?.let { cell ->
                if (!cell.offerChecked && s.quest == null) {
                    cell.questOffer = createQuestOffer()
                    cell.offerChecked = true
                }
            }
        }
        s.note(
            when (event) {
                Event.CHEST -> "상자를 열까요?"
                Event.NPC -> "여행자를 만났습니다"
                else -> "상인을 만났습니다"
            }
        )
    }

    private fun createQuestOffer(): Quest? {
        val s = state
        val candidates = buildList {
            val monsters =
                s.dungeon?.cells?.count { it.event == Event.MONSTER && !it.resolved } ?: 0
            val chests = s.dungeon?.cells?.count { it.event == Event.CHEST && !it.resolved } ?: 0
            val kills = if (s.floor < 50) 5 else min(5, monsters)
            val boxes = if (s.floor < 50) 3 else min(3, chests)
            if (kills > 0) add(Quest("몬스터 ${kills}마리 처치", "kill", kills, reward = 80 + s.floor * 8))
            if (boxes > 0) add(Quest("상자 ${boxes}개 열기", "chest", boxes, reward = 100 + s.floor * 8))
            if (s.floor < 50)
                add(
                    Quest(
                        "지하 ${s.floor + 1}층 도달",
                        "floor",
                        s.floor + 1,
                        progress = s.floor,
                        reward = 70 + s.floor * 6,
                    )
                )
        }
        return candidates.takeIf { it.isNotEmpty() }?.let { it[s.nextInt(it.size)] }
    }

    private fun leaveEncounter(message: String? = null) {
        val s = state
        s.encounter = null
        s.mode = if (s.dungeon == null) Mode.SURFACE else Mode.EXPLORE
        s.selection = 0
        if (message != null) s.note(message)
        controls.skipCurrentEvent()
    }

    fun choices(): List<Choice> {
        val s = state
        fun c(label: String, action: String, arg: Long = 0) = Choice(label, action, arg)
        return when (s.mode) {
            Mode.SURFACE ->
                buildList {
                    add(c("1층부터 탐험", "enter", 1))
                    s.devices.sorted().forEach { add(c("장치로 지하 ${it}층", "enter", it.toLong())) }
                    add(c("가방 / 장비", "inventory"))
                    add(c("장착 현황", "equipment"))
                    add(c("성장 배분 (${s.points})", "growth"))
                    add(c("골드 보관소", "bank"))
                    add(c("지상 상점", "shop"))
                    add(c("기록 / 도움말", "help"))
                    if (debugTools) add(c("개발용 테스트", "debug"))
                }
            Mode.EXPLORE ->
                buildList {
                    val m = s.dungeon ?: return@buildList
                    val cell = m.here()
                    if (!cell.resolved)
                        when (cell.event) {
                            Event.CHEST ->
                                add(c("상자 살펴보기", "encounter", Event.CHEST.ordinal.toLong()))
                            Event.HEAL -> add(c("회복 장소 사용", "heal"))
                            Event.MERCHANT ->
                                add(c("상인 만나기", "encounter", Event.MERCHANT.ordinal.toLong()))
                            Event.NPC -> add(c("NPC 만나기", "encounter", Event.NPC.ordinal.toLong()))
                            Event.EXIT -> add(c("출구로 이동", "exit"))
                            else -> Unit
                        }
                    MapGenerator.directions(m).forEach { d ->
                        val rel = (d - m.direction + 4) % 4
                        val label = listOf("앞으로", "오른쪽으로", "뒤로", "왼쪽으로")[rel]
                        add(c(label, "move", d.toLong()))
                    }
                    add(c("가방 / 장비", "inventory"))
                    add(c("성장 배분 (${s.points})", "growth"))
                    add(c("장착 현황", "equipment"))
                    add(c("미션 / 기록", "help"))
                    if (s.debug) add(c("개발용 테스트", "debug"))
                }
            Mode.MOVING -> listOf(c("이동 중 · B로 멈추기", "stop"))
            Mode.COMBAT_ATTACK ->
                listOf(
                    c("일반 공격 · 명중 ${(GameBalance.model("normal_hit")*100).toInt()}%", "attack"),
                    c("강공격 · 명중 ${(GameBalance.model("strong_hit")*100).toInt()}%", "strong"),
                    c("회복약 사용", "potion"),
                    c("도주 시도", "flee"),
                )
            Mode.COMBAT_DEFEND -> listOf(c("방어 · 피해 감소", "guard"), c("회피 · 성공 시 무피해", "dodge"))
            Mode.VICTORY -> listOf(c("전투 결과 확인", "battleContinue"))
            Mode.ENCOUNTER ->
                buildList {
                    when (s.encounter) {
                        Event.CHEST -> add(c("열기", "chest"))
                        Event.MERCHANT -> add(c("거래하기", "shop"))
                        Event.NPC ->
                            when {
                                s.quest?.complete == true -> add(c("보상 받기", "claim"))
                                s.quest != null -> Unit
                                s.dungeon?.here()?.questOffer != null -> add(c("수락", "questAccept"))
                                else -> Unit
                            }
                        else -> Unit
                    }
                    add(
                        c(
                            if (
                                s.encounter == Event.NPC &&
                                    s.quest == null &&
                                    s.dungeon?.here()?.questOffer != null
                            )
                                "거절"
                            else "지나가기",
                            "encounterLeave",
                        )
                    )
                }
            Mode.TRAP_GUIDE -> emptyList()
            Mode.FLOOR_CLEAR ->
                if (s.floor == 50) listOf(c("50층 엔딩 · 계승 장비 선택", "ending"))
                else
                    listOf(
                        c("다음 ${s.floor+1}층으로", "enter", (s.floor + 1).toLong()),
                        c("지상으로 귀환", "return"),
                        c("가방 / 장비", "inventory"),
                        c("성장 배분 (${s.points})", "growth"),
                    )
            Mode.INHERIT ->
                s.inventory
                    .filter { !it.inherited && it.kind != Kind.POTION }
                    .map { c("계승 선택: ${it.name}", "inherit", it.id) }
            Mode.NOTICE -> listOf(c("계속", "notice"))
            Mode.TRAP -> emptyList()
            Mode.MENU -> menuChoices()
        }
    }

    private fun menuChoices(): List<Choice> {
        val s = state
        val menu = s.menu ?: return emptyList()
        fun c(label: String, action: String, arg: Long = 0) = Choice(label, action, arg)
        val items =
            when (menu.type) {
                "inventory" ->
                    s.inventory.map { item ->
                        c(
                            "${if(item.inherited)"◆ " else ""}${item.name}${if(item.id in s.equipment.values)" [착용]" else ""}",
                            "item",
                            item.id,
                        )
                    }
                "item" -> {
                    val g = s.inventory.find { it.id == menu.argument }
                    if (g == null) emptyList()
                    else if (g.kind == Kind.POTION) listOf(c("회복약 마시기", "useItem", g.id))
                    else
                        buildList {
                            Equipment.allowedSlots(g).forEach {
                                add(c("${it.label}에 장착", "equip", it.ordinal.toLong()))
                            }
                            if (g.id in s.equipment.values) add(c("장착 해제", "unequip", g.id))
                        }
                }
                "growth" ->
                    Stat.entries.map {
                        c("${it.label} 올리기 (${s.points}P)", "stat", it.ordinal.toLong())
                    }
                "equipment" ->
                    Slot.entries.map { slot ->
                        val item = s.inventory.find { it.id == s.equipment[slot] }
                        c(
                            "${slot.label}: ${item?.name ?: "비어 있음"}",
                            "slotItem",
                            slot.ordinal.toLong(),
                        )
                    }
                "bank" ->
                    listOf(
                        c("소지 ${s.gold}G 전부 보관", "deposit"),
                        c("보관 ${s.bank}G 전부 인출", "withdraw"),
                    )
                "shop" ->
                    s.shop.map { c("${it.name} · ${Equipment.buyPrice(s,it)}G", "buy", it.id) } +
                        c("아이템 판매", "sellMenu")
                "sell" ->
                    s.inventory.map {
                        c(
                            "${it.name}${Equipment.equippedLabel(s, it.id)} 판매 · ${Equipment.sellPrice(it)}G",
                            "sell",
                            it.id,
                        )
                    }
                "quantity" -> {
                    val potion = s.shop.find { it.id == menu.argument && it.kind == Kind.POTION }
                    if (potion == null) emptyList()
                    else
                        listOf(1, 3, 5).map {
                            c(
                                "회복약 ${it}개 구매 · ${Equipment.buyPrice(s,potion)*it}G",
                                "buyQuantity",
                                it.toLong(),
                            )
                        }
                }
                "help" ->
                    listOf(
                        c("장착 현황 / 상세 능력치", "equipment"),
                        c("성장 배분 (${s.points}P)", "growth"),
                        c("가방 / 장비", "inventory"),
                        c(
                            "${s.quest?.let { "미션 ${it.progress}/${it.target}: ${it.title}" } ?: "진행 중인 미션 없음"}",
                            "questInfo",
                        ),
                    )
                "debug" ->
                    buildList {
                        repeat(5) { add(c("함정 ${it+1} 바로 테스트", "testTrap", it.toLong())) }
                        add(c("일반 전투 테스트", "testBattle"))
                        add(c("10층 보스 테스트", "testBoss"))
                        add(c("50층 계승 테스트", "testEnding"))
                        add(c("모든 장비 / 성장 포인트 지급", "testGear"))
                        listOf(1, 10, 30, 50).forEach {
                            add(c("${it}층 이동", "testFloor", it.toLong()))
                        }
                        add(c("사망 처리 테스트", "testDeath"))
                        add(c("고정 시드 20261028", "testSeed"))
                        add(c("테스트 종료 · 원래 게임 복원", "debugExit"))
                    }
                else -> emptyList()
            }
        return items + c("돌아가기", "back")
    }

    fun press(button: Int) {
        if (state.mode == Mode.TRAP) {
            rawDown(button)
            rawUp(button)
            return
        }
        controls.press(button)
    }

    /** Touch menus and tests invoke current choices directly, without selection cycling. */
    fun choose(choice: Choice) {
        choices()
            .firstOrNull { it.action == choice.action && it.argument == choice.argument }
            ?.let(::execute)
    }

    fun hold(button: Int) {
        controls.press(button, held = true)
    }

    fun navigateBack() {
        if (state.mode == Mode.MENU) back() else controls.reset()
    }

    fun rawDown(button: Int) {
        if (state.mode == Mode.TRAP) {
            state.trap?.let { Traps.down(it, button) }
            applyTrapResult()
        }
    }

    fun rawUp(button: Int) {
        if (state.mode == Mode.TRAP) {
            val t = state.trap ?: return
            if (t.phase != TrapPhase.RESULT) {
                Traps.up(t, button)
                applyTrapResult()
            }
        }
    }

    fun pause() {
        state.trap?.let(Traps::pause)
    }

    fun tick(deltaMs: Long) {
        val s = state
        val delta = deltaMs.coerceIn(0L, 250L)
        s.playMs += delta
        if (s.mode == Mode.MOVING) {
            s.movementMs += delta
            if (s.movementMs >= 600) {
                s.movementMs -= 600
                moveOne()
            }
        }
        if (s.mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE)) {
            s.mode = Mode.TRAP
            s.trap?.let { Traps.tick(it, delta) }
            applyTrapResult()
            if (
                s.mode == Mode.TRAP &&
                    s.trap?.phase == TrapPhase.RESULT &&
                    s.trap!!.elapsedMs >= Traps.RESULT_MS
            )
                finishTrap()
        }
    }

    private fun open(type: String, arg: Long = 0L) {
        val s = state
        if (s.mode == Mode.MENU) s.menu?.let { s.menuStack.add(it) }
        s.menu = Menu(type, arg, if (s.mode == Mode.MENU) s.menu!!.returnMode else s.mode)
        s.mode = Mode.MENU
        s.selection = 0
    }

    private fun back() {
        val s = state
        if (s.menuStack.isNotEmpty()) s.menu = s.menuStack.removeAt(s.menuStack.lastIndex)
        else {
            s.mode = s.menu?.returnMode ?: Mode.SURFACE
            s.menu = null
        }
        s.selection = 0
    }

    private fun closeMenu() {
        val s = state
        s.mode = s.menu?.returnMode ?: Mode.SURFACE
        s.menu = null
        s.menuStack.clear()
        s.selection = 0
    }

    private fun execute(c: Choice) {
        val s = state
        when (c.action) {
            "battleContinue" -> {
                val result = s.battleResult ?: return
                s.battleResult = null
                s.lastImpact = null
                s.mode = result.returnMode
                s.selection = 0
                s.note(if (s.mode == Mode.FLOOR_CLEAR) "보스 격파 · 층 클리어" else "전투 종료 · 탐험을 계속하세요")
            }
            "encounter" -> beginEncounter(Event.entries[c.argument.toInt()])
            "encounterLeave" -> leaveEncounter("탐험을 계속합니다")
            "questAccept" -> {
                if (s.quest != null) return
                val offer = s.dungeon?.here()?.questOffer ?: return
                s.quest = offer.copy()
                leaveEncounter("미션 수락 · ${offer.title}")
            }
            "enter" -> enterFloor(c.argument.toInt())
            "return" -> surface(false)
            "move" -> {
                val dungeon = s.dungeon ?: return
                val facing = dungeon.direction
                val backward = c.argument.toInt() == (facing + 2) % 4
                dungeon.direction = c.argument.toInt()
                s.mode = Mode.MOVING
                s.movementMs = 0
                moveOne()
                if (backward && s.dungeon === dungeon) {
                    dungeon.direction = facing
                    if (s.mode == Mode.MOVING) s.mode = Mode.EXPLORE
                }
            }
            "stop" -> {
                s.mode = Mode.EXPLORE
                s.selection = 0
            }
            "inventory",
            "growth",
            "equipment",
            "bank",
            "help" -> open(c.action)
            "item" -> open("item", c.argument)
            "slotItem" -> {
                val id = s.equipment[Slot.entries[c.argument.toInt()]]
                if (id != null) open("item", id)
            }
            "equip" -> {
                val id = s.menu?.argument ?: return
                if (Equipment.equip(s, id, Slot.entries[c.argument.toInt()])) s.note("장비를 변경했습니다")
                closeMenu()
            }
            "unequip" -> {
                Equipment.unequip(s, c.argument)
                s.note("장비를 해제했습니다")
                closeMenu()
            }
            "useItem" -> {
                usePotion(c.argument)
                closeMenu()
            }
            "stat" -> spendPoint(Stat.entries[c.argument.toInt()])
            "deposit" -> {
                s.bank += s.gold
                s.gold = 0
                s.note("골드를 지상 보관소에 맡겼습니다")
            }
            "withdraw" -> {
                s.gold += s.bank
                s.bank = 0
                s.note("보관 골드를 인출했습니다")
            }
            "shop" -> open("shop")
            "buy" -> {
                val item = s.shop.find { it.id == c.argument } ?: return
                if (item.kind == Kind.POTION) {
                    open("quantity", item.id)
                    return
                }
                val price = Equipment.buyPrice(s, item)
                if (s.gold >= price) {
                    s.gold -= price
                    s.shop.remove(item)
                    s.inventory.add(item)
                    s.note("${item.name} 구매")
                    s.selection = s.selection.coerceAtMost(choices().lastIndex)
                } else s.note("골드가 부족합니다")
            }
            "buyQuantity" -> {
                val potion =
                    s.shop.find { it.id == s.menu?.argument && it.kind == Kind.POTION } ?: return
                val count = c.argument.toInt()
                if (count !in listOf(1, 3, 5)) return
                val price = Equipment.buyPrice(s, potion) * count
                if (s.gold < price) {
                    s.note("골드가 부족합니다")
                    return
                }
                s.gold -= price
                repeat(count) { s.inventory.add(potion.copy(id = s.nextId++)) }
                s.note("회복약 ${count}개 구매 · ${price}G")
            }
            "sellMenu" -> open("sell")
            "sell" -> {
                val item = s.inventory.find { it.id == c.argument } ?: return
                s.gold += Equipment.sellPrice(item)
                Equipment.unequip(s, item.id)
                s.inventory.remove(item)
                s.note("${item.name} 판매")
                s.selection = s.selection.coerceAtMost(choices().lastIndex)
            }
            "chest" -> openChest()
            "heal" -> {
                val m = s.dungeon ?: return
                if (!m.here().resolved) {
                    m.here().resolved = true
                    s.hp = min(stats().hp, s.hp + stats().hp * .45)
                    s.note("회복 장소: HP 45% 회복")
                }
            }
            "claim" -> {
                val q = s.quest
                if (q?.complete == true) {
                    val reward =
                        (q.reward * (if (Equipment.charm(s) == Charm.QUEST) 1.25 else 1.0)).toInt()
                    s.gold += reward
                    s.quest = null
                    s.note("미션 보상 ${reward}G 획득")
                    s.dungeon?.here()?.let { if (it.event == Event.NPC) it.resolved = true }
                    leaveEncounter()
                }
            }
            "questInfo" ->
                s.note(
                    s.quest?.let { "${it.title} (${it.progress}/${it.target}) · 귀환/사망 시 종료" }
                        ?: "버튼 이름의 행동을 바로 실행 · 길게 누르기로 가방·회복약·도주 · 출구에서 귀환"
                )
            "exit" -> exitFloor()
            "attack" -> attack(false)
            "strong" -> attack(true)
            "guard" -> defend(false)
            "dodge" -> defend(true)
            "potion" -> {
                if (usePotion()) {
                    s.mode = Mode.COMBAT_DEFEND
                    s.selection = 0
                }
            }
            "flee" -> flee()
            "ending" -> beginInheritance()
            "inherit" -> newCycle(c.argument)
            "back" -> back()
            "debug" -> {
                if (!s.debug) debugBackup = SaveCodec.encode(s)
                s.debug = true
                open("debug")
            }
            "debugExit" -> {
                debugBackup?.let { state = SaveCodec.decode(it) }
                state.debug = false
                debugBackup = null
                state.note("원래 게임으로 복원했습니다")
            }
            "testTrap" -> {
                s.menu = null
                s.menuStack.clear()
                s.trap = Traps.create(s, c.argument.toInt())
                s.mode = Mode.TRAP
                s.selection = 0
            }
            "testBattle" -> {
                s.menu = null
                s.menuStack.clear()
                startBattle(false)
            }
            "testBoss" -> {
                enterFloor(10)
                startBattle(true)
            }
            "testEnding" -> {
                s.floor = 50
                s.inventory.add(ItemFactory.create(s, Kind.SWORD, 50))
                beginInheritance()
            }
            "testGear" -> {
                Kind.entries.forEach { s.inventory.add(ItemFactory.create(s, it, max(1, s.floor))) }
                s.points += 30
                s.gold += 1000
                s.note("테스트용 장비·성장·골드 지급")
            }
            "testFloor" -> enterFloor(c.argument.toInt())
            "testDeath" -> die()
            "testSeed" -> {
                s.rng = 20261028L
                s.note("난수 시드를 고정했습니다")
            }
        }
    }

    fun enterFloor(floor: Int) {
        require(floor in 1..50)
        val s = state
        s.floor = floor
        s.highest = max(s.highest, floor)
        s.dungeon = MapGenerator.generate(s, floor)
        s.enemy = null
        s.battleResult = null
        s.trap = null
        s.encounter = null
        s.menu = null
        s.menuStack.clear()
        s.mode = Mode.EXPLORE
        s.selection = 0
        s.movementMs = 0
        s.shop = MutableList(5) { ItemFactory.create(s) }
        s.shop.add(ItemFactory.create(s, Kind.POTION))
        s.quest?.takeIf { it.kind == "floor" }?.let { it.progress = max(it.progress, floor) }
        s.note("지하 ${floor}층 · 갈림길에서 방향을 선택하세요")
    }

    private fun moveOne() {
        val s = state
        val m = s.dungeon ?: return
        val xx = m.x + MapGenerator.dx[m.direction]
        val yy = m.y + MapGenerator.dy[m.direction]
        if (m.cell(xx, yy).wall) {
            s.mode = Mode.EXPLORE
            s.selection = 0
            return
        }
        m.arrivalDirection = m.direction
        m.x = xx
        m.y = yy
        m.reveal()
        val cell = m.here()
        if (!cell.resolved && cell.event != Event.NONE) {
            s.mode = Mode.EXPLORE
            s.selection = 0
            when (cell.event) {
                Event.MONSTER -> startBattle(false)
                Event.TRAP -> {
                    s.trap = Traps.create(s, cell.trapType)
                    s.mode = Mode.TRAP
                }
                Event.CHEST,
                Event.NPC,
                Event.MERCHANT -> beginEncounter(cell.event)
                Event.EXIT -> exitFloor()
                else ->
                    s.note(
                        when (cell.event) {
                            Event.CHEST -> "아이템 상자를 발견했습니다"
                            Event.HEAL -> "회복 장소를 발견했습니다"
                            Event.MERCHANT -> "상인을 만났습니다"
                            Event.NPC -> "미션 NPC를 만났습니다"
                            else -> ""
                        }
                    )
            }
            return
        }
        val next = MapGenerator.directions(m).filter { it != (m.direction + 2) % 4 }
        if (next.size == 1) m.direction = next.first()
        else {
            s.mode = Mode.EXPLORE
            s.selection = 0
            s.note(if (next.isEmpty()) "막다른 길입니다" else "갈림길: 방향을 선택하세요")
        }
    }

    private fun exitFloor() {
        val s = state
        val m = s.dungeon ?: return
        if (s.floor % 10 == 0 && !m.bossCleared) {
            startBattle(true)
            return
        }
        s.mode = Mode.FLOOR_CLEAR
        s.selection = 0
        s.note("${s.floor}층 클리어! 다음 층 또는 지상 귀환")
    }

    private fun openChest() {
        val s = state
        val cell = s.dungeon?.here() ?: return
        if (cell.resolved) return
        cell.resolved = true
        s.quest?.takeIf { it.kind == "chest" }?.let { it.progress++ }
        s.encounter = null
        s.mode = Mode.EXPLORE
        if (s.random() < .20) {
            startBattle(false)
            s.enemy = s.enemy!!.copy(name = "상자 속 ${s.enemy!!.name}")
            s.note("상자에서 몬스터가 튀어나왔습니다!")
            return
        }
        val extra = Equipment.charm(s) == Charm.CHEST && s.random() < .25
        val gear = ItemFactory.create(s)
        s.inventory.add(gear)
        if (extra) s.inventory.add(ItemFactory.create(s))
        val gold = 15 + s.floor * 3 + s.nextInt(15)
        s.gold += gold
        s.note("${gear.name} · ${gold}G${if(extra)" · 추가 아이템!" else ""}")
    }

    fun startBattle(boss: Boolean) {
        val s = state
        val floor = max(1, s.floor)
        val hp = (300 + floor * 26.0) * (if (boss) 1.7 else 1.0)
        s.enemy =
            Enemy(
                if (boss) "${floor}층의 수문장" else listOf("동굴 슬라임", "녹슨 기사", "심연 박쥐")[s.nextInt(3)],
                hp,
                hp,
                (65 + floor * 4.0) * (if (boss) 1.2 else 1.0),
                20 + floor * 1.8,
                10.0 + floor * .3,
                boss,
            )
        s.encounter = null
        s.battleId++
        s.battleResult = null
        s.lastImpact = null
        s.mode = Mode.COMBAT_ATTACK
        s.selection = 0
        s.note("${s.enemy!!.name} 조우 · 3인칭 전투 시작")
    }

    fun attack(strong: Boolean) {
        val s = state
        if (s.mode != Mode.COMBAT_ATTACK) return
        val enemy = s.enemy ?: return
        val p = stats()
        var damage = 0.0
        val critical = s.random() < p.crit
        if (
            s.random() <
                (if (strong) GameBalance.model("strong_hit") else GameBalance.model("normal_hit"))
        )
            damage =
                p.attack * (if (strong) GameBalance.model("strong_damage") else 1.0) * 100 /
                    (100 + enemy.defense) * (if (critical) p.critDamage else 1.0)
        val previousHp = enemy.hp
        enemy.hp = max(0.0, enemy.hp - damage)
        impact(
            ImpactTarget.ENEMY,
            previousHp - enemy.hp,
            if (damage == 0.0) ImpactOutcome.MISS else ImpactOutcome.DAMAGE,
            critical && damage > 0,
        )
        s.note(
            if (damage == 0.0) "공격이 빗나갔습니다"
            else "${if(critical)"치명타! " else ""}${damage.roundToInt()} 피해"
        )
        if (enemy.hp <= 0) {
            victory()
            return
        }
        s.mode = Mode.COMBAT_DEFEND
        s.selection = 0
    }

    fun defend(dodge: Boolean) {
        val s = state
        if (s.mode != Mode.COMBAT_DEFEND) return
        val e = s.enemy ?: return
        val p = stats()
        val chance =
            (GameBalance.model("dodge_base") +
                    (p.agility - e.precision) * GameBalance.model("dodge_per_agility_gap"))
                .coerceIn(GameBalance.model("dodge_min"), GameBalance.model("dodge_max"))
        val shield = Equipment.equipped(s).any { it.kind == Kind.SHIELD }
        val critical = s.random() < .10
        var damage = e.attack * 100 / (100 + p.defense) * (if (critical) 1.5 else 1.0)
        if (dodge) {
            if (s.random() < chance) damage = 0.0
        } else
            damage *=
                1.0 -
                    GameBalance.model("guard_reduction") -
                    (if (shield) GameBalance.shield("guard_bonus") else 0.0)
        val previousHp = s.hp
        s.hp = max(0.0, s.hp - damage)
        impact(
            ImpactTarget.PLAYER,
            previousHp - s.hp,
            if (damage == 0.0) ImpactOutcome.DODGE
            else if (!dodge) ImpactOutcome.GUARD else ImpactOutcome.DAMAGE,
            critical && damage > 0,
        )
        s.note(
            if (damage == 0.0) "회피 성공!"
            else "${if(dodge)"회피 실패 · " else "방어 · "}${damage.roundToInt()} 피해"
        )
        if (s.hp <= 0) {
            die()
            return
        }
        s.mode = Mode.COMBAT_ATTACK
        s.selection = 0
    }

    private fun impact(
        target: ImpactTarget,
        damage: Double,
        outcome: ImpactOutcome,
        critical: Boolean = false,
    ) {
        val s = state
        s.impactSequence++
        s.lastImpact =
            BattleImpact(
                s.impactSequence,
                target,
                if (damage > 0) damage.roundToInt().coerceAtLeast(1) else 0,
                outcome,
                critical,
            )
    }

    private fun victory() {
        val s = state
        val enemy = s.enemy ?: return
        val gold = 20 + s.floor * 4
        val experience = 25 + s.floor * 5
        val items = mutableListOf<String>()
        s.gold += gold
        gainExperience(experience)
        s.quest?.takeIf { it.kind == "kill" }?.let { it.progress++ }
        if (enemy.boss) {
            s.devices.add(s.floor)
            s.dungeon?.bossCleared = true
            val reward = ItemFactory.create(s, Kind.entries[s.nextInt(Kind.entries.size - 1)])
            s.inventory.add(reward)
            items.add(reward.name)
        } else {
            s.dungeon?.here()?.resolved = true
            if (s.random() < (.25 + stats().luck * .003).coerceAtMost(.65)) {
                val reward = ItemFactory.create(s)
                s.inventory.add(reward)
                items.add(reward.name)
            }
        }
        val returnMode =
            if (enemy.boss) Mode.FLOOR_CLEAR
            else if (s.dungeon == null) Mode.SURFACE else Mode.EXPLORE
        s.battleResult = BattleResult(enemy.copy(hp = 0.0), gold, experience, items, returnMode)
        s.enemy = null
        s.mode = Mode.VICTORY
        s.selection = 0
        s.note("전투 승리 · ${enemy.name} 격파 · ${gold}G / 경험치 ${experience}")
    }

    private fun flee() {
        val s = state
        val e = s.enemy ?: return
        val chance = (.35 + (stats().agility - e.precision) * .006).coerceIn(.15, .85)
        if (s.random() < chance) {
            s.enemy = null
            s.lastImpact = null
            s.mode = Mode.EXPLORE
            s.dungeon?.let { m ->
                val back = ((m.arrivalDirection.takeIf { it in 0..3 } ?: m.direction) + 2) % 4
                if (!m.cell(m.x + MapGenerator.dx[back], m.y + MapGenerator.dy[back]).wall) {
                    m.x += MapGenerator.dx[back]
                    m.y += MapGenerator.dy[back]
                    m.reveal()
                }
            }
            s.note("도주 성공! 이전 통로로 물러났습니다")
        } else {
            s.mode = Mode.COMBAT_DEFEND
            s.note("도주 실패! 방어 행동을 선택하세요")
        }
        s.selection = 0
    }

    private fun gainExperience(amount: Int) {
        val s = state
        s.experience += amount
        while (s.experience >= s.level * 80) {
            s.experience -= s.level * 80
            s.level++
            s.points += 3
        }
    }

    fun spendPoint(stat: Stat) {
        val s = state
        if (s.points <= 0) {
            s.note("배분할 성장 포인트가 없습니다")
            return
        }
        val amount =
            when (stat) {
                Stat.HP -> 25.0
                Stat.ATTACK -> 6.0
                Stat.DEFENSE -> 5.0
                Stat.AGILITY -> 3.0
                Stat.CRIT -> .015
                Stat.CRIT_DAMAGE -> .08
                Stat.LUCK -> 3.0
            }
        s.base = s.base.add(stat, amount)
        s.points--
        if (stat == Stat.HP) s.hp = min(stats().hp, s.hp + amount)
        s.note("${stat.label} 성장 · 남은 포인트 ${s.points}")
    }

    private fun usePotion(id: Long? = null): Boolean {
        val s = state
        val item = s.inventory.find { it.kind == Kind.POTION && (id == null || it.id == id) }
        if (item == null) {
            s.note("회복약이 없습니다")
            return false
        }
        s.inventory.remove(item)
        s.hp = min(stats().hp, s.hp + stats().hp * .35)
        s.note("회복약: HP 35% 회복")
        return true
    }

    private fun applyTrapResult() {
        val s = state
        val t = s.trap ?: return
        if (t.phase != TrapPhase.RESULT || t.applied) return
        t.applied = true
        s.dungeon?.here()?.let { if (it.event == Event.TRAP) it.resolved = true }
        if (!t.success) {
            val damage = stats().hp * .15 + s.floor * 2
            val previousHp = s.hp
            s.hp = max(0.0, s.hp - damage)
            impact(ImpactTarget.PLAYER, previousHp - s.hp, ImpactOutcome.DAMAGE)
            s.note("함정 회피 실패 · ${damage.roundToInt()} 피해")
            if (s.hp <= 0) die()
        } else s.note("함정 회피 성공!")
    }

    private fun finishTrap() {
        state.trap = null
        state.mode = if (state.dungeon == null) Mode.SURFACE else Mode.EXPLORE
        state.selection = 0
    }

    fun die() {
        surface(true)
    }

    private fun surface(dead: Boolean) {
        val s = state
        if (dead) {
            s.inventory.clear()
            s.equipment.clear()
            s.gold = 0
        }
        s.hp = stats().hp
        s.quest = null
        s.floor = 0
        s.dungeon = null
        s.enemy = null
        s.battleResult = null
        s.trap = null
        s.encounter = null
        s.mode = Mode.SURFACE
        s.menu = null
        s.menuStack.clear()
        s.selection = 0
        s.shop = MutableList(6) { ItemFactory.create(s, floor = max(1, s.highest)) }
        s.shop.add(ItemFactory.create(s, Kind.POTION, 1))
        s.note(if (dead) "사망 · 지상 부활. 장비·아이템·소지 골드를 잃었습니다" else "지상 귀환 · 아이템과 골드를 안전하게 가져왔습니다")
    }

    fun beginInheritance() {
        val s = state
        if (s.inventory.none { !it.inherited && it.kind != Kind.POTION })
            s.inventory.add(ItemFactory.create(s, Kind.SWORD, 50))
        s.mode = Mode.INHERIT
        s.selection = 0
        s.menu = null
        s.menuStack.clear()
        s.note("50층 클리어! 기존 계승 ${s.inventory.count { it.inherited }}개 자동 보존 + 새 장비 하나 선택")
    }

    fun newCycle(id: Long) {
        val s = state
        val chosen =
            s.inventory.find { it.id == id && !it.inherited && it.kind != Kind.POTION } ?: return
        chosen.inherited = true
        s.inventory.removeAll { !it.inherited }
        s.equipment.clear()
        s.cycle++
        s.clears++
        s.level = 1
        s.experience = 0
        s.points = 0
        s.base = Stats()
        s.hp = s.base.hp
        s.gold = 0
        s.bank = 0
        s.devices.clear()
        s.floor = 0
        s.dungeon = null
        s.enemy = null
        s.trap = null
        s.encounter = null
        s.quest = null
        s.menu = null
        s.menuStack.clear()
        s.mode = Mode.SURFACE
        s.selection = 0
        s.shop = MutableList(5) { ItemFactory.create(s, floor = 1) }
        s.note("${s.cycle}회차 시작 · 계승 장비 ${s.inventory.size}개 · 능력치/돈/장치 초기화")
    }
}
