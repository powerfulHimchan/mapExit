package com.powerfulhimchan.mapexit.core

import kotlin.math.min

/** Isolated maps, clocks and reward receipts; the host expedition is restored intact. */
object ChallengeStages {
    val runeNames = listOf("해", "달", "별", "산", "물", "꽃")
    const val BRAZIER = 10
    const val BLADE = 20
    const val FALLING = 21
    const val WALL = 22
    const val MOVE_MS = 600L

    private fun stream(seed: Long, salt: Long): State {
        var v = seed xor salt
        v = (v xor (v ushr 30)) * -4658895280553007687L
        v = (v xor (v ushr 27)) * -7723592293110705685L
        return State(bootstrapped = true, rng = v xor (v ushr 31))
    }

    fun floors(seed: Long): List<Int> = listOf(11, 26, 41).mapIndexed { i, first ->
        first + stream(seed, 0x504F5254414CL + i).nextInt(4)
    }

    fun kindFor(seed: Long, floor: Int): ChallengeKind =
        ChallengeKind.entries[stream(seed, floor * 104729L + 0x4B494E44L).nextInt(ChallengeKind.entries.size)]

    fun placePortal(s: State, map: Dungeon, floor: Int = s.floor) {
        if (s.challenge != null || floor !in 1..50) return
        val seed = s.challengeProgress.seed ?: (s.visitorSeed ?: s.rng).also { s.challengeProgress.seed = it }
        if (floor !in floors(seed)) return
        map.cells.firstOrNull { it.event == Event.PORTAL }?.let {
            if (floor in s.challengeProgress.usedFloors) it.resolved = true
            return
        }
        val spaces = map.cells.indices.filter {
            val c = map.cells[it]
            !c.wall && c.event == Event.NONE && it != map.y * map.width + map.x
        }
        if (spaces.isEmpty()) return
        val cell = map.cells[spaces[stream(seed, floor * 8191L).nextInt(spaces.size)]]
        cell.event = Event.PORTAL
        cell.resolved = floor in s.challengeProgress.usedFloors
    }

    private fun maze(seed: Long, size: Int): Dungeon {
        val r = stream(seed, 0x4D415A45L)
        val m = Dungeon(size, size, MutableList(size * size) { Cell() }, direction = 1)
        fun carve(x: Int, y: Int) {
            m.cell(x, y).wall = false
            val dirs = mutableListOf(0, 1, 2, 3)
            for (i in 3 downTo 1) {
                val j = r.nextInt(i + 1); val old = dirs[i]; dirs[i] = dirs[j]; dirs[j] = old
            }
            for (d in dirs) {
                val nx = x + MapGenerator.dx[d] * 2; val ny = y + MapGenerator.dy[d] * 2
                if (nx in 1 until size - 1 && ny in 1 until size - 1 && m.cell(nx, ny).wall) {
                    m.cell(x + MapGenerator.dx[d], y + MapGenerator.dy[d]).wall = false
                    carve(nx, ny)
                }
            }
        }
        carve(1, 1)
        val dist = MapGenerator.distances(m, 1, 1)
        m.cells[dist.indices.maxBy { dist[it] }].event = Event.EXIT
        m.direction = MapGenerator.directions(m).first()
        m.reveal()
        return m
    }

