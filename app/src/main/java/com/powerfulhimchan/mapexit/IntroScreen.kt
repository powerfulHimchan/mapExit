package com.powerfulhimchan.mapexit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Selected concept 05. Fit the complete artwork rather than cropping its title or hero. */
@Composable
internal fun IntroScreen(onStart: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "start prompt")
    val opacity by pulse.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "prompt brightness",
    )
    Box(
        Modifier.fillMaxSize().background(Color(0xFF02070F))
            .semantics {
                paneTitle = "한 층만 더"
                contentDescription = "화면을 터치하여 시작"
            }
            .clickable(role = Role.Button, onClickLabel = "게임 시작", onClick = onStart)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val artwork = painterResource(R.drawable.intro_door)
            val artRatio = artwork.intrinsicSize.width / artwork.intrinsicSize.height
            val artWidth = minOf(maxWidth, maxHeight * artRatio)
            val artHeight = artWidth / artRatio
            Box(Modifier.width(artWidth).height(artHeight)) {
                Image(
                    artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().height(artHeight * .115f)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "화면을 터치하여 시작",
                        color = Color(0xFFF2CD8A),
                        fontSize = (artWidth.value * .044f).coerceIn(10f, 22f).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(opacity).clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
