package com.powerfulhimchan.mapexit.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class GameEngine(
    var state: State = State(),
    private val debugTools: Boolean = false,
    private val mapSeed: (() -> Long)? = null,
) {
    private var debugBackup: String? = null
    private var trainingBackup: String? = null
    val controls = ContextControls(this)

    init {
        if (!state.bootstrapped) {
            state.bootstrapped = true
            state.inventory.add(ItemFactory.create(state, Kind.SWORD, 1, variantId = "longsword"))
            state.inventory.add(ItemFactory.create(state, Kind.SHIELD, 1))
            repeat(2) { state.inventory.add(ItemFactory.create(state, Kind.POTION, 1)) }
            Equipment.equip(state, state.inventory[0].id, Slot.RIGHT)
            Equipment.equip(state, state.inventory[1].id, Slot.LEFT)
            state.shop = MutableList(6) { ItemFactory.create(state, floor = 1) }
            state.shop.add(ItemFactory.create(state, Kind.POTION, 1))
            state.note("출정 준비: 장비와 소모품을 확인한 뒤 던전에 진입하세요")
        }
        if (
            !state.training &&
                state.mode == Mode.MENU &&
                state.menu?.type in listOf("training", "trainingTraps", "trainingChallenges")
        ) {
            state.mode = state.menu?.returnMode ?: Mode.SURFACE
            state.menu = null
            state.menuStack.clear()
        }
        if(state.quickEquipIds.isEmpty() && state.mode in listOf(Mode.EXPLORE,Mode.VICTORY)) {
            state.quickEquipIds=(state.chestLoot?.items ?: state.battleResult?.rewards ?: emptyList())
                .filter { it.kind!=Kind.POTION && itemOwned(it.id) }.map { it.id }
        }
        if (state.shop.none { it.kind == Kind.POTION && it.potionGrade == PotionGrade.GREATER })
            state.shop.add(ItemFactory.potion(state, PotionGrade.GREATER))
        Achievements.initialize(state)
        if (state.challenge == null) state.dungeon?.let { ChallengeStages.placePortal(state, it) }
        if (state.mode == Mode.TRAP_GUIDE) state.mode = Mode.TRAP
        // Saves from before expedition records can still report their current depth.
        if (state.dungeon != null) state.runDeepestFloor = max(state.runDeepestFloor, state.floor)
        if (state.mode == Mode.MENU && state.menu?.type == "npc") {
            state.menu = null
            state.menuStack.clear()
            beginEncounter(Event.NPC)
        }
    }

    private fun itemOwned(id:Long)=state.inventory.any { it.id==id }

    fun stats() = Equipment.stats(state)

    fun trainingMenu() {
        if (!state.training) return
        state.challenge?.let { state.dungeon = it.hostDungeon; state.challenge = null }
        state.enemy = null
        state.trap = null
        state.battleResult = null
        state.lastImpact = null
        state.encounter = null
        state.chestLoot = null
        state.menuStack.clear()
        state.mode = Mode.MENU
        state.menu = Menu("training", returnMode = Mode.SURFACE)
    }

    fun exitTraining() {
        if (!state.training) return
        trainingBackup?.let { state = SaveCodec.decode(it) }
            ?: run { state = GameEngine(State(), debugTools, mapSeed).state }
        state.training = false
        state.debug = false
        trainingBackup = null
        controls.reset()
    }

    private fun beginTraining(activity: String, trapType: Int = 0, npcRole: NpcRole = NpcRole.QUEST) {
        if (!state.training) {
            val normal =
                state.copy(
                    mode = state.menu?.returnMode ?: Mode.SURFACE,
                    menu = null,
                    menuStack = mutableListOf(),
                )
            trainingBackup = SaveCodec.encode(normal)
        }
        state =
            GameEngine(State(rng = state.rng), false)
                .state
                .copy(training = true, debug = true, gold = 1000)
        enterFloor(1)
        when (activity) {
            "trap" -> {
                state.trap = Traps.create(state, trapType)
                state.mode = Mode.TRAP
            }
            "challenge" -> {
                state.dungeon!!.here().event = Event.PORTAL
                state.dungeon!!.here().resolved = false
                ChallengeStages.open(state, ChallengeKind.entries[trapType])
            }
            "battle" -> startBattle(false)
            "merchant",
            "npc" -> {
                val event = if (activity == "merchant") Event.MERCHANT else Event.NPC
                state.dungeon!!.here().event = event
                if (event == Event.NPC) {
                    state.dungeon!!.here().npcRole = npcRole
                    if (npcRole == NpcRole.HEALER) state.hp = stats().hp * .4
                }
                beginEncounter(event)
            }
        }
        controls.reset()
    }

    private fun beginEncounter(event: Event) {
        val s = state
        if (event !in listOf(Event.CHEST, Event.NPC, Event.MERCHANT)) return
        val currentCell = s.dungeon?.here() ?: return
        if (currentCell.event != event || currentCell.resolved) return
        s.encounter = event
        s.mode = Mode.ENCOUNTER
        s.selection = 0
        if (event == Event.NPC) {
            s.dungeon?.here()?.let { cell ->
                if (cell.npcRole == NpcRole.BEGGAR && cell.npcGift == null)
                    cell.npcGift = NpcInteractions.giftFor(s.visitorSeed ?: s.rng, s.floor)
                if (cell.npcRole == NpcRole.QUEST && !cell.offerChecked && s.quest == null) {
                    cell.questOffer = createQuestOffer()
                    cell.offerChecked = true
                }
            }
        }
        s.note(
            when (event) {
                Event.CHEST -> "상자를 열까요?"
                Event.NPC -> if (NpcInteractions.role(s) == NpcRole.QUEST) "여행자를 만났습니다" else "${NpcInteractions.role(s).title}를 만났습니다"
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
            val boxes = min(3, chests + (50 - s.floor).coerceAtLeast(0) * 2)
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
        if (s.training) trainingMenu()
    }

    fun lootChoices(): List<Choice> = state.quickEquipIds.mapNotNull { id ->
        state.inventory.find { it.id == id && it.kind != Kind.POTION }?.let { item ->
            Choice(if (id in state.equipment.values) "[착용중]" else "바로 장착 · ${item.name}", "equipLoot", id)
        }
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
                    add(c("테스트 메뉴", "training"))
                    if (debugTools) add(c("개발용 테스트", "debug"))
                }
            Mode.EXPLORE ->
                buildList {
                    addAll(lootChoices())
                    val m = s.dungeon ?: return@buildList
                    add(c("지상으로 · 탐험 포기", "retreat"))
                    val cell = m.here()
                    if (!cell.resolved)
                        when (cell.event) {
                            Event.CHEST ->
                                add(c("상자 살펴보기", "encounter", Event.CHEST.ordinal.toLong()))
                            Event.HEAL -> add(c("회복의 수정 사용", "heal"))
                            Event.MERCHANT ->
                                add(c("상인 만나기", "encounter", Event.MERCHANT.ordinal.toLong()))
                            Event.NPC -> add(c("NPC 만나기", "encounter", Event.NPC.ordinal.toLong()))
                            Event.EXIT -> add(c("출구로 이동", "exit"))
                            Event.PORTAL -> add(c("특별 도전 살펴보기", "challengeEnter"))
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
            Mode.MOVING -> listOf(c("이동 중 · 멈추기", "stop"), c("지상으로 · 탐험 포기", "retreat"))
            Mode.RUN_RESULT -> listOf(c("준비 화면으로", "runContinue"))
            Mode.COMBAT_ATTACK ->
                listOf(
                    c("일반 공격 · 명중 ${(GameBalance.model("normal_hit")*100).toInt()}%", "attack"),
                    c("강공격 · 명중 ${(GameBalance.model("strong_hit")*100).toInt()}%", "strong"),
                    c("회복약 사용", "potion"),
                    c("상급 회복약 사용", "strongPotion"),
                    c("도주 시도", "flee"),
                )
            Mode.COMBAT_DEFEND -> listOf(c("방어 · 피해 감소", "guard"), c("회피 · 성공 시 무피해", "dodge"))
            Mode.VICTORY -> listOf(c("전투 결과 확인", "battleContinue"))
            Mode.ENCOUNTER ->
                buildList {
                    when (s.encounter) {
                        Event.CHEST -> add(c("열기", "chest"))
                        Event.MERCHANT -> add(c("거래하기", "shop"))
                        Event.NPC -> {
                            val role = NpcInteractions.role(s)
                            if (role != NpcRole.QUEST) {
                                if (!NpcInteractions.used(s)) add(c(NpcInteractions.actionLabel(role), role.action))
                            } else when {
                                s.quest?.complete == true -> add(c("보상 받기", "claim"))
                                s.quest != null -> Unit
                                s.dungeon?.here()?.questOffer != null -> add(c("수락", "questAccept"))
                                else -> Unit
                            }
                        }
                        else -> Unit
                    }
                    add(
                        c(
                            if (
                                s.encounter == Event.NPC && NpcInteractions.role(s) == NpcRole.QUEST &&
                                    s.quest == null &&
                                    s.dungeon?.here()?.questOffer != null
                            )
                                "거절"
                            else "지나가기",
                            "encounterLeave",
                        )
                    )
                }
            Mode.CHALLENGE -> ChallengeStages.choices(s)
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
                "slotInventory" ->
                    s.inventory.filter { Slot.entries[menu.argument.toInt()] in Equipment.allowedSlots(it) }.map {
                        c("${it.name}${if(it.id in s.equipment.values) " [착용중]" else ""}", "item", it.id)
                    }
                "inventory" ->
                    s.inventory.map { item ->
                        c(
                            "${if(item.inherited)"◆ " else ""}${item.name}${if(item.id in s.equipment.values)" [착용중]" else ""}",
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
                    } + c("체력 모두 회복 · 1P", "growthHeal")
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
                    s.shop.map { c(it.name, "buy", it.id) } +
                        c("아이템 판매", "sellMenu")
                "sell" ->
                    s.inventory.map {
                        c(
                            "${it.name}${Equipment.equippedLabel(s, it.id)} 판매 · ${Equipment.sellPrice(it)}G",
                            "sell",
                            it.id,
                        )
                    }
                "shopItem" -> {
                    val item = s.shop.find { it.id == menu.argument }
                    if (item == null) listOf(c("취소", "back"))
                    else listOf(c("구매", "purchase", item.id), c("취소", "back"))
                }
                "help" -> listOf(c("몬스터 도감", "bestiary"), c("업적", "achievements"), c("특별 도전 기록", "challengeRecords"))
                "challengeRecords" -> emptyList()
                "trainingChallenges" -> ChallengeKind.entries.map { c(it.title, "trainChallenge", it.ordinal.toLong()) }
                "achievements" -> buildList {
                    Achievements.all.forEachIndexed { index, a ->
                        if (a.id in s.achievements.unlocked && a.id !in s.achievements.claimed)
                            add(c("보상 받기 · ${a.name}", "claimAchievement", index.toLong()))
                        if (a.title != null && a.id in s.achievements.claimed)
                            add(c("칭호 선택 · ${a.title}", "selectTitle", index.toLong()))
                    }
                    add(c("칭호 해제", "selectTitle", -1))
                }
                "bestiary" -> emptyList()
                "training" ->
                    listOf(
                        c("장애물 피하기", "trainingTraps"),
                        c("특별 도전 7종", "trainingChallenges"),
                        c("전투", "trainBattle"),
                        c("상인", "trainMerchant"),
                        c("미션 NPC 대화", "trainNpc"),
                        c("구걸 NPC 대화", "trainBeggar"),
                        c("치료사 대화", "trainHealer"),
                        c("약초 수집가 대화", "trainHerbalist"),
                    )
                "trainingTraps" ->
                    listOf("뜨거운 물 솥", "바닥 칼날", "좁혀오는 벽", "기억 석판", "천장 나무봉").mapIndexed { i, name ->
                        c(name, "trainTrap", i.toLong())
                    }
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
        return if (menu.type == "shopItem") items else items + c("돌아가기", "back")
    }

    fun press(button: Int) {
        if (state.mode == Mode.TRAP) {
            state.trap?.let { Traps.tap(it, button) }
            applyTrapResult()
            return
        }
        controls.press(button)
    }

    /** Touch menus and tests invoke current choices directly, without selection cycling. */
    fun choose(choice: Choice) {
        (choices() + if (state.mode in listOf(Mode.EXPLORE, Mode.VICTORY) || state.menu?.type in listOf("shop", "achievements") || state.mode == Mode.CHALLENGE && state.challenge?.phase == ChallengePhase.RESULT) lootChoices() else emptyList())
            .firstOrNull { it.action == choice.action && it.argument == choice.argument }
            ?.let(::execute)
    }

    fun navigateBack() {
        if (state.mode == Mode.CHALLENGE) {
            when (state.challenge?.phase) {
                ChallengePhase.INTRO, ChallengePhase.RESULT -> ChallengeStages.leave(state)
                ChallengePhase.PAUSED -> ChallengeStages.resume(state)
                else -> ChallengeStages.pause(state)
            }
            controls.reset()
        } else if (state.mode == Mode.MENU) back()
        else if (state.mode == Mode.EXPLORE && state.chestLoot != null) state.chestLoot = null
        else controls.reset()
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
        if (state.mode == Mode.CHALLENGE) ChallengeStages.pause(state)
        state.trap?.let(Traps::pause)
    }

    fun tick(deltaMs: Long) {
        val s = state
        val delta = deltaMs.coerceIn(0L, 250L)
        s.playMs += delta
        if (s.dungeon != null && s.mode != Mode.RUN_RESULT) s.runElapsedMs += delta
        if (s.mode == Mode.CHALLENGE) { ChallengeStages.tick(s, delta); return }
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

    /** Reward bag entry is available only for actual equipment receipts, including worn rewards. */
    fun canOpenLootInventory(): Boolean {
        val rewards = when (state.mode) {
            Mode.VICTORY -> state.battleResult?.rewards
            Mode.EXPLORE -> state.chestLoot?.items
            else -> null
        }
        return rewards?.any { it.kind != Kind.POTION } == true
    }

    fun openLootInventory(): Boolean = canOpenLootInventory() && openDungeonPanel("inventory")

    /** Header shortcuts stop automatic walking before opening a real menu. */
    fun openDungeonPanel(type: String): Boolean {
        if (
            type !in listOf("equipment", "growth", "inventory", "help") ||
                state.dungeon == null ||
                state.mode !in listOf(Mode.EXPLORE, Mode.MOVING, Mode.FLOOR_CLEAR, Mode.VICTORY)
        )
            return false
        if (state.mode == Mode.MOVING) {
            state.mode = Mode.EXPLORE
            state.movementMs = 0L
        }
        if (type == "growth") state.levelUpNotice = null
        open(type)
        return true
    }

    fun openLevelUpGrowth(): Boolean {
        if (state.levelUpNotice == null || state.mode !in listOf(Mode.SURFACE, Mode.EXPLORE, Mode.MOVING, Mode.FLOOR_CLEAR, Mode.VICTORY)) return false
        if (state.mode == Mode.MOVING) { state.mode = Mode.EXPLORE; state.movementMs = 0 }
        state.levelUpNotice = null
        open("growth")
        return true
    }

    fun turnBack() {
        val map = state.dungeon ?: return
        if (state.mode != Mode.EXPLORE && !(state.mode == Mode.CHALLENGE && state.challenge?.phase == ChallengePhase.ACTIVE)) return
        if (state.mode == Mode.CHALLENGE && state.challenge?.autoMoving == true) return
        map.direction = (map.direction + 2) % 4
        controls.skipCurrentEvent()
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
        if (s.training && s.menu?.type == "training") {
            exitTraining()
            return
        }
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

    private fun returnToGearSelection() {
        val s = state
        val index =
            s.menuStack.indexOfLast { it.type == "equipment" }.takeIf { it >= 0 }
                ?: s.menuStack.indexOfLast { it.type == "inventory" }
        if (index < 0) {
            closeMenu()
            return
        }
        val destination = s.menuStack[index]
        s.menuStack = s.menuStack.take(index).toMutableList()
        s.menu = destination
        s.mode = Mode.MENU
        s.selection = 0
    }

    private fun execute(c: Choice) {
        val s = state
        if (s.mode == Mode.CHALLENGE && c.action != "equipLoot") {
            ChallengeStages.execute(s, c)
            return
        }
        when (c.action) {
            "challengeEnter" -> { ChallengeStages.open(s); controls.reset() }
            "retreat" -> endRun(retreated = true)
            "runContinue" -> {
                s.runResult = null
                s.lastImpact = null
                s.mode = Mode.SURFACE
                controls.reset()
            }
            "battleContinue" -> {
                val result = s.battleResult ?: return
                s.battleResult = null
                s.quickEquipIds = emptyList()
                s.lastImpact = null
                s.mode = result.returnMode
                s.selection = 0
                s.note(if (s.mode == Mode.FLOOR_CLEAR) "보스 격파 · 층 클리어" else "전투 종료 · 탐험을 계속하세요")
                if (s.training) trainingMenu()
            }
            "encounter" -> beginEncounter(Event.entries[c.argument.toInt()])
            "encounterLeave" -> leaveEncounter("탐험을 계속합니다")
            "npcDonate", "npcHeal", "npcPotion" -> {
                val result = NpcInteractions.use(s, c.action)
                if (result.success) {
                    if (c.action == "npcDonate") Achievements.event(s, "donations")
                    leaveEncounter(result.message)
                } else s.note(result.message)
            }
            "questAccept" -> {
                if (s.quest != null) return
                val offer = s.dungeon?.here()?.questOffer ?: return
                s.quest = offer.copy()
                leaveEncounter("미션 수락 · ${offer.title}")
            }
            "enter" -> enterFloor(c.argument.toInt())
            "return" -> surface(false)
            "move" -> {
                s.chestLoot = null
                s.quickEquipIds = emptyList()
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
            "bestiary",
            "achievements",
            "challengeRecords",
            "help" -> {
                if (c.action == "growth") s.levelUpNotice = null
                open(c.action)
            }
            "claimAchievement" -> Achievements.claim(s, c.argument.toInt())
            "selectTitle" -> Achievements.selectTitle(s, c.argument.toInt())
            "equipLoot" -> {
                val item = s.inventory.find { it.id == c.argument && it.id in s.quickEquipIds && it.kind != Kind.POTION && it.id !in s.equipment.values } ?: return
                val slot = Equipment.quickSlot(s, item) ?: return
                if (Equipment.equip(s, item.id, slot)) s.note("${item.name} 장착")
            }
            "item" -> {
                val slot = s.menu?.takeIf { it.type == "slotInventory" }?.argument?.toInt()?.let { Slot.entries[it] }
                if (slot != null && s.equipment[slot] == null) {
                    if (Equipment.equip(s, c.argument, slot)) s.note("장비를 변경했습니다")
                    returnToGearSelection()
                } else open("item", c.argument)
            }
            "slotItem" -> {
                val id = s.equipment[Slot.entries[c.argument.toInt()]]
                if (id != null) open("item", id) else open("slotInventory", c.argument)
            }
            "equip" -> {
                val id = s.menu?.argument ?: return
                if (Equipment.equip(s, id, Slot.entries[c.argument.toInt()])) s.note("장비를 변경했습니다")
                returnToGearSelection()
            }
            "unequip" -> {
                Equipment.unequip(s, c.argument)
                s.note("장비를 해제했습니다")
                returnToGearSelection()
            }
            "useItem" -> {
                usePotion(c.argument)
                closeMenu()
            }
            "stat" -> spendPoint(Stat.entries[c.argument.toInt()])
            "growthHeal" -> restoreWithGrowthPoint()
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
            "shop" -> { s.quickEquipIds = emptyList(); open("shop") }
            "buy" -> open("shopItem", c.argument)
            "purchase" -> purchase(c.argument, 1)
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
                    s.note("회복의 수정: HP 45% 회복")
                }
            }
            "claim" -> {
                val q = s.quest
                if (q?.complete == true) {
                    val reward =
                        (q.reward * (if (Equipment.charm(s) == Charm.QUEST) 1.25 else 1.0)).toInt()
                    s.gold += reward
                    s.quest = null
                    Achievements.event(s, "quests")
                    s.note("미션 보상 ${reward}G 획득")
                    s.dungeon?.here()?.let { if (it.event == Event.NPC) it.resolved = true }
                    leaveEncounter()
                }
            }
            "questInfo" ->
                s.note(
                    s.quest?.let { "${it.title} (${it.progress}/${it.target}) · 귀환/사망 시 종료" }
                        ?: "버튼 이름의 행동을 바로 실행 · 상단 메뉴로 가방·기록·회복약·도주 · 출구에서 귀환"
                )
            "exit" -> exitFloor()
            "attack" -> attack(false)
            "strong" -> attack(true)
            "guard" -> defend(false)
            "dodge" -> defend(true)
            "potion", "strongPotion" -> {
                if (usePotion(grade = if (c.action == "strongPotion") PotionGrade.GREATER else PotionGrade.BASIC)) {
                    s.mode = Mode.COMBAT_DEFEND
                    s.selection = 0
                }
            }
            "flee" -> flee()
            "ending" -> beginInheritance()
            "inherit" -> newCycle(c.argument)
            "back" -> back()
            "training" -> open("training")
            "trainingTraps" -> open("trainingTraps")
            "trainingChallenges" -> open("trainingChallenges")
            "trainChallenge" -> beginTraining("challenge", c.argument.toInt())
            "trainTrap" -> beginTraining("trap", c.argument.toInt())
            "trainBattle" -> beginTraining("battle")
            "trainMerchant" -> beginTraining("merchant")
            "trainNpc" -> beginTraining("npc")
            "trainBeggar" -> beginTraining("npc", npcRole = NpcRole.BEGGAR)
            "trainHealer" -> beginTraining("npc", npcRole = NpcRole.HEALER)
            "trainHerbalist" -> beginTraining("npc", npcRole = NpcRole.HERBALIST)
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

    fun purchase(itemId: Long, count: Int): Boolean {
        val s = state
        if (s.mode != Mode.MENU || s.menu?.type != "shopItem" || s.menu?.argument != itemId) return false
        val item = s.shop.find { it.id == itemId } ?: return false
        if (count !in listOf(1, 3, 5) || item.kind != Kind.POTION && count != 1) return false
        val price = Equipment.buyPrice(s, item) * count
        if (s.gold < price) { s.note("골드가 부족합니다"); return false }
        s.gold -= price
        if (item.kind == Kind.POTION) {
            repeat(count) { s.inventory.add(item.copy(id = s.nextId++)) }
            s.quickEquipIds = emptyList()
        } else { s.shop.remove(item); s.inventory.add(item); s.quickEquipIds = listOf(item.id) }
        s.note("${item.name}${if(count > 1) " ${count}개" else ""} 구매 · ${price}G")
        back()
        return true
    }

    fun enterFloor(floor: Int) {
        require(floor in 1..50)
        if (state.challenge != null) return
        val s = state
        if (s.dungeon == null) {
            s.runKills = 0
            s.runChests = 0
            s.runTraps = 0
            s.runElapsedMs = 0L
            s.runDeepestFloor = 0
        }
        s.runResult = null
        s.quickEquipIds = emptyList()
        s.chestLoot = null
        s.runDeepestFloor = max(s.runDeepestFloor, floor)
        s.floor = floor
        s.highest = max(s.highest, floor)
        mapSeed?.let { s.rng = it() }
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
        s.shop.add(ItemFactory.potion(s, PotionGrade.GREATER))
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
        s.chestLoot = null
        s.quickEquipIds = emptyList()
        s.message = "통로를 탐험하고 있습니다"
        val cell = m.here()
        if (!cell.resolved && cell.event != Event.NONE) {
            s.mode = Mode.EXPLORE
            s.selection = 0
            when (cell.event) {
                Event.MONSTER -> startBattle(false)
                Event.ELITE -> startBattle(false, elite = true)
                Event.TRAP -> {
                    s.trap = Traps.create(s, cell.trapType)
                    s.mode = Mode.TRAP
                }
                Event.CHEST,
                Event.NPC,
                Event.MERCHANT -> beginEncounter(cell.event)
                Event.EXIT -> exitFloor()
                Event.PORTAL -> { s.note("특별 도전 포털 · 살펴보거나 지나갈 수 있습니다") }
                else ->
                    s.note(
                        when (cell.event) {
                            Event.CHEST -> "아이템 상자를 발견했습니다"
                            Event.HEAL -> "회복의 수정를 발견했습니다"
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
        if (s.floor % 5 == 0 && !m.eliteCleared && m.cells.none { it.event == Event.ELITE }) {
            startBattle(false, elite = true)
            return
        }
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
        s.runChests++
        Achievements.event(s, "chests")
        s.chestLoot = null
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
        val rewards = mutableListOf(gear)
        if (extra) {
            val bonus = ItemFactory.create(s)
            s.inventory.add(bonus)
            rewards.add(bonus)
        }
        val gold = 15 + s.floor * 3 + s.nextInt(15)
        s.gold += gold
        s.quickEquipIds = rewards.filter { it.kind != Kind.POTION }.map { it.id }
        s.chestLoot = ChestLootNotice(rewards, gold)
        s.note("${gear.name} · ${gold}G${if(extra)" · 추가 아이템!" else ""}")
    }

    fun startBattle(boss: Boolean, elite: Boolean = false) {
        if (state.challenge != null) return
        val s = state
        val floor = max(1, s.floor)
        val hp = (300 + floor * 26.0) * (if (boss) 1.7 else if (elite) 1.4 else 1.0)
        val species = when {
            boss || elite -> MonsterCatalog.special(floor, boss)
            else -> MonsterCatalog.find(s.dungeon?.here()?.takeIf { it.event == Event.MONSTER }?.monsterId)
                ?.takeIf { it.band == MonsterCatalog.band(floor) }
                ?: MonsterCatalog.forFloor(floor)[s.nextInt(6)]
        }
        s.enemy =
            Enemy(
                if (elite) "${floor}층의 정예 기사" else species.name,
                hp,
                hp,
                (65 + floor * 4.0) * (if (boss) 1.2 else if (elite) 1.1 else 1.0),
                (20 + floor * 1.8) * (if (elite) 1.1 else 1.0),
                10.0 + floor * .3,
                boss,
                elite = elite,
                speciesId = species.id,
            )
        MonsterCatalog.record(s, s.enemy!!, encounter = 1)
        Achievements.evaluate(s)
        s.enemy!!.let { it.intent = CombatTactics.pattern(it).first() }
        s.encounter = null
        s.quickEquipIds = emptyList()
        s.battleId++
        s.battleResult = null
        s.lastImpact = null
        s.mode = Mode.COMBAT_ATTACK
        s.selection = 0
        s.note("${s.enemy!!.name} 조우")
    }

    fun attack(strong: Boolean) {
        val s = state
        if (s.mode != Mode.COMBAT_ATTACK) return
        val enemy = s.enemy ?: return
        val p = stats()
        val opening = enemy.opening
        enemy.opening = false
        var damage = 0.0
        val criticalRoll = s.random()
        val hit =
            s.random() <
                ((if (strong) GameBalance.model("strong_hit")
                else GameBalance.model("normal_hit")) + if (strong && opening) .20 else 0.0)
        val lucky = if (hit) Luck.roll(s, p) else AttackLuck()
        val critical = criticalRoll < Luck.criticalChance(p.crit, lucky)
        if (hit)
            damage =
                p.attack *
                    Luck.attackMultiplier(lucky) *
                    (if (opening || enemy.intent == EnemyIntent.RECOVER) 1.35 else 1.0) *
                    (if (enemy.intent == EnemyIntent.GUARD && !strong) .55 else 1.0) *
                    (if (strong) GameBalance.model("strong_damage") else 1.0) *
                    100 / (100 + enemy.defense) * (if (critical) p.critDamage else 1.0)
        val guardBreak = hit && strong && enemy.intent == EnemyIntent.GUARD
        val tactic =
            when {
                guardBreak -> "방패 파괴"
                hit && (opening || enemy.intent == EnemyIntent.RECOVER) -> "빈틈 공격"
                hit && enemy.intent == EnemyIntent.GUARD -> "몬스터 방패에 경감"
                else -> ""
            }
        val previousHp = enemy.hp
        enemy.hp = max(0.0, enemy.hp - damage)
        impact(
            ImpactTarget.ENEMY,
            previousHp - enemy.hp,
            if (damage == 0.0) ImpactOutcome.MISS else ImpactOutcome.DAMAGE,
            critical && damage > 0,
            lucky.attackBoost,
            lucky.criticalBoost,
            tactic,
        )
        s.note(
            if (damage == 0.0) "공격이 빗나갔습니다"
            else
                "${if(tactic.isNotEmpty()) "$tactic · " else ""}${if(lucky.attackBoost) "행운 공격 강화 · " else ""}${if(lucky.criticalBoost) "행운 치명 확률 강화 · " else ""}${if(critical)"치명타! " else ""}${damage.roundToInt()} 피해"
        )
        if (enemy.hp <= 0) {
            victory()
            return
        }
        if (guardBreak || enemy.intent == EnemyIntent.RECOVER) {
            enemy.opening = guardBreak
            CombatTactics.advance(enemy)
            s.mode = Mode.COMBAT_ATTACK
            s.note(
                if (guardBreak) "방패 파괴! 다음 공격 강화"
                else if (hit) "빈틈 공격! ${damage.roundToInt()} 피해 · 반격 없음" else "공격이 빗나갔습니다 · 몬스터는 휴식 중"
            )
        } else s.mode = Mode.COMBAT_DEFEND
        s.selection = 0
    }

    fun defend(dodge: Boolean) {
        val s = state
        if (s.mode != Mode.COMBAT_DEFEND) return
        val e = s.enemy ?: return
        val p = stats()
        if (e.intent == EnemyIntent.RECOVER) {
            CombatTactics.advance(e)
            s.mode = Mode.COMBAT_ATTACK
            s.selection = 0
            s.note("몬스터는 숨을 고릅니다 · 공격 기회")
            return
        }
        val chance =
            (GameBalance.model("dodge_base") +
                    (p.agility - e.precision) * GameBalance.model("dodge_per_agility_gap") +
                    when (e.intent) {
                        EnemyIntent.HEAVY -> .25
                        EnemyIntent.FLURRY -> -.15
                        else -> 0.0
                    })
                .coerceIn(
                    GameBalance.model("dodge_min"),
                    if (e.intent == EnemyIntent.HEAVY) .80 else GameBalance.model("dodge_max"),
                )
        val shield = Equipment.equipped(s).any { it.kind == Kind.SHIELD }
        val critical = s.random() < .10
        val incoming = e.attack * CombatTactics.incomingMultiplier(e.intent)
        var damage = Defense.damageAfterArmor(incoming, p.defense) * (if (critical) 1.5 else 1.0)
        if (dodge) {
            if (s.random() < chance) damage = 0.0
        } else
            damage *=
                1.0 -
                    (if (e.intent == EnemyIntent.FLURRY) .60
                    else GameBalance.model("guard_reduction")) -
                    (if (shield) GameBalance.shield("guard_bonus") else 0.0)
        val fullGuard = !dodge && s.random() < Defense.zeroDamageChance(p, incoming)
        if (fullGuard) damage = 0.0
        val createsOpening =
            (dodge && damage == 0.0 && e.intent == EnemyIntent.HEAVY) ||
                (!dodge && e.intent == EnemyIntent.FLURRY)
        val tactic = if (createsOpening) "빈틈 발생 · 다음 공격 강화" else ""
        val previousHp = s.hp
        s.hp = max(0.0, s.hp - damage)
        impact(
            ImpactTarget.PLAYER,
            previousHp - s.hp,
            if (fullGuard) ImpactOutcome.BLOCK
            else if (damage == 0.0) ImpactOutcome.DODGE
            else if (!dodge) ImpactOutcome.GUARD else ImpactOutcome.DAMAGE,
            critical && damage > 0,
            tactic = tactic,
        )
        s.note(
            (if (fullGuard) "완전 방어! 피해를 받지 않았습니다"
            else if (damage == 0.0) "회피 성공!"
            else "${if(dodge)"회피 실패 · " else "방어 · "}${damage.roundToInt()} 피해") +
                if (createsOpening) " · $tactic" else ""
        )
        if (s.hp <= 0) {
            die()
            return
        }
        e.opening = createsOpening
        CombatTactics.advance(e)
        s.mode = Mode.COMBAT_ATTACK
        s.selection = 0
    }

    private fun impact(
        target: ImpactTarget,
        damage: Double,
        outcome: ImpactOutcome,
        critical: Boolean = false,
        luckyAttack: Boolean = false,
        luckyCritical: Boolean = false,
        tactic: String = "",
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
                luckyAttack,
                luckyCritical,
                tactic,
            )
    }

    private fun victory() {
        val s = state
        val enemy = s.enemy ?: return
        MonsterCatalog.record(s, enemy, defeat = 1)
        Achievements.victory(s, enemy)
        val gold = ((20 + s.floor * 4) * (if (enemy.elite) 1.5 else 1.0)).toInt()
        val experience = ((25 + s.floor * 5) * (if (enemy.elite) 1.5 else 1.0)).toInt()
        val items = mutableListOf<String>()
        val rewards = mutableListOf<Gear>()
        s.runKills++
        s.gold += gold
        gainExperience(experience)
        s.quest?.takeIf { it.kind == "kill" }?.let { it.progress++ }
        if (enemy.boss) {
            s.devices.add(s.floor)
            s.dungeon?.bossCleared = true
            val reward = ItemFactory.bossWeapon(s)
            s.inventory.add(reward)
            items.add(reward.name)
            rewards.add(reward)
        } else {
            if (enemy.elite) s.dungeon?.eliteCleared = true
            s.dungeon?.here()?.let { if (it.event != Event.EXIT) it.resolved = true }
            if (enemy.elite || s.random() < (.25 + stats().luck * .003).coerceAtMost(.65)) {
                val reward = if (enemy.elite) ItemFactory.create(s, Kind.entries.filter { it != Kind.POTION }[s.nextInt(Kind.entries.size - 1)]) else ItemFactory.create(s)
                s.inventory.add(reward)
                items.add(reward.name)
                rewards.add(reward)
            }
        }
        val returnMode =
            if (enemy.boss || enemy.elite && s.dungeon?.here()?.event == Event.EXIT && s.floor % 10 != 0) Mode.FLOOR_CLEAR
            else if (s.dungeon == null) Mode.SURFACE else Mode.EXPLORE
        s.quickEquipIds = rewards.filter { it.kind != Kind.POTION }.map { it.id }
        s.battleResult =
            BattleResult(enemy.copy(hp = 0.0), gold, experience, items, returnMode, rewards)
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
        val before = s.level
        s.experience += amount
        while (s.experience >= s.experienceRequired) {
            s.experience -= s.experienceRequired
            s.level++
            s.points += 3
        }
        if (s.level > before) {
            val pending = s.levelUpNotice
            s.levelUpNotice =
                LevelUpNotice(
                    pending?.from ?: before,
                    s.level,
                    (pending?.gainedPoints ?: 0) + (s.level - before) * 3,
                )
        }
    }

    fun spendPoint(stat: Stat) {
        val s = state
        if (s.points <= 0) {
            s.note("배분할 성장 포인트가 없습니다")
            return
        }
        val amount = Growth.amount(stat)
        s.base = s.base.add(stat, amount)
        s.points--
        if (stat == Stat.HP) s.hp = min(stats().hp, s.hp + amount)
        s.note("${stat.label} 성장 · 남은 포인트 ${s.points}")
    }

    fun restoreWithGrowthPoint(): Boolean {
        val s = state
        if (s.mode != Mode.MENU || s.menu?.type != "growth" || s.points <= 0 || s.hp <= 0 || s.hp >= stats().hp) return false
        s.points--
        s.hp = stats().hp
        s.note("체력 모두 회복 · 성장 포인트 1P 사용 · 남은 ${s.points}P")
        return true
    }

    private fun usePotion(id: Long? = null, grade: PotionGrade? = null): Boolean {
        val s = state
        val item = s.inventory.find { it.kind == Kind.POTION && (id == null || it.id == id) &&
            (grade == null || it.potionGrade == grade) }
        if (item == null) {
            s.note("${grade?.label ?: "회복약"}이 없습니다")
            return false
        }
        val maximum = stats().hp
        if (s.hp <= 0) {
            s.note("지금은 회복약을 사용할 수 없습니다")
            return false
        }
        s.inventory.remove(item)
        s.hp = min(maximum, s.hp + maximum * item.potionGrade.healRatio)
        s.note("${item.name}: HP ${(item.potionGrade.healRatio * 100).toInt()}% 회복")
        return true
    }

    private fun applyTrapResult() {
        val s = state
        val t = s.trap ?: return
        if (t.phase != TrapPhase.RESULT || t.applied) return
        t.applied = true
        if (t.success) {
            s.runTraps++
            Achievements.event(s, "traps")
        }
        s.dungeon?.here()?.let { if (it.event == Event.TRAP) it.resolved = true }
        if (!t.success) {
            val damage = stats().hp * .15 + s.floor * 2
            val previousHp = s.hp
            s.hp = max(0.0, s.hp - damage)
            impact(ImpactTarget.PLAYER, previousHp - s.hp, ImpactOutcome.DAMAGE)
            s.note("${if (t.type == 4 && t.action == 1 && t.actionAtMs < t.beamCrouchStartMs) "너무 일찍 숙였습니다 · " else ""}함정 회피 실패 · ${damage.roundToInt()} 피해")
            if (s.hp <= 0) die()
        } else s.note("함정 회피 성공!")
    }

    private fun finishTrap() {
        state.trap = null
        state.mode = if (state.dungeon == null) Mode.SURFACE else Mode.EXPLORE
        state.selection = 0
        if (state.training) trainingMenu()
    }

    fun die() {
        if (state.challenge != null) {
            if (state.challenge?.phase == ChallengePhase.INTRO) ChallengeStages.leave(state)
            else ChallengeStages.finish(state, false, "도전이 중단되었습니다")
            return
        }
        if (state.mode == Mode.RUN_RESULT) return
        state.enemy?.let { MonsterCatalog.record(state, it, death = 1) }
        state.chestLoot = null
        endRun(retreated = false)
    }

    private fun endRun(retreated: Boolean) {
        val s = state
        if (s.mode == Mode.RUN_RESULT) return
        val result =
            RunResult(
                retreated,
                s.floor,
                max(s.floor, s.runDeepestFloor),
                s.runKills,
                s.runChests,
                s.runTraps,
                s.runElapsedMs,
                s.gold,
                s.inventory.size,
            )
        surface(true)
        s.runResult = result
        s.mode = Mode.RUN_RESULT
        s.note(
            if (retreated) "탐험 포기 · 사망과 동일하게 장비·아이템·소지 골드를 잃었습니다" else "사망했습니다 · 이번 탐험 기록을 확인하세요"
        )
        controls.reset()
    }

    private fun surface(dead: Boolean) {
        val s = state
        s.runResult = null
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
        s.shop.add(ItemFactory.potion(s, PotionGrade.GREATER))
        s.note(if (dead) "사망 · 지상 부활. 장비·아이템·소지 골드를 잃었습니다" else "지상 귀환 · 아이템과 골드를 안전하게 가져왔습니다")
    }

    fun beginInheritance() {
        if (state.challenge != null) return
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
        if (state.challenge != null) return
        val s = state
        val chosen =
            s.inventory.find { it.id == id && !it.inherited && it.kind != Kind.POTION } ?: return
        chosen.inherited = true
        s.inventory.removeAll { !it.inherited }
        s.equipment.clear()
        s.cycle++
        s.visitorSeed = null
        s.npcServicesUsed.clear()
        s.challengeProgress.seed = null
        s.challengeProgress.usedFloors.clear()
        s.challenge = null
        s.clears++
        s.level = 1
        s.chestLoot = null
        s.levelUpNotice = null
        s.experience = 0
        s.points = 0
        s.base = Stats()
        s.hp = s.base.hp
        s.gold = 0
        s.bank = 0
        s.runResult = null
        s.runDeepestFloor = 0
        s.runKills = 0
        s.runChests = 0
        s.runTraps = 0
        s.runElapsedMs = 0L
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
        s.shop.add(ItemFactory.potion(s, PotionGrade.BASIC))
        s.shop.add(ItemFactory.potion(s, PotionGrade.GREATER))
        Achievements.evaluate(s)
        s.note("${s.cycle}회차 시작 · 계승 장비 ${s.inventory.size}개 · 능력치/돈/장치 초기화")
    }
}
