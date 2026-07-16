package com.grepiu.aidiary

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import com.grepiu.aidiary.mvi.effect.DiaryEffect
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.mvi.viewmodel.DiaryViewModel
import com.grepiu.aidiary.ui.screens.DiaryDetailScreen
import com.grepiu.aidiary.ui.screens.DiaryListScreen
import com.grepiu.aidiary.ui.screens.DiaryWriteScreen
import com.grepiu.aidiary.ui.theme.AIDiaryTheme

/**
 * 온디바이스 AI 다이어리 앱의 메인 엔트리 액티비티입니다.
 * 2D 모드와 XR 공간 UI 모드를 둘 다 대응합니다.
 */
class MainActivity : ComponentActivity() {

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AIDiaryTheme {
                val viewModel: DiaryViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                val context = LocalContext.current

                // 일회성 부수 효과(Toast 등) 구독 및 실행
                LaunchedEffect(Unit) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is DiaryEffect.ShowToast -> {
                                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                            }
                            is DiaryEffect.AnalysisComplete -> {
                                Toast.makeText(context, "AI 일기 분석이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                            is DiaryEffect.TranscriptionResult -> {
                                Toast.makeText(context, "음성 변환 완료!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val spatialConfiguration = LocalSpatialConfiguration.current
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    Subspace {
                        MySpatialContent(
                            state = state,
                            viewModel = viewModel,
                            onRequestHomeSpaceMode = spatialConfiguration::requestHomeSpaceMode
                        )
                    }
                } else {
                    My2DContent(
                        state = state,
                        viewModel = viewModel,
                        onRequestFullSpaceMode = spatialConfiguration::requestFullSpaceMode
                    )
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun MySpatialContent(
    state: DiaryState,
    viewModel: DiaryViewModel,
    onRequestHomeSpaceMode: () -> Unit
) {
    SpatialPanel(SubspaceModifier.width(1080.dp).height(720.dp).resizable().movable()) {
        Surface {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        Orbiter(
            position = ContentEdge.Top,
            offset = 20.dp,
            alignment = Alignment.End,
            shape = SpatialRoundedCornerShape(CornerSize(28.dp))
        ) {
            HomeSpaceModeIconButton(
                onClick = onRequestHomeSpaceMode,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun My2DContent(
    state: DiaryState,
    viewModel: DiaryViewModel,
    onRequestFullSpaceMode: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
            // XR 기기 세션이 활성화된 상태에서만 공간화 모드 전환 버튼 노출
            if (!LocalInspectionMode.current && LocalSession.current != null) {
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 20.dp)
                )
            }
        }
    }
}

/**
 * 현재 MVI State의 Phase에 근거하여 목록, 작성, 상세 화면을 분기/제어하는 라우터 컴포저블입니다.
 */
@Composable
fun DiaryAppNavigationRouter(
    state: DiaryState,
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier
) {
    when (state.phase) {
        DiaryPhase.LIST -> {
            DiaryListScreen(
                state = state,
                onSelectDiary = { diary ->
                    viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.DETAIL, diary))
                },
                onWriteDiary = {
                    viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.WRITE))
                },
                onStartDownload = {
                    viewModel.processIntent(DiaryIntent.StartDownload)
                },
                onCancelDownload = {
                    viewModel.processIntent(DiaryIntent.CancelDownload)
                },
                onDismissNotice = {
                    viewModel.processIntent(DiaryIntent.ShowDownloadNotice(false))
                },
                onDismissWifiWarning = {
                    viewModel.processIntent(DiaryIntent.ShowWifiWarning(false))
                },
                modifier = modifier
            )
        }
        DiaryPhase.WRITE -> {
            DiaryWriteScreen(
                state = state,
                onTitleChange = { title ->
                    viewModel.processIntent(DiaryIntent.UpdateDraft(title = title))
                },
                onContentChange = { content ->
                    viewModel.processIntent(DiaryIntent.UpdateDraft(content = content))
                },
                onAnalyzeDiary = {
                    viewModel.processIntent(DiaryIntent.AnalyzeDiary)
                },
                onSaveDiary = {
                    viewModel.processIntent(DiaryIntent.SaveDiary)
                },
                onBack = {
                    viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.LIST))
                },
                onStartRecording = {
                    viewModel.processIntent(DiaryIntent.StartRecording)
                },
                onStopRecording = {
                    viewModel.processIntent(DiaryIntent.StopRecording)
                },
                modifier = modifier
            )
        }
        DiaryPhase.DETAIL -> {
            state.selectedDiary?.let { diary ->
                DiaryDetailScreen(
                    diary = diary,
                    onDelete = {
                        viewModel.processIntent(DiaryIntent.DeleteDiary(diary.id))
                    },
                    onBack = {
                        viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.LIST))
                    },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    AIDiaryTheme {
        My2DContent(
            state = DiaryState(),
            viewModel = viewModel(),
            onRequestFullSpaceMode = {}
        )
    }
}