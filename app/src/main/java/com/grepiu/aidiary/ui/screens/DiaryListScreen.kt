package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.repository.Goal
import com.grepiu.aidiary.data.repository.PlannerTask
import com.grepiu.aidiary.data.repository.DiaryMeta
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.ui.components.DownloadStatusCard
import com.grepiu.aidiary.ui.components.DeviceUnsupportedModalDialog
import com.grepiu.aidiary.ui.theme.DiaryAccent
import com.grepiu.aidiary.ui.theme.PlannerAccent
import com.grepiu.aidiary.ui.theme.GoalsAccent
import com.grepiu.aidiary.ui.theme.ChatAccent
import com.grepiu.aidiary.ui.theme.EmotionJoy
import com.grepiu.aidiary.ui.theme.EmotionSadness
import com.grepiu.aidiary.ui.theme.EmotionAnger
import com.grepiu.aidiary.ui.theme.EmotionAnxiety
import com.grepiu.aidiary.ui.theme.EmotionCalm
import com.grepiu.aidiary.ui.theme.DiaryTypeColor
import com.grepiu.aidiary.ui.theme.PostTypeColor
import com.grepiu.aidiary.ui.theme.NoteTypeColor
import com.grepiu.aidiary.ui.util.DiaryViewMode
import com.grepiu.aidiary.ui.util.DayItem
import com.grepiu.aidiary.ui.util.dateStringOf
import com.grepiu.aidiary.ui.util.buildMonthGrid
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 캘린더 날짜 아이템 모델
 */
data class CalendarDay(
    val dateString: String, // 포맷: yyyy-MM-dd
    val dayName: String,    // "월", "화" ...
    val dayOfMonth: String, // "18"
    val isToday: Boolean
)

/**
 * 플래너 기능이 통합된 고품격 스마트 다이어리 목록/대시보드 화면입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    state: DiaryState,
    onSelectDiary: (DiaryMeta) -> Unit,
    onWriteDiary: (ContentType) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onStartSherpaDownload: () -> Unit = {},
    onDismissSherpaNotice: () -> Unit = {},
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showBackupDialog by remember { mutableStateOf(false) }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Text(
                    text = "데이터 백업 및 복원",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "모든 일기 본문과 분석 데이터, 그리고 첨부된 사진/동영상 미디어를 하나의 ZIP 파일로 내보내거나, 기존 백업에서 전체 복원할 수 있습니다.\n\n※ 복원 시 동일한 날짜의 일기는 덮어써집니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showBackupDialog = false
                            onIntent(DiaryIntent.RequestImportBackup)
                        }
                    ) {
                        Text("가져오기 (Import)", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            showBackupDialog = false
                            onIntent(DiaryIntent.RequestExportBackup)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("내보내기 (Export)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("취소")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 1) 백업/복원 진행 중 로딩 다이얼로그
    if (state.isBackupProcessing) {
        AlertDialog(
            onDismissRequest = {}, // 취소 불가
            title = null,
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = state.backupProgressMessage ?: "데이터 백업/복원을 진행 중입니다…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {},
            dismissButton = null,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2) 백업/복원 성공 안내 다이얼로그
    if (state.backupSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { onIntent(DiaryIntent.DismissBackupSuccess) },
            title = {
                Text(
                    text = "백업 및 복원 완료",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = state.backupSuccessMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onIntent(DiaryIntent.DismissBackupSuccess) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("확인", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 3) Wi-Fi 경고 다이얼로그 (모바일 데이터 다운로드 확인)
    if (state.showWifiWarning) {
        val isSherpa = state.wifiWarningSource == "sherpa"
        val modelName = if (isSherpa) "음성인식 모델 (Sherpa)" else "AI 언어 모델 (Gemma)"
        val downloadSize = if (isSherpa) "약 1.05GB" else "약 2.3GB"
        AlertDialog(
            onDismissRequest = { onIntent(DiaryIntent.ShowWifiWarning(false)) },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100)) },
            title = { Text("Wi-Fi 연결 확인", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "현재 Wi-Fi에 연결되어 있지 않습니다.\n\n" +
                    "${modelName} 다운로드(${downloadSize})는 대용량이므로 " +
                    "Wi-Fi 환경에서 다운로드하는 것을 권장합니다.\n\n" +
                    "모바일 데이터로 진행 시 데이터 요금이 많이 나올 수 있습니다.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSherpa) onStartSherpaDownload() else onStartDownload()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) { Text("데이터로 다운로드", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(DiaryIntent.ShowWifiWarning(false)) }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 4) 기기 사양 부족 상세 안내 다이얼로그 (2030 타깃 세련된 모달)
    if (state.showDeviceUnsupportedDialog) {
        DeviceUnsupportedModalDialog(
            onConfirm = { onIntent(DiaryIntent.UnsupportedDeviceConfirm) }
        )
    }

    // 5) 전문가 수준의 설정(Settings) 페이지 (슬라이드 & 페이드 애니메이션 적용)
    AnimatedVisibility(
        visible = state.isSettingsOpen,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(250))
    ) {
        DiarySettingsScreen(
            state = state,
            onIntent = onIntent,
            onBack = { onIntent(DiaryIntent.ToggleSettingsScreen(false)) }
        )
    }

    if (state.isSettingsOpen) {
        return
    }

    // 캘린더 일주일+ 일자 데이터 생성 (선택된 날짜 기준 앞뒤 7일씩 총 15일 구성)
    val calendarDays = remember(state.selectedDateString) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("E", Locale.KOREAN)
        val numFormat = SimpleDateFormat("d", Locale.getDefault())
        
        val cal = Calendar.getInstance()
        val todayStr = format.format(cal.time)
        
        // 기준일을 선택된 날짜로 설정
        try {
            format.parse(state.selectedDateString)?.let {
                cal.time = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val list = mutableListOf<CalendarDay>()
        for (i in 0..14) {
            val dateStr = format.format(cal.time)
            list.add(
                CalendarDay(
                    dateString = dateStr,
                    dayName = dayFormat.format(cal.time),
                    dayOfMonth = numFormat.format(cal.time),
                    isToday = dateStr == todayStr
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    // 포커스 아웃용
    var newTaskText by remember { mutableStateOf("") }
    var newGoalText by remember { mutableStateOf("") }
    
    // 기록 필터링을 위한 선택된 타입 상태 (null은 전체)
    var selectedTypeFilter by remember { mutableStateOf<ContentType?>(null) }

    // 전역 보기 모드 (리스트/블로그/달력) — ViewModel state.viewMode로 단방향 상태 유지 (화면 전환 후에도 영속)
    val globalViewMode = state.viewMode

    // AI 비서 바텀시트 표시 여부 — 헤더 좌측 AI 버튼으로 열림. 어느 보기 모드에서든 접근.
    var showAiSheet by rememberSaveable { mutableStateOf(false) }

    var isSearchFocused by remember { mutableStateOf(false) }
    val isSearchActive = (state.searchQuery.isNotBlank() || isSearchFocused) && state.activeTab == "DIARY"
    // 헤더/캘린더/탭셀렉터 숨김 (검색 포커스 시). AI는 바텀시트라 헤더에 영향 없음. TopAppBar는 항상 표시.
    val isHeaderHidden = isSearchActive

    var showDatePickerModal by remember { mutableStateOf(false) }

    // 스크롤 기반 헤더 접힘(collapse). 스탯카드+캘린더만 접고 탭바는 sticky 유지.
    // isHeaderHidden(검색/챗 포커스) 와 별개로 동작한다.
    val headerCollapsed = remember { mutableStateOf(false) }
    // 탭 전환 시엔 헤더를 다시 펼쳐 방향감 제공.
    LaunchedEffect(state.activeTab) { headerCollapsed.value = false }
    val collapseScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 아래로 스크롤(콘텐츠가 위로 이동, y<0) → 접기 / 위로 스크롤(y>0) → 펼치기
                if (available.y < -4f) headerCollapsed.value = true
                else if (available.y > 4f) headerCollapsed.value = false
                return Offset.Zero
            }
        }
    }

    // AI 가 추천한 플래너 할 일을 입력란에 1회성으로 반영하고, 사용 후엔 상태를 비웁니다.
    LaunchedEffect(state.suggestedPlannerTaskText) {
        val suggested = state.suggestedPlannerTaskText ?: return@LaunchedEffect
        newTaskText = if (suggested.length > 50) suggested.substring(0, 50) else suggested
        onIntent(DiaryIntent.ClearSuggestedPlannerTask)
    }

    Scaffold(
        topBar = {
            DiaryTopAppBar(
                state = state,
                onIntent = onIntent,
                onShowBackupDialog = { showBackupDialog = it },
                context = context,
                isSearchActive = isSearchActive || isSearchFocused,
                isChatFocused = false,
                isDiaryTab = state.activeTab == "DIARY",
                onSearchClick = {
                    isSearchFocused = true
                },
                onSearchClose = {
                    isSearchFocused = false
                    onIntent(DiaryIntent.ClearDiarySearch)
                },
                onOpenDatePicker = {
                    showDatePickerModal = true
                }
            )
        },
        bottomBar = {
            if (!isHeaderHidden && (globalViewMode != DiaryViewMode.LIST || state.activeTab == "DIARY")) {
                FloatingWritePill(onWriteDiary = onWriteDiary)
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        0.6f to MaterialTheme.colorScheme.background,
                    )
                )
        ) {
            // 전역 헤더 — 좌측 AI 비서 버튼 + 우측 보기 모드 토글(LIST/BLOG/CALENDAR). 헤더 숨김이 아닐 때만.
            if (!isHeaderHidden) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { showAiSheet = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI 비서",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    ViewModeToggle(mode = globalViewMode, onModeChange = { onIntent(DiaryIntent.SelectViewMode(it)) })
                }
            }

            when (globalViewMode) {
                DiaryViewMode.LIST -> {
            if (!isHeaderHidden) {
                // ── AI 상태 표시줄 ──
                AiStatusBar(
                    state = state,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload,
                    onDismissNotice = onDismissNotice,
                    onStartSherpaDownload = onStartSherpaDownload,
                    onDismissSherpaNotice = onDismissSherpaNotice,
                    onUnsupportedDeviceClose = { onIntent(DiaryIntent.UnsupportedDeviceClose) }
                )

                // ── 데일리 스탯 카드 + 캘린더 (스크롤 시 접힘) ──
                AnimatedVisibility(
                    visible = !headerCollapsed.value,
                    enter = expandVertically(tween(220), expandFrom = Alignment.Top) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(160))
                ) {
                    Column {
                        DailyOverviewHeader(
                            state = state,
                            onIntent = onIntent,
                            showBackupDialog = showBackupDialog,
                            onShowBackupDialog = { showBackupDialog = it },
                            context = context
                        )

                        // A. 글래스모피즘 캘린더 스트립 컨테이너 (LIST 모드에서 항상 표시)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            tonalElevation = 1.dp,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            WeeklyCalendarStrip(
                                days = calendarDays,
                                selectedDateStr = state.selectedDateString,
                                diaryDates = state.diaryDates,
                                plannerTasks = state.plannerTasks,
                                goals = state.goals,
                                onDateSelect = { onIntent(DiaryIntent.SelectDate(it)) }
                            )
                        }
                    }
                }

            // B. 세그먼티드 탭 셀렉터 (다이어리, 플래너, 나의 목표) — sticky (접히지 않음). AI는 헤더 버튼으로 분리.
            TabSelector(
                activeTab = state.activeTab,
                onTabSelect = { onIntent(DiaryIntent.ChangeTab(it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            }

            // C. 탭별 메인 컨텐츠 영역 (애니메이션 전환)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(collapseScrollConnection)
                    .padding(horizontal = 16.dp)
            ) {
                val tabOrder = remember { listOf("DIARY", "PLANNER", "GOALS") }
                AnimatedContent(
                    targetState = state.activeTab,
                    transitionSpec = {
                        // 탭 순서에 따라 좌/우 방향 슬라이드 + 페이드 (세그먼트 페이저 느낌)
                        val fromIdx = tabOrder.indexOf(initialState).coerceAtLeast(0)
                        val toIdx = tabOrder.indexOf(targetState).coerceAtLeast(0)
                        val dir = if (toIdx >= fromIdx) 1 else -1
                        (slideInHorizontally(tween(240)) { w -> dir * w / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { w -> -dir * w / 6 } + fadeOut(tween(160)))
                    },
                    label = "TabContentTransition",
                    modifier = Modifier.fillMaxSize()
                ) { activeTab ->
                    when (activeTab) {
                    "DIARY" -> {
                        // 일기 탭 렌더링
                        DiaryTabContent(
                            state = state,
                            selectedTypeFilter = selectedTypeFilter,
                            onTypeFilterChange = { selectedTypeFilter = it },
                            onSelectDiary = onSelectDiary,
                            onLoadMore = { onIntent(DiaryIntent.LoadMoreDiaries) },
                            onWriteDiary = { contentType -> onWriteDiary(contentType) },
                            onStartDownload = onStartDownload,
                            onCancelDownload = onCancelDownload,
                            onDismissNotice = onDismissNotice,
                            onDismissWifiWarning = onDismissWifiWarning,
                            onStartSherpaDownload = onStartSherpaDownload,
                            onDismissSherpaNotice = onDismissSherpaNotice,
                            onSearch = { onIntent(DiaryIntent.SearchDiaries(it)) },
                            onClearSearch = { onIntent(DiaryIntent.ClearDiarySearch) },
                            isSearchFocused = isSearchFocused,
                            onSearchFocusChange = { isSearchFocused = it },
                            onCancelSearch = {
                                isSearchFocused = false
                                onIntent(DiaryIntent.ClearDiarySearch)
                            },
                            onDateSelectFromCalendar = { onIntent(DiaryIntent.SelectDate(it)) }
                        )
                    }
                    "PLANNER" -> {
                        // 플래너 할 일 탭 렌더링
                        PlannerTabContent(
                            state = state,
                            newTaskText = newTaskText,
                            onTextChange = { if (it.length <= 50) newTaskText = it },
                            onAddTask = { text, start, end, loc, repeat, days, endDate ->
                                if (text.isNotBlank()) {
                                    onIntent(
                                        DiaryIntent.AddPlannerTask(
                                            text = text,
                                            dateString = state.selectedDateString,
                                            startTime = start,
                                            endTime = end,
                                            location = loc,
                                            isRepeat = repeat,
                                            repeatDays = days,
                                            repeatEndDateString = endDate
                                        )
                                    )
                                    newTaskText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            onToggleTask = { onIntent(DiaryIntent.TogglePlannerTask(it)) },
                            onDeleteTask = { task -> onIntent(DiaryIntent.DeletePlannerTask(task.id)) },
                            onDeleteTaskSeries = { task ->
                                task.seriesId?.let { sid -> onIntent(DiaryIntent.DeletePlannerTaskSeries(sid)) }
                            },
                            onSuggestTask = { intent -> onIntent(intent) },
                            onClearSuggestedTask = { onIntent(DiaryIntent.ClearSuggestedPlannerTask) },
                            onRequestBriefing = { onIntent(DiaryIntent.RequestBriefing("PLANNER")) }
                        )
                    }
                    "GOALS" -> {
                        // 목표 관리 탭 렌더링
                        GoalsTabContent(
                            state = state,
                            newGoalText = newGoalText,
                            onTextChange = { newGoalText = it },
                            onAddGoal = { text, category ->
                                if (text.isNotBlank()) {
                                    onIntent(DiaryIntent.AddGoal(text, category))
                                    newGoalText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            onToggleGoal = { onIntent(DiaryIntent.ToggleGoal(it)) },
                            onDeleteGoal = { onIntent(DiaryIntent.DeleteGoal(it)) },
                            onRequestBriefing = { onIntent(DiaryIntent.RequestBriefing("GOALS")) }
                        )
                    }
                    }
                }
            }
                }
                DiaryViewMode.BLOG -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        UnifiedBlogView(
                            diaries = state.allDiaryMetas,
                            tasks = state.plannerTasks,
                            goals = state.goals,
                            onSelectDiary = onSelectDiary
                        )
                    }
                }
                DiaryViewMode.CALENDAR -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        UnifiedCalendarView(
                            diaries = state.allDiaryMetas,
                            tasks = state.plannerTasks,
                            goals = state.goals,
                            diaryDatesUnified = com.grepiu.aidiary.ui.util.unifiedDateSet(state.allDiaryMetas, state.plannerTasks, state.goals),
                            selectedDateString = state.selectedDateString,
                            onDateSelect = { onIntent(DiaryIntent.SelectDate(it)) },
                            onSelectDiary = onSelectDiary
                        )
                    }
                }
            }

            // 자체 커스텀 날짜 선택 달력 모달 (DatePickerDialog 대체)
            if (showDatePickerModal) {
                CustomDatePickerModal(
                    initialDateString = state.selectedDateString,
                    diaryDatesUnified = com.grepiu.aidiary.ui.util.unifiedDateSet(state.allDiaryMetas, state.plannerTasks, state.goals),
                    onDismiss = { showDatePickerModal = false },
                    onDateSelected = { dateStr ->
                        onIntent(DiaryIntent.SelectDate(dateStr))
                        showDatePickerModal = false
                    }
                )
            }

            // AI 비서 바텀시트 — 헤더 좌측 버튼으로 열림. 프리셋 칩 + 기존 챗봇 UI 재사용.
            if (showAiSheet) {
                AiAssistantSheet(
                    state = state,
                    onDismiss = { showAiSheet = false },
                    onSendChat = { onIntent(DiaryIntent.SendChatMessage(it)) },
                    onClearHistory = { onIntent(DiaryIntent.ClearChatHistory) },
                    onPreset = { kind -> onIntent(DiaryIntent.RequestAiPreset(kind)) },
                    onStartDownload = onStartDownload
                )
            }
        }
    }
}

/**
 * TopAppBar — 노멀 / 검색 / 채팅 모드 간 부드러운 애니메이션 전환
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryTopAppBar(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    onShowBackupDialog: (Boolean) -> Unit,
    context: android.content.Context,
    isSearchActive: Boolean,
    isChatFocused: Boolean,
    isDiaryTab: Boolean,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onOpenDatePicker: () -> Unit
) {
    val barMode = when {
        isSearchActive && isDiaryTab -> "search"
        isChatFocused -> "chat"
        else -> "normal"
    }

    val transitionSpec: AnimatedContentTransitionScope<String>.() -> ContentTransform = {
        when {
            initialState == "normal" && targetState == "search" ->
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200)))
            initialState == "search" && targetState == "normal" ->
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
            else ->
                (fadeIn(tween(250)) togetherWith fadeOut(tween(200)))
        }
    }

    AnimatedContent(
        targetState = barMode,
        transitionSpec = transitionSpec,
        label = "TopAppBarMode",
    ) { mode ->
        when (mode) {
            "search" -> SearchModeTopBar(state, onIntent, onSearchClose)
            "chat" -> ChatModeTopBar()
            else -> NormalModeTopBar(state, onIntent, onShowBackupDialog, context, isDiaryTab, onSearchClick, onOpenDatePicker)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchModeTopBar(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    onSearchClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var searchText by rememberSaveable(state.searchQuery) { mutableStateOf(state.searchQuery) }
    LaunchedEffect(state.searchQuery) { searchText = state.searchQuery }

    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    if (it.trim().isNotBlank()) onIntent(DiaryIntent.SearchDiaries(it))
                    else onIntent(DiaryIntent.ClearDiarySearch)
                },
                singleLine = true,
                placeholder = { Text("다이어리 기록 검색...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (searchText.isNotBlank()) {
                        IconButton(onClick = {
                            searchText = ""
                            onIntent(DiaryIntent.ClearDiarySearch)
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "지우기", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                searchText = ""
                focusManager.clearFocus()
                onSearchClose()
            }) {
                Icon(Icons.Default.ArrowBack, "검색 닫기")
            }
        },
        actions = {},
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatModeTopBar() {
    TopAppBar(
        title = {
            Text("AI 비서", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalModeTopBar(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    onShowBackupDialog: (Boolean) -> Unit,
    context: android.content.Context,
    isDiaryTab: Boolean,
    onSearchClick: () -> Unit,
    onOpenDatePicker: () -> Unit
) {
    val isToday = remember(state.selectedDateString) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) == state.selectedDateString
    }
    val title = "오늘의 기록"

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isToday) {
                    val sub = remember(state.selectedDateString) {
                        try {
                            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val date = format.parse(state.selectedDateString) ?: Date()
                            SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN).format(date)
                        } catch (_: Exception) { state.selectedDateString }
                    }
                    Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        },
        actions = {
            if (isDiaryTab) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, "검색", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isToday) {
                FilledTonalButton(
                    onClick = {
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        onIntent(DiaryIntent.SelectDate(todayStr))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(32.dp)
                ) { Text("오늘", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            IconButton(onClick = onOpenDatePicker) {
                Icon(Icons.Default.DateRange, "날짜 선택 모달", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onIntent(DiaryIntent.ToggleSettingsScreen(true)) }) {
                Icon(Icons.Default.Settings, "설정", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    )
}

/**
 * AI 상태 컴팩트 바 — 모델 다운로드/초기화/준비 상태를 AppBar 아래에 표시
 */
