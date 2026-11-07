package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class SpriteFramesTest {
    @Test fun contactArtChangesAtTheSameInstantAsDamageAndReturnsToIdle() {
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            for (action in listOf("attack", "strong")) {
                assertEquals("${action}_${kind.name.lowercase()}_2", SpriteFrames.hero(action, kind, 319, 319))
                assertEquals("${action}_${kind.name.lowercase()}_3", SpriteFrames.hero(action, kind, 320, 320))
                assertTrue(SpriteFrames.hero(action, kind, 760, 760).startsWith("idle_"))
            }
        }
    }
    @Test fun ReactionsStartAtImpactAndDeathTakesPriorityOverAttack() {
        assertEquals("guard_2", SpriteFrames.hero("guard", Kind.SWORD, 319, 319, damaged = true))
        assertEquals("hit_0", SpriteFrames.hero("guard", Kind.SWORD, 320, 320, damaged = true))
        assertEquals("death_0", SpriteFrames.hero("guard", Kind.SWORD, 320, 320, damaged = true, dead = true))
        assertEquals("death_3", SpriteFrames.monster(true, 700, 700, hit = true, dying = true))
        assertEquals("attack_3", SpriteFrames.monster(true, 320, 320))
    }
    @Test fun independentStrikeClocksAndNegativeIdleClocksStayWithinTheAtlas() {
        assertEquals(SpriteFrames.hero("attack", Kind.CLAW, 320, 1000), SpriteFrames.hero("attack", Kind.CLAW, 320, 1760))
        assertEquals("idle_1", SpriteFrames.hero(null, null, 760, -1))
        assertEquals("guard_1", SpriteFrames.monster(false, 760, -1, guarding = true))
        assertEquals((0..5).toSet(), (0L until 760).map(SpriteFrames::phase).toSet())
    }
}
