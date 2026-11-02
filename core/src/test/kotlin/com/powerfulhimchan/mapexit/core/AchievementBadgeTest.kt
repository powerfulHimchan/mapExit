package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class AchievementBadgeTest {
    @Test fun badgeCountsOnlyKnownUnlockedUnclaimedRewardsAndDisappearsAtZero() {
        val s=State(bootstrapped=true)
        assertEquals("업적",Achievements.menuLabel(s))
        s.achievements.unlocked.addAll(listOf("first-victory","traps-20","quests-5","removed-achievement"))
        s.achievements.claimed.addAll(listOf("first-victory","other-old-achievement"))
        assertEquals(2,Achievements.pendingRewards(s))
        assertEquals("업적 (2)",Achievements.menuLabel(s))
        s.achievements.claimed.add("traps-20")
        assertEquals("업적 (1)",Achievements.menuLabel(s))
        s.achievements.claimed.add("quests-5")
        assertEquals("업적",Achievements.menuLabel(s))
        val cold=SaveCodec.decode(SaveCodec.encode(s))
        assertEquals("업적",Achievements.menuLabel(cold))
    }
}
