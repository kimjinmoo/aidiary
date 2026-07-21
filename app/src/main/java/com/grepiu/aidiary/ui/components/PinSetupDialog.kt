package com.grepiu.aidiary.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 설정 화면에서 4자리 비밀번호(PIN)를 등록하거나 기존 비밀번호를 검증 해제하기 위한 모달 다이얼로그입니다.
 */
@Composable
fun PinSetupDialog(
    isDisableMode: Boolean = false,
    onPinCompleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(if (isDisableMode) 0 else 1) } // 0: 해제확인, 1: 신규입력, 2: 재확인입력
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val titleText = when (step) {
        0 -> "비밀번호 확인"
        1 -> "비밀번호 설정"
        else -> "비밀번호 재확인"
    }

    val subtitleText = when (step) {
        0 -> "다이어리 잠금을 해제하기 위해\n현재 4자리 비밀번호를 입력해주세요."
        1 -> "다이어리를 보호할\n새로운 4자리 비밀번호를 입력해주세요."
        else -> "확인을 위해 비밀번호를\n한번 더 입력해주세요."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(320.dp)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                // 닫기 버튼 & 자물쇠 아이콘
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4자리 비밀번호 입력 도트 (● ○ ○ ○)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < currentPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                        )
                    }
                }

                // 에러 메시지
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 0~9 번호 키패드 Grid
                KeypadGrid(
                    onKeyClick = { digit ->
                        if (currentPin.length < 4) {
                            errorMessage = null
                            val newPin = currentPin + digit
                            currentPin = newPin

                            if (newPin.length == 4) {
                                when (step) {
                                    0 -> {
                                        // 해제 확인 모드
                                        onPinCompleted(newPin)
                                    }
                                    1 -> {
                                        // 1단계 입력 완료 -> 2단계 재확인
                                        firstPin = newPin
                                        currentPin = ""
                                        step = 2
                                    }
                                    2 -> {
                                        // 2단계 입력 완료 -> 일치 여부 검증
                                        if (newPin == firstPin) {
                                            onPinCompleted(newPin)
                                        } else {
                                            errorMessage = "비밀번호가 일치하지 않습니다."
                                            currentPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (currentPin.isNotEmpty()) {
                            currentPin = currentPin.dropLast(1)
                            errorMessage = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KeypadGrid(
    onKeyClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .then(
                                if (key.isNotEmpty()) {
                                    Modifier.clickable {
                                        if (key == "DEL") onDeleteClick()
                                        else onKeyClick(key)
                                    }
                                } else Modifier
                            )
                    ) {
                        when (key) {
                            "DEL" -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "지우기",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            "" -> { /* 빈 공간 */ }
                            else -> {
                                Text(
                                    text = key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
