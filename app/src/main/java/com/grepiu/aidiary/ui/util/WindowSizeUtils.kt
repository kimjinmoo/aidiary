package com.grepiu.aidiary.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 현재 창 폭(dp)을 기준으로 [WindowSizeClass] 를 반환한다.
 * COMPACT(<600, 일반 폰) / MEDIUM(600~839, 소형 태블릿·폴더블) / EXPANDED(≥840, 태블릿·XR 패널).
 * LayoutCoordinates 없이 [LocalConfiguration] 만으로 동작해 최상위 라우터에서 바로 쓸 수 있다.
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> WindowSizeClass.COMPACT
        widthDp < 840 -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

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
