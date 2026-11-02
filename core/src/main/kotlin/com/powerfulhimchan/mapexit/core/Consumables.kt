package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable
import kotlin.math.min

@Serializable enum class EffectScope(val label:String) { PERMANENT("영구 성장"), FLOOR("층 강화"), TIMED("시간 강화") }
@Serializable data class ActiveEffect(val itemId:String,val remainingMs:Long=0,val floor:Int=0)
data class ConsumableSpec(val id:String,val stat:Stat,val scope:EffectScope,val amount:Double,val name:String) {
    val proportional get() = scope!=EffectScope.PERMANENT && stat in listOf(Stat.HP,Stat.ATTACK,Stat.DEFENSE,Stat.AGILITY)
    fun summary():String {
        val value=if(proportional || stat in listOf(Stat.CRIT,Stat.CRIT_DAMAGE)) amount*100 else amount
        val number=if(value==value.toInt().toDouble()) value.toInt().toString() else "${kotlin.math.round(value*10)/10}"
        val unit=if(proportional) "%" else if(stat in listOf(Stat.CRIT,Stat.CRIT_DAMAGE)) "%p" else ""
        return "${stat.label} +$number$unit · "+when(scope) {
            EffectScope.PERMANENT -> "영구 유지 · 사망·회차 변경 후에도 보존"
            EffectScope.FLOOR -> "현재 층을 떠날 때까지"
            EffectScope.TIMED -> "활동 시간 120초 · 메뉴·스토리에서는 정지"
        } + if(stat==Stat.HP && scope!=EffectScope.PERMANENT) " · 즉시 회복 없음" else ""
    }
}
object Consumables {
    private val names=listOf("생명","힘","수호","바람","집중","격노","행운")
    private val permanent=listOf(5.0,1.0,1.0,1.0,.002,.01,1.0)
    private val floor=listOf(.20,.20,.25,.20,.10,.25,10.0)
    private val timed=listOf(.35,.30,.40,.35,.15,.40,15.0)
    val all=EffectScope.entries.flatMap { scope -> Stat.entries.mapIndexed { i, stat ->
        val amount=when(scope) { EffectScope.PERMANENT->permanent[i];EffectScope.FLOOR->floor[i];EffectScope.TIMED->timed[i] }
        val suffix=when(scope) { EffectScope.PERMANENT->"열매";EffectScope.FLOOR->"비약";EffectScope.TIMED->"물약" }
        ConsumableSpec("${scope.name.lowercase()}_${stat.name.lowercase()}",stat,scope,amount,"${names[i]}의 $suffix")
    } }
    fun find(id:String?)=all.firstOrNull { it.id==id }
    fun create(s:State,spec:ConsumableSpec)=Gear(s.nextId++,Kind.CONSUMABLE,spec.name,
        consumableId=spec.id,price=when(spec.scope) { EffectScope.PERMANENT->400;EffectScope.FLOOR->65;EffectScope.TIMED->85 })
    fun random(s:State,scope:EffectScope?=null):Gear {
        val choices=all.filter { scope==null || it.scope==scope }
        return create(s,choices[s.nextInt(choices.size)])
    }
    fun stock(s:State) {
        // Permanent growth is earned, never included in a merchant's stock.
        for(scope in listOf(EffectScope.FLOOR,EffectScope.TIMED))
            if(s.shop.none { find(it.consumableId)?.scope==scope }) s.shop.add(random(s,scope))
    }
    fun reward(s:State):Gear? = if(s.random()<Luck.good(.12,Equipment.stats(s).luck,.25)) random(s,EffectScope.PERMANENT) else null
    fun category(g:Gear)=find(g.consumableId)?.scope?.label ?: if(g.kind==Kind.POTION) "회복" else if(g.equippable) "장비" else "강화서"
    fun blockedReason(s:State,g:Gear):String? {
        val spec=find(g.consumableId) ?: return "사용할 수 없는 아이템입니다"
        val mode=if(s.mode==Mode.MENU) s.menu?.returnMode else s.mode
        if(s.hp<=0 || mode !in listOf(Mode.SURFACE,Mode.EXPLORE,Mode.FLOOR_CLEAR,Mode.COMBAT_ATTACK)) return "지금은 사용할 수 없습니다"
        if(spec.scope!=EffectScope.PERMANENT && s.dungeon==null) return "던전에서 사용할 수 있습니다"
        if(spec.scope==EffectScope.FLOOR && s.effects.any { it.itemId==spec.id && it.floor==s.floor }) return "현재 층에서 이미 적용 중입니다"
        return null
    }
    fun use(s:State,id:Long):Boolean {
        val gear=s.inventory.firstOrNull { it.id==id && it.kind==Kind.CONSUMABLE } ?: return false
        blockedReason(s,gear)?.let { s.note(it);return false }
        val spec=find(gear.consumableId) ?: return false
        if(spec.scope==EffectScope.PERMANENT) s.permanentBonus=s.permanentBonus.add(spec.stat,spec.amount)
        else {
            val duration=(120_000*NecklaceCatalog.multiplier(s,Charm.DURATION,.25)).toLong()
            s.effects=s.effects.filterNot { it.itemId==spec.id }+ActiveEffect(spec.id,if(spec.scope==EffectScope.TIMED) duration else 0,s.floor)
        }
        s.inventory.remove(gear)
        s.note("${spec.name} 사용 · ${spec.summary()}")
        return true
    }
    /** Strongest temporary bonus per stat; a floor effect resumes when a stronger timed one ends. */
    fun value(s:State,stat:Stat):Double = s.effects.mapNotNull { effect ->
        find(effect.itemId)?.takeIf { it.stat==stat && (it.scope==EffectScope.FLOOR && effect.floor==s.floor || it.scope==EffectScope.TIMED && effect.remainingMs>0) }?.amount
    }.maxOrNull() ?: 0.0
    fun tick(s:State,delta:Long) {
        if(s.mode !in listOf(Mode.EXPLORE,Mode.MOVING,Mode.COMBAT_ATTACK,Mode.COMBAT_DEFEND,Mode.TRAP,Mode.TRAP_GUIDE,Mode.CHALLENGE)) return
        val old=s.effects
        s.effects=old.map { e -> if(find(e.itemId)?.scope==EffectScope.TIMED) e.copy(remainingMs=(e.remainingMs-delta.coerceAtLeast(0)).coerceAtLeast(0)) else e }
            .filter { find(it.itemId)?.scope!=EffectScope.TIMED || it.remainingMs>0 }
        if(old.size!=s.effects.size) s.hp=min(s.hp,Equipment.stats(s).hp)
    }
    fun leaveFloor(s:State) {
        s.effects=s.effects.filter { find(it.itemId)?.scope==EffectScope.TIMED }
        s.hp=min(s.hp,Equipment.stats(s).hp)
    }
    fun clear(s:State) { s.effects=emptyList();s.hp=min(s.hp,Equipment.stats(s).hp) }
    fun labels(s:State)=s.effects.mapNotNull { e -> find(e.itemId)?.let { spec ->
        "${spec.stat.label} ↑ · "+if(spec.scope==EffectScope.FLOOR) "현재 층" else {
            val seconds=(e.remainingMs+999)/1000
            "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
        }
    } }
    fun preventDeath(s:State):Boolean {
        if(s.hp>0 || s.dungeon==null || s.unyieldingUsed || Equipment.charm(s)!=Charm.UNYIELDING) return false
        s.unyieldingUsed=true
        s.hp=Equipment.stats(s).hp*(.20+Equipment.charmRefinement(s)).coerceAtMost(.50)
        s.note("불굴의 목걸이 발동 · HP ${s.hp.toInt()}로 생존 · 이번 출정 사용 완료")
        return true
    }
}
