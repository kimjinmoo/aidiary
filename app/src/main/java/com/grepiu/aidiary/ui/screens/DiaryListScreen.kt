package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

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

    var isSearchFocused by remember { mutableStateOf(false) }
    var isChatInputFocused by remember { mutableStateOf(false) }
    val isSearchActive = (state.searchQuery.isNotBlank() || isSearchFocused) && state.activeTab == "DIARY"
    // 헤더/캘린더/탭셀렉터를 숨겨야 하는 상태 (검색 활성 또는 AI 비서 입력 포커스)
    val isHeaderHidden = isSearchActive || (isChatInputFocused && state.activeTab == "CHAT")

    // AI 가 추천한 플래너 할 일을 입력란에 1회성으로 반영하고, 사용 후엔 상태를 비웁니다.
    LaunchedEffect(state.suggestedPlannerTaskText) {
        val suggested = state.suggestedPlannerTaskText ?: return@LaunchedEffect
        newTaskText = if (suggested.length > 50) suggested.substring(0, 50) else suggested
        onIntent(DiaryIntent.ClearSuggestedPlannerTask)
    }

    Scaffold(
        bottomBar = {
            if (state.activeTab == "DIARY" && !isHeaderHidden) {
                WriteActionBar(onWriteDiary = onWriteDiary)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding()
                .fillMaxSize()
        ) {
            if (!isHeaderHidden) {
                // C. 공간 효율적이고 고급스러운 상단 오늘의 날짜/인사말 헤더
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val headerTitle = remember(state.selectedDateString) {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayStr = format.format(Date())
                    if (state.selectedDateString == todayStr) "오늘의 기록" else "기록 탐색"
                }

                val headerSub = remember(state.selectedDateString) {
                    try {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val date = format.parse(state.selectedDateString) ?: Date()
                        SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN).format(date)
                    } catch (e: Exception) {
                        state.selectedDateString
                    }
                }

                Column {
                    Text(
                        text = headerTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = headerSub,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isSelectedToday = remember(state.selectedDateString) {
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val todayStr = format.format(Date())
                        state.selectedDateString == todayStr
                    }

                    if (!isSelectedToday) {
                        Button(
                            onClick = {
                                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val todayStr = format.format(Date())
                                onIntent(DiaryIntent.SelectDate(todayStr))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("오늘", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 달력 피커 버튼 (과거/미래 무한 탐색)
                    IconButton(
                        onClick = {
                            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val cal = Calendar.getInstance()
                            try {
                                format.parse(state.selectedDateString)?.let { cal.time = it }
                            } catch (e: Exception) {}
                            
                            val dialog = android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectCal = Calendar.getInstance()
                                    selectCal.set(Calendar.YEAR, year)
                                    selectCal.set(Calendar.MONTH, month)
                                    selectCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    onIntent(DiaryIntent.SelectDate(format.format(selectCal.time)))
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "오늘") { _, _ ->
                                val todayStr = format.format(Date())
                                onIntent(DiaryIntent.SelectDate(todayStr))
                            }
                            dialog.show()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "달력 선택 피커",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // A. 상단 주간 캘린더 스트립
            WeeklyCalendarStrip(
                days = calendarDays,
                selectedDateStr = state.selectedDateString,
                diaries = state.diaries,
                plannerTasks = state.plannerTasks,
                goals = state.goals,
                onDateSelect = { onIntent(DiaryIntent.SelectDate(it)) }
            )

            // B. 세그먼티드 탭 셀렉터 (다이어리, 플래너, 나의 목표, AI 비서)
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
                    .padding(horizontal = 16.dp)
            ) {
                when (state.activeTab) {
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
                            onRequestBriefing = { onIntent(DiaryIntent.RequestBriefing("DIARY")) },
                            onSearch = { onIntent(DiaryIntent.SearchDiaries(it)) },
                            onClearSearch = { onIntent(DiaryIntent.ClearDiarySearch) },
                            isSearchFocused = isSearchFocused,
                            onSearchFocusChange = { isSearchFocused = it },
                            onCancelSearch = {
                                isSearchFocused = false
                                onIntent(DiaryIntent.ClearDiarySearch)
                            }
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
                    "CHAT" -> {
                        // AI 비서 RAG 챗봇 탭 렌더링
                        ChatTabContent(
                            state = state,
                            onSendChat = { onIntent(DiaryIntent.SendChatMessage(it)) },
                            onClearHistory = { onIntent(DiaryIntent.ClearChatHistory) },
                            onInputFocusChange = { isChatInputFocused = it }
                        )
                    }
                }
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
    diaries: List<DiaryMeta>,
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
            .padding(vertical = 10.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(days, key = { it.dateString }) { day ->
                val isSelected = day.dateString == selectedDateStr
                
                // 1. 해당 일자에 작성된 기록(일기)이 있는지 판별
                val hasDiary = remember(diaries, day.dateString) {
                    diaries.any { diary ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(diary.timestamp)) == day.dateString
                    }
                }
                
                // 2. 해당 일자에 할 일(플래너)이 있는지 판별
                val hasTask = remember(plannerTasks, day.dateString) {
                    plannerTasks.any { task ->
                        task.dateString == day.dateString
                    }
                }

                // 3. 해당 일자에 목표가 등록되었는지 판별 (timestamp 변환 매칭)
                val hasGoal = remember(goals, day.dateString) {
                    goals.any { goal ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(goal.timestamp)) == day.dateString
                    }
                }

                // 부드러운 스케일 및 색상 전환 애니메이션
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 0.95f,
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarScale"
                )

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarTextColor"
                )

                val subTextColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "CalendarSubTextColor"
                )

                val cellBorder = if (isSelected) {
                    BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(backgroundColor)
                        .border(cellBorder, RoundedCornerShape(18.dp))
                        .clickable { onDateSelect(day.dateString) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = if (day.isToday) "오늘" else day.dayName,
                            fontSize = 11.sp,
                            color = if (day.isToday && !isSelected) MaterialTheme.colorScheme.primary else subTextColor,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = day.dayOfMonth,
                            fontSize = 18.sp,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // 하단 항목 존재 표시용 미니 컬러 도트 세트 (기록: 블루, 플래너: 오렌지, 목표: 그린)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (hasDiary) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFF42A5F5))
                                )
                            }
                            if (hasTask) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFFFFB74D))
                                )
                            }
                            if (hasGoal) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFF66BB6A))
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
            Triple("PLANNER", "플래너", Icons.Default.DateRange),
            Triple("GOALS", "목표", Icons.Default.TaskAlt),
            Triple("CHAT", "AI 비서", Icons.Default.Star)
        )
    }
    val selectedIndex = remember(activeTab) {
        tabs.indexOfFirst { it.first == activeTab }.coerceAtLeast(0)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
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
                .height(40.dp)
        ) {
            val maxWidth = this.maxWidth
            val tabWidth = maxWidth / 4

            // 슬라이딩 백그라운드 인디케이터
            val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                ),
                label = "TabIndicatorOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        )
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
                                tint = itemTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = itemTextColor,
                                maxLines = 1,
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
    onRequestBriefing: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    isSearchFocused: Boolean,
    onSearchFocusChange: (Boolean) -> Unit,
    onCancelSearch: () -> Unit
) {
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

    // 선택된 날짜에 쓴 일기 및 유형(ContentType) 중첩 필터링.
    // 검색 모드일 땐 날짜/타입 필터 무시하고 검색 결과 그대로 노출.
    val filteredDiaries = remember(state.diaries, state.selectedDateString, selectedTypeFilter, isSearchMode) {
        if (isSearchMode) state.diaries
        else state.diaries.filter { diary ->
            val dateMatch = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(diary.timestamp)) == state.selectedDateString

            val typeMatch = if (selectedTypeFilter != null) {
                diary.contentType == selectedTypeFilter
            } else {
                true
            }
            dateMatch && typeMatch
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
        // ===== 검색바 (v4) - 상단 고정, 취소 버튼 포함 =====
        DiarySearchBar(
            query = state.searchQuery,
            isSearching = state.isSearching,
            onSubmit = onSearch,
            onClear = onClearSearch,
            isFocused = isSearchFocused,
            onFocusChange = onSearchFocusChange,
            onCancel = {
                focusManager.clearFocus()
                onClearSearch()
                onCancelSearch()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // AI 브리핑 카드 (탭 상단, 사용자가 명시적으로 요청)
            item {
                AiBriefingCard(
                    title = "기록 AI 브리핑",
                    briefing = state.diaryBriefing,
                    isLoading = state.isBriefingDiary,
                    isModelReady = state.isModelReady,
                    onRequest = onRequestBriefing
                )
            }

            // AI 모델 다운로드 카드
            if (!state.isModelReady) {
                item {
                    DownloadStatusCard(
                        state = state,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDismissNotice = onDismissNotice,
                        onDismissWifiWarning = onDismissWifiWarning
                    )
                }
            }

            // 리스트 필터링 옵션 바 및 책갈피 인덱스 탭 디자인의 타입 필터
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val typeName = when {
                        isSearchMode -> "'${state.searchQuery}' 검색 결과"
                        selectedTypeFilter == ContentType.DIARY -> "일기"
                        selectedTypeFilter == ContentType.POST -> "새 글"
                        selectedTypeFilter == ContentType.NOTE -> "메모"
                        else -> "전체 기록"
                    }
                    Text(
                        text = if (isSearchMode) "$typeName (${filteredDiaries.size}개)"
                               else "$parsedDateText $typeName (${filteredDiaries.size}개)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 다이어리 감성의 책갈피 인덱스 탭 디자인 필터 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val filterItems = listOf(
                        Triple(null as ContentType?, "전체", Icons.AutoMirrored.Filled.MenuBook to Color(0xFF90A4AE)),
                        Triple(ContentType.DIARY, "일기", Icons.AutoMirrored.Filled.MenuBook to Color(0xFF42A5F5)),
                        Triple(ContentType.POST, "새 글", Icons.Default.EditNote to Color(0xFF66BB6A)),
                        Triple(ContentType.NOTE, "메모", Icons.Default.NoteAlt to Color(0xFFFFA726))
                    )

                    filterItems.forEach { (type, label, iconPair) ->
                        val isSelected = selectedTypeFilter == type
                        val offsetByState by animateDpAsState(
                            targetValue = if (isSelected) (-6).dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BookmarkOffset"
                        )
                        
                        val alphaByState by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.65f,
                            label = "BookmarkAlpha"
                        )

                        val itemColor = iconPair.second
                        val itemIcon = iconPair.first

                        // 선택 시 Solid 파스텔배경 + 흰색(White) 텍스트/아이콘으로 시각적 퀄리티 대폭 상승
                        val bgColor = if (isSelected) itemColor else itemColor.copy(alpha = 0.05f)
                        val borderColor = if (isSelected) Color.Transparent else itemColor.copy(alpha = 0.2f)
                        val textColor = if (isSelected) Color.White else itemColor.copy(alpha = 0.8f)

                        Surface(
                            onClick = { onTypeFilterChange(type) },
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .offset(y = offsetByState)
                                .alpha(alphaByState),
                            shadowElevation = if (isSelected) 4.dp else 0.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = itemIcon,
                                    contentDescription = label,
                                    tint = textColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // 책갈피 탭 하단의 구분선
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            }
        }

        // 일기 항목들
        if (filteredDiaries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
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

                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "$parsedDateText 에 작성된 ${typeName}가 없습니다.\n오늘 있었던 일이나 생각을 기록해보세요!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { onWriteDiary(ContentType.DIARY) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "오늘의 ${typeName} 남기기", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(filteredDiaries, key = { it.id }) { diary ->
                DiaryListItemCard(diary = diary, onClick = { onSelectDiary(diary) })
            }
            // 페이지네이션: 검색 모드가 아니고, 더 보기 가능할 때만 끝에 도달하면 추가 로드
            if (!isSearchMode && state.diaryHasMore) {
                item {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLoadingMoreDiaries) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "더 보기 (${filteredDiaries.size}/${state.diaryTotalCount})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onLoadMore() }
                            )
                        }
                    }
                }
            }
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
 * 4. [플래너 할 일] 탭 본문 영역
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
    onRequestBriefing: () -> Unit
) {
    val context = LocalContext.current

    // 선택된 날짜에 필터링된 할 일 목록
    val tasksForDate = remember(state.plannerTasks, state.selectedDateString) {
        state.plannerTasks.filter { it.dateString == state.selectedDateString }
    }

    val parsedDateText = remember(state.selectedDateString) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(state.selectedDateString) ?: Date()
            SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(date)
        } catch (e: Exception) {
            state.selectedDateString
        }
    }

    var isInputFocused by remember { mutableStateOf(false) }
    
    // 시간 및 장소 로컬 입력 상태
    var startTime by remember { mutableStateOf<String?>(null) }
    var endTime by remember { mutableStateOf<String?>(null) }
    var locationText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    // 반복 등록 상태
    var isRepeat by remember { mutableStateOf(false) }
    val selectedDays = remember { mutableStateListOf<Int>() } // 1(월) .. 7(일)
    var repeatEndDateStr by remember { mutableStateOf<String?>(null) }

    // 반복 계획 삭제 확인 다이얼로그 대상 (null 이면 다이얼로그 미표시)
    var taskPendingDelete by remember { mutableStateOf<PlannerTask?>(null) }

    val showTimePicker = { isStart: Boolean ->
        val cal = Calendar.getInstance()
        val currentStr = if (isStart) startTime else endTime
        if (!currentStr.isNullOrBlank()) {
            val parts = currentStr.split(":")
            if (parts.size == 2) {
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 12)
                cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
            }
        }
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                if (isStart) {
                    startTime = formatted
                } else {
                    endTime = formatted
                }
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    val showEndDatePicker = {
        val cal = Calendar.getInstance()
        if (!repeatEndDateStr.isNullOrBlank()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.parse(repeatEndDateStr)?.let { cal.time = it }
            } catch (e: Exception) {}
        }
        android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                repeatEndDateStr = formatted
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val handleAddTask = {
        if (newTaskText.isNotBlank()) {
            onAddTask(
                newTaskText,
                startTime,
                endTime,
                locationText.takeIf { it.isNotBlank() },
                isRepeat,
                selectedDays.toList(),
                repeatEndDateStr
            )
            // 리셋
            startTime = null
            endTime = null
            locationText = ""
            isRepeat = false
            selectedDays.clear()
            repeatEndDateStr = null
            isExpanded = false
        } else {
            android.widget.Toast.makeText(
                context,
                "할 일을 입력해주세요.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // LazyColumn 내부로 입력 폼을 편입시켜 키보드가 활성화되었을 때 자동으로 포커스 영역이 밀려 올라오도록 처리
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 0. AI 브리핑 카드 (탭 상단)
        item {
            AiBriefingCard(
                title = "플래너 AI 브리핑",
                briefing = state.plannerBriefing,
                isLoading = state.isBriefingPlanner,
                isModelReady = state.isModelReady,
                onRequest = onRequestBriefing
            )
        }

        // 1. 입력 폼 아이템
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isInputFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = newTaskText,
                            onValueChange = onTextChange,
                            placeholder = { Text(text = "$parsedDateText 계획 추가...", fontSize = 14.sp) },
                            singleLine = true,
                            suffix = {
                                Text(
                                    text = "${newTaskText.length}/50",
                                    fontSize = 11.sp,
                                    color = if (newTaskText.length >= 50) MaterialTheme.colorScheme.error 
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { handleAddTask() }),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isInputFocused = it.isFocused }
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        // AI 자동 플래너명 추천 버튼
                        AiSuggestPlannerTaskButton(
                            isModelReady = state.isModelReady,
                            isSuggesting = state.isSuggestingPlannerTask,
                            onClick = {
                                onSuggestTask(
                                    DiaryIntent.SuggestPlannerTask(
                                        startTime = startTime,
                                        endTime = endTime,
                                        location = locationText.takeIf { it.isNotBlank() },
                                        isRepeat = isRepeat,
                                        repeatDays = selectedDays.toList(),
                                        repeatEndDateString = repeatEndDateStr
                                    )
                                )
                            }
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        // 상세 정보 확장 토글 버튼
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.EditNote,
                                contentDescription = "시간/장소 설정",
                                tint = if (isExpanded || startTime != null || endTime != null || locationText.isNotEmpty() || isRepeat) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { handleAddTask() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "추가",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 시작/종료시간 & 장소 입력 확장 폼
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            )

                            // 시간 선택 칩셋 Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 시작 시간
                                SuggestionChip(
                                    onClick = { showTimePicker(true) },
                                    label = {
                                        Text(
                                            text = startTime?.let { "시작: $it" } ?: "⏰ 시작 시간",
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (startTime != null) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                )

                                // 종료 시간
                                SuggestionChip(
                                    onClick = { showTimePicker(false) },
                                    label = {
                                        Text(
                                            text = endTime?.let { "종료: $it" } ?: "⏰ 종료 시간",
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (endTime != null) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                )

                                // 지우기 버튼 (시간 리셋)
                                if (startTime != null || endTime != null) {
                                    TextButton(
                                        onClick = {
                                            startTime = null
                                            endTime = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("시간 리셋", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 장소 입력 텍스트 필드 (텍스트 가독성 고도화)
                            OutlinedTextField(
                                value = locationText,
                                onValueChange = { locationText = it },
                                placeholder = { Text(text = "📍 장소 입력 (예: 강남역 카페, 집 등)", fontSize = 12.5.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            )

                            // 1. 반복 설정 토글 스위치 형태의 칩셋 Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = if (isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "반복 계획으로 등록",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = isRepeat,
                                    onCheckedChange = { isRepeat = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }

                            // 2. 반복 설정이 켜졌을 때 요일 선택 및 종료일 피커 노출
                            AnimatedVisibility(visible = isRepeat) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "반복 요일 (다중 선택)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // 월~일 요일 토글 버튼 그룹
                                    val daysList = listOf("월" to 1, "화" to 2, "수" to 3, "목" to 4, "금" to 5, "토" to 6, "일" to 7)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        daysList.forEach { (name, value) ->
                                            val isSelected = selectedDays.contains(value)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else Color.Transparent
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        if (isSelected) selectedDays.remove(value) else selectedDays.add(value)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = name,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // 반복 종료일 선택 영역
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "반복 종료일",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        SuggestionChip(
                                            onClick = { showEndDatePicker() },
                                            label = {
                                                Text(
                                                    text = repeatEndDateStr?.let { "종료: $it" } ?: "📅 종료일 선택",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (repeatEndDateStr != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (repeatEndDateStr != null) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 리스트 타이틀 아이템
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "이날의 플래너 계획 (${tasksForDate.size}개)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // 3. 할 일 목록 아이템들 분기
        if (tasksForDate.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Text(
                        text = "오늘 세운 계획이 없습니다.\n할 일을 기록하고 똑똑하게 실천해 보세요!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(tasksForDate, key = { it.id }) { task ->
                PlannerTaskItemRow(
                    task = task,
                    onToggle = { onToggleTask(task.id) },
                    onDelete = {
                        if (task.seriesId != null) {
                            taskPendingDelete = task
                        } else {
                            onDeleteTask(task)
                        }
                    }
                )
            }
        }

        // 4. 여백 확보용 하단 간격 아이템
        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // 반복 계획 일괄 등록된 할 일의 삭제 옵션 선택 다이얼로그
    taskPendingDelete?.let { pending ->
        PlannerSeriesDeleteDialog(
            task = pending,
            onDeleteAll = {
                onDeleteTaskSeries(pending)
                taskPendingDelete = null
            },
            onDeleteThisOnly = {
                onDeleteTask(pending)
                taskPendingDelete = null
            },
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
        else -> "AI 자동 플래너명"
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
        "일반" to Color(0xFF78909C),
        "건강" to Color(0xFF66BB6A),
        "공부" to Color(0xFF42A5F5),
        "커리어" to Color(0xFFAB47BC),
        "자산" to Color(0xFFFFA726),
        "취미" to Color(0xFFEC407A)
    )
    var selectedCategory by remember { mutableStateOf("일반") }
    var isInputFocused by remember { mutableStateOf(false) }
    var isFinishedExpanded by remember { mutableStateOf(false) }

    // 진행 중인 목표와 완료된 목표 분리
    val activeGoals = remember(state.goals) { state.goals.filter { !it.isCompleted } }
    val completedGoalsList = remember(state.goals) { state.goals.filter { it.isCompleted } }

    // 입력 필드 포커스 시 LazyColumn 이 입력 카드를 키보드 위로 스크롤하도록 요청
    val inputBringIntoView = remember { BringIntoViewRequester() }
    val inputFocusScope = rememberCoroutineScope()

    // 단일 LazyColumn 으로 통합: 대시보드 + 입력 + 목록 모두 스크롤 대상.
    // 키보드(ime) 가 올라와도 부모 Column 의 imePadding 으로 LazyColumn 높이가 줄어들고,
    // 입력 필드의 BringIntoViewRequester 가 키보드에 가려지지 않도록 자동 스크롤한다.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // AI 브리핑 카드 (탭 상단, 사용자가 명시적으로 요청)
        item {
            AiBriefingCard(
                title = "목표 AI 브리핑",
                briefing = state.goalsBriefing,
                isLoading = state.isBriefingGoals,
                isModelReady = state.isModelReady,
                onRequest = onRequestBriefing
            )
        }

        // A. 목표 진행률 대시보드 (원형 게이지 및 동적 응원 메시지 도입)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
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
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            strokeWidth = 7.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "${(progressRatio * 100).toInt()}%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val welcomeMsg = when {
                            progressRatio >= 1.0f -> "와! 모든 목표를 달성하셨어요! 🎉"
                            progressRatio >= 0.7f -> "정말 멋진 성취입니다! 목표 달성이 코앞이에요. 👏"
                            progressRatio >= 0.3f -> "좋은 속도로 나아가고 있습니다. 힘내세요! 💪"
                            progressRatio > 0f -> "시작이 반이에요! 매일 다짐을 이뤄보세요. ✨"
                            else -> "나만의 성장 목표를 세우고 채워 나가볼까요? 🎯"
                        }
                        Text(
                            text = welcomeMsg,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (totalGoals > 0) "${totalGoals}개 중 ${completedGoals}개 완료" else "등록된 다짐이 없습니다.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // B. 목표 작성 카드 (카테고리 선택 + 입력)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isInputFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(inputBringIntoView)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (catName, catColor) ->
                            val isCatSelected = selectedCategory == catName
                            val catBg = if (isCatSelected) catColor.copy(alpha = 0.18f) else Color.Transparent
                            val catBorder = if (isCatSelected) catColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(catBg)
                                    .border(1.dp, catBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedCategory = catName }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = catName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCatSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentThemeColor = categories.find { it.first == selectedCategory }?.second ?: MaterialTheme.colorScheme.primary
                        OutlinedTextField(
                            value = newGoalText,
                            onValueChange = onTextChange,
                            placeholder = { Text(text = "새로운 장기 다짐 설정하기...", fontSize = 13.5.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newGoalText.isNotBlank()) {
                                    onAddGoal(newGoalText, selectedCategory)
                                }
                            }),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    isInputFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        inputFocusScope.launch {
                                            inputBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                        )

                        IconButton(
                            onClick = {
                                if (newGoalText.isNotBlank()) {
                                    onAddGoal(newGoalText, selectedCategory)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = currentThemeColor
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "추가",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // C-1. 진행 중인 다짐 섹션
        item {
            Text(
                text = "나의 활성 다짐 (${activeGoals.size}개)",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (activeGoals.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Text(
                        text = "현재 진행 중인 다짐이 없습니다.\n상단에서 나를 빛내줄 새로운 목표를 세워보세요!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
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

        // C-2. 명예의 전당 (달성 완료 섹션)
        if (completedGoalsList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = { isFinishedExpanded = !isFinishedExpanded },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🏆 명예의 전당 (달성 완료 ${completedGoalsList.size}개)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (isFinishedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(
            width = 1.dp,
            color = if (goal.isCompleted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)
                    else catColor.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (goal.isCompleted) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (goal.isCompleted) 0.98f else 1.0f)
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

/**
 * 6. [AI 비서] 챗봇 탭 본문 영역
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTabContent(
    state: DiaryState,
    onSendChat: (String) -> Unit,
    onClearHistory: () -> Unit,
    onInputFocusChange: (Boolean) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isInputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // 키보드가 시스템 백버튼 등으로 닫혀도 포커스 해제가 안 되는 문제 해결:
    // WindowInsets.isImeVisible 로 IME 상태를 감지하여 키보드가 사라지면 강제로 clearFocus()
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isInputFocused) {
            focusManager.clearFocus()
        }
    }

    // 대화가 누적될 때마다 자동으로 스크롤 하단 이동
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    // 채팅 목록 스크롤 시 키보드 자동 닫기 (포커스 해제)
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && isInputFocused) {
            focusManager.clearFocus()
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .imePadding()
    ) {
        // 상단 타이틀 및 초기화/접기 버튼
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = "온디바이스 RAG AI 비서",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 입력 포커스 중일 때만 '접기' 버튼 노출
                AnimatedVisibility(
                    visible = isInputFocused,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
                ) {
                    IconButton(
                        onClick = { focusManager.clearFocus() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "키보드 닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (state.chatMessages.isNotEmpty()) {
                    Text(
                        text = "대화 비우기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { onClearHistory() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 메시지 표시 영역
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.chatMessages.isEmpty()) {
                // 첫 진입 시 웰컴 메세지 & RAG 추천 질문 가이드 출력
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "작성된 기록과 일정을 기억하는 AI 비서입니다.\n오프라인 보안 상태에서 안전하게 답변합니다.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = "💡 이런 것들을 물어보세요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val suggestions = listOf(
                        "내가 최근에 파스타 맛있게 먹었다고 한 맛집 어디야?",
                        "오늘 계획한 할 일 목록 다 말해줘",
                        "내 다이어리에 기록된 주요 장기 목표가 뭐야?"
                    )

                    suggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSendChat(suggestion) }
                        ) {
                            Text(
                                text = "“ $suggestion ”",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.chatMessages) { msg ->
                        val isUser = msg.sender == "USER"
                        
                        Row(
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isUser) {
                                // AI 아이콘
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🤖", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            val bubbleBg = if (isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            
                            val bubbleTextColor = if (isUser) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            // 모던 비대칭 라운딩 처리
                            val bubbleShape = if (isUser) {
                                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp)
                            } else {
                                RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp, topStart = 4.dp, bottomEnd = 20.dp)
                            }

                            val cellBorder = if (isUser) {
                                null
                            } else {
                                BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                )
                            }

                            Card(
                                shape = bubbleShape,
                                colors = CardDefaults.cardColors(containerColor = bubbleBg),
                                border = cellBorder,
                                modifier = Modifier.widthIn(max = 270.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = bubbleTextColor,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 하단 텍스트 전송 바
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isInputFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(text = "기록된 과거 정보 물어보기...", fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !state.isGeneratingChat) {
                            onSendChat(inputText)
                            inputText = ""
                        }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            isInputFocused = it.isFocused
                            onInputFocusChange(it.isFocused)
                        }
                )

                if (state.isGeneratingChat) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 6.dp)
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendChat(inputText)
                                inputText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "보내기",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (typeIcon, typeLabel, typeColor) = getContentTypeUI(diary.contentType)
                Icon(
                    imageVector = typeIcon,
                    contentDescription = typeLabel,
                    tint = typeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (diary.emotion.isNotBlank() && diary.emotion != "Neutral") {
                    Spacer(modifier = Modifier.width(8.dp))
                    EmotionChipSmall(diary.emotion)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = diary.title.ifBlank { "(제목 없음)" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (previewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = previewText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
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
        "Joy" -> Color(0xFFFFB300)
        "Sadness" -> Color(0xFF1976D2)
        "Anger" -> Color(0xFFD32F2F)
        "Anxiety" -> Color(0xFF7B1FA2)
        "Calm" -> Color(0xFF388E3C)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = emotion, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
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
        "Joy" -> Pair("😊 기쁨", Color(0xFFD4AF37))
        "Calm" -> Pair("🌿 평온", Color(0xFF2E7D32))
        "Sadness" -> Pair("😢 슬픔", Color(0xFF1565C0))
        "Anxiety" -> Pair("😰 불안", Color(0xFF7B1FA2))
        "Anger" -> Pair("😡 분노", Color(0xFFC62828))
        else -> Pair("⚪ 보통", Color(0xFF555555))
    }
}

/**
 * 콘텐츠 타입별 아이콘/라벨/색상 (목록·상세 공통).
 */
fun getContentTypeUI(type: ContentType): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Color> {
    return when (type) {
        ContentType.DIARY -> Triple(Icons.AutoMirrored.Filled.MenuBook, "일기", Color(0xFF1565C0))
        ContentType.POST -> Triple(Icons.Default.EditNote, "새 글", Color(0xFF6A1B9A))
        ContentType.NOTE -> Triple(Icons.Default.NoteAlt, "메모", Color(0xFF2E7D32))
    }
}

/**
 * 상업 서비스 수준 하단 글쓰기 허브 바 (재설계 v2).
 *
 * 구조:
 *  1행 — "✍️ 기록하기" 대형 그라디언트 CTA 버튼 (타입 선택 없이 즉시 작성 화면 진입)
 *  2행 — 타입 퀵셀렉 칩 [일기 · 새 글 · 메모] (선택 시 해당 타입으로 바로 진입, 강제 아님)
 *
 * UX 철학:
 *  - 메인 플로우는 "기록하기" → 작성 화면 → 타입은 내부에서 AI 자동 분류 or 수동 변경
 *  - 타입을 미리 알고 있을 때만 퀵셀렉 칩 활용
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

    val typeChips = listOf(
        Pair(ContentType.DIARY, "일기"),
        Pair(ContentType.POST, "새 글"),
        Pair(ContentType.NOTE, "메모")
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 24.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp
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
                    .padding(top = 12.dp, bottom = 16.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            )

            // ── 1행: 메인 CTA 버튼 ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(mainScale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = mainInteraction,
                        indication = null,
                        onClick = { onWriteDiary(ContentType.DIARY)
                        }
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

            Spacer(Modifier.height(12.dp))

            // ── 2행: 타입 퀵셀렉 칩 ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                typeChips.forEach { (type, label) ->
                    val chipInteraction = remember { MutableInteractionSource() }
                    val isChipPressed by chipInteraction.collectIsPressedAsState()
                    val chipScale by animateFloatAsState(
                        targetValue = if (isChipPressed) 0.93f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "ChipScale_$label"
                    )
                    val (chipIcon, _, chipColor) = getContentTypeUI(type)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .scale(chipScale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipColor.copy(alpha = 0.08f))
                            .border(
                                width = 1.dp,
                                color = chipColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null,
                                onClick = { onWriteDiary(type) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = label,
                                tint = chipColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = chipColor
                            )
                        }
                    }
                }
            }

            // 시스템 네비게이션 바 여백
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
