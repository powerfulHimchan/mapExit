package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*

/** Nine labeled hit targets laid out in the same positions as a worn outfit. */
@Composable
internal fun EquipmentOverview(vm: GameModel) {
    val s = vm.engine.state
    val rows =
        listOf(
            listOf(null, Slot.HEAD, Slot.NECK),
            listOf(Slot.LEFT, Slot.BODY, Slot.RIGHT),
            listOf(Slot.RING1, Slot.PANTS, Slot.RING2),
            listOf(null, Slot.BOOTS, null),
        )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "장착 현황 · ${s.equipment.size}/9 부위",
            color = mint,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { slot ->
                    if (slot == null) Spacer(Modifier.weight(1f))
                    else {
                        val gear = s.inventory.find { it.id == s.equipment[slot] }
                        val label = "${slot.label}: ${gear?.name ?: "비어 있음"}"
                        Column(
                            Modifier.weight(1f)
                                .heightIn(min = 116.dp)
                                .background(panel, RoundedCornerShape(6.dp))
                                .border(
                                    1.dp,
                                    if (gear != null) mint.copy(alpha = .6f) else line,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable(role = Role.Button) {
                                    if (s.mode != Mode.MENU || s.menu?.type != "equipment")
                                        vm.choose(Choice("장착 현황", "equipment"))
                                    vm.choose(Choice(label, "slotItem", slot.ordinal.toLong()))
                                }
                                .semantics(mergeDescendants = true) { contentDescription = label }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                slot.label,
                                color = if (gear != null) mint else muted,
                                fontSize = 11.sp,
                            )
                            val emptyKind =
                                when (slot) {
                                    Slot.HEAD -> Kind.HEAD
                                    Slot.BODY -> Kind.BODY
                                    Slot.LEFT -> Kind.SHIELD
                                    Slot.RIGHT -> Kind.SWORD
                                    Slot.PANTS -> Kind.PANTS
                                    Slot.BOOTS -> Kind.BOOTS
                                    Slot.NECK -> Kind.NECKLACE
                                    else -> Kind.RING
                                }
                            ItemArt(
                                gear?.kind ?: emptyKind,
                                Modifier.size(40.dp).alpha(if (gear == null) .25f else 1f),
                            )
                            Text(
                                gear?.name ?: "비어 있음",
                                color = if (gear != null) pale else muted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                            )
                            if (gear?.twoHanded == true)
                                Text("양손 점유", color = amber, fontSize = 10.sp)
                            else if (gear == null) Text("눌러서 장비 찾기", color = muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}
