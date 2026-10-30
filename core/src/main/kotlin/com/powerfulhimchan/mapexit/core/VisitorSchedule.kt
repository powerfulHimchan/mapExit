package com.powerfulhimchan.mapexit.core

object VisitorSchedule {
    /** Each visitor independently appears first on floor 3/4, then every 3/4 floors. */
    fun floors(seed: Long, event: Event): List<Int> {
        require(event == Event.NPC || event == Event.MERCHANT)
        val salt = if (event == Event.NPC) 0x4E50435F53454544L else 0x4D45524348414E54L
        val random = State(bootstrapped = true, rng = seed xor salt)
        val result = mutableListOf<Int>()
        var floor = 3 + random.nextInt(2)
        while (floor <= 50) {
            result.add(floor)
            floor += 3 + random.nextInt(2)
        }
        return result
    }
}
