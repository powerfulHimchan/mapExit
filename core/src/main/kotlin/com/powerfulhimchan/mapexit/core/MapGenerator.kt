package com.powerfulhimchan.mapexit.core

import kotlin.math.abs

object MapGenerator {
    val dx = intArrayOf(0, 1, 0, -1)
    val dy = intArrayOf(-1, 0, 1, 0)

    fun generate(s: State, floor: Int, size: Int = 15): Dungeon {
        val n = if (size % 2 == 0) size + 1 else size
        require(n >= 9 && n <= 31)
        // Persist one schedule for the cycle; revisiting or changing map seeds cannot reroll
        // visitors.
        val visitorSeed = s.visitorSeed ?: s.rng.also { s.visitorSeed = it }
        val map = Dungeon(n, n, MutableList(n * n) { Cell() })
        fun carve(x: Int, y: Int) {
            map.cell(x, y).wall = false
            val dirs = mutableListOf(0, 1, 2, 3)
            for (i in 3 downTo 1) {
                val j = s.nextInt(i + 1)
                val v = dirs[i]
                dirs[i] = dirs[j]
                dirs[j] = v
            }
            for (d in dirs) {
                val xx = x + dx[d] * 2
                val yy = y + dy[d] * 2
                if (xx in 1 until n - 1 && yy in 1 until n - 1 && map.cell(xx, yy).wall) {
                    map.cell(x + dx[d], y + dy[d]).wall = false
                    carve(xx, yy)
                }
            }
        }
        carve(1, 1)
        val distance = distances(map, 1, 1)
        val exit = distance.indices.maxBy { distance[it] }
        map.cells[exit].event = Event.EXIT
        val elite = if (floor % 5 == 0) {
            val x = exit % n; val y = exit / n
            (0..3).map { d -> (y + dy[d]) * n + x + dx[d] }
                .first { it in map.cells.indices && !map.cells[it].wall && distance[it] == distance[exit] - 1 }
                .also { map.cells[it].event = Event.ELITE }
        } else -1
        val spaces =
            map.cells.indices
                .filter { !map.cells[it].wall && it != n + 1 && it != exit && it != elite }
                .toMutableList()
        fun place(event: Event, count: Int) {
            repeat(count) {
                if (spaces.isNotEmpty()) {
                    val i = spaces.removeAt(s.nextInt(spaces.size))
                    map.cells[i].event = event
                    if (event == Event.TRAP) map.cells[i].trapType = s.nextInt(5)
                }
            }
        }
        place(Event.MONSTER, 7 + floor / 15)
        place(Event.CHEST, 2)
        place(Event.HEAL, 2)
        place(
            Event.MERCHANT,
            if (floor in VisitorSchedule.floors(visitorSeed, Event.MERCHANT)) 1 else 0,
        )
        place(Event.NPC, if (floor in VisitorSchedule.floors(visitorSeed, Event.NPC)) 1 else 0)
        place(Event.TRAP, 3)
        val species = MonsterCatalog.forFloor(floor)
        map.cells.forEachIndexed { index, cell ->
            if (cell.event == Event.MONSTER) cell.monsterId = species[(index + floor) % species.size].id
            if (cell.event == Event.NPC) {
                cell.npcRole = NpcInteractions.roleFor(visitorSeed, floor)
                if (cell.npcRole == NpcRole.BEGGAR) cell.npcGift = NpcInteractions.giftFor(visitorSeed, floor)
                if (cell.npcRole != NpcRole.QUEST && floor in s.npcServicesUsed) cell.resolved = true
            }
        }
        map.reveal()
        return map
    }

    fun distances(map: Dungeon, x: Int, y: Int): IntArray {
        val dist = IntArray(map.cells.size) { -1 }
        val queue = ArrayDeque<Int>()
        val first = y * map.width + x
        if (map.cell(x, y).wall) return dist
        dist[first] = 0
        queue.add(first)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val xx = i % map.width
            val yy = i / map.width
            for (d in 0..3) {
                val nx = xx + dx[d]
                val ny = yy + dy[d]
                val j = ny * map.width + nx
                if (
                    nx in 0 until map.width &&
                        ny in 0 until map.height &&
                        !map.cell(nx, ny).wall &&
                        dist[j] < 0
                ) {
                    dist[j] = dist[i] + 1
                    queue.add(j)
                }
            }
        }
        return dist
    }

    fun directions(map: Dungeon): List<Int> =
        (0..3).filter { !map.cell(map.x + dx[it], map.y + dy[it]).wall }

    fun charmVisible(map: Dungeon, x: Int, y: Int): Boolean = abs(x - map.x) + abs(y - map.y) <= 4
}
