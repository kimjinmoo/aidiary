package com.grepiu.aidiary.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.grepiu.aidiary.BuildConfig

/**
 * Google AdMob 네이티브 광고 Composable 컴포넌트.
 * 타임라인 피드 내에 카드 형태로 자연스럽게 녹아드는 네이티브 광고입니다.
 * 기본값으로 BuildConfig.ADMOB_NATIVE_ID (debug: 테스트 ID, release: 운영 ID)를 사용합니다.
 */
@Composable
fun AdMobNativeAd(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_NATIVE_ID
) {
    val context = LocalContext.current
    var nativeAdState by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAdState?.destroy()
                nativeAdState = ad
            }
            .build()
        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAdState?.destroy()
            nativeAdState = null
        }
    }

    val currentAd = nativeAdState ?: return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        factory = { ctx ->
            val nativeAdView = NativeAdView(ctx)

            // 메인 카드 컨테이너 (둥근 모서리 + 연한 패딩)
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 28, 32, 28)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(AndroidColor.parseColor("#14808080")) // 반투명 표면 틴트
                    setStroke(2, AndroidColor.parseColor("#25808080"))
                }
                background = shape
            }

            // 상단 행 (광고 배지 + 아이콘 + 헤드라인)
            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val adBadge = TextView(ctx).apply {
                text = "광고"
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(AndroidColor.parseColor("#FFFFFF"))
                setPadding(12, 4, 12, 4)
                val badgeShape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 8f
                    setColor(AndroidColor.parseColor("#6366F1")) // Accent Indigo
                }
                background = badgeShape
            }

            val iconView = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64).apply {
                    setMargins(16, 0, 16, 0)
                }
            }

            val headlineView = TextView(ctx).apply {
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(AndroidColor.parseColor("#222222"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            headerRow.addView(adBadge)
            headerRow.addView(iconView)
            headerRow.addView(headlineView)
            container.addView(headerRow)

            // 본문 설명 (Body)
            val bodyView = TextView(ctx).apply {
                textSize = 12f
                setTextColor(AndroidColor.parseColor("#555555"))
                maxLines = 2
                setPadding(0, 16, 0, 16)
            }
            container.addView(bodyView)

            // CTA 실행 버튼 (Call to Action)
            val ctaButton = Button(ctx).apply {
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(AndroidColor.parseColor("#FFFFFF"))
                val btnShape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(AndroidColor.parseColor("#6366F1"))
                }
                background = btnShape
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(ctaButton)

            // NativeAdView 에 바인딩
            nativeAdView.headlineView = headlineView
            nativeAdView.bodyView = bodyView
            nativeAdView.iconView = iconView
            nativeAdView.callToActionView = ctaButton

            nativeAdView.addView(container)
            nativeAdView
        },
        update = { view ->
            val ad = currentAd
            (view.headlineView as? TextView)?.text = ad.headline

            if (ad.body != null) {
                (view.bodyView as? TextView)?.apply {
                    text = ad.body
                    visibility = View.VISIBLE
                }
            } else {
                view.bodyView?.visibility = View.GONE
            }

            if (ad.icon?.drawable != null) {
                (view.iconView as? ImageView)?.apply {
                    setImageDrawable(ad.icon?.drawable)
                    visibility = View.VISIBLE
                }
            } else {
                view.iconView?.visibility = View.GONE
            }

            if (ad.callToAction != null) {
                (view.callToActionView as? Button)?.apply {
                    text = ad.callToAction
                    visibility = View.VISIBLE
                }
            } else {
                view.callToActionView?.visibility = View.GONE
            }

            view.setNativeAd(ad)
        }
    )
}