@Composable
private fun AiStatusBar(
    state: DiaryState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onStartSherpaDownload: () -> Unit,
    onDismissSherpaNotice: () -> Unit,
    onUnsupportedDeviceClose: () -> Unit = {}
) {
    val showLlmNotice = !state.isModelReady && !state.isLowRamDevice && !state.isDeviceUnsupported && state.showDownloadNotice
    val showSherpaNotice = !state.isSherpaModelReady && state.showSherpaDownloadNotice

    val showAiStatus = state.isDownloadingModel ||
        state.isExtractingModel || state.isModelInitializing ||
        showLlmNotice || showSherpaNotice ||
        state.isDeviceUnsupported

    if (!showAiStatus) return

    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        visible.value = true
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(tween(300)) + expandVertically(tween(300), expandFrom = Alignment.Top),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(max = 132.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── LLM 다운로드 안내 ──
            if (showLlmNotice && !state.isDownloadingModel && !state.isExtractingModel) {
                NoticeRow(
                    emoji = "\uD83E\uDDE0", label = "AI 언어 모델 설치 (2.3GB)",
                    buttonLabel = "다운로드", onAction = onStartDownload, onDismiss = onDismissNotice
                )
            }
            // ── STT 다운로드 안내 ──
            if (showSherpaNotice && !state.isDownloadingModel && !state.isExtractingModel) {
                NoticeRow(
                    emoji = "\uD83C\uDF99\uFE0F", label = "음성인식 모델 설치 (1.0GB)",
                    buttonLabel = "다운로드", onAction = onStartSherpaDownload, onDismiss = onDismissSherpaNotice
                )
            }
            // ── 다운로드 진행 ──
            if (state.isDownloadingModel || state.isExtractingModel) {
                ProgressRow(state, onCancelDownload)
            }
            // ── 초기화 중 ──
            if (state.isModelInitializing) {
                InitRow()
            }
            // ── 기기 미지원 ──
            if (state.isDeviceUnsupported) {
                UnsupportedRow(state, onUnsupportedDeviceClose)
            }
            // ── 준비 완료 ──
            if (state.isModelReady && !state.isDownloadingModel && !state.isExtractingModel &&
                !state.isDeviceUnsupported && !state.showDownloadNotice && !state.showSherpaDownloadNotice) {
                ReadyRow()
            }
        }
    }
}

