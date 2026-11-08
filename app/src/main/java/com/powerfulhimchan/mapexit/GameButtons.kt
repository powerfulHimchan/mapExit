package com.powerfulhimchan.mapexit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared engraved metal frame for every action, menu tile and selected control. */
internal fun Modifier.gameButtonSurface(
    enabled: Boolean = true,
    selected: Boolean = false,
    pressed: Boolean = false,
): Modifier = battlePanel(edge = if (selected) amber else Color(0xFF9B7C7B), pressed = pressed, enabled = enabled)

@Composable
internal fun Modifier.gameClickable(
    enabled: Boolean = true,
    selected: Boolean? = null,
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    return gameButtonSurface(enabled, selected == true, pressed)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick)
        .semantics { if (selected != null) this.selected = selected }
}

@Composable
internal fun GameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.heightIn(min = 40.dp).gameClickable(enabled, selected, onClick = onClick)
        .semantics(mergeDescendants = true) {}.padding(contentPadding),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) pale else muted) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) {
                content()
            }
        }
    }
}
