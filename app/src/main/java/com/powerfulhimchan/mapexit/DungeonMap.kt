package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** State/Dungeon fields mutate in place: subscribe in the draw phase, not just composition. */
@Composable
internal fun DungeonMap(vm: GameModel, modifier: Modifier, expanded: Boolean = false) {
    Canvas(modifier) {
        vm.revision
        drawMap(vm.engine.state,expanded)
    }
}
