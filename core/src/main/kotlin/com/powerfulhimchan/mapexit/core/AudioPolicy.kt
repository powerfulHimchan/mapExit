package com.powerfulhimchan.mapexit.core

data class AudioCue(val key: String, val delay: Long = 0L, val gain: Float = 1f)

/** Presentation-only policy. Never consumes the game's random state. */
object AudioPolicy {
    fun music(s: State, animating: Boolean = false): String? {
        val mode = if (s.mode == Mode.MENU) s.menu?.returnMode else s.mode
        return when {
            animating || mode in listOf(Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND) -> "battle"
            mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE, Mode.CHALLENGE) -> "obstacle"
            mode == Mode.ENCOUNTER && s.encounter in listOf(Event.NPC, Event.MERCHANT, Event.HEAL) -> "npc"
            mode in listOf(Mode.SURFACE, Mode.RUN_RESULT, Mode.INHERIT) -> null
            s.dungeon != null -> "dungeon_${(s.floor.coerceIn(1,50)-1)/10+1}"
            else -> null
        }
    }
    fun weapon(kind: Kind?, strong: Boolean): String =
        "${when(kind) { Kind.SPEAR->"spear"; Kind.HAMMER->"hammer"; Kind.CLAW->"claw"; Kind.WHIP->"whip"; else->"sword" }}_${if(strong)"heavy" else "normal"}"
    private fun contact(key: String): Long = when(key) {
        "sword_normal"->68; "sword_heavy"->162; "spear_normal"->102; "spear_heavy"->248
        "hammer_normal"->158; "hammer_heavy"->318; "claw_normal","claw_heavy"->55
        "whip_normal"->179; "whip_heavy"->281; else->160
    }
    fun monster(family: String): String = when(family) {
        "wisp"->"monster_magic"; "bat","spider"->"monster_bite"; "knight"->"monster_claw"; else->"monster_slam"
    }
    fun trap(type: Int) = when(type) { 0->"trap_water"; 1,3->"trap_blade"; else->"trap_rock" }
    fun battle(impact: BattleImpact, kind: Kind?, family: String, strong: Boolean,
        defending: Boolean, victory: Boolean, death: Boolean, levelUp: Boolean): List<AudioCue> {
        val cues=mutableListOf<AudioCue>()
        val prep=if(defending && impact.target==ImpactTarget.PLAYER) BattleAnimation.DEFENSE_DELAY_MS else 0L
        val key=weapon(kind,strong)
        fun strike(start: Long, hit: Boolean, critical: Boolean) {
            cues+=AudioCue(if(hit)key else "dodge", start+(BattleAnimation.HIT_MS-if(hit)contact(key) else 130).coerceAtLeast(0))
            if(hit && critical)cues+=AudioCue("critical",start+BattleAnimation.HIT_MS,.8f)
        }
        if(impact.target==ImpactTarget.ENEMY) {
            if(impact.strikes.isEmpty())strike(prep,impact.outcome!=ImpactOutcome.MISS,impact.critical)
            else impact.strikes.forEachIndexed { i,hit -> strike(prep+i*BattleAnimation.DURATION_MS,hit.damage>0,hit.critical) }
        } else {
            cues+=AudioCue(monster(family),prep+BattleAnimation.HIT_MS-180)
            cues+=AudioCue(when(impact.outcome) {
                ImpactOutcome.GUARD,ImpactOutcome.BLOCK->"shield_block"
                ImpactOutcome.DODGE,ImpactOutcome.MISS->"dodge"; else->"player_hit"
            },prep+BattleAnimation.HIT_MS)
            if(impact.critical && impact.amount>0)cues+=AudioCue("critical",prep+BattleAnimation.HIT_MS,.55f)
            impact.counterHit?.let { strike(prep+BattleAnimation.DURATION_MS,it.damage>0,it.critical) }
        }
        val count=impact.strikes.size.coerceAtLeast(1)+if(impact.counterHit!=null)1 else 0
        val finish=prep+(count-1)*BattleAnimation.DURATION_MS+BattleAnimation.HIT_MS+140
        if(victory)cues+=AudioCue("enemy_defeat",finish,.7f)
        if(death)cues+=AudioCue("player_death",finish)
        if(levelUp)cues+=AudioCue("level_up",finish+150,.75f)
        return cues
    }
}

data class AudioSnapshot(
    val floor:Int,val mode:Mode,val x:Int?,val y:Int?,val hp:Double,val level:Int,val gold:Int,
    val chests:Int,val battleId:Long,val impact:Long,val gear:Map<Slot,Long>,
    val upgrade:Pair<Long,Int>?,val trapType:Int?,val trapPhase:TrapPhase?,val progress:Int?,
    val encounter:Event?,val enemyFamily:String?,
) {
    companion object {
        fun capture(s:State)=AudioSnapshot(s.floor,s.mode,s.dungeon?.x,s.dungeon?.y,s.hp,s.level,s.gold,
            s.runChests,s.battleId,s.impactSequence,s.equipment.toMap(),s.upgradeResult?.after?.let { it.id to it.refinements },
            s.trap?.type,s.trap?.phase,s.trap?.progress,s.encounter,
            s.enemy?.let { MonsterCatalog.describe(it,s.floor).family })
    }
}