@Composable
private fun NoticeRow(emoji: String, label: String, buttonLabel: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                Text(buttonLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                Text("닫기", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressRow(state: DiaryState, onCancel: () -> Unit) {
    val progress = state.modelDownloadProgress
    val pct = (progress * 100).toInt()
    val isExtracting = state.isExtractingModel

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isExtracting) "압축 해제 중… $pct%"
                               else "AI 다운로드 $pct%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "⚠ 화면 끄지 마세요 · 꺼지면 처음부터 재시작",
                        fontSize = 10.sp,
                        color = Color(0xFFE65100),
                        letterSpacing = (-0.1).sp
                    )
                }
                if (!isExtracting) {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("취소", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isExtracting) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}


@Composable
private fun InitRow() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text("AI 메모리 로딩 중…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun UnsupportedRow(state: DiaryState, onClose: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(state.deviceUnsupportedReason ?: "AI 사용에 사양이 부족합니다", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                Text("닫기", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReadyRow() {
    Surface(color = GoalsAccent.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(GoalsAccent))
            Spacer(Modifier.width(6.dp))
            Text("AI 준비됨", fontSize = 11.sp, color = GoalsAccent, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * 데일리 스탯 카드 — 오늘 기록 / 전체 기록 / 상태 한눈에 보기
 */
@Composable
private fun DailyOverviewHeader(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    showBackupDialog: Boolean,
    onShowBackupDialog: (Boolean) -> Unit,
    context: android.content.Context
) {
    val todayEntryCount = remember(state.diaries, state.selectedDateString) {
        state.diaries.count {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == state.selectedDateString
        }
    }
    val totalEntries = state.diaryTotalCount

    val isToday = remember(state.selectedDateString) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) == state.selectedDateString
    }
    val isFuture = remember(state.selectedDateString) {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val selected = format.parse(state.selectedDateString) ?: return@remember false
            selected.after(Date())
        } catch (_: Exception) { false }
    }

    val (dateIcon, dateLabel, dateValue) = when {
        isToday -> Triple(Icons.Default.WbSunny, "오늘", "기록 중")
        isFuture -> Triple(Icons.Default.Event, "예정", "미래")
        else -> Triple(Icons.Default.History, "탐색", "과거")
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            StatChip(Icons.AutoMirrored.Filled.MenuBook, "오늘 기록", "${todayEntryCount}개", DiaryAccent, Modifier.weight(1f))
            StatChip(Icons.Default.CollectionsBookmark, "전체 기록", "${totalEntries}개", PlannerAccent, Modifier.weight(1f))
            StatChip(
                dateIcon, dateLabel, dateValue,
                if (isToday || isFuture) GoalsAccent else ChatAccent,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.08f), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 플로팅 하단 글쓰기 필 — FAB 스타일의 떠 있는 CTA 버튼
 */
@Composable
fun FloatingWritePill(
    onWriteDiary: (ContentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "FloatPillScale"
    )
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 12.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(54.dp).scale(scale)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) {
                    onWriteDiary(ContentType.DIARY)
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.EditNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("기록하기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.2.sp)
            }
        }
    }
}

/**
 * 1. 주간 캘린더 스트립 컴포저블
 */
@Composable
fun WeeklyCalendarStrip(
    days: List<CalendarDay>,
    selectedDateStr: String,
    diaryDates: Set<String>,
    plannerTasks: List<PlannerTask>,
    goals: List<Goal>,
    onDateSelect: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDateStr, days) {
        val index = days.indexOfFirst { it.dateString == selectedDateStr }
        if (index >= 0) {
            val targetIndex = (index - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(days, key = { it.dateString }) { day ->
                val isSelected = day.dateString == selectedDateStr

                val hasDiary = diaryDates.contains(day.dateString)
                val hasTask = remember(plannerTasks, day.dateString) {
                    plannerTasks.any { task -> task.dateString == day.dateString }
                }
                val hasGoal = remember(goals, day.dateString) {
                    goals.any { goal ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(goal.timestamp)) == day.dateString
                    }
                }

                val defaultSubColor = MaterialTheme.colorScheme.onSurfaceVariant
                val dayOfWeekColor = remember(day.dayName, isSelected, defaultSubColor) {
                    if (isSelected) Color.White.copy(alpha = 0.85f)
                    else when (day.dayName) {
                        "일" -> Color(0xFFE53935)
                        "토" -> Color(0xFF3D7BB5)
                        else -> defaultSubColor
                    }
                }

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "CalendarScale"
                )

                val cardBgColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarBgColor"
                )

                val dayTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarTextColor"
                )

                val borderStroke = when {
                    day.isToday && !isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    isSelected -> null
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                }

                Surface(
                    onClick = { onDateSelect(day.dateString) },
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = borderStroke,
                    shadowElevation = if (isSelected) 4.dp else 0.dp,
                    modifier = Modifier
                        .scale(scale)
                        .width(54.dp)
                        .height(78.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 9.dp, horizontal = 4.dp)
                    ) {
                        // 요일 라벨
                        Text(
                            text = if (day.isToday) "오늘" else day.dayName,
                            fontSize = 11.5.sp,
                            color = if (day.isToday && !isSelected) MaterialTheme.colorScheme.primary else dayOfWeekColor,
                            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        // 일자 수치
                        Text(
                            text = day.dayOfMonth,
                            fontSize = 18.sp,
                            color = dayTextColor,
                            fontWeight = FontWeight.Bold
                        )

                        // 하단 3색 미니 도트 (기록/계획/목표)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(7.dp)
                        ) {
                            if (hasDiary) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else DiaryAccent)
                                )
                            }
                            if (hasTask) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.85f) else PlannerAccent)
                                )
                            }
                            if (hasGoal) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.7f) else GoalsAccent)
                                )
                            }
                            if (!hasDiary && !hasTask && !hasGoal) {
                                Spacer(modifier = Modifier.height(5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. 세그먼티드 탭 셀렉터 컴포저블
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabSelector(
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    val tabs = remember {
        listOf(
            Triple("DIARY", "기록", Icons.AutoMirrored.Filled.MenuBook),
            Triple("PLANNER", "계획", Icons.Default.DateRange),
            Triple("GOALS", "목표", Icons.Default.TaskAlt)
        )
    }
    val selectedIndex = remember(activeTab) {
        tabs.indexOfFirst { it.first == activeTab }.coerceAtLeast(0)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(42.dp)
        ) {
            val maxWidth = this.maxWidth
            val tabWidth = maxWidth / 3

            // 슬라이딩 백그라운드 인디케이터
            val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                ),
                label = "TabIndicatorOffset"
            )
            val indicatorColor by animateColorAsState(
                targetValue = tabAccentColor(activeTab),
                animationSpec = tween(durationMillis = 300),
                label = "TabIndicatorColor"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(indicatorColor, indicatorColor.copy(alpha = 0.85f))
                        )
                    )
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = indicatorColor.copy(alpha = 0.25f),
                        spotColor = indicatorColor.copy(alpha = 0.2f)
                    )
            )

            // 탭 항목들
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                tabs.forEachIndexed { index, (tabId, label, icon) ->
                    val isSelected = index == selectedIndex
                    val itemTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 200),
                        label = "TabTextColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTabSelect(tabId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                // 선택: 흰색(액센트 배경 위) / 미선택: 탭별 고유 액센트색 → 4색 구분 유지
                                tint = if (isSelected) itemTextColor else tabAccentColor(tabId),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = itemTextColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. [다이어리] 탭 본문 영역
 */
@Composable
fun DiaryTabContent(
    state: DiaryState,
    selectedTypeFilter: ContentType?,
    onTypeFilterChange: (ContentType?) -> Unit,
    onSelectDiary: (DiaryMeta) -> Unit,
    onLoadMore: () -> Unit,
    onWriteDiary: (ContentType) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onStartSherpaDownload: () -> Unit = {},
    onDismissSherpaNotice: () -> Unit = {},
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    isSearchFocused: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    onCancelSearch: () -> Unit,
    onDateSelectFromCalendar: (String) -> Unit
) {
    // 리스트는 선택일 기록(selectedDateDiaries, 페이지네이션 무관 조회)을 쓰므로 진입 시 로드.
    LaunchedEffect(Unit) { onDateSelectFromCalendar(state.selectedDateString) }

    // 선택된 날짜의 포맷 변환 (예: 2026-07-18 -> 7월 18일)
    val parsedDateText = remember(state.selectedDateString) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(state.selectedDateString) ?: Date()
            SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(date)
        } catch (e: Exception) {
            state.selectedDateString
        }
    }

    val isSearchMode = state.searchQuery.isNotBlank()

    // 선택된 날짜에 쓴 일기 및 유형(ContentType) 필터링.
    // 검색 모드: 검색 결과(state.diaries) 그대로.
    // 날짜 모드: selectedDateDiaries(날짜 범위 조회 결과, 페이지네이션 무관 → 오래된 날짜도 정상) + 타입 필터.
    val filteredDiaries = remember(state.selectedDateDiaries, state.diaries, selectedTypeFilter, isSearchMode) {
        when {
            isSearchMode -> state.diaries
            selectedTypeFilter != null -> state.selectedDateDiaries.filter { it.contentType == selectedTypeFilter }
            else -> state.selectedDateDiaries
        }
    }

    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 타입 필터 (고정 헤더). 보기 모드 토글은 전역 상단바로 이동됨.
        DiaryTypeFilterRow(
            selectedTypeFilter = selectedTypeFilter,
            onTypeFilterChange = onTypeFilterChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )

        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 선택일 날짜 타이틀 + 기록 수 배지 (보기 모드 토글/타입 필터는 공통 헤더로 이동됨)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    val typeName = when {
                        isSearchMode -> "'${state.searchQuery}' 검색 결과"
                        selectedTypeFilter == ContentType.DIARY -> "일기"
                        selectedTypeFilter == ContentType.POST -> "새 글"
                        selectedTypeFilter == ContentType.NOTE -> "메모"
                        else -> "전체 기록"
                    }
                    Text(
                        text = if (isSearchMode) typeName
                               else parsedDateText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "${filteredDiaries.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

        // 일기 항목들
        if (filteredDiaries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp)
                    ) {
                        val typeName = when (selectedTypeFilter) {
                            ContentType.DIARY -> "일기"
                            ContentType.POST -> "새 글"
                            ContentType.NOTE -> "메모"
                            null -> "기록"
                        }
                        val emptyIcon = when (selectedTypeFilter) {
                            ContentType.DIARY -> Icons.AutoMirrored.Filled.MenuBook
                            ContentType.POST -> Icons.Default.EditNote
                            ContentType.NOTE -> Icons.Default.NoteAlt
                            null -> Icons.AutoMirrored.Filled.MenuBook
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = emptyIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "$parsedDateText 에 작성된 ${typeName}가 없습니다",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "오늘 있었던 일이나 생각을 기록해보세요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                    }
                }
            }
        } else {
            items(filteredDiaries.size, key = { filteredDiaries[it].id }) { index ->
                val diary = filteredDiaries[index]
                val itemVisible = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 60L)
                    itemVisible.value = true
                }
                AnimatedVisibility(
                    visible = itemVisible.value,
                    enter = fadeIn(tween(400, delayMillis = 0)) + 
                            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 4 }
                ) {
                    DiaryListItemCard(diary = diary, onClick = { onSelectDiary(diary) })
                }
            }
            // 날짜 모드는 selectedDateDiaries(그날 전체 조회)라 페이지네이션 불필요.
            // 검색 모드 결과 페이지네이션이 필요하면 별도 처리(현재 검색은 단일 결과 세트).
        }

        // 여백 공간 확보
        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
        }
    }
}

