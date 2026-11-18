package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class AudioPolicyTest {
    private fun state(floor:Int)=State(floor=floor,mode=Mode.EXPLORE,dungeon=Dungeon(3,3,MutableList(9){Cell()}))
    @Test fun allTenFloorBoundariesUseTheCorrectTheme() {
        for(f in 1..50)assertEquals("dungeon_${(f-1)/10+1}",AudioPolicy.music(state(f)))
        assertEquals("dungeon_5",AudioPolicy.music(state(99)))
    }
    @Test fun combatMenuAndUnfinishedAnimationKeepTheBattleTheme() {
        val s=state(11);s.mode=Mode.COMBAT_DEFEND;assertEquals("battle",AudioPolicy.music(s))
        s.mode=Mode.MENU;s.menu=Menu("combatItems",returnMode=Mode.COMBAT_ATTACK)
        assertEquals("battle",AudioPolicy.music(s))
        s.mode=Mode.VICTORY;assertEquals("battle",AudioPolicy.music(s,true));assertEquals("dungeon_2",AudioPolicy.music(s))
    }
    @Test fun chestIsNotAnNpcButMerchantShopIs() {
        val s=state(25);s.mode=Mode.ENCOUNTER
        for(e in listOf(Event.NPC,Event.MERCHANT,Event.HEAL)){s.encounter=e;assertEquals("npc",AudioPolicy.music(s))}
        s.encounter=Event.CHEST;assertEquals("dungeon_3",AudioPolicy.music(s))
        s.encounter=Event.MERCHANT;s.mode=Mode.MENU;s.menu=Menu("shop",returnMode=Mode.ENCOUNTER)
        assertEquals("npc",AudioPolicy.music(s))
    }
    @Test fun obstaclesDifferFromSurfaceAndDeath() {
        val s=state(41)
        for(m in listOf(Mode.TRAP,Mode.TRAP_GUIDE,Mode.CHALLENGE)){s.mode=m;assertEquals("obstacle",AudioPolicy.music(s))}
        for(m in listOf(Mode.SURFACE,Mode.INHERIT,Mode.RUN_RESULT)){s.mode=m;assertNull(AudioPolicy.music(s))}
    }
    private fun plan(i:BattleImpact,defense:Boolean=false,victory:Boolean=false,level:Boolean=false)=
        AudioPolicy.battle(i,Kind.SPEAR,"wisp",false,defense,victory,false,level)
    @Test fun defenseSoundWaitsForTheHalfSecondPreparation() {
        val cues=plan(BattleImpact(1,ImpactTarget.PLAYER,0,ImpactOutcome.BLOCK),true)
        assertEquals(BattleAnimation.DEFENSE_DELAY_MS+BattleAnimation.HIT_MS,cues.single{it.key=="shield_block"}.delay)
        assertTrue(cues.all{it.delay>=BattleAnimation.DEFENSE_DELAY_MS})
        assertEquals("monster_magic",cues.first().key)
    }
    @Test fun doubleHitCriticalAndRewardsFollowEachAnimationFrame() {
        val impact=BattleImpact(2,ImpactTarget.ENEMY,30,ImpactOutcome.DAMAGE,strikes=listOf(AttackHit(10.0),AttackHit(20.0,true)))
        val cues=plan(impact,victory=true,level=true)
        val attacks=cues.filter{it.key=="spear_normal"}
        assertEquals(2,attacks.size);assertEquals(BattleAnimation.DURATION_MS,attacks[1].delay-attacks[0].delay)
        val hit=BattleAnimation.DURATION_MS+BattleAnimation.HIT_MS
        assertEquals(hit,cues.single{it.key=="critical"}.delay)
        assertTrue(cues.single{it.key=="enemy_defeat"}.delay>hit)
        assertTrue(cues.single{it.key=="level_up"}.delay>hit)
    }
    @Test fun counterCriticalUsesTheCounterAnimation() {
        val cues=plan(BattleImpact(3,ImpactTarget.PLAYER,5,ImpactOutcome.GUARD,counterHit=AttackHit(15.0,true)),true)
        assertEquals(BattleAnimation.DEFENSE_DELAY_MS+BattleAnimation.DURATION_MS+BattleAnimation.HIT_MS,cues.single{it.key=="critical"}.delay)
    }
    @Test fun missHasNoEmbeddedWeaponImpactOrCritical() {
        assertEquals(listOf("dodge"),plan(BattleImpact(4,ImpactTarget.ENEMY,0,ImpactOutcome.MISS,true)).map{it.key})
    }
    @Test fun weaponFamiliesUseTenDistinctAttacks() {
        val keys=listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP).flatMap{listOf(AudioPolicy.weapon(it,false),AudioPolicy.weapon(it,true))}
        assertEquals(10,keys.toSet().size)
    }
    @Test fun snapshotIsIndependentOfMutableWorldAndEquipment() {
        val s=state(1);s.equipment[Slot.RIGHT]=7
        val copy=AudioSnapshot.capture(s);s.dungeon!!.x=2;s.equipment[Slot.RIGHT]=8
        assertEquals(1,copy.x);assertEquals(7L,copy.gear[Slot.RIGHT])
    }
}
