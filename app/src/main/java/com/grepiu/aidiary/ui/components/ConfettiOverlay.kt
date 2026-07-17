package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * 꽃가루 파티클 모델
 */
data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val particleSize: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val shapeSquare: Boolean
)

/**
 * 화면 전체에 꽃가루 파티를 날려주는 컴포저블 레이어
 */
@Composable
fun ConfettiOverlay(
    particles: List<ConfettiParticle>,
    modifier: Modifier = Modifier
) {
    if (particles.isEmpty()) return

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        particles.forEach { particle ->
            val path = Path().apply {
                if (particle.shapeSquare) {
                    addRect(
                        Rect(
                            particle.x - particle.particleSize / 2,
                            particle.y - particle.particleSize / 2,
                            particle.x + particle.particleSize / 2,
                            particle.y + particle.particleSize / 2
                        )
                    )
                } else {
                    addOval(
                        Rect(
                            particle.x - particle.particleSize / 2,
                            particle.y - particle.particleSize / 2,
                            particle.x + particle.particleSize / 2,
                            particle.y + particle.particleSize / 2
                        )
                    )
                }
            }
            rotate(
                degrees = particle.rotation,
                pivot = Offset(particle.x, particle.y)
            ) {
                drawPath(path, particle.color)
            }
        }
    }
}