/**
 * 다이어리 검색바 v4. FTS5 기반으로 제목/본문에서 부분 문자열 + 날짜 가중치로 정렬된 결과를
 * [DiaryState.diaries] 에 채워넣는다. 검색 활성 시 우측에 '취소' 버튼이 슬라이드-인된다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DiarySearchBar(
    query: String,
    isSearching: Boolean,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var localText by rememberSaveable(query) { mutableStateOf(query) }
    LaunchedEffect(query) { localText = query }

    // 시스템 백버튼등으로 키보드 닫혀도 포커스 해제 안 되는 문제 해결
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isFocused) {
            focusManager.clearFocus()
        }
    }

    val isActive = isFocused || localText.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = localText,
            onValueChange = {
                localText = it
                if (it.trim().isNotBlank()) {
                    onSubmit(it)
                } else {
                    onClear()
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp)
                ),
            singleLine = true,
            placeholder = {
                Text(
                    text = "다이어리 기록 검색...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                when {
                    isSearching -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    localText.isNotBlank() -> {
                        IconButton(
                            onClick = {
                                localText = ""
                                onClear()
                                // 텍스트만 지우고 포커스는 유지 (취소는 별도 버튼)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "텍스트 지우기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (localText.isNotBlank()) onSubmit(localText.trim())
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(24.dp)
        )

        // 취소 버튼 — 검색 활성 시 슬라이드-인
        AnimatedVisibility(
            visible = isActive,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            TextButton(
                onClick = {
                    localText = ""
                    focusManager.clearFocus()
                    onCancel()
                },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "취소",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


/**
 * 4. [계획] 탭 본문 영역 — 프로페셔널 태스크 플래너 UI
 */
