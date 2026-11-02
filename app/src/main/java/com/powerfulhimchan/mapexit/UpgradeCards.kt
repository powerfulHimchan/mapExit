package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.powerfulhimchan.mapexit.core.*

@androidx.compose.runtime.Composable
internal fun UpgradeResultScreen(vm: GameModel, modifier: Modifier) {
    val result = vm.engine.state.upgradeResult ?: return
    fun value(stat: Stat, number: Double, percent: Boolean): String {
        val scaled = if(percent || stat in listOf(Stat.CRIT,Stat.CRIT_DAMAGE)) number*100 else number
        val rounded = kotlin.math.round(scaled*10)/10
        val text = if(rounded==rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
        return text + if(percent) "%" else if(stat in listOf(Stat.CRIT,Stat.CRIT_DAMAGE)) "%p" else ""
    }
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=16.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("강화 성공",color=mint,fontSize=26.sp,fontWeight=FontWeight.Black)
            ItemArt(result.after,Modifier.size(72.dp))
            Text("${result.before.name} → ${result.after.name}",color=amber,fontSize=18.sp,lineHeight=26.sp)
            Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(8.dp)).padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                for(stat in Stat.entries) for(percent in listOf(false,true)) {
                    val before=if(percent)result.before.percent.value(stat) else result.before.flatValue(stat)
                    val after=if(percent)result.after.percent.value(stat) else result.after.flatValue(stat)
                    if(kotlin.math.abs(after-before) > 1e-8) {
                        Text(stat.label + if(percent) " (비율)" else "",color=pale,fontSize=14.sp)
                        Text("${value(stat,before,percent)} → ${value(stat,after,percent)}  (+${value(stat,after-before,percent)})",
                            color=mint,fontSize=18.sp,fontWeight=FontWeight.Bold,
                            modifier=Modifier.semantics { contentDescription="강화 변화 · ${stat.label}${if(percent) " 비율" else ""}" })
                    }
                }
                if(result.after.kind==Kind.NECKLACE) {
                    if(result.after.charm==Charm.MAP)
                        Text("탐지 범위 ${4+result.before.refinements} → ${4+result.after.refinements}칸 (+1)",color=mint,fontSize=18.sp)
                    else Text("추가 강화 효과 ${result.before.refinements*5} → ${result.after.refinements*5}%p (+5%p)",color=mint,fontSize=18.sp)
                }
            }
            Text("강화서 1개 사용",color=muted,fontSize=13.sp)
        }
        GameButton(onClick={vm.choose(Choice("확인","upgradeDone"))},modifier=Modifier.fillMaxWidth()) { Text("확인",color=mint) }
    }
}

internal fun LazyListScope.upgradeCards(vm: GameModel, menu: String) {
    val s=vm.engine.state
    item(key="upgrade-guide") {
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(if(menu=="upgradeTargets") "강화서를 사용할 장비를 선택하세요. 한 개를 소모해 선택한 장비의 +등급을 1 올립니다."
                else "같은 이름·종류·등급 장비 2개 → 한 단계 높은 장비 1개. 보스 옵션이 있으면 옵션도 같아야 합니다.",color=pale,fontSize=13.sp,lineHeight=19.sp)
            if(menu!="upgradeTargets") {
                Text("실패 시 재료 1개만 소모하며 기준 장비는 유지됩니다. 계승 장비는 재료로 소모하지 않습니다.",color=amber,fontSize=12.sp,lineHeight=18.sp)
                Text(s.message,color=mint,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.semantics { contentDescription="대장간 결과" })
            }
        }
    }
    val pairs=Forge.pairs(s)
    val targets=if(menu=="upgradeTargets") s.inventory.filter { it.equippable }
        else pairs.filter { menu!="forgeConfirm" || it.base.id==s.menu?.argument }.map { it.base }
    if(targets.isEmpty()) item { Text(if(menu=="upgradeTargets") "강화할 장비가 없습니다. 강화서는 소모되지 않습니다." else "같은 등급의 동일 장비가 2개 필요합니다.",color=muted,fontSize=14.sp) }
    items(targets,key={"upgrade:${it.id}"}) { gear ->
        val next=Forge.upgraded(gear)
        val pair=pairs.firstOrNull { it.base.id==gear.id }
        val action=when(menu) { "upgradeTargets"->"upgradeTarget";"forgeConfirm"->"forgeDo";else->"forgePair" }
        val choice=vm.engine.choices().firstOrNull { it.action==action && it.argument==gear.id }
        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(8.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ItemArt(gear,Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text((if(gear.inherited) "◆ " else "")+gear.name+Equipment.equippedLabel(s,gear.id),color=pale,fontSize=15.sp)
                    Text(gear.summary(),color=muted,fontSize=12.sp,lineHeight=18.sp)
                }
            }
            if(menu!="upgradeTargets" && pair!=null) {
                Text("소모 재료: ${pair.material.name}${Equipment.equippedLabel(s,pair.material.id)}",color=amber,fontSize=12.sp)
                Text(pair.material.summary(),color=muted,fontSize=11.sp,lineHeight=16.sp)
                val failure=Forge.failureChance(s)
                Text(String.format(java.util.Locale.ROOT,"성공 %.1f%% · 실패 %.1f%% · 행운에 따라 변동",(1-failure)*100,failure*100),color=amber,fontSize=12.sp)
            }
            Text("→ ${next.name}",color=mint,fontSize=15.sp)
            Text(next.summary(),color=mint,fontSize=12.sp,lineHeight=18.sp)
            if(choice!=null) GameButton(onClick={vm.choose(choice)},modifier=Modifier.fillMaxWidth().semantics { contentDescription=choice.label }) {
                Text(if(menu=="upgradeTargets") "강화서 1개 사용" else if(menu=="forgeConfirm") "합성 실행" else "합성 확인",color=mint)
            }
        }
    }
}
