package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import com.grepiu.aidiary.BuildConfig

/**
 * Google AdMob 배너 광고 Composable 컴포넌트.
 * 개발 빌드(debug)에서는 구글 공식 테스트 배너 ID,
 * 서명/AAB 빌드(release)에서는 빌드 설정의 운영 배너 광고 ID를 자동으로 적용합니다.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
