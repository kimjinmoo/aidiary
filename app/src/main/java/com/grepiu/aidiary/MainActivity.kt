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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.ui.util.WindowSizeClass
import com.grepiu.aidiary.ui.util.rememberWindowSizeClass
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
import com.grepiu.aidiary.analytics.AnalyticsManager
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
import com.grepiu.aidiary.ui.screens.DiaryWelcomeScreen
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
        AnalyticsManager.init(this)
        com.google.android.gms.ads.MobileAds.initialize(this)

        setContent {
            val viewModel: DiaryViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            AIDiaryTheme(appTheme = state.appTheme) {
                val context = LocalContext.current
                val view = androidx.compose.ui.platform.LocalView.current

                // 모델 다운로드/압축해제 중 화면 꺼짐 방지 (대용량 작업 중단 방지)
                view.keepScreenOn = state.isDownloadingModel || state.isExtractingModel

                // 앱 백그라운드-포그라운드 수명주기 감지하여 앱 복귀 시 자물쇠 잠금 적용
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            viewModel.processIntent(DiaryIntent.SetAppLockedState(true))
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

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

                // 위치 권한 요청 런처
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (fineGranted || coarseGranted) {
                        viewModel.startLocationFetchFlow()
                    } else {
                        Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                // 온보딩 화면에서 마이크·카메라·위치 권한을 일괄 요청하는 런처
                val allPermissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    viewModel.processIntent(DiaryIntent.AllPermissionsResolved)
                }

                // 갤러리 픽업 런처 (PhotoPicker - 다중 선택)
                val pickImageLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        viewModel.processIntent(DiaryIntent.ImagesPicked(uris))
                    }
                }

                // 비디오 픽업 런처 (단일)
                val pickVideoLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.processIntent(DiaryIntent.VideoPicked(uri))
                    }
                }

                // 클라우드 파일 픽업 런처 (다중 선택)
                val pickCloudLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        viewModel.processIntent(DiaryIntent.CloudFilesPicked(uris))
                    }
                }

                // 백업 파일 생성(내보내기) 런처
                val exportBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
                ) { uri ->
                    if (uri != null) {
                        viewModel.processIntent(DiaryIntent.ExportBackup(uri))
                    }
                }

                // 백업 파일 열기(가져오기) 런처
                val importBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.processIntent(DiaryIntent.ImportBackup(uri))
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
                        // FileProvider 의 content:// URI 를 그대로 전달. ViewModel 이 ContentResolver 로 읽어 복사.
                        viewModel.processIntent(DiaryIntent.CameraImageCaptured(uri))
                    }
                }

                // 일회성 부수 효과(Toast 등) 구독 및 실행
                LaunchedEffect(Unit) {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is DiaryEffect.ShowToast -> {
                                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
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
                            is DiaryEffect.RequestLocationPermission -> {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            is DiaryEffect.RequestAllWelcomePermissions -> {
                                allPermissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            is DiaryEffect.LaunchCamera -> {
                                pendingCameraUri.value = effect.targetUri
                                takePictureLauncher.launch(effect.targetUri)
                            }
                            is DiaryEffect.LaunchVideoPicker -> {
                                pickVideoLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            }
                            is DiaryEffect.LaunchCloudPicker -> {
                                pickCloudLauncher.launch("*/*")
                            }
                            is DiaryEffect.LaunchExportBackupPicker -> {
                                exportBackupLauncher.launch(effect.fileName)
                            }
                            is DiaryEffect.LaunchImportBackupPicker -> {
                                importBackupLauncher.launch(arrayOf("application/zip"))
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
                            onPickCloud = {
                                viewModel.processIntent(DiaryIntent.RequestCloudImport)
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
                        onPickCloud = {
                            viewModel.processIntent(DiaryIntent.RequestCloudImport)
                        },
                        onRequestFullSpaceMode = spatialConfiguration::requestFullSpaceMode
                    )
                }

                // 다이어리 앱 자물쇠 풀스크린 잠금 오버레이
                if (state.isAppLocked) {
                    com.grepiu.aidiary.ui.screens.AppLockScreen(
                        errorText = state.lockErrorText,
                        onUnlock = { pin ->
                            viewModel.processIntent(DiaryIntent.UnlockApp(pin))
                        }
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
    onPickCloud: () -> Unit,
    onRequestHomeSpaceMode: () -> Unit
) {
    SpatialPanel(SubspaceModifier.width(1080.dp).height(720.dp).resizable().movable()) {
        Surface {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
                onPickCloud = onPickCloud,
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
    onPickCloud: () -> Unit,
    onRequestFullSpaceMode: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            DiaryAppNavigationRouter(
                state = state,
                viewModel = viewModel,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
                onPickCloud = onPickCloud,
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
 * 현재 MVI State의 Phase에 근거하여 목록, 작성, 상세 화면을 분기/제어하는 라우터 컴포저블.
 * Phase 전환 방향에 따라 슬라이드-업/다운 혹은 페이드 애니메이션을 적용합니다.
 */
// Phase의 화면 깊이(depth) — 숫자가 클수록 '앞으로 나온' 화면
private fun DiaryPhase.depth(): Int = when (this) {
    DiaryPhase.SPLASH  -> 0
    DiaryPhase.WELCOME -> 1
    DiaryPhase.LIST    -> 2
    DiaryPhase.WRITE   -> 3
    DiaryPhase.DETAIL  -> 3
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DiaryAppNavigationRouter(
    state: DiaryState,
    viewModel: DiaryViewModel,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 태블릿/XR(EXPANDED)에서는 목록+상세 2-pane. 그 외엔 기존 phase 단일 화면.
    val twoPane = rememberWindowSizeClass() == WindowSizeClass.EXPANDED
    // 2-pane 에서는 LIST/DETAIL 이 한 화면(좌 목록·우 상세)이므로 전환 애니메이션을 묶는다.
    val animTarget = if (twoPane && (state.phase == DiaryPhase.LIST || state.phase == DiaryPhase.DETAIL)) {
        DiaryPhase.LIST
    } else {
        state.phase
    }
    AnimatedContent(
        targetState = animTarget,
        transitionSpec = {
            val fromDepth = initialState.depth()
            val toDepth   = targetState.depth()
            when {
                // 앞으로 이동 (LIST→WRITE, LIST→DETAIL)
                toDepth > fromDepth -> (
                    slideInVertically(
                        animationSpec = tween(350),
                        initialOffsetY = { it / 4 }
                    ) + fadeIn(tween(300))
                ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { -it / 8 }
                    ) + fadeOut(tween(200))
                )
                // 뒤로 이동 (WRITE→LIST, DETAIL→LIST)
                toDepth < fromDepth -> (
                    slideInVertically(
                        animationSpec = tween(350),
                        initialOffsetY = { -it / 8 }
                    ) + fadeIn(tween(300))
                ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { it / 4 }
                    ) + fadeOut(tween(200))
                )
                // 같은 깊이 또는 초기 진입 (SPLASH→LIST 등)
                else -> (
                    fadeIn(tween(400))
                ) togetherWith (
                    fadeOut(tween(250))
                )
            }
        },
        label = "PhaseTransition",
        modifier = modifier
    ) { phase ->
        when (phase) {
            DiaryPhase.SPLASH -> {
                DiarySplashScreen(
                    onTimeout = {
                        viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.LIST))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DiaryPhase.WELCOME -> {
                DiaryWelcomeScreen(
                    onIntent = { intent -> viewModel.processIntent(intent) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DiaryPhase.LIST -> {
                if (twoPane) {
                    ExpandedListDetail(state = state, viewModel = viewModel)
                } else {
                    ListRoute(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
                }
            }
            DiaryPhase.WRITE -> {
                DiaryWriteScreen(
                    state = state,
                    onIntent = { intent -> viewModel.processIntent(intent) },
                    onContentTypeChange = { contentType ->
                        viewModel.processIntent(DiaryIntent.UpdateDraftType(contentType))
                    },
                    onUpdateTitleStyle = { style ->
                        viewModel.processIntent(DiaryIntent.UpdateDraftTitleStyle(style))
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
                    onPickVideo = { viewModel.requestVideoImport() },
                    onPickCloud = onPickCloud,
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
                    onUpdateTitle = { text ->
                        viewModel.processIntent(DiaryIntent.UpdateDraftTitle(text))
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
                    onStartDownload = {
                        viewModel.processIntent(DiaryIntent.StartDownload)
                    },
                    onStartSherpaDownload = {
                        viewModel.processIntent(DiaryIntent.StartSherpaDownload)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            DiaryPhase.DETAIL -> {
                DetailRoute(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 목록 화면 라우트 — DiaryListScreen 에 ViewModel 콜백을 배선. 2-pane/단일 화면 양쪽에서 재사용.
 */
@Composable
private fun ListRoute(
    state: DiaryState,
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier
) {
    DiaryListScreen(
        state = state,
        onSelectDiary = { meta -> viewModel.processIntent(DiaryIntent.LoadFullDiary(meta.id)) },
        onWriteDiary = { contentType ->
            viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.WRITE, initialContentType = contentType))
        },
        onStartDownload = { viewModel.processIntent(DiaryIntent.StartDownload) },
        onCancelDownload = { viewModel.processIntent(DiaryIntent.CancelDownload) },
        onDismissNotice = { viewModel.processIntent(DiaryIntent.ShowDownloadNotice(false)) },
        onDismissWifiWarning = { viewModel.processIntent(DiaryIntent.ShowWifiWarning(false)) },
        onStartSherpaDownload = { viewModel.processIntent(DiaryIntent.StartSherpaDownload) },
        onDismissSherpaNotice = { viewModel.processIntent(DiaryIntent.DismissSherpaDownloadNotice) },
        onIntent = { intent -> viewModel.processIntent(intent) },
        modifier = modifier
    )
}

/**
 * 상세 화면 라우트 — 선택된 기록이 있을 때만 렌더. 2-pane/단일 화면 양쪽에서 재사용.
 */
@Composable
private fun DetailRoute(
    state: DiaryState,
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier
) {
    state.selectedDiary?.let { diary ->
        DiaryDetailScreen(
            diary = diary,
            onEdit = { viewModel.processIntent(DiaryIntent.EditDiary(diary)) },
            onDelete = { viewModel.processIntent(DiaryIntent.DeleteDiary(diary.id)) },
            onBack = { viewModel.processIntent(DiaryIntent.NavigateTo(DiaryPhase.LIST)) },
            modifier = modifier
        )
    }
}

/**
 * 태블릿/XR(EXPANDED) 2-pane: 좌측 고정폭 목록 + 우측 상세(또는 안내 플레이스홀더).
 * 목록에서 기록 선택 시 phase=DETAIL 이 되어 우측 pane 이 채워지고, 뒤로가기 시 플레이스홀더로 복귀.
 */
@Composable
private fun ExpandedListDetail(
    state: DiaryState,
    viewModel: DiaryViewModel
) {
    Row(modifier = Modifier.fillMaxSize()) {
        ListRoute(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .width(dimensionResource(id = R.dimen.list_pane_width))
                .fillMaxHeight()
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.phase == DiaryPhase.DETAIL && state.selectedDiary != null) {
                DetailRoute(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                DetailPlaceholder()
            }
        }
    }
}

/**
 * 2-pane 우측 빈 상태 — 목록에서 기록을 고르라는 안내.
 */
@Composable
private fun DetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "왼쪽 목록에서 기록을 선택하세요",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 16.dp)
            )
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
            onPickCloud = {},
            onRequestFullSpaceMode = {}
        )
    }
}