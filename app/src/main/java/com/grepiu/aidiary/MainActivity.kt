package com.grepiu.aidiary

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.mvi.effect.DiaryEffect
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.mvi.viewmodel.DiaryViewModel
import com.grepiu.aidiary.ui.screens.DiaryDetailScreen
import com.grepiu.aidiary.ui.screens.DiaryListScreen
import com.grepiu.aidiary.ui.screens.DiaryWriteScreen
import com.grepiu.aidiary.ui.screens.DiarySplashScreen
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

                // 오디오 권한 요청 런처
                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.processIntent(DiaryIntent.StartRecording)
                    } else {
                        Toast.makeText(context, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                // 카메라 권한 요청 런처
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.requestCameraCapture()
                    } else {
                        Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                // 갤러리 픽업 런처 (PhotoPicker)
                val pickImageLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.processIntent(DiaryIntent.ImagePicked(uri))
                    }
                }

                // 카메라 촬영 런처
                val pendingCameraUri = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)
                }
                val takePictureLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    val uri = pendingCameraUri.value
                    pendingCameraUri.value = null
                    if (success && uri != null) {
                        viewModel.processIntent(DiaryIntent.CameraImageCaptured(uri.toString().removePrefix("file://")))
                    }
                }

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
                            is DiaryEffect.RequestAudioPermission -> {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            is DiaryEffect.RequestCameraPermission -> {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            is DiaryEffect.LaunchCamera -> {
                                pendingCameraUri.value = effect.targetUri
                                takePictureLauncher.launch(effect.targetUri)
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
                            onPickGallery = {
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onTakePhoto = {
                                viewModel.requestCameraCapture()
                            },
                            onRequestHomeSpaceMode = spatialConfiguration::requestHomeSpaceMode
                        )
                    }
                } else {
                    My2DContent(
                        state = state,
                        viewModel = viewModel,
                        onPickGallery = {
                            pickImageLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onTakePhoto = {
                            viewModel.requestCameraCapture()
                        },
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
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRequestHomeSpaceMode: () -> Unit
) {
    SpatialPanel(SubspaceModifier.width(1080.dp).height(720.dp).resizable().movable()) {
        Surface {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
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
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRequestFullSpaceMode: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
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
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.phase) {
        DiaryPhase.SPLASH -> {
            DiarySplashScreen(
                onTimeout = {
                    viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.LIST))
                },
                modifier = modifier
            )
        }
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
                onIntent = { intent -> viewModel.processIntent(intent) },
                modifier = modifier
            )
        }
        DiaryPhase.WRITE -> {
            DiaryWriteScreen(
                state = state,
                onTitleChange = { title ->
                    viewModel.processIntent(DiaryIntent.UpdateDraft(title = title))
                },
                onContentTypeChange = { contentType ->
                    viewModel.processIntent(DiaryIntent.UpdateDraftType(contentType))
                },
                onAddBlock = { block ->
                    viewModel.processIntent(DiaryIntent.AddBlock(block))
                },
                onInsertBlock = { index, block ->
                    viewModel.processIntent(DiaryIntent.InsertBlock(index, block))
                },
                onUpdateBlockText = { blockId, text, formatting ->
                    viewModel.processIntent(DiaryIntent.UpdateBlockText(blockId, text, formatting))
                },
                onUpdateBlockCaption = { blockId, caption ->
                    viewModel.processIntent(DiaryIntent.UpdateBlockCaption(blockId, caption))
                },
                onRemoveBlock = { blockId ->
                    viewModel.processIntent(DiaryIntent.RemoveBlock(blockId))
                },
                onMoveBlock = { blockId, dir ->
                    viewModel.processIntent(DiaryIntent.MoveBlock(blockId, dir))
                },
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
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
                onSuggestTitle = {
                    viewModel.processIntent(DiaryIntent.SuggestTitle)
                },
                onClassifyType = {
                    viewModel.processIntent(DiaryIntent.ClassifyContentType)
                },
                onProofreadBlock = { blockId ->
                    viewModel.processIntent(DiaryIntent.ProofreadBlock(blockId))
                },
                onDecorateBlock = { blockId ->
                    viewModel.processIntent(DiaryIntent.DecorateBlock(blockId))
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
            onPickGallery = {},
            onTakePhoto = {},
            onRequestFullSpaceMode = {}
        )
    }
}