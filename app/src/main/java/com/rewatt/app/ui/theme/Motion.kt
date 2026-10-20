package com.rewatt.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * Shared motion vocabulary for reWatt. Kept small and crisp to match a professional dashboard:
 * strong ease-out, sub-200ms feedback, visual-only (no layout shifts).
 */

/** Strong ease-out (cubic-bezier 0.23, 1, 0.32, 1) — punchier than the stock easings. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Fast, snappy spec for press/affordance feedback. */
fun <T> pressTween() = tween<T>(durationMillis = 140, easing = EaseOutStrong)

/**
 * Subtle press feedback: scales the element down slightly while pressed so taps feel responsive.
 * Visual only (does not affect layout). Pass the SAME [interactionSource] you give to
 * `clickable()`/`Button(...)` so the scale tracks real presses.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 140, easing = EaseOutStrong),
        label = "pressScale",
    )
    this.scale(scale)
}