@Composable
fun PlannerTabContent(
    state: DiaryState,
    newTaskText: String,
    onTextChange: (String) -> Unit,
    onAddTask: (String, String?, String?, String?, Boolean, List<Int>, String?) -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (PlannerTask) -> Unit,
    onDeleteTaskSeries: (PlannerTask) -> Unit,
    onSuggestTask: (DiaryIntent.SuggestPlannerTask) -> Unit,
    onClearSuggestedTask: () -> Unit = {},
    onRequestBriefing: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val tasksForDate = remember(state.plannerTasks, state.selectedDateString) {
        state.plannerTasks.filter { it.dateString == state.selectedDateString }
    }

    val parsedDateText = remember(state.selectedDateString) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(state.selectedDateString) ?: Date()
            SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(date)
        } catch (e: Exception) { state.selectedDateString }
    }

    var startTime by remember { mutableStateOf<String?>(null) }
    var endTime by remember { mutableStateOf<String?>(null) }
    var locationText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) } // "time", "location", "repeat" — 한 번에 하나만
    var isRepeat by remember { mutableStateOf(false) }
    val selectedDays = remember { mutableStateListOf<Int>() }
    var repeatEndDateStr by remember { mutableStateOf<String?>(null) }
    var taskPendingDelete by remember { mutableStateOf<PlannerTask?>(null) }

    var showTimePickerModal by remember { mutableStateOf(false) }
    var isPickingStartTime by remember { mutableStateOf(true) }
    var showRepeatDatePickerModal by remember { mutableStateOf(false) }

    val showTimePicker = { isStart: Boolean ->
        isPickingStartTime = isStart
        showTimePickerModal = true
    }

    val showEndDatePicker = {
        showRepeatDatePickerModal = true
    }

    val resetForm = {
        startTime = null; endTime = null; locationText = ""
        isRepeat = false; selectedDays.clear(); repeatEndDateStr = null
        isExpanded = false; expandedSection = null
    }

    val handleAddTask = {
        if (newTaskText.isNotBlank()) {
            onAddTask(newTaskText, startTime, endTime, locationText.takeIf { it.isNotBlank() }, isRepeat, selectedDays.toList(), repeatEndDateStr)
            resetForm()
            focusManager.clearFocus()
        } else {
            android.widget.Toast.makeText(context, "할 일을 입력해주세요.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val hasDetail = startTime != null || endTime != null || locationText.isNotBlank() || isRepeat

    // AI 추천 결과가 생성되면 입력 폼 텍스트필드에 자동으로 세팅
    LaunchedEffect(state.suggestedPlannerTaskText) {
        if (!state.suggestedPlannerTaskText.isNullOrBlank()) {
            onTextChange(state.suggestedPlannerTaskText)
            onClearSuggestedTask()
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ── 입력 폼 ──
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 메인 입력행: 텍스트 필드 + 추가 버튼
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTaskText,
                            onValueChange = onTextChange,
                            placeholder = { Text("$parsedDateText 계획 추가...", fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                Text(
                                    "${newTaskText.length}/50",
                                    fontSize = 11.sp,
                                    color = if (newTaskText.length >= 50) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { handleAddTask() }),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(200)
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { handleAddTask() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, "추가", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }

                    // 퀵 옵션 칩 행 (저해상도/좁은 화면 대응 가로 스크롤)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // 시간 칩
                        FilterChip(
                            selected = startTime != null || endTime != null,
                            onClick = {
                                val next = if (expandedSection == "time") null else "time"
                                expandedSection = next
                                isExpanded = next != null
                            },
                            label = {
                                Text(
                                    if (startTime != null || endTime != null) {
                                        listOfNotNull(startTime, endTime).joinToString("~")
                                    } else "시간",
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                        // 장소 칩
                        FilterChip(
                            selected = locationText.isNotBlank(),
                            onClick = {
                                val next = if (expandedSection == "location") null else "location"
                                expandedSection = next
                                isExpanded = next != null
                            },
                            label = { Text(if (locationText.isNotBlank()) locationText.take(8) else "장소", fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                        // 반복 칩
                        FilterChip(
                            selected = isRepeat,
                            onClick = {
                                val next = if (expandedSection == "repeat") null else "repeat"
                                expandedSection = next
                                isExpanded = next != null
                            },
                            label = { Text(if (isRepeat && selectedDays.isNotEmpty()) "매주" else "반복", fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                        // AI 추천 칩
                        if (state.isModelReady) {
                            AssistChip(
                                onClick = {
                                    onSuggestTask(DiaryIntent.SuggestPlannerTask(
                                        startTime = startTime, endTime = endTime,
                                        location = locationText.takeIf { it.isNotBlank() },
                                        isRepeat = isRepeat, repeatDays = selectedDays.toList(), repeatEndDateString = repeatEndDateStr
                                    ))
                                },
                                label = {
                                    Text(
                                        if (state.isSuggestingPlannerTask) "✨ AI 생성 중..." else "✨ AI 자동 추천",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    if (state.isSuggestingPlannerTask) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }

                    // 상세 옵션 확장 — expandedSection 에 따라 한 섹션만 표시
                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                            Spacer(Modifier.height(10.dp))

                            // ── 시간 선택 섹션 (터치 이벤트를 100% 보장하는 Surface 클릭 버튼) ──
                            AnimatedVisibility(visible = expandedSection == "time") {
                                Column {
                                    Text("시간 설정 ⏰", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 시작 시간 선택 버튼
                                        Surface(
                                            onClick = { showTimePicker(true) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (startTime != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, if (startTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = startTime ?: "시작 시간",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (startTime != null) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (startTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text("~", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        // 종료 시간 선택 버튼
                                        Surface(
                                            onClick = { showTimePicker(false) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (endTime != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, if (endTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = endTime ?: "종료 시간",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (endTime != null) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (endTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (startTime != null || endTime != null) {
                                            IconButton(onClick = { startTime = null; endTime = null }, modifier = Modifier.size(38.dp)) {
                                                Icon(Icons.Default.Close, "리셋", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }

                            // ── 장소 섹션 ──
                            AnimatedVisibility(visible = expandedSection == "location") {
                                OutlinedTextField(
                                    value = locationText,
                                    onValueChange = { locationText = it },
                                    placeholder = { Text("📍 장소 (예: 강남역 카페)", fontSize = 13.sp) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // ── 반복 섹션 ──
                            AnimatedVisibility(visible = expandedSection == "repeat") {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Refresh, null, tint = if (isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("반복 계획", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Switch(
                                            checked = isRepeat,
                                            onCheckedChange = { isRepeat = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                                        )
                                    }
                                    AnimatedVisibility(visible = isRepeat) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Text("반복 요일", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(6.dp))
                                            val daysList = listOf("월" to 1, "화" to 2, "수" to 3, "목" to 4, "금" to 5, "토" to 6, "일" to 7)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                daysList.forEach { (name, value) ->
                                                    val sel = selectedDays.contains(value)
                                                    FilterChip(
                                                        selected = sel,
                                                        onClick = { if (sel) selectedDays.remove(value) else selectedDays.add(value) },
                                                        label = { Text(name, fontSize = 12.sp) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("종료일", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(
                                                    onClick = { showEndDatePicker() },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text(repeatEndDateStr ?: "선택", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 리스트 헤더 ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text("이날의 계획", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Text("${tasksForDate.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }

        // ── 할 일 목록 ──
        if (tasksForDate.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlaylistAddCheck, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("오늘 세운 계획이 없습니다", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("할 일을 기록하고 똑똑하게 실천해 보세요", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(tasksForDate, key = { it.id }) { task ->
                PlannerTaskItemRow(
                    task = task,
                    onToggle = { onToggleTask(task.id) },
                    onDelete = {
                        if (task.seriesId != null) taskPendingDelete = task
                        else onDeleteTask(task)
                    }
                )
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }

    if (showTimePickerModal) {
        CustomTimePickerModal(
            title = if (isPickingStartTime) "시작 시간 설정" else "종료 시간 설정",
            initialTime = if (isPickingStartTime) startTime else endTime,
            onDismiss = { showTimePickerModal = false },
            onTimeSelected = { timeFmt ->
                if (isPickingStartTime) startTime = timeFmt else endTime = timeFmt
                showTimePickerModal = false
            },
            onTimeCleared = {
                if (isPickingStartTime) startTime = null else endTime = null
                showTimePickerModal = false
            }
        )
    }

    if (showRepeatDatePickerModal) {
        CustomDatePickerModal(
            initialDateString = repeatEndDateStr ?: state.selectedDateString,
            diaryDatesUnified = emptySet(),
            onDismiss = { showRepeatDatePickerModal = false },
            onDateSelected = { dateStr ->
                repeatEndDateStr = dateStr
                showRepeatDatePickerModal = false
            }
        )
    }

    taskPendingDelete?.let { pending ->
        PlannerSeriesDeleteDialog(
            task = pending,
            onDeleteAll = { onDeleteTaskSeries(pending); taskPendingDelete = null },
            onDeleteThisOnly = { onDeleteTask(pending); taskPendingDelete = null },
            onCancel = { taskPendingDelete = null }
        )
    }
}

/**
 * 플래너 할 일 행 아이템
 */
@Composable
fun PlannerTaskItemRow(
    task: PlannerTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.55f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "TaskAlpha"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.05f else 0.95f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "TaskCheckScale"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "TaskCardBgColor"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(
            width = 1.dp,
            color = if (task.isCompleted) Color.Transparent
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (task.isCompleted) 0.98f else 1.0f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 커스텀 체크박스
            Box(
                modifier = Modifier
                    .scale(checkScale)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "완료",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(alpha)
            ) {
                Text(
                    text = task.text,
                    fontSize = 14.sp,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant 
                            else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                
                // 시간 및 장소 뱃지
                val hasTime = !task.startTime.isNullOrEmpty() || !task.endTime.isNullOrEmpty()
                val hasLocation = !task.location.isNullOrEmpty()
                
                if (hasTime || hasLocation) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasTime) {
                            val timeText = buildString {
                                append("⏰ ")
                                if (!task.startTime.isNullOrEmpty()) {
                                    append(task.startTime)
                                }
                                if (!task.endTime.isNullOrEmpty()) {
                                    append(" ~ ")
                                    append(task.endTime)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = timeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        if (hasLocation) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = "📍 ${task.location}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 반복 계획(일괄 등록된 시리즈) 의 삭제 옵션을 묻는 3버튼 다이얼로그.
 * - 전체 삭제: 같은 seriesId 의 모든 할 일 제거
 * - 이 날만 삭제: 현재 선택한 날짜의 1건만 제거
 * - 취소: 변경 없음
 */
@Composable
fun PlannerSeriesDeleteDialog(
    task: PlannerTask,
    onDeleteAll: () -> Unit,
    onDeleteThisOnly: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "반복 계획을 어떻게 삭제할까요?",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = "\"${task.text}\" 는 반복 계획으로 등록되어 있어요.\n전체 반복 일정을 모두 지울지, 오늘 일정만 지울지 선택해 주세요.",
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDeleteAll,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("전체 삭제", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDeleteThisOnly) {
                    Text("이 날만 삭제", color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                }
                TextButton(onClick = onCancel) {
                    Text("취소")
                }
            }
        }
    )
}

/**
 * 플래너 입력란 옆에 붙는 AI 자동 플래너명 추천 버튼.
 *  - 모델 미준비: dimmed 처리 + "AI 모델 미준비" 안내
 *  - 추천 중: 작은 CircularProgressIndicator 표시
 *  - 준비 완료: AutoAwesome 아이콘 (primary tint), 클릭 시 추천 요청
 */
@Composable
private fun AiSuggestPlannerTaskButton(
    isModelReady: Boolean,
    isSuggesting: Boolean,
    onClick: () -> Unit
) {
    val active = isModelReady && !isSuggesting
    val tint = when {
        isSuggesting -> MaterialTheme.colorScheme.primary
        isModelReady -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    val description = when {
        isSuggesting -> "AI 추천 생성 중"
        !isModelReady -> "AI 모델 미준비"
        else -> "AI 자동 계획 추천"
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSuggesting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
                color = tint
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 기록/플래너/목표 탭 공용 AI 브리핑 카드.
 * - briefing == null && isLoading == false  : 빈 상태 + "브리핑 받기" 버튼
 * - isLoading == true                       : 로딩 인디케이터 + "생성 중" 텍스트
 * - briefing != null && isLoading == false  : 브리핑 본문 + "다시 요청" 버튼
 *
 * 모델 미준비 시 카드 전체를 dimmed 처리하고 버튼 비활성화.
 */
@Composable
fun AiBriefingCard(
    title: String = "AI 브리핑",
    briefing: String?,
    isLoading: Boolean,
    isModelReady: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isModelReady) 0.18f else 0.08f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isModelReady) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isModelReady) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 다시 요청 / 요청 버튼
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isModelReady && !isLoading, onClick = onRequest),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (briefing == null) "브리핑 요청" else "브리핑 다시 요청",
                            tint = if (isModelReady) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                !isModelReady -> {
                    Text(
                        text = "AI 모델이 준비되면 브리핑을 받을 수 있어요.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                isLoading && briefing == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 가 데이터를 분석해 브리핑을 만들고 있어요…",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                briefing.isNullOrBlank() -> {
                    Text(
                        text = "우측 상단 새로고침 아이콘을 눌러 브리핑을 받아보세요.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = briefing,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 5. [목표 기록] 탭 본문 영역
 */
@Composable
fun GoalsTabContent(
    state: DiaryState,
    newGoalText: String,
    onTextChange: (String) -> Unit,
    onAddGoal: (String, String) -> Unit,
    onToggleGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onRequestBriefing: () -> Unit
) {
    val totalGoals = state.goals.size
    val completedGoals = state.goals.count { it.isCompleted }
    val progressRatio = if (totalGoals > 0) completedGoals.toFloat() / totalGoals else 0f

    // 프로그레스 바 애니메이션 적용
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = tween(durationMillis = 600),
        label = "GoalProgressAnimation"
    )

    // 카테고리 태그 선택 상태
    val categories = listOf(
        "전체" to GoalsAccent,
        "건강" to Color(0xFF66BB6A),
        "공부" to Color(0xFF42A5F5),
        "커리어" to Color(0xFFAB47BC),
        "자산" to Color(0xFFFFA726),
        "취미" to Color(0xFFEC407A),
    )
    var selectedCategory by remember { mutableStateOf("전체") }
    var isFinishedExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val goalBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // 진행 중인 목표와 완료된 목표 분리
    val activeGoals = remember(state.goals, selectedCategory) {
        state.goals.filter { !it.isCompleted && (selectedCategory == "전체" || it.category == selectedCategory) }
    }
    val completedGoalsList = remember(state.goals) { state.goals.filter { it.isCompleted } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // A. 목표 진행률 대시보드 (원형 게이지 및 동적 응원 메시지 도입)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(72.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            color = GoalsAccent,
                            trackColor = GoalsAccent.copy(alpha = 0.12f),
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "${(progressRatio * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoalsAccent
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (totalGoals > 0) "${totalGoals}개 중 ${completedGoals}개 달성" else "아직 등록된 목표가 없습니다",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalGoals > 0 && progressRatio < 1f) "조금씩 꾸준히 나아가고 있어요"
                                   else if (progressRatio >= 1f) "모든 목표를 달성했어요!"
                                   else "첫 목표를 세워볼까요?",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // B. 목표 작성 카드 (카테고리 선택 + 입력)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        categories.forEach { (catName, catColor) ->
                            FilterChip(
                                selected = selectedCategory == catName,
                                onClick = { selectedCategory = catName },
                                label = { Text(catName, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentColor = categories.find { it.first == selectedCategory }?.second ?: GoalsAccent
                        OutlinedTextField(
                            value = newGoalText,
                            onValueChange = onTextChange,
                            placeholder = { Text("새로운 목표 설정...", fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newGoalText.isNotBlank()) { onAddGoal(newGoalText, selectedCategory); focusManager.clearFocus() }
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(goalBringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(200)
                                            goalBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (newGoalText.isNotBlank()) { onAddGoal(newGoalText, selectedCategory); focusManager.clearFocus() }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = currentColor),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, "추가", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }

        // C-1. 진행 중인 목표
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text("진행 중", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Text("${activeGoals.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }

        if (activeGoals.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp)) {
                        Surface(shape = CircleShape, color = GoalsAccent.copy(alpha = 0.1f), modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.TaskAlt, null, tint = GoalsAccent.copy(alpha = 0.5f), modifier = Modifier.size(24.dp)) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("진행 중인 목표가 없습니다", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(activeGoals, key = { it.id }) { goal ->
                GoalItemRow(
                    goal = goal,
                    onToggle = { onToggleGoal(goal.id) },
                    onDelete = { onDeleteGoal(goal.id) }
                )
            }
        }

        // C-2. 완료된 목표
        if (completedGoalsList.isNotEmpty()) {
            item {
                Surface(
                    onClick = { isFinishedExpanded = !isFinishedExpanded },
                    shape = RoundedCornerShape(14.dp),
                    color = GoalsAccent.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("달성 완료 ${completedGoalsList.size}개", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GoalsAccent)
                        Icon(if (isFinishedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = GoalsAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (isFinishedExpanded) {
                items(completedGoalsList, key = { it.id }) { goal ->
                    GoalItemRow(
                        goal = goal,
                        onToggle = { onToggleGoal(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

/**
 * 목표 목록 행 아이템 (이모지 카테고리 뱃지 및 AI 코멘트 말풍선 확장)
 */
@Composable
fun GoalItemRow(
    goal: Goal,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (goal.isCompleted) 0.65f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "GoalAlpha"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (goal.isCompleted) 1.05f else 0.95f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "GoalCheckScale"
    )
    val cardBgColor by animateColorAsState(
        targetValue = if (goal.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "GoalCardBgColor"
    )

    // 카테고리별 이모지 및 색상 매핑
    val (catEmoji, catColor) = remember(goal.category) {
        when (goal.category) {
            "건강" -> "🏃‍♂️" to Color(0xFF66BB6A)
            "공부" -> "📚" to Color(0xFF42A5F5)
            "커리어" -> "💼" to Color(0xFFAB47BC)
            "자산" -> "💰" to Color(0xFFFFA726)
            "취미" -> "🎨" to Color(0xFFEC407A)
            else -> "🎯" to Color(0xFF78909C)
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, if (goal.isCompleted) Color.Transparent else catColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (goal.isCompleted) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 체크박스
                Box(
                    modifier = Modifier
                        .scale(checkScale)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (goal.isCompleted) catColor
                            else catColor.copy(alpha = 0.12f)
                        )
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (goal.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "완료",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. 카테고리 이모지 뱃지
                Text(
                    text = catEmoji,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // 3. 목표 텍스트
                Text(
                    text = goal.text,
                    fontSize = 14.sp,
                    color = if (goal.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant 
                            else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = if (goal.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(alpha)
                )

                // 4. 삭제 버튼
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 5. 완료 시 AI 축하 코멘트 말풍선 영역 노출
            if (goal.isCompleted && !goal.aiCongratulationText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = catColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, catColor.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "🤖 AI 멘토:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = goal.aiCongratulationText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class AiPresetChipItem(
    val kind: String,
    val label: String,
    val icon: String,
    val bgColor: Color,
    val borderColor: Color,
    val textColor: Color
)

/**
 * AI 비서 바텀시트 — 헤더 좌측 AI 버튼으로 열림. 상단 프리셋 칩 + 기존 [ChatTabContent] 재사용.
 * 어느 보기 모드(목록/블로그/달력)에서든 접근 가능.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AiAssistantSheet(
    state: DiaryState,
    onDismiss: () -> Unit,
    onSendChat: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPreset: (String) -> Unit,
    onStartDownload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.93f)
                .fillMaxWidth()
                .imePadding()
        ) {
            // 헤더: 타이틀 + 뱃지 + 액션 버튼 (대화 초기화, 닫기)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✨", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 비서",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "On-Device",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Text(
                        text = if (state.isModelReady) "100% 오프라인 · 개인정보 보호" else "모델 준비 필요",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                if (state.chatMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "대화 초기화",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = 8.dp)
            )

            // 프리셋 칩 — 파스텔 컬러 톤과 1:1 매칭된 가로 퀵 칩 모음 (원터치 요약 질의)
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val goalsAccentColor = GoalsAccent
            val diaryAccentColor = DiaryAccent

            val presets = remember(primary, secondary, tertiary, goalsAccentColor, diaryAccentColor) {
                listOf(
                    AiPresetChipItem("WEEK_SUMMARY", "이번주 써머리", "📊", primary.copy(alpha = 0.12f), primary.copy(alpha = 0.28f), primary),
                    AiPresetChipItem("MONTH_EMOTION", "이번달 감정 분석", "🎭", tertiary.copy(alpha = 0.12f), tertiary.copy(alpha = 0.28f), tertiary),
                    AiPresetChipItem("NEXT_WEEK_PLAN", "다음주 계획 정리", "📅", secondary.copy(alpha = 0.12f), secondary.copy(alpha = 0.28f), secondary),
                    AiPresetChipItem("GOALS_STATUS", "현재 목표 현황", "🎯", goalsAccentColor.copy(alpha = 0.12f), goalsAccentColor.copy(alpha = 0.28f), goalsAccentColor),
                    AiPresetChipItem("RECENT", "최근 기록 브리핑", "📝", diaryAccentColor.copy(alpha = 0.12f), diaryAccentColor.copy(alpha = 0.28f), diaryAccentColor)
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets.size, key = { presets[it].kind }) { idx ->
                    val item = presets[idx]
                    val isEnabled = !state.isGeneratingChat

                    Surface(
                        onClick = { if (isEnabled) onPreset(item.kind) },
                        enabled = isEnabled,
                        shape = RoundedCornerShape(14.dp),
                        color = item.bgColor,
                        border = BorderStroke(1.dp, item.borderColor),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(item.icon, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = item.textColor
                            )
                        }
                    }
                }
            }

            // 기존 챗봇 UI 재사용 (메시지 스트림 + 입력창 + 모델 다운로드 CTA)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                ChatTabContent(
                    state = state,
                    onSendChat = onSendChat,
                    onClearHistory = onClearHistory,
                    onStartDownload = onStartDownload
                )
            }
        }
    }
}

/**
 * 6. [AI 비서] 챗봇 탭 본문 영역
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTabContent(
    state: DiaryState,
    onSendChat: (String) -> Unit,
    onClearHistory: () -> Unit,
    onInputFocusChange: (Boolean) -> Unit = {},
    onStartDownload: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) focusManager.clearFocus()
    }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    val sendMessage = {
        if (inputText.isNotBlank() && !state.isGeneratingChat) {
            onSendChat(inputText.trim())
            inputText = ""
        }
    }

    // ── 사양 미달 기기 (RAM 6GB 이하) 제한 가이드 화면 ──
    if (state.isLowRamDevice || state.isDeviceUnsupported) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "AI 비서 사용 제한 (사양 미달)",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "RAM 6GB 이하 기기는 AI 언어 모델 구동 사양에 미달하여 AI 비서 기능을 지원하지 않습니다.\n\n신규 글 작성 시 음성 입력(STT) 기능은 계속 사용할 수 있습니다.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
        }
        return
    }

    // ── 모델 미설치 시 가이드 화면 ──
    if (!state.isModelReady && !state.isDownloadingModel && !state.isExtractingModel && !state.isModelInitializing) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = CircleShape, color = ChatAccent.copy(alpha = 0.08f), modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Psychology, null, tint = ChatAccent.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "AI 비서를 사용하려면\nAI 모델 설치가 필요합니다",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "기기에 AI 언어 모델(Gemma, 약 2.3GB)을 설치하면\n네트워크 없이도 대화가 가능합니다",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onStartDownload,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChatAccent),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI 모델 설치하기 (약 2.3GB)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("100% 온디바이스 · 오프라인 · 개인정보 보호", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        return
    }

    // ── 모델 다운로드/초기화 중 ──
    if (!state.isModelReady) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(color = ChatAccent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                if (state.isDownloadingModel) "AI 모델 다운로드 중...\n${(state.modelDownloadProgress * 100).toInt()}%"
                else if (state.isExtractingModel) "모델 압축 해제 중..."
                else "AI 모델 초기화 중...",
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "잠시만 기다려주세요",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // ── 정상 채팅 UI ──

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ── 헤더: AI 비서 + 대화 비우기 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = ChatAccent.copy(alpha = 0.1f), modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 14.sp) }
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("AI 비서", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (state.isModelReady) "무엇이든 물어보세요" else "AI 모델 준비 중...",
                        fontSize = 11.sp, color = if (state.isModelReady) ChatAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.chatMessages.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("대화 비우기", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ── 메시지 영역 ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.chatMessages.isEmpty()) {
                // 웰컴 화면
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                ) {
                    Surface(shape = CircleShape, color = ChatAccent.copy(alpha = 0.08f), modifier = Modifier.size(80.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 32.sp) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "안녕하세요! 당신의 다이어리 AI 비서입니다",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "작성하신 기록과 일정을 기억하고\n궁금한 점에 답변해 드릴게요",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(28.dp))

                    val suggestions = listOf(
                        "최근에 맛있게 먹은 음식이 뭐였지?" to Icons.Default.Restaurant,
                        "오늘 계획된 할 일 알려줘" to Icons.Default.Checklist,
                        "내가 세운 목표들 정리해줘" to Icons.Default.TrackChanges,
                    )

                    suggestions.forEach { (text, icon) ->
                        Surface(
                            onClick = { onSendChat(text) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Icon(icon, null, tint = ChatAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                // 채팅 메시지 리스트
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.chatMessages.size, key = { it }) { index ->
                        val msg = state.chatMessages[index]
                        val isUser = msg.sender == "USER"

                        Row(
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isUser) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 14.sp) }
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            Column(
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                                modifier = Modifier.widthIn(max = 520.dp)
                            ) {
                                Card(
                                    shape = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                            else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUser) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)) else null,
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 1.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(
                                            text = msg.text,
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp,
                                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                        )

                                        if (!isUser) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val context = LocalContext.current
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("AI 답변", msg.text)
                                                        clipboard.setPrimaryClip(clip)
                                                        android.widget.Toast.makeText(context, "답변이 복사되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "복사",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AI 응답 생성 중 표시 (마지막 AI 메시지 하단)
                        if (!isUser && index == state.chatMessages.lastIndex && state.isGeneratingChat) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 42.dp, top = 6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "AI가 다이어리 기록을 기반으로 답변 작성 중...",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 입력 바 ──
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("AI 비서에게 질문을 입력하세요...", fontSize = 13.5.sp) },
                    singleLine = true,
                    enabled = !state.isGeneratingChat,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    modifier = Modifier.weight(1f).onFocusChanged { onInputFocusChange(it.isFocused) }
                )

                if (state.isGeneratingChat) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "보내기", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}


/**
 * 일기 카드 1개 표시. v3.1 부터 [com.grepiu.aidiary.data.repository.DiaryMeta] (메타만) 을 받아
 * 제목/감정/미리보기만 그린다. 썸네일/첫 이미지는 풀 로드 시점에만 표시하도록 단순화.
 */
@Composable
fun DiaryListItemCard(diary: DiaryMeta, onClick: () -> Unit) {
    val dateText = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(diary.timestamp))
    val previewText = diary.contentPreview.replace("\n", " ").trim()
        .ifBlank { "(본문 없음)" }
    val (typeIcon, typeLabel, typeColor) = getContentTypeUI(diary.contentType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(typeColor, typeColor.copy(alpha = 0.5f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = typeColor.copy(alpha = 0.1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = typeLabel,
                                tint = typeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = typeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = typeColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    if (diary.emotion.isNotBlank() && diary.emotion != "Neutral") {
                        Spacer(modifier = Modifier.weight(1f))
                        EmotionChipSmall(diary.emotion)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = diary.title.ifBlank { "(제목 없음)" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )
                if (previewText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = previewText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

/**
 * 감정 라벨을 작은 칩으로 표시 (목록 카드용). 메인 theme EmotionChip 과는 별도 가벼운 버전.
 */
@Composable
private fun EmotionChipSmall(emotion: String) {
    val color = when (emotion) {
        "Joy" -> EmotionJoy
        "Sadness" -> EmotionSadness
        "Anger" -> EmotionAnger
        "Anxiety" -> EmotionAnxiety
        "Calm" -> EmotionCalm
        else -> Color.Gray
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = emotion,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * 구버전 호환용 (외부에서 호출 가능성 대비). v3.1 에서 본문/이미지가 필요하면 풀 로드 후 사용.
 */
@Composable
@Deprecated("DiaryMeta 기반으로 전환됨. 풀 DiaryEntry 가 필요하면 ViewModel 의 LoadFullDiary 사용")
fun DiaryListItemCard(diary: DiaryEntry, onClick: () -> Unit) {
    val dateText = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(diary.timestamp))
    val context = LocalContext.current
    val firstImageBlock = remember(diary.id) {
        diary.blocks.firstOrNull { it is ContentBlock.ImageBlock } as? ContentBlock.ImageBlock
    }
    val thumbnailFile: File? = remember(firstImageBlock?.relativePath) {
        val rel = firstImageBlock?.relativePath
        if (rel.isNullOrBlank()) null
        else File(context.filesDir, rel).takeIf { it.exists() }
    }
    val previewText = remember(diary.id) {
        diary.contentText.replace("\n", " ").trim()
    }
    val attachmentCount = remember(diary.id) {
        diary.blocks.count { it is ContentBlock.ImageBlock }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 상단 메타 바 (타입 뱃지 + 날짜 + 감정 뱃지)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val (typeIcon, typeLabel, typeColor) = getContentTypeUI(diary.contentType)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = typeColor.copy(alpha = 0.1f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = typeColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = typeLabel,
                                    color = typeColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = dateText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val (emotionText, emotionColor) = getEmotionUI(diary.emotion)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = emotionColor.copy(alpha = 0.1f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = emotionText,
                            color = emotionColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = diary.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = previewText,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                val hasAiFeedback = diary.aiAnalysis != null || diary.blocks.any { it is ContentBlock.TagAiBlock }
                if (hasAiFeedback || attachmentCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasAiFeedback) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "✨ AI 피드백",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (attachmentCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "🖼️ 사진 ${attachmentCount}장",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (thumbnailFile != null) {
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = "기록 썸네일",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

/**
 * 영어 감정 코드명에 상응하는 한글 라벨과 색상 테마를 정의하는 헬퍼 함수
 */
fun getEmotionUI(emotion: String): Pair<String, Color> {
    return when (emotion) {
        "Joy" -> Pair("😊 기쁨", EmotionJoy)
        "Calm" -> Pair("🌿 평온", EmotionCalm)
        "Sadness" -> Pair("😢 슬픔", EmotionSadness)
        "Anxiety" -> Pair("😰 불안", EmotionAnxiety)
        "Anger" -> Pair("😡 분노", EmotionAnger)
        else -> Pair("⚪ 보통", Color(0xFF8A7B78))
    }
}

/**
 * 콘텐츠 타입별 아이콘/라벨/색상 (목록·상세 공통).
 */
@Composable
fun getContentTypeUI(type: ContentType): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Color> {
    return when (type) {
        ContentType.DIARY -> Triple(Icons.AutoMirrored.Filled.MenuBook, "일기", DiaryTypeColor)
        ContentType.POST -> Triple(Icons.Default.EditNote, "새 글", PostTypeColor)
        ContentType.NOTE -> Triple(Icons.Default.NoteAlt, "메모", NoteTypeColor)
    }
}

/**
 * 상단 탭 인디케이터 / 달력 도트 / 탭 관련 UI 공용 액센트 색상.
 * DIARY / PLANNER / GOALS 는 WeeklyCalendarStrip 도트 색과 동일하게 통일.
 * CHAT 은 AI 비서 탭 전용 색.
 */
@Composable
private fun tabAccentColor(tabId: String): Color = when (tabId) {
    "DIARY"   -> DiaryAccent
    "PLANNER" -> PlannerAccent
    "GOALS"   -> GoalsAccent
    "CHAT"    -> ChatAccent
    else      -> MaterialTheme.colorScheme.primary
}

/**
 * 상업 서비스 수준 하단 글쓰기 허브 바 (재설계 v2).
 *
 * 구조:
 *  "✍️ 기록하기" 대형 그라디언트 CTA 버튼 (타입 선택 없이 즉시 작성 화면 진입)
 *
 * UX 철학:
 *  - "기록하기" → 작성 화면 → 타입은 내부에서 AI 자동 분류 or 수동 변경
 */
@Composable
fun WriteActionBar(
    onWriteDiary: (ContentType) -> Unit,
    modifier: Modifier = Modifier
) {
    // 메인 버튼 인터랙션
    val mainInteraction = remember { MutableInteractionSource() }
    val isMainPressed by mainInteraction.collectIsPressedAsState()
    val mainScale by animateFloatAsState(
        targetValue = if (isMainPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "MainButtonScale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 핸들
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            )

            // ── 1행: 메인 CTA 버튼 ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(mainScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    .clickable(
                        interactionSource = mainInteraction,
                        indication = null,
                        onClick = { onWriteDiary(ContentType.DIARY) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "기록하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // 시스템 네비게이션 바 여백
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 기록 타입 필터 칩 행 — 심플하고 프로페셔널한 FilterChip 기반.
 * LIST/BLOG/CALENDAR 3모드 공통 헤더에서 사용되며, 좁은 화면에서 칩이 잘리지 않도록 가로 스크롤을 적용한다.
 */
@Composable
private fun DiaryTypeFilterRow(
    selectedTypeFilter: ContentType?,
    onTypeFilterChange: (ContentType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        val chips = listOf(
            Triple(null as ContentType?, "전체", Icons.AutoMirrored.Filled.MenuBook),
            Triple(ContentType.DIARY, "일기", Icons.AutoMirrored.Filled.MenuBook),
            Triple(ContentType.POST, "새 글", Icons.Default.EditNote),
            Triple(ContentType.NOTE, "메모", Icons.Default.NoteAlt),
        )
        chips.forEach { (type, label, icon) ->
            val selected = selectedTypeFilter == type
            // 콘텐츠 타입별 고유색으로 필터칩 구분 (전체=primary, 일기=로즈, 새글=모브, 메모=세이지)
            val chipColor = when (type) {
                ContentType.DIARY -> DiaryTypeColor
                ContentType.POST -> PostTypeColor
                ContentType.NOTE -> NoteTypeColor
                else -> MaterialTheme.colorScheme.primary
            }
            FilterChip(
                selected = selected,
                onClick = { onTypeFilterChange(type) },
                label = {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        // 미선택 라벨도 타입색으로 은은하게 물들여 구분
                        color = if (selected) Color.White else chipColor
                    )
                },
                leadingIcon = {
                    Icon(
                        icon,
                        label,
                        // 선택: 흰색(타입색 배경 위) / 미선택: 타입색 아이콘
                        tint = if (selected) Color.White else chipColor,
                        modifier = Modifier.size(if (selected) 16.dp else 14.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
            )
        }
    }
}

/** 리스트/블로그/달력 세그먼트 토글 */
@Composable
private fun ViewModeToggle(
    mode: DiaryViewMode,
    onModeChange: (DiaryViewMode) -> Unit
) {
    val items = listOf(
        Triple(DiaryViewMode.LIST, Icons.Default.ViewList, "리스트"),
        Triple(DiaryViewMode.BLOG, Icons.Default.ViewAgenda, "블로그"),
        Triple(DiaryViewMode.CALENDAR, Icons.Default.CalendarMonth, "달력"),
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            items.forEach { (m, icon, desc) ->
                val selected = m == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onModeChange(m) }
                        .semantics { this.selected = selected; role = Role.Tab }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, desc,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 블로그 날짜 구분 헤더 (오늘/어제 상대 표기 + M월 d일 (E)) */
@Composable
private fun BlogDateHeader(dateString: String) {
    val label = remember(dateString) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = fmt.format(Date())
        val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        val pretty = try {
            SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(fmt.parse(dateString) ?: Date())
        } catch (_: Exception) { dateString }
        when (dateString) {
            today -> "오늘 · $pretty"
            yesterday -> "어제 · $pretty"
            else -> pretty
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("📌", fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

/** X (트위터) / Threads (쓰레드) 컨셉 전역 통합 피드 */
@Composable
private fun UnifiedBlogView(
    diaries: List<DiaryMeta>,
    tasks: List<PlannerTask>,
    goals: List<Goal>,
    onSelectDiary: (DiaryMeta) -> Unit
) {
    val groups = remember(diaries, tasks, goals) {
        com.grepiu.aidiary.ui.util.buildUnifiedByDate(diaries, tasks, goals)
    }

    // 상세 화면 이탈 후 복원 시 스크롤 위치 100% 보존
    val listState = rememberLazyListState()

    // 최초 1회 오늘 날짜 자동 스크롤 여부 영속화
    var hasScrolledToToday by rememberSaveable { mutableStateOf(false) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    LaunchedEffect(groups) {
        if (!hasScrolledToToday && groups.isNotEmpty()) {
            var targetIndex = 0
            var currentIndex = 0
            var found = false

            for ((date, items) in groups) {
                if (date == todayStr || date <= todayStr) {
                    targetIndex = currentIndex
                    found = true
                    break
                }
                currentIndex += 1 + items.size // 헤더 1개 + 아이템들
            }

            if (found && targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
            hasScrolledToToday = true
        }
    }

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("🧵", fontSize = 28.sp) }
                }
                Spacer(Modifier.height(14.dp))
                Text("아직 작성된 쓰레드 피드가 없어요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("일기, 계획, 목표를 등록하면 타임라인이 시작됩니다", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groups.forEach { (date, items) ->
            item(key = "uh_$date") { BlogDateHeader(date) }
            items(items.size, key = { dayItemKey(items[it]) }) { index ->
                val item = items[index]
                val isLast = index == items.lastIndex
                BlogThreadPostCard(
                    dayItem = item,
                    isLastInGroup = isLast,
                    onSelectDiary = onSelectDiary
                )
            }
        }
    }
}

private fun dayItemKey(di: DayItem): String = when (di) {
    is DayItem.DiaryItem -> "d_${di.meta.id}"
    is DayItem.TaskItem -> "t_${di.task.id}"
    is DayItem.GoalDayItem -> "g_${di.goal.id}"
}

/**
 * X (Twitter) & Threads 쓰레드 타임라인 피드 카드 컴포저블.
 * 좌측 수직 스레드 선(Thread Line)과 아바타, 우측 타임라인 포스트 및 소셜 반응 바.
 */
@Composable
private fun BlogThreadPostCard(
    dayItem: DayItem,
    isLastInGroup: Boolean,
    onSelectDiary: (DiaryMeta) -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }

    val avatarIcon = when (dayItem) {
        is DayItem.DiaryItem -> "✨"
        is DayItem.TaskItem -> "📌"
        is DayItem.GoalDayItem -> "🎯"
    }

    val typeLabel = when (dayItem) {
        is DayItem.DiaryItem -> "일기"
        is DayItem.TaskItem -> "계획"
        is DayItem.GoalDayItem -> "목표"
    }

    val typeAccent = when (dayItem) {
        is DayItem.DiaryItem -> DiaryAccent
        is DayItem.TaskItem -> PlannerAccent
        is DayItem.GoalDayItem -> GoalsAccent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // 1. 좌측 수직 스레드 아바타 & 커넥터 라인
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(42.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = typeAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, typeAccent.copy(alpha = 0.25f)),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(avatarIcon, fontSize = 14.sp)
                }
            }

            if (!isLastInGroup) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f, fill = true)
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.width(8.dp))

        // 2. 우측 쓰레드 포스트 카드 본문
        Surface(
            onClick = {
                if (dayItem is DayItem.DiaryItem) onSelectDiary(dayItem.meta)
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // (1) 핸들 헤더 (@my.diary · 뱃지)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "나의 기록",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "@my.diary",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 일기 감정 표출
                    if (dayItem is DayItem.DiaryItem && dayItem.meta.emotion.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = dayItem.meta.emotion,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // (2) 포스트 본문 텍스트
                when (dayItem) {
                    is DayItem.DiaryItem -> {
                        if (dayItem.meta.title.isNotBlank()) {
                            Text(
                                text = dayItem.meta.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(
                            text = dayItem.meta.contentPreview,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is DayItem.TaskItem -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (dayItem.task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = PlannerAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = dayItem.task.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (dayItem.task.isCompleted) TextDecoration.LineThrough else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!dayItem.task.startTime.isNullOrBlank() || !dayItem.task.location.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!dayItem.task.startTime.isNullOrBlank()) {
                                    Text(
                                        text = "⏰ ${dayItem.task.startTime}" + (dayItem.task.endTime?.let { " ~$it" } ?: ""),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!dayItem.task.location.isNullOrBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "📍 ${dayItem.task.location}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is DayItem.GoalDayItem -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = GoalsAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = dayItem.goal.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (dayItem.goal.isCompleted) TextDecoration.LineThrough else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (dayItem.goal.category.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "🏷️ ${dayItem.goal.category}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // (3) 하단 X / Threads 소셜 반응 바 (Social Action Bar)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    // 💬 대화
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                android.widget.Toast.makeText(context, "AI 비서에서 더 자세히 질문해 보세요 ✨", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "댓글/소통",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("AI 대화", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }

                    // ❤️ 좋아요 토글
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isLiked = !isLiked }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "좋아요",
                            tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // 📋 텍스트 복사
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val textToCopy = when (dayItem) {
                                    is DayItem.DiaryItem -> "${dayItem.meta.title}\n${dayItem.meta.contentPreview}"
                                    is DayItem.TaskItem -> dayItem.task.text
                                    is DayItem.GoalDayItem -> dayItem.goal.text
                                }
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("쓰레드 텍스트", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "쓰레드가 복사되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "복사",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // 📤 공유
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val textToShare = when (dayItem) {
                                    is DayItem.DiaryItem -> "[AI 다이어리 쓰레드]\n${dayItem.meta.title}\n${dayItem.meta.contentPreview}"
                                    is DayItem.TaskItem -> "[AI 다이어리 계획]\n${dayItem.task.text}"
                                    is DayItem.GoalDayItem -> "[AI 다이어리 목표]\n${dayItem.goal.text}"
                                }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "쓰레드 공유"))
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 통합 피드/달력의 계획(할 일) 행 */
@Composable
private fun UnifiedTaskRow(task: PlannerTask) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(
                if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null, tint = PlannerAccent, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PlannerAccent.copy(alpha = 0.12f)) {
                        Text("계획", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlannerAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (!task.startTime.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(task.startTime!! + (task.endTime?.let { "~$it" } ?: ""),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(task.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!task.location.isNullOrBlank()) {
                    Text("📍 ${task.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 통합 피드/달력의 목표 행 */
@Composable
private fun UnifiedGoalRow(goal: Goal) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = GoalsAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = GoalsAccent.copy(alpha = 0.12f)) {
                        Text("목표", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoalsAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(goal.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                Text(goal.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 전역 통합 달력 — 사람 손으로 색칠하고 작성한 듯한 다이어리 핸드메이드 감성 UI/UX 뷰. */
@Composable
private fun UnifiedCalendarView(
    diaries: List<DiaryMeta>,
    tasks: List<PlannerTask>,
    goals: List<Goal>,
    diaryDatesUnified: Set<String>,
    selectedDateString: String,
    onDateSelect: (String) -> Unit,
    onSelectDiary: (DiaryMeta) -> Unit
) {
    val initCal = remember(selectedDateString) {
        Calendar.getInstance().apply {
            try { time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateString) ?: Date() }
            catch (_: Exception) {}
        }
    }
    var year by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(initCal.get(Calendar.MONTH) + 1) }

    LaunchedEffect(selectedDateString) {
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateString) }
            .getOrNull()?.let {
                val c = Calendar.getInstance().apply { time = it }
                year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) + 1
            }
    }

    val cells = remember(year, month) { buildMonthGrid(year, month) }
    val monthPrefix = remember(year, month) { "%04d-%02d-".format(year, month) }
    val dayItems = remember(selectedDateString, diaries, tasks, goals) {
        com.grepiu.aidiary.ui.util.unifiedForDate(selectedDateString, diaries, tasks, goals)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 상단 아날로그 종이 네비게이션 칩
        item(key = "ucal_nav") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) {
                        Icon(Icons.Default.ChevronLeft, "이전 달", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗓️", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${year}년 ${month}월",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) {
                        Icon(Icons.Default.ChevronRight, "다음 달", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // 2. 요일 라벨 헤더
        item(key = "ucal_dow") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf(
                    "일" to Color(0xFFE53935),
                    "월" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "화" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "수" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "목" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "금" to MaterialTheme.colorScheme.onSurfaceVariant,
                    "토" to Color(0xFF3D7BB5)
                ).forEach { (d, color) ->
                    Text(
                        text = d,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. 달력 셀 그리드 (형광펜 색칠 & 미니 스티커 도트 감성)
        items(cells.chunked(7), key = { row -> "urow_" + row.joinToString("_") { it?.toString() ?: "x" } }) { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                week.forEachIndexed { colIdx, day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val dateStr = monthPrefix + "%02d".format(day)
                        val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                        val isToday = dateStr == todayStr
                        val isSelected = dateStr == selectedDateString

                        // 일기/계획/목표 3색 존재 여부 계산
                        val hasDiary = remember(diaries, dateStr) { diaries.any { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == dateStr } }
                        val hasTask = remember(tasks, dateStr) { tasks.any { it.dateString == dateStr } }
                        val hasGoal = remember(goals, dateStr) { goals.any { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == dateStr } }

                        // 연속 계획 판별 (전날 및 다음날 동일/반복 태스크 연동)
                        val prevDateStr = remember(dateStr) { getOffsetDateStr(dateStr, -1) }
                        val nextDateStr = remember(dateStr) { getOffsetDateStr(dateStr, 1) }

                        val hasPrevTask = remember(tasks, dateStr) {
                            if (!hasTask) false
                            else {
                                val curTasks = tasks.filter { it.dateString == dateStr }
                                val prevTasks = tasks.filter { it.dateString == prevDateStr }
                                curTasks.any { cur ->
                                    prevTasks.any { prev -> (cur.seriesId != null && cur.seriesId == prev.seriesId) || (cur.text == prev.text) }
                                }
                            }
                        }

                        val hasNextTask = remember(tasks, dateStr) {
                            if (!hasTask) false
                            else {
                                val curTasks = tasks.filter { it.dateString == dateStr }
                                val nextTasks = tasks.filter { it.dateString == nextDateStr }
                                curTasks.any { cur ->
                                    nextTasks.any { next -> (cur.seriesId != null && cur.seriesId == next.seriesId) || (cur.text == next.text) }
                                }
                            }
                        }

                        val textColor = when {
                            isSelected -> Color.White
                            colIdx == 0 -> Color(0xFFE53935)
                            colIdx == 6 -> Color(0xFF3D7BB5)
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        val cellBgColor = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        }

                        val borderStroke = when {
                            isToday && !isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            isSelected -> null
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        }

                        Surface(
                            onClick = { onDateSelect(dateStr) },
                            shape = RoundedCornerShape(16.dp),
                            color = cellBgColor,
                            border = borderStroke,
                            shadowElevation = if (isSelected) 3.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.92f)
                                .padding(2.5.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$day",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )

                                // 하단 아날로그 형광펜 연속 줄 (Highlighter Line Band) & 미니 스티커 도트
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 1. 연속 계획 형광펜 라인 (두께 14.dp의 손으로 슥 그은 스케줄 밴드)
                                    if (hasTask) {
                                        val highlighterColor = if (isSelected) Color.White.copy(alpha = 0.85f) else PlannerAccent.copy(alpha = 0.35f)
                                        val textColor = if (isSelected) PlannerAccent else MaterialTheme.colorScheme.onSurface

                                        val (barShape, fillModifier) = when {
                                            // 1. 연속 중간 (양쪽 모두 형광펜으로 연결된 중간일)
                                            hasPrevTask && hasNextTask -> {
                                                Pair(
                                                    RoundedCornerShape(0.dp),
                                                    Modifier.fillMaxWidth().height(14.dp)
                                                )
                                            }
                                            // 2. 연속 시작일 (오늘부터 오른쪽으로 형광펜 칠해진 날)
                                            hasNextTask -> {
                                                Pair(
                                                    RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp),
                                                    Modifier.fillMaxWidth().padding(start = 4.dp).height(14.dp)
                                                )
                                            }
                                            // 3. 연속 종료일 (왼쪽에서 형광펜 칠해져 오는 날)
                                            hasPrevTask -> {
                                                Pair(
                                                    RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp),
                                                    Modifier.fillMaxWidth().padding(end = 4.dp).height(14.dp)
                                                )
                                            }
                                            // 4. 단일 날짜 계획 (형광펜 미니 캡슐 마킹)
                                            else -> {
                                                Pair(
                                                    RoundedCornerShape(7.dp),
                                                    Modifier.width(24.dp).height(14.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = barShape,
                                            color = highlighterColor,
                                            modifier = fillModifier
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "📌",
                                                    fontSize = 8.5.sp,
                                                    lineHeight = 10.sp
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(14.dp))
                                    }

                                    Spacer(Modifier.height(3.dp))

                                    // 2. 일기/목표 보조 미니 스티커 도트
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(5.dp)
                                    ) {
                                        if (hasDiary) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else DiaryAccent)
                                            )
                                        }
                                        if (hasGoal) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White.copy(alpha = 0.7f) else GoalsAccent)
                                            )
                                        }
                                        if (!hasDiary && !hasGoal) {
                                            Spacer(modifier = Modifier.height(4.5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 하단 다이어리 스크랩북 세션 헤더
        item(key = "ucal_sel_header") {
            val pretty = remember(selectedDateString) {
                try {
                    SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDateString) ?: Date()
                    )
                } catch (_: Exception) { selectedDateString }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("📖", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$pretty 의 기록 스크랩북",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 5. 선택 일자 스크랩북 포스트 목록
        if (dayItems.isEmpty()) {
            item(key = "ucal_empty") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = "이 날은 아직 작성된 기록이 없어요 ✏️",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(dayItems, key = { dayItemKey(it) }) { di ->
                when (di) {
                    is DayItem.DiaryItem -> DiaryListItemCard(diary = di.meta, onClick = { onSelectDiary(di.meta) })
                    is DayItem.TaskItem -> UnifiedTaskRow(di.task)
                    is DayItem.GoalDayItem -> UnifiedGoalRow(di.goal)
                }
            }
        }
    }
}

/**
 * 안드로이드 레거시 DatePickerDialog를 대체하는 자체 프리미엄 캘린더 모달 다이얼로그.
 * 앱 전체 디자인 시스템(Pretendard, 3색 도트, 요일 컬러링, 선택 링 등)과 100% 통일됩니다.
 */
@Composable
private fun CustomDatePickerModal(
    initialDateString: String,
    diaryDatesUnified: Set<String>,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { fmt.format(Date()) }

    val initialCal = remember(initialDateString) {
        Calendar.getInstance().apply {
            try { time = fmt.parse(initialDateString) ?: Date() } catch (_: Exception) {}
        }
    }

    var year by remember { mutableStateOf(initialCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(initialCal.get(Calendar.MONTH) + 1) }
    var tempSelectedDate by remember { mutableStateOf(initialDateString) }

    val cells = remember(year, month) { buildMonthGrid(year, month) }
    val monthPrefix = remember(year, month) { "%04d-%02d-".format(year, month) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 헤더 (아이콘 + 제목)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "날짜 선택",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2. 월 선택 컨트롤 (< 2026년 7월 >)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { if (month == 1) { month = 12; year-- } else month-- },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "이전 달", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${year}년 ${month}월",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { if (month == 12) { month = 1; year++ } else month++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, "다음 달", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. 요일 라벨 (일: 빨강, 토: 파랑, 평일: onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    listOf(
                        "일" to Color(0xFFE53935),
                        "월" to MaterialTheme.colorScheme.onSurfaceVariant,
                        "화" to MaterialTheme.colorScheme.onSurfaceVariant,
                        "수" to MaterialTheme.colorScheme.onSurfaceVariant,
                        "목" to MaterialTheme.colorScheme.onSurfaceVariant,
                        "금" to MaterialTheme.colorScheme.onSurfaceVariant,
                        "토" to Color(0xFF3D7BB5)
                    ).forEach { (d, color) ->
                        Text(
                            text = d,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. 날짜 그리드
                Column(modifier = Modifier.fillMaxWidth()) {
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            week.forEachIndexed { colIdx, day ->
                                if (day == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    val dateStr = monthPrefix + "%02d".format(day)
                                    val isToday = dateStr == todayStr
                                    val isSelected = dateStr == tempSelectedDate
                                    val hasEntry = diaryDatesUnified.contains(dateStr)

                                    val dayColor = when {
                                        isSelected -> Color.White
                                        colIdx == 0 -> Color(0xFFE53935)
                                        colIdx == 6 -> Color(0xFF3D7BB5)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                else Color.Transparent
                                            )
                                            .then(
                                                if (isToday && !isSelected) Modifier.border(
                                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                                    CircleShape
                                                ) else Modifier
                                            )
                                            .clickable { tempSelectedDate = dateStr },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$day",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = dayColor
                                            )
                                            if (hasEntry) {
                                                Box(
                                                    Modifier
                                                        .padding(top = 1.dp)
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else DiaryAccent)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 하단 버튼 영역 ("오늘로 이동" | "취소", "선택")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val todayCal = Calendar.getInstance()
                            year = todayCal.get(Calendar.YEAR)
                            month = todayCal.get(Calendar.MONTH) + 1
                            tempSelectedDate = todayStr
                        }
                    ) {
                        Text("오늘", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("취소", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { onDateSelected(tempSelectedDate) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("선택", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 안드로이드 레거시 TimePickerDialog를 대체하는 자체 프리미엄 시간 선택 모달 다이얼로그.
 * 시작시간/종료시간을 직관적으로 선택하며 앱 전체 디자인 시스템과 100% 통합됩니다.
 */
@Composable
private fun CustomTimePickerModal(
    title: String,
    initialTime: String?,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit,
    onTimeCleared: () -> Unit
) {
    var isAm by remember {
        mutableStateOf(
            if (initialTime.isNullOrBlank()) true
            else {
                val hour = initialTime.split(":").firstOrNull()?.toIntOrNull() ?: 9
                hour < 12
            }
        )
    }

    var selectedHour12 by remember {
        mutableStateOf(
            if (initialTime.isNullOrBlank()) 9
            else {
                val hour24 = initialTime.split(":").firstOrNull()?.toIntOrNull() ?: 9
                when {
                    hour24 == 0 -> 12
                    hour24 > 12 -> hour24 - 12
                    else -> hour24
                }
            }
        )
    }

    var selectedMinute by remember {
        mutableStateOf(
            if (initialTime.isNullOrBlank()) 0
            else {
                initialTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            }
        )
    }

    val currentFormattedTime = remember(isAm, selectedHour12, selectedMinute) {
        val hour24 = when {
            isAm && selectedHour12 == 12 -> 0
            isAm -> selectedHour12
            !isAm && selectedHour12 == 12 -> 12
            else -> selectedHour12 + 12
        }
        String.format(Locale.getDefault(), "%02d:%02d", hour24, selectedMinute)
    }

    val displayAmPm = if (isAm) "오전" else "오후"
    val displayHour = String.format(Locale.getDefault(), "%02d", selectedHour12)
    val displayMin = String.format(Locale.getDefault(), "%02d", selectedMinute)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 모달 타이틀 헤더
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2. 실시간 시각 디스플레이 대형 카드
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 14.dp)
                    ) {
                        Text(
                            text = displayAmPm,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "$displayHour : $displayMin",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 오전 / 오후 (AM/PM) 토글 세그먼트
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(3.dp)
                ) {
                    Surface(
                        onClick = { isAm = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAm) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "오전 (AM)",
                                fontSize = 12.5.sp,
                                fontWeight = if (isAm) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAm) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Surface(
                        onClick = { isAm = false },
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isAm) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "오후 (PM)",
                                fontSize = 12.5.sp,
                                fontWeight = if (!isAm) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isAm) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 시(Hour) 선택 그리드 (1 ~ 12 시)
                Text(
                    text = "시 (Hour)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                )

                val hours = (1..12).toList()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hours.chunked(6).forEach { rowHours ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            rowHours.forEach { h ->
                                val isSelected = h == selectedHour12
                                Surface(
                                    onClick = { selectedHour12 = h },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$h",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. 분(Minute) 퀵 칩
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "분 (Minute)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selectedMinute = (selectedMinute - 1 + 60) % 60 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = "${selectedMinute}분",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { selectedMinute = (selectedMinute + 1) % 60 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                val minuteChips = listOf(0, 10, 15, 20, 30, 45)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    minuteChips.forEach { m ->
                        val isSelected = m == selectedMinute
                        Surface(
                            onClick = { selectedMinute = m },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d분", m),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 6. 하단 액션 버튼 바
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onTimeCleared) {
                        Text("시간 해제", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("취소", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { onTimeSelected(currentFormattedTime) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("선택", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun getOffsetDateStr(dateStr: String, offsetDays: Int): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = fmt.parse(dateStr) ?: Date()
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_MONTH, offsetDays)
        }
        fmt.format(cal.time)
    } catch (_: Exception) { "" }
}
