package com.grepiu.aidiary.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

data class AdaptiveLayoutInfo(
    val windowSizeClass: WindowSizeClass,
    val isLandscape: Boolean,
    val widthDp: Float,
    val heightDp: Float
)

@Composable
fun rememberAdaptiveLayoutInfo(
    coordinates: LayoutCoordinates
): AdaptiveLayoutInfo {
    val density = LocalDensity.current
    val size = coordinates.size
    val widthDp = with(density) { size.width.toDp().value }
    val heightDp = with(density) { size.height.toDp().value }
    val isLandscape = widthDp > heightDp
    val windowSizeClass = when {
        widthDp < 600f -> WindowSizeClass.COMPACT
        widthDp < 840f -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
    return remember(widthDp, heightDp) {
        AdaptiveLayoutInfo(windowSizeClass, isLandscape, widthDp, heightDp)
    }
}