    /** Current position to target, including both endpoints. Also used for clue validation. */
    fun path(map: Dungeon, target: Int = map.cells.indexOfFirst { it.event == Event.EXIT }): List<Int> {
        if (target !in map.cells.indices || map.cells[target].wall) return emptyList()
        val start = map.y * map.width + map.x
        val previous = IntArray(map.cells.size) { -1 }; previous[start] = start
        val queue = ArrayDeque<Int>(); queue.add(start)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (i == target) break
            for (d in 0..3) {
                val x = i % map.width + MapGenerator.dx[d]; val y = i / map.width + MapGenerator.dy[d]
                val n = y * map.width + x
                if (x in 0 until map.width && y in 0 until map.height && !map.cell(x, y).wall && previous[n] < 0) {
                    previous[n] = i; queue.add(n)
                }
            }
        }
        if (previous[target] < 0) return emptyList()
        val out = mutableListOf(target)
        while (out.last() != start) out.add(previous[out.last()])
        return out.reversed()
    }

    fun open(s: State, practice: ChallengeKind? = null): Boolean {
        val host = s.dungeon ?: return false
        if (s.challenge != null || s.mode != Mode.EXPLORE || host.here().event != Event.PORTAL || host.here().resolved) return false
        if (practice != null && !s.training && !s.debug) return false
        val seed = s.challengeProgress.seed ?: (s.visitorSeed ?: s.rng).also { s.challengeProgress.seed = it }
        if (practice == null && (s.floor !in floors(seed) || s.floor in s.challengeProgress.usedFloors)) return false
        val kind = practice ?: kindFor(seed, s.floor)
        val runSeed = stream(seed, s.floor * 65537L + kind.ordinal).rng
        val map = maze(runSeed, if (kind in listOf(ChallengeKind.MEMORY, ChallengeKind.STEPS, ChallengeKind.HAZARDS)) 9 else 11)
        val route = path(map)
        val order = mutableListOf(1, 2, 3)
        val r = stream(runSeed, 0x5345414CL)
        for (i in 2 downTo 1) { val j = r.nextInt(i + 1); val v = order[i]; order[i] = order[j]; order[j] = v }
        val positions = listOf(route.size / 4, route.size / 2, route.size * 3 / 4).map { route[it.coerceIn(1, route.lastIndex - 1)] }
        when (kind) {
            ChallengeKind.SEALS -> positions.forEachIndexed { i, index ->
                map.cells[index].event = Event.CHALLENGE; map.cells[index].challengeMark = i + 1
            }
            ChallengeKind.TORCH -> route.drop(1).dropLast(1).filterIndexed { i, _ -> i % 7 == 5 }.forEach {
                map.cells[it].event = Event.CHALLENGE; map.cells[it].challengeMark = BRAZIER
            }
            ChallengeKind.HAZARDS -> positions.forEachIndexed { i, index ->
                map.cells[index].event = Event.CHALLENGE; map.cells[index].challengeMark = BLADE + i
            }
            else -> Unit
        }
        s.challenge = ChallengeRun(kind, runSeed, host, s.floor, route.size - 1 + 4, sealOrder = order)
        s.dungeon = map; s.mode = Mode.CHALLENGE; s.enemy = null; s.trap = null
        s.battleResult = null; s.chestLoot = null; s.quickEquipIds = emptyList(); s.encounter = null
        s.note("특별 도전 · ${kind.title}")
        return true
    }

    fun start(s: State) {
        val c = s.challenge ?: return
        if (c.phase != ChallengePhase.INTRO) return
        c.phase = if (c.kind == ChallengeKind.MEMORY) ChallengePhase.PREVIEW else ChallengePhase.ACTIVE
        c.hostDungeon.here().resolved = true
        if (!s.training && !s.debug) {
            s.challengeProgress.usedFloors.add(c.hostFloor)
            s.challengeProgress.records.getOrPut(c.kind) { ChallengeRecord() }.attempts++
        }
    }

    fun choices(s: State): List<Choice> {
        val c = s.challenge ?: return emptyList()
        fun choice(label: String, action: String, arg: Long = 0L) = Choice(label, action, arg)
        return when (c.phase) {
            ChallengePhase.INTRO -> listOf(choice("도전 시작", "challengeStart"), choice("던전으로 돌아가기", "challengeLeave"))
            ChallengePhase.PREVIEW -> listOf(choice("기억 완료 · 출발", "challengeRemember"), choice("일시정지", "challengePause"))
            ChallengePhase.PAUSED -> listOf(choice("도전 계속", "challengeResume"), choice("도전 포기", "challengeGiveUp"))
            ChallengePhase.RESULT -> listOf(choice("던전으로 복귀", "challengeLeave"))
            ChallengePhase.HAZARD -> buildList {
                add(choice("일시정지", "challengePause"))
                add(choice("대기 · 0.5초", "challengeWait"))
                if (c.hazardCooldownMs <= 0) add(choice("통과", "challengeCross"))
            }
            ChallengePhase.ACTIVE -> buildList {
                add(choice("일시정지", "challengePause"))
                if (c.autoMoving) {
                    add(choice("멈추기", "challengeStop")); return@buildList
                }
                val map = s.dungeon ?: return@buildList
                if (map.here().event == Event.CHALLENGE && !map.here().resolved && c.kind in listOf(ChallengeKind.SEALS, ChallengeKind.TORCH))
                    add(choice(if (c.kind == ChallengeKind.SEALS) "${runeNames[map.here().challengeMark - 1]} 제단 작동" else "횃불 보충", "challengeUse"))
                MapGenerator.directions(map).forEach { d ->
                    add(choice(listOf("앞으로", "오른쪽 길", "뒤로", "왼쪽 길")[(d - map.direction + 4) % 4], "move", d.toLong()))
                }
            }
        }
    }

    fun execute(s: State, choice: Choice) {
        when (choice.action) {
            "move" -> move(s, choice.argument.toInt())
            "challengeStop" -> stop(s)
            "challengeStart" -> start(s)
            "challengeRemember" -> s.challenge?.takeIf { it.phase == ChallengePhase.PREVIEW }?.let { it.previewMs = 0; it.phase = ChallengePhase.ACTIVE }
            "challengeLeave" -> leave(s)
            "challengeGiveUp" -> finish(s, false, "도전을 포기했습니다")
            "challengeResume" -> resume(s)
            "challengePause" -> pause(s)
            "challengeUse" -> interact(s)
            "challengeWait" -> tick(s, 500)
            "challengeCross" -> cross(s)
        }
    }

    fun pause(s: State): Boolean {
        val c = s.challenge ?: return false
        if (c.phase !in listOf(ChallengePhase.ACTIVE, ChallengePhase.HAZARD, ChallengePhase.PREVIEW)) return false
        stop(s)
        c.pausedFrom = c.phase; c.phase = ChallengePhase.PAUSED
        return true
    }

    fun resume(s: State) {
        val c = s.challenge ?: return
        if (c.phase == ChallengePhase.PAUSED) c.phase = c.pausedFrom
    }

    fun stop(s: State) {
        s.challenge?.let { it.autoMoving = false; it.movementMs = 0 }
    }

    fun move(s: State, direction: Int) {
        val c = s.challenge ?: return
        val map = s.dungeon ?: return
        if (c.phase != ChallengePhase.ACTIVE || c.autoMoving || direction !in MapGenerator.directions(map)) return
        c.autoMoving = true; c.movementMs = 0
        moveOne(s, direction)
    }

    private fun moveOne(s: State, direction: Int) {
        val c = s.challenge ?: return
        val map = s.dungeon ?: return
        if (c.phase != ChallengePhase.ACTIVE || direction !in MapGenerator.directions(map)) return
        if (c.kind == ChallengeKind.STEPS && c.remainingSteps == 0) { finish(s, false, "발걸음을 모두 사용했습니다"); return }
        if (c.kind.seconds > 0 && c.remainingMs == 0L) { finish(s, false, "제한 시간이 끝났습니다"); return }
        val old = map.y * map.width + map.x
        if (c.kind == ChallengeKind.COLLAPSE) c.collapseAt.putIfAbsent(old, c.elapsedMs + 3000)
        map.x += MapGenerator.dx[direction]; map.y += MapGenerator.dy[direction]
        map.direction = direction; map.arrivalDirection = direction; map.reveal(); c.steps++
        c.feedback = ""
        val cell = map.here()
        if (cell.event == Event.EXIT) {
            if (c.kind != ChallengeKind.SEALS || c.seals == 3) {
                finish(s, true, "출구에 도달했습니다"); return
            }
            c.feedback = "출구가 봉인되어 있습니다 · 제단 ${c.seals}/3"
        }
        if (c.kind == ChallengeKind.STEPS && c.remainingSteps == 0) { finish(s, false, "발걸음을 모두 사용했습니다"); return }
        if (c.kind == ChallengeKind.HAZARDS && cell.event == Event.CHALLENGE && !cell.resolved) {
            stop(s)
            c.phase = ChallengePhase.HAZARD; c.hazardMs = 0; c.hazardCooldownMs = 0
            return
        }
        // Stop at decisions and interactions, including a still-sealed exit.
        if (cell.event == Event.EXIT || cell.event == Event.CHALLENGE && !cell.resolved) { stop(s); return }
        val next = MapGenerator.directions(map).filter { it != (direction + 2) % 4 }
        if (next.size == 1) map.direction = next.single() else stop(s)
    }

    fun interact(s: State) {
        val c = s.challenge ?: return
        val cell = s.dungeon?.here() ?: return
        if (c.phase != ChallengePhase.ACTIVE || cell.event != Event.CHALLENGE || cell.resolved) return
        when (c.kind) {
            ChallengeKind.TORCH -> {
                c.torchMs = 22000; cell.resolved = true; c.feedback = "횃불을 보충했습니다"
            }
            ChallengeKind.SEALS -> {
                if (cell.challengeMark == c.sealOrder.getOrNull(c.seals)) {
                    c.seals++; cell.resolved = true
                    c.feedback = if (c.seals == 3) "봉인이 풀렸습니다 · 출구로 가세요" else "제단 작동 ${c.seals}/3"
                } else {
                    c.elapsedMs += 10000; c.feedback = "순서가 다릅니다 · 10초 차감"
                    if (c.remainingMs == 0L) finish(s, false, "제한 시간이 끝났습니다")
                }
            }
            else -> Unit
        }
    }

    fun hazardSafe(mark: Int, ms: Long): Boolean {
        val phase = ms % 4000
        return when (mark) {
            BLADE -> phase in 1400..2600
            FALLING -> phase in 1800..3000
            WALL -> phase in 0..900 || phase in 3100..3999
            else -> false
        }
    }

    fun cross(s: State) {
        val c = s.challenge ?: return
        val cell = s.dungeon?.here() ?: return
        if (c.phase != ChallengePhase.HAZARD || c.hazardCooldownMs > 0) return
        if (hazardSafe(cell.challengeMark, c.hazardMs)) {
            cell.resolved = true; c.phase = ChallengePhase.ACTIVE; c.feedback = "장애물 통과 성공"
        } else {
            c.mistakes++; c.elapsedMs += 8000; c.hazardMs = 0; c.hazardCooldownMs = 700
            c.feedback = "통과 실패 · 실수 ${c.mistakes}/3 · 8초 차감"
            if (c.mistakes >= 3 || c.remainingMs == 0L) finish(s, false, if (c.mistakes >= 3) "장애물 통과에 세 번 실패했습니다" else "제한 시간이 끝났습니다")
        }
    }

    fun tick(s: State, delta: Long) {
        val c = s.challenge ?: return
        val dt = delta.coerceIn(0, 500)
        if (c.phase == ChallengePhase.PREVIEW) {
            c.previewMs = (c.previewMs - dt).coerceAtLeast(0)
            if (c.previewMs == 0L) c.phase = ChallengePhase.ACTIVE
            return
        }
        if (c.phase !in listOf(ChallengePhase.ACTIVE, ChallengePhase.HAZARD)) return
        c.elapsedMs += dt
        if (c.kind.seconds > 0 && c.remainingMs == 0L) { finish(s, false, "제한 시간이 끝났습니다"); return }
        if (c.phase == ChallengePhase.HAZARD) {
            c.hazardMs += dt; c.hazardCooldownMs = (c.hazardCooldownMs - dt).coerceAtLeast(0)
        }
        if (c.kind == ChallengeKind.TORCH) {
            c.torchMs = (c.torchMs - dt).coerceAtLeast(0)
            if (c.torchMs == 0L) { finish(s, false, "횃불이 꺼졌습니다"); return }
        }
        if (c.kind == ChallengeKind.COLLAPSE) {
            val map = s.dungeon ?: return
            val current = map.y * map.width + map.x
            for ((cell, deadline) in c.collapseAt.toMap()) if (c.elapsedMs >= deadline) {
                c.collapsedCells.add(cell)
                if (cell == current) { finish(s, false, "발밑의 바닥이 무너졌습니다"); return }
                map.cells[cell].wall = true; c.collapseAt.remove(cell); c.navigationRevision++
            }
            if (path(map).isEmpty()) { finish(s, false, "출구로 이어지는 통로가 무너졌습니다"); return }
        }
        if (c.phase == ChallengePhase.ACTIVE && c.autoMoving) {
            c.movementMs += dt
            if (c.movementMs >= MOVE_MS) {
                c.movementMs -= MOVE_MS
                val map = s.dungeon ?: return
                if (map.direction in MapGenerator.directions(map)) moveOne(s, map.direction) else stop(s)
            }
        }
    }

    fun finish(s: State, success: Boolean, message: String) {
        val c = s.challenge ?: return
        if (c.phase in listOf(ChallengePhase.INTRO, ChallengePhase.RESULT)) return
        stop(s)
        c.phase = ChallengePhase.RESULT; c.success = success; c.result = message
        if (success && !c.rewardApplied) {
            c.rewardApplied = true
            if (!s.training && !s.debug) {
                val record = s.challengeProgress.records.getOrPut(c.kind) { ChallengeRecord() }
                record.clears++
                record.bestTimeMs = min(record.bestTimeMs ?: Long.MAX_VALUE, c.elapsedMs)
                record.bestSteps = minOf(record.bestSteps ?: Int.MAX_VALUE, c.steps)
                c.rewardGold = 60 + c.hostFloor * 4
                val rewards = mutableListOf<Gear>()
                if (c.kind in listOf(ChallengeKind.COLLAPSE, ChallengeKind.HAZARDS, ChallengeKind.STEPS)) {
                    val r = stream(c.seed, 0x524557415244L)
                    val v = WeaponCatalog.all[r.nextInt(WeaponCatalog.all.size)]
                    val g = ItemFactory.create(r, v.kind, (c.hostFloor + 10).coerceAtMost(60), v.id)
                    rewards.add(g.copy(id = s.nextId++, name = "도전자의 ${g.name}"))
                }
                repeat(if (rewards.isEmpty()) 2 else 1) { rewards.add(ItemFactory.potion(s, PotionGrade.GREATER)) }
                c.rewards = rewards; s.gold += c.rewardGold; s.inventory.addAll(rewards)
                s.quickEquipIds = rewards.filter { it.kind != Kind.POTION }.map { it.id }
            }
        }
        s.note("${if (success) "도전 성공" else "도전 실패"} · ${c.kind.title} · $message")
    }

    fun leave(s: State) {
        val c = s.challenge ?: return
        if (c.phase !in listOf(ChallengePhase.INTRO, ChallengePhase.RESULT)) return
        s.dungeon = c.hostDungeon; s.floor = c.hostFloor; s.mode = Mode.EXPLORE
        s.challenge = null; s.quickEquipIds = emptyList(); s.selection = 0
        s.note(if (c.phase == ChallengePhase.INTRO) "도전을 지나갔습니다 · 나중에 다시 입장할 수 있습니다" else "특별 도전 종료 · 원래 던전으로 복귀했습니다")
    }

    fun runeSymbol(index: Int): Int = (index * 7 + index / 11).mod(runeNames.size)
    fun runeNumber(index: Int): Int = index.mod(17) + 1
    fun rune(index: Int): String = "${runeNames[runeSymbol(index)]}-${runeNumber(index)}"

    /** A mark belongs to the open cell touching this wall face, never to its back side. */
    fun wallRuneIndex(map: Dungeon, wallX: Int, wallY: Int, fromX: Int, fromY: Int): Int? {
        if (fromX !in 0 until map.width || fromY !in 0 until map.height ||
            kotlin.math.abs(wallX - fromX) + kotlin.math.abs(wallY - fromY) != 1 ||
            !map.cell(wallX, wallY).wall || map.cell(fromX, fromY).wall) return null
        val openings = (0..3).filter { !map.cell(fromX + MapGenerator.dx[it], fromY + MapGenerator.dy[it]).wall }
        val face = when {
            openings.size == 1 -> (openings.single() + 2) % 4
            openings.size == 2 && (openings[0] + 2) % 4 != openings[1] -> (0..3).first { it !in openings }
            else -> return null
        }
        if (wallX != fromX + MapGenerator.dx[face] || wallY != fromY + MapGenerator.dy[face]) return null
        return fromY * map.width + fromX
    }

    fun clue(s: State): String {
        val c = s.challenge ?: return ""
        val m = s.dungeon ?: return ""
        val here = m.y * m.width + m.x
        return when {
            c.feedback.isNotBlank() -> c.feedback
            c.kind == ChallengeKind.COLLAPSE -> path(m).getOrNull(1)?.let { "안정된 문양: ${rune(it)} · 지나간 바닥은 3초 뒤 붕괴" } ?: "출구를 찾으세요"
            c.kind == ChallengeKind.SEALS && here == m.width + 1 -> "입구 문양: ${c.sealOrder.joinToString(" → ") { runeNames[it - 1] }}"
            c.kind == ChallengeKind.SEALS && m.here().event == Event.CHALLENGE && !m.here().resolved -> "${runeNames[m.here().challengeMark - 1]} 제단 · 순서를 기억하세요"
            c.kind == ChallengeKind.TORCH && m.here().event == Event.CHALLENGE && !m.here().resolved -> "불이 남은 화로입니다 · 횃불을 보충하세요"
            else -> "벽의 그림과 번호를 기억하세요 · ${listOf("북", "동", "남", "서")[m.direction]}쪽을 보는 중"
        }
    }

    fun featureSprite(cell: Cell): String = when (cell.challengeMark) {
        in 1..3 -> "challenge_altar_${cell.challengeMark}"
        BRAZIER -> "challenge_brazier"
        BLADE -> "challenge_blades"
        FALLING -> "challenge_falling"
        else -> "challenge_wall"
    }
}
