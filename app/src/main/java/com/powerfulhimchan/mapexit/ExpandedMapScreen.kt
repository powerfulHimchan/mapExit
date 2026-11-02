package com.powerfulhimchan.mapexit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ExpandedMapScreen(vm: GameModel) {
    BackHandler { vm.closeMap() }
    Column(Modifier.fillMaxSize().background(ink).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.fillMaxWidth().weight(1f).background(panel).border(1.dp,line)
            .semantics { contentDescription = "확대 지도" }) { drawMap(vm.engine.state,expanded = true) }
        GameButton(onClick = vm::closeMap,modifier = Modifier.fillMaxWidth()) { Text("돌아가기",color = mint) }
    }
}
