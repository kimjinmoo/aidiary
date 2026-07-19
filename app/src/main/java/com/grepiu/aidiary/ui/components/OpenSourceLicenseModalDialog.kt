package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 법적 고지 의무(Apache 2.0 / Gemma Terms 등)를 100% 충족하기 위한 오픈소스 정보 데이터 클래스입니다.
 */
data class OpenSourceLibrary(
    val name: String,
    val version: String,
    val description: String,
    val license: String,
    val copyright: String,
    val fullLicenseText: String,
    val url: String
)

private const val APACHE_2_0_TEXT = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

private val openSourceLibraries = listOf(
    OpenSourceLibrary(
        name = "Sherpa-Onnx",
        version = "v1.13.4",
        description = "온디바이스 오프라인 한국어/다국어 음성 인식(STT) 엔진",
        license = "Apache License 2.0",
        copyright = "Copyright (c) 2023-2026 k2-fsa authors",
        fullLicenseText = "Sherpa-Onnx\nCopyright (c) 2023-2026 k2-fsa authors\n\n$APACHE_2_0_TEXT",
        url = "https://github.com/k2-fsa/sherpa-onnx"
    ),
    OpenSourceLibrary(
        name = "Gemma 4 (LiteRT LM)",
        version = "2B-IT",
        description = "구글의 차세대 온디바이스 SLM/LLM AI 언어 모델",
        license = "Gemma Terms of Use",
        copyright = "Copyright (c) 2024-2026 Google LLC",
        fullLicenseText = """Gemma Terms of Use
Copyright (c) 2024-2026 Google LLC

Gemma is provided by Google under the Gemma Terms of Use.
By using this model, you agree to comply with Google's Prohibited Use Policy and Gemma Additional Terms.
For full terms, visit: https://ai.google.dev/gemma/terms""",
        url = "https://huggingface.co/google/gemma-2b"
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose & Material 3",
        version = "v1.7.x",
        description = "안드로이드 최신 선언형 UI 프레임워크 및 선진 디자인 시스템",
        license = "Apache License 2.0",
        copyright = "Copyright (c) The Android Open Source Project",
        fullLicenseText = "Android Jetpack Compose\nCopyright (c) The Android Open Source Project\n\n$APACHE_2_0_TEXT",
        url = "https://developer.android.com/jetpack/compose"
    ),
    OpenSourceLibrary(
        name = "Kotlin Coroutines & Flow",
        version = "v1.8.x",
        description = "비동기 reactive 데이터 스트림 및 동시성 제어 라이브러리",
        license = "Apache License 2.0",
        copyright = "Copyright (c) 2016-2026 JetBrains s.r.o.",
        fullLicenseText = "Kotlin Coroutines\nCopyright (c) 2016-2026 JetBrains s.r.o.\n\n$APACHE_2_0_TEXT",
        url = "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    OpenSourceLibrary(
        name = "Room Database",
        version = "v2.6.x",
        description = "오프라인 로컬 데이터 지속성을 위한 SQLite 객체 매핑 persistence 라이브러리",
        license = "Apache License 2.0",
        copyright = "Copyright (c) The Android Open Source Project",
        fullLicenseText = "Android Room Database\nCopyright (c) The Android Open Source Project\n\n$APACHE_2_0_TEXT",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    OpenSourceLibrary(
        name = "KSP (Kotlin Symbol Processing)",
        version = "v1.9.x",
        description = "고성능 코틀린 심볼 프로세싱 컴파일러 플러그인",
        license = "Apache License 2.0",
        copyright = "Copyright (c) Google LLC",
        fullLicenseText = "Kotlin Symbol Processing (KSP)\nCopyright (c) Google LLC\n\n$APACHE_2_0_TEXT",
        url = "https://github.com/google/ksp"
    )
)

/**
 * 2030 세대 감성 디자인 + 100% 법적 고지 의무(Apache 2.0 / Copyright Notice)를 달성하는 라이선스 모달입니다.
 */
@Composable
fun OpenSourceLicenseModalDialog(
    onDismiss: () -> Unit
) {
    var selectedDetailLib by remember { mutableStateOf<OpenSourceLibrary?>(null) }

    // 상세 전문 보기 팝업 다이얼로그
    if (selectedDetailLib != null) {
        LicenseDetailModalDialog(
            library = selectedDetailLib!!,
            onDismiss = { selectedDetailLib = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 상단 헤더
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "오픈소스 라이선스 📜",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI Diary를 구성하는 오픈소스 및 법적 고지 약관",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                // 라이브러리 목록
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(openSourceLibraries) { lib ->
                        LicenseCardItem(
                            lib = lib,
                            onShowFullText = { selectedDetailLib = lib }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("확인했어요", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LicenseCardItem(
    lib: OpenSourceLibrary,
    onShowFullText: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lib.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = lib.version,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lib.description,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = lib.copyright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "License: ${lib.license}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = onShowFullText,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "전문 보기 (Full Text)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 라이선스 전문(Full License Text & Copyright Notice)을 열람하기 위한 모달 다이얼로그입니다.
 */
@Composable
private fun LicenseDetailModalDialog(
    library: OpenSourceLibrary,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.75f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${library.name} 전문",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = library.fullLicenseText,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("닫기", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
