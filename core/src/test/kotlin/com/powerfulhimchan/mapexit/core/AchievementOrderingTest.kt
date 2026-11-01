package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class AchievementOrderingTest {
    private fun fixture(): GameEngine {
        val e = GameEngine()
        e.state.achievements.apply {
            unlocked.add("first-victory"); claimed.add("first-victory"); traps=20; donations=3
        }
        Achievements.evaluate(e.state)
        e.choose(e.choices().first { it.action=="help" })
        e.choose(e.choices().first { it.action=="achievements" })
        return e
    }

    @Test fun readyRewardsComeFirstLockedStayInCatalogueOrderAndClaimedGoLast() {
        val e = fixture()
        val order = Achievements.displayOrder(e.state)
        assertEquals(listOf("traps-20","donations-3"),order.take(2).map { it.value.id })
        assertEquals("first-victory",order.last().value.id)
        val locked = Achievements.all.filter { it.id !in e.state.achievements.unlocked }
        assertEquals(locked,order.drop(2).dropLast(1).map { it.value })
        val snapshot = SaveCodec.encode(e.state)
        assertEquals(order,Achievements.displayOrder(GameEngine(SaveCodec.decode(snapshot)).state))
        assertEquals(snapshot,SaveCodec.encode(e.state))
    }

    @Test fun reorderedClaimAndTitleActionsKeepOriginalIdsAndReorderAfterClaim() {
        val e = fixture()
        val index = Achievements.all.indexOfFirst { it.id=="traps-20" }
        val choice = e.choices().first { it.action=="claimAchievement" }
        assertEquals(index.toLong(),choice.argument)
        e.choose(choice)
        assertEquals(1,e.state.points); assertEquals(0,e.state.gold)
        assertEquals("donations-3",Achievements.displayOrder(e.state).first().value.id)
        assertEquals(listOf("first-victory","traps-20"),Achievements.displayOrder(e.state).takeLast(2).map { it.value.id })
        e.choose(e.choices().first { it.action=="selectTitle" && it.argument==index.toLong() })
        assertEquals("함정 전문가",Achievements.title(e.state))
        e.choose(choice); assertEquals(1,e.state.points)
    }
}
