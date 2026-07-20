package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.ui.theme.Pretendard

/**
 * 앱 전체 다이얼로그 디자인 통일 컴포넌트.
 *
 * 디자인 시스템:
 *  - Shape : RoundedCornerShape(24.dp) 고정
 *  - containerColor : MaterialTheme.colorScheme.surface (테마 자동 대응)
 *  - 아이콘 : 24.dp 사이즈, primaryContainer 배경의 48dp 원형 뱃지
 *  - 타이틀 : titleLarge / ExtraBold / Pretendard
 *  - 본문 : bodyMedium / onSurfaceVariant
 *  - confirmButton : 채워진 Button (primary)
 *  - dismissButton : TextButton (onSurfaceVariant)
 *
 * 위험(Destructive) 액션의 경우 [AppDestructiveDialog] 사용.
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. 기본 AppDialog – 일반 확인/취소 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "취소",
    onDismissAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        icon = icon?.let { iv ->
            {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iv,
                            contentDescription = null,
                            tint = iconTint ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        },
        title = {
            Text(
                text = title,
                fontFamily = Pretendard,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = text,
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                extraActions?.invoke(this)
                if (dismissText != null) {
                    TextButton(onClick = onDismissAction ?: onDismiss) {
                        Text(
                            dismissText,
                            fontFamily = Pretendard,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(confirmText, fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AppDestructiveDialog – 삭제 등 위험 액션용 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppDestructiveDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "취소",
    icon: ImageVector? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        icon = icon?.let { iv ->
            {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        },
        title = {
            Text(
                text = title,
                fontFamily = Pretendard,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                text()
                extraContent?.invoke(this)
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        dismissText,
                        fontFamily = Pretendard,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(confirmText, fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. AppLoadingDialog – 작업 진행 중 로딩 다이얼로그 (닫기 불가)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppLoadingDialog(
    message: String,
) {
    AlertDialog(
        onDismissRequest = {}, // 닫기 불가
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        title = null,
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = message,
                    fontFamily = Pretendard,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {}
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. AppWarningDialog – 경고 액션용 (주황 톤, Wi-Fi 등)
// ─────────────────────────────────────────────────────────────────────────────
private val WarningOrange = Color(0xFFE65100)
private val WarningOrangeContainer = Color(0xFFFFF3E0)

@Composable
fun AppWarningDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "취소",
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        icon = icon?.let { iv ->
            {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WarningOrangeContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iv,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        },
        title = {
            Text(
                text = title,
                fontFamily = Pretendard,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = text,
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        dismissText,
                        fontFamily = Pretendard,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningOrange
                    )
                ) {
                    Text(confirmText, fontFamily = Pretendard, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    )
}
