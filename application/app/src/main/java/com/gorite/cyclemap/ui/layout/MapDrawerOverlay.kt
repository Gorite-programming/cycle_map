package com.gorite.cyclemap.ui.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gorite.cyclemap.ui.theme.CycleMapColors
import com.gorite.cyclemap.ui.theme.LocalAnimationEnabled
import com.gorite.cyclemap.ui.theme.Motion

@Composable
internal fun BoxScope.MapDrawerOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = Motion.resolveEffectiveAnimation(
        LocalAnimationEnabled.current,
        Motion.isSystemAnimationEnabled(context),
    )
    BackHandler(enabled = visible, onBack = onDismiss)

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(30f)) {
        val drawerWidth = minOf(maxWidth * 0.86f, 360.dp)
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(Motion.duration(190, animationsEnabled))),
            exit = fadeOut(animationSpec = tween(Motion.duration(210, animationsEnabled))),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CycleMapColors.MapScrim.copy(alpha = 0.60f))
                    .clickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f),
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(
                    Motion.duration(260, animationsEnabled),
                    easing = Motion.DecelerateEasing,
                ),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(
                    Motion.duration(210, animationsEnabled),
                    easing = Motion.AccelerateEasing,
                ),
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                content()
            }
        }
    }
}
