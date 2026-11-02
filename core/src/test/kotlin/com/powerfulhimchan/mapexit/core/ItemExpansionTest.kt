package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ItemExpansionTest {
    private fun state()=State(bootstrapped=true,rng=721,nextId=100)
    private fun dungeon():GameEngine=GameEngine(state()).also { it.enterFloor(1) }
    private fun item(s:State,id:String):Gear=Consumables.create(s,Consumables.find(id)!!).also { s.inventory.add(it) }
    private fun wear(s:State,charm:Charm,refinements:Int=0) {
        val g=Gear(s.nextId++,Kind.NECKLACE,NecklaceCatalog.names.getValue(charm),charm=charm,refinements=refinements)
        s.inventory.add(g);Equipment.equip(s,g.id,Slot.NECK)
    }
    @Test fun everyWeaponAndArmorHasExactlySixIdentityPreservingCombinations() {
        assertEquals(20,ArmorCatalog.all.size);assertEquals(10,PrefixCatalog.byKind.size)
        val identities=mutableSetOf<String>()
        for(kind in PrefixCatalog.byKind.keys) {
            val variants=WeaponCatalog.of(kind).map { it.id }+ArmorCatalog.of(kind).map { it.id }
            assertEquals(4,variants.size);assertEquals(5,PrefixCatalog.byKind.getValue(kind).size)
            for(v in variants) for(prefix in listOf<String?>(null)+PrefixCatalog.byKind.getValue(kind).map { it.id }) {
                val g=ItemFactory.create(state(),kind,1,v,prefix)
                assertEquals(v,g.variantId);assertEquals(prefix,g.prefixId)
                assertTrue(identities.add("$kind:$v:${g.prefixId}"))
                assertEquals(g,SaveCodec.decode(SaveCodec.encode(state().copy(inventory=mutableListOf(g)))).inventory.single())
            }
        }
        assertEquals(240,identities.size)
    }
    @Test fun swordBonusAndEnhancementAreAddedExactlyOnceAndPenaltiesStayFixed() {
        val g=Gear(1,Kind.SWORD,"장검 (+1)",flat=WeaponCatalog.all.first().stats(1))
        val sharp=PrefixCatalog.apply(g,"sharp")
        assertEquals(24.0,sharp.flatValue(Stat.ATTACK),.0001)
        assertEquals(48.0,Forge.upgraded(sharp).flatValue(Stat.ATTACK),.0001)
        val rusty=PrefixCatalog.apply(g,"rusty")
        assertEquals(20.0,rusty.flatValue(Stat.ATTACK),.0001)
        val up=Forge.upgraded(rusty)
        assertEquals(42.0,up.flatValue(Stat.ATTACK),.0001)
        assertEquals(.40,up.flatValue(Stat.CRIT),.0001)
        assertTrue(up.name.startsWith("녹슨 장검"))
        assertEquals(2,up.prefixRank)
    }
    @Test fun twoHandedPrefixAndBossBonusesApplyOnceBeforeRingPercentages() {
        val s=state()
        val g=ItemFactory.create(s,Kind.SPEAR,1,"longspear","solid").copy(tier=1,prefixRank=1,
            flat=WeaponCatalog.of(Kind.SPEAR).first().stats(1),affixes=listOf(WeaponAffix(Stat.ATTACK,4.0)))
        s.inventory.add(g);Equipment.equip(s,g.id,Slot.RIGHT)
        val r=Gear(s.nextId++,Kind.RING,"시험",percent=Stats.zero().copy(attack=.2));s.inventory.add(r);Equipment.equip(s,r.id,Slot.RING1)
        assertEquals((100+45+3+4)*1.2,Equipment.stats(s).attack,.0001)
        assertEquals(g.affixes,Forge.upgraded(g).affixes)
    }
    @Test fun differentPrefixesDoNotStackOrCombine() {
        val s=state();val g=ItemFactory.create(s,Kind.SWORD,1,"longsword")
        s.inventory.addAll(listOf(PrefixCatalog.apply(g,"sharp"),PrefixCatalog.apply(g.copy(id=g.id+1),"agile")))
        assertEquals(2,InventoryStacks.groups(s.inventory).size)
        assertTrue(Forge.pairs(s).isEmpty())
    }
    @Test fun normalGenerationCanReachAllArmorPrefixesRingsAndNecklacesButNeverPermanentFruits() {
        val s=state();val armor=mutableSetOf<String>();val prefixes=mutableSetOf<String>();val rings=mutableSetOf<String>();val charms=mutableSetOf<Charm>()
        repeat(20000) {
            val g=ItemFactory.create(s)
            if(ArmorCatalog.all.any { it.id==g.variantId }) armor.add(g.variantId!!)
            g.prefixId?.let { prefixes.add("${g.kind}:$it") }
            if(g.kind==Kind.RING) rings.add(g.name.substringBefore(" (+"))
            if(g.kind==Kind.NECKLACE) charms.add(g.charm)
            assertNotEquals(EffectScope.PERMANENT,Consumables.find(g.consumableId)?.scope)
        }
        assertEquals(20,armor.size);assertEquals(50,prefixes.size);assertEquals(12,rings.size);assertEquals(10,charms.size)
    }
    @Test fun merchantsAlwaysStockBothTemporaryTypesAndNeverFruits() {
        val e=dungeon()
        val scopes=e.state.shop.mapNotNull { Consumables.find(it.consumableId)?.scope }
        assertTrue(EffectScope.FLOOR in scopes);assertTrue(EffectScope.TIMED in scopes);assertFalse(EffectScope.PERMANENT in scopes)
    }
    @Test fun allTwentyOneConsumablesHaveCorrectCategoryAndAreNotEquipment() {
        assertEquals(21,Consumables.all.size)
        for(spec in Consumables.all) {
            val g=Consumables.create(state(),spec)
            assertFalse(g.equippable);assertTrue(Equipment.allowedSlots(g).isEmpty())
            assertTrue(g.summary().contains(spec.stat.label));assertEquals(spec.scope.label,Consumables.category(g))
        }
    }
    @Test fun temporaryEffectsUseStrongestValueAndFloorEffectReturnsAfterExpiry() {
        val s=dungeon().state
        assertTrue(Consumables.use(s,item(s,"floor_attack").id));assertEquals(120.0,Equipment.stats(s).attack,.001)
        assertTrue(Consumables.use(s,item(s,"timed_attack").id));assertEquals(130.0,Equipment.stats(s).attack,.001)
        Consumables.tick(s,120000)
        assertEquals(120.0,Equipment.stats(s).attack,.001)
    }
    @Test fun effectsPauseInMenusAndPersistExactlyThroughSaveReload() {
        val s=dungeon().state;Consumables.use(s,item(s,"timed_defense").id)
        s.mode=Mode.MENU;s.menu=Menu("inventory",returnMode=Mode.EXPLORE)
        Consumables.tick(s,20000);assertEquals(120000L,s.effects.single().remainingMs)
        val cold=SaveCodec.decode(SaveCodec.encode(s));assertEquals(s.effects,cold.effects)
        cold.mode=Mode.EXPLORE;Consumables.tick(cold,1000)
        assertEquals(119000L,cold.effects.single().remainingMs);assertEquals(120000L,s.effects.single().remainingMs)
    }
    @Test fun changingFloorOnlyRemovesFloorEffectsAndDeathRemovesAllTemporaryEffects() {
        val e=dungeon();val s=e.state
        Consumables.use(s,item(s,"floor_attack").id);Consumables.use(s,item(s,"timed_defense").id)
        e.enterFloor(2);assertEquals(listOf("timed_defense"),s.effects.map { it.itemId })
        s.hp=0.0;e.die();assertTrue(s.effects.isEmpty())
    }
    @Test fun permanentGrowthSurvivesDeathColdLoadAndInheritanceCycleReset() {
        val e=dungeon();val s=e.state
        Consumables.use(s,item(s,"permanent_attack").id);s.hp=0.0;e.die()
        assertEquals(1.0,s.permanentBonus.attack,.001)
        val cold=GameEngine(SaveCodec.decode(SaveCodec.encode(s)))
        cold.state.mode=Mode.INHERIT;cold.state.floor=50
        cold.state.inventory.add(Gear(999,Kind.SWORD,"장검"))
        cold.choose(cold.choices().first { it.action=="inherit" })
        assertEquals(1.0,cold.state.permanentBonus.attack,.001)
        assertTrue(Equipment.stats(cold.state).attack>=101)
    }
    @Test fun duplicateFloorBuffIsNotConsumedAndTimedReuseRefreshesDuration() {
        val s=dungeon().state
        Consumables.use(s,item(s,"floor_agility").id)
        val duplicate=item(s,"floor_agility");assertFalse(Consumables.use(s,duplicate.id));assertTrue(duplicate in s.inventory)
        Consumables.use(s,item(s,"timed_agility").id);Consumables.tick(s,5000)
        Consumables.use(s,item(s,"timed_agility").id)
        assertEquals(120000L,s.effects.first { it.itemId=="timed_agility" }.remainingMs)
        assertEquals(2,s.effects.size)
    }
    @Test fun durationNecklaceIsSampledOnUseAndSwitchingGearDoesNotChangeClock() {
        val s=dungeon().state;wear(s,Charm.DURATION,1)
        Consumables.use(s,item(s,"timed_luck").id)
        assertEquals(156000L,s.effects.single().remainingMs)
        Equipment.unequip(s,s.equipment[Slot.NECK]!!)
        assertEquals(156000L,s.effects.single().remainingMs)
    }
    @Test fun hpBuffDoesNotHealAndExpiredMaxHpClampsCurrentHp() {
        val s=dungeon().state;s.hp=100.0
        Consumables.use(s,item(s,"timed_hp").id)
        assertEquals(486.0,Equipment.stats(s).hp,.001);assertEquals(100.0,s.hp,.001)
        s.hp=486.0;Consumables.tick(s,120000);assertEquals(360.0,s.hp,.001)
    }
    @Test fun combatUseConsumesAttackTurnAndDefenseUseIsRejectedWithoutItemLoss() {
        val e=dungeon();e.startBattle(false);val s=e.state;s.mode=Mode.COMBAT_ATTACK
        val g=item(s,"timed_attack")
        e.choose(e.choices().first { it.action=="combatItems" })
        e.choose(e.choices().first { it.action=="item" && it.argument==g.id })
        e.choose(e.choices().first { it.action=="useItem" })
        assertEquals(Mode.COMBAT_DEFEND,s.mode);assertFalse(g in s.inventory)
        val second=item(s,"floor_defense");assertFalse(Consumables.use(s,second.id));assertTrue(second in s.inventory)
    }
    @Test fun unyieldingOnlyWorksOnceEvenAfterReloadAndNecklaceSwapping() {
        val e=dungeon();val s=e.state;wear(s,Charm.UNYIELDING)
        s.hp=0.0;e.die();assertTrue(s.hp>0);assertTrue(s.unyieldingUsed);assertNotEquals(Mode.RUN_RESULT,s.mode)
        val cold=GameEngine(SaveCodec.decode(SaveCodec.encode(s)))
        wear(cold.state,Charm.UNYIELDING,7);cold.state.hp=0.0;cold.die()
        assertEquals(Mode.RUN_RESULT,cold.state.mode)
        cold.enterFloor(1);assertFalse(cold.state.unyieldingUsed)
    }
    @Test fun abyssSealRequiresThreeDistinctCyclesAndClaimIsIdempotent() {
        val s=state();s.mode=Mode.MENU;s.menu=Menu("achievements",returnMode=Mode.SURFACE)
        s.achievements.initialized=true;s.achievements.finalBossCycles.addAll(listOf(1,2))
        val index=Achievements.all.indexOfFirst { it.id=="boss-50-thrice" }
        Achievements.evaluate(s);assertFalse(Achievements.claim(s,index))
        s.achievements.finalBossCycles.add(3);Achievements.evaluate(s)
        assertTrue(Achievements.claim(s,index));assertFalse(Achievements.claim(s,index))
        assertEquals(1,s.inventory.count { it.variantId=="abyss-seal" })
        assertEquals(1,s.inventory.count { Consumables.find(it.consumableId)?.scope==EffectScope.PERMANENT })
    }
    @Test fun oldSaveWithoutNewFieldsRetainsOriginalGearAndZeroBonuses() {
        val s=SaveCodec.decode("""{"inventory":[{"id":1,"kind":"SWORD","name":"장검 (+2)","tier":2}],"nextId":2}""")
        assertEquals(Stats.zero(),s.permanentBonus);assertTrue(s.effects.isEmpty());assertNull(s.inventory.single().prefixId)
        assertEquals("장검 (+2)",s.inventory.single().name)
    }
    @Test fun healingNecklaceChangesActualPotionRecovery() {
        val e=GameEngine(state());val s=e.state;wear(s,Charm.HEALING);s.hp=100.0
        val potion=ItemFactory.potion(s,PotionGrade.BASIC);s.inventory.add(potion)
        e.choose(Choice("가방","inventory"));e.choose(Choice("회복약","item",potion.id))
        e.choose(e.choices().first { it.action=="useItem" })
        assertEquals(244.9,s.hp,.001)
    }
    @Test fun experienceAndGoldNecklacesChangeRealVictoryRewards() {
        for((charm,expectedGold,expectedXp) in listOf(Triple(Charm.TRAINING,24,34),Triple(Charm.GOLD,27,30))) {
            val e=dungeon();wear(e.state,charm);e.startBattle(false)
            e.state.mode=Mode.COMBAT_ATTACK;e.state.enemy!!.hp=.01;e.state.rng=0
            e.attack(false)
            assertEquals(Mode.VICTORY,e.state.mode)
            assertEquals(expectedGold,e.state.battleResult!!.gold)
            assertEquals(expectedXp,e.state.battleResult!!.experience)
        }
    }
    @Test fun legacyArmorStacksAndCombinesWithMatchingNewOriginalVariant() {
        val s=state();val old=Gear(1,Kind.BODY,"갑옷 (+1)",flat=Stats.zero().copy(hp=15.0,defense=4.0),price=35)
        val fresh=old.copy(id=2,variantId="body")
        s.inventory.addAll(listOf(old,fresh))
        assertEquals(1,InventoryStacks.groups(s.inventory).size)
        assertEquals(1,Forge.pairs(s).size)
    }

    @Test fun challengeIntroCancelPreservesFloorEffectButStartingChallengeClearsIt() {
        val e=dungeon();val s=e.state;s.debug=true;s.dungeon!!.here().event=Event.PORTAL
        Consumables.use(s,item(s,"floor_attack").id)
        assertTrue(ChallengeStages.open(s,ChallengeKind.LOST))
        assertEquals(1,s.effects.size)
        ChallengeStages.leave(s)
        assertEquals(1,s.effects.size)
        assertTrue(ChallengeStages.open(s,ChallengeKind.LOST))
        ChallengeStages.start(s)
        assertTrue(s.effects.isEmpty())
    }
    @Test fun challengeIntroPauseAndResultsDoNotDrainTimedEffects() {
        val s=dungeon().state;s.debug=true;s.dungeon!!.here().event=Event.PORTAL
        Consumables.use(s,item(s,"timed_attack").id)
        assertTrue(ChallengeStages.open(s,ChallengeKind.LOST))
        for(phase in listOf(ChallengePhase.INTRO,ChallengePhase.PAUSED,ChallengePhase.RESULT)) {
            s.challenge!!.phase=phase;Consumables.tick(s,1000)
            assertEquals(120000L,s.effects.single().remainingMs)
        }
        s.challenge!!.phase=ChallengePhase.ACTIVE;Consumables.tick(s,1000)
        assertEquals(119000L,s.effects.single().remainingMs)
    }

    @Test fun blockFeedbackAndLethalFixturesUseExplicitIntentsInsteadOfShopRandomness() {
        val e=GameEngine(State(base=Stats(defense=400.0,luck=200.0)));e.enterFloor(1);e.startBattle(false)
        e.state.mode=Mode.COMBAT_DEFEND;e.state.enemy!!.intent=EnemyIntent.STRIKE;e.state.rng=0
        e.defend(false)
        assertEquals(ImpactOutcome.BLOCK,e.state.lastImpact!!.outcome)
        assertEquals("완전 방어 · 0 피해",e.state.lastImpact!!.label())
        e.state.mode=Mode.COMBAT_DEFEND;e.state.enemy!!.intent=EnemyIntent.FLURRY;e.state.rng=0
        e.defend(false)
        assertTrue(e.state.lastImpact!!.label().contains("빈틈 발생"))
        e.state.hp=1.0;e.state.mode=Mode.COMBAT_DEFEND;e.state.enemy!!.intent=EnemyIntent.RECOVER
        e.defend(false);assertEquals(1.0,e.state.hp,.001)
        e.state.mode=Mode.COMBAT_DEFEND;e.state.enemy!!.intent=EnemyIntent.STRIKE;e.state.rng=1
        e.defend(false);assertEquals(Mode.RUN_RESULT,e.state.mode)
    }

}
