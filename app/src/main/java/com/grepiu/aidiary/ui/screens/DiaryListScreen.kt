package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.repository.Goal
import com.grepiu.aidiary.data.repository.PlannerTask
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.ui.components.DownloadStatusCard
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
    onSelectDiary: (DiaryEntry) -> Unit,
    onWriteDiary: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 캘린더 일주일+ 일자 데이터 생성 (오늘 앞뒤 7일씩 총 15일 구성)
    val calendarDays = remember {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("E", Locale.KOREAN)
        val numFormat = SimpleDateFormat("d", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = format.format(cal.time)
        
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
    
    // 일기 보기 필터: 해당 날짜 일기만 볼지 여부 (기본 true로 세팅하여 날짜별 일기 쓰기 경험 극대화)
    var filterByDate by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "다이어리 App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "하루의 계획, 목표, 그리고 마음 분석까지 스마트하게",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            if (state.activeTab == "DIARY") {
                FloatingActionButton(
                    onClick = onWriteDiary,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "새 일기 쓰기",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // A. 상단 주간 캘린더 스트립
            WeeklyCalendarStrip(
                days = calendarDays,
                selectedDateStr = state.selectedDateString,
                diaries = state.diaries,
                onDateSelect = { onIntent(DiaryIntent.SelectDate(it)) }
            )

            // B. 세그먼티드 탭 셀렉터 (다이어리, 플래너, 나의 목표, AI 비서)
            TabSelector(
                activeTab = state.activeTab,
                onTabSelect = { onIntent(DiaryIntent.ChangeTab(it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                            filterByDate = filterByDate,
                            onFilterChange = { filterByDate = it },
                            onSelectDiary = onSelectDiary,
                            onWriteDiary = onWriteDiary,
                            onStartDownload = onStartDownload,
                            onCancelDownload = onCancelDownload,
                            onDismissNotice = onDismissNotice,
                            onDismissWifiWarning = onDismissWifiWarning
                        )
                    }
                    "PLANNER" -> {
                        // 플래너 할 일 탭 렌더링
                        PlannerTabContent(
                            state = state,
                            newTaskText = newTaskText,
                            onTextChange = { newTaskText = it },
                            onAddTask = {
                                if (newTaskText.isNotBlank()) {
                                    onIntent(DiaryIntent.AddPlannerTask(newTaskText, state.selectedDateString))
                                    newTaskText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            onToggleTask = { onIntent(DiaryIntent.TogglePlannerTask(it)) },
                            onDeleteTask = { onIntent(DiaryIntent.DeletePlannerTask(it)) }
                        )
                    }
                    "GOALS" -> {
                        // 목표 관리 탭 렌더링
                        GoalsTabContent(
                            state = state,
                            newGoalText = newGoalText,
                            onTextChange = { newGoalText = it },
                            onAddGoal = {
                                if (newGoalText.isNotBlank()) {
                                    onIntent(DiaryIntent.AddGoal(newGoalText))
                                    newGoalText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            onToggleGoal = { onIntent(DiaryIntent.ToggleGoal(it)) },
                            onDeleteGoal = { onIntent(DiaryIntent.DeleteGoal(it)) }
                        )
                    }
                    "CHAT" -> {
                        // AI 비서 RAG 챗봇 탭 렌더링
                        ChatTabContent(
                            state = state,
                            onSendChat = { onIntent(DiaryIntent.SendChatMessage(it)) },
                            onClearHistory = { onIntent(DiaryIntent.ClearChatHistory) }
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
    diaries: List<DiaryEntry>,
    onDateSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(days) { day ->
                val isSelected = day.dateString == selectedDateStr
                
                // 해당 일자에 작성된 일기가 있는지 판별
                val hasDiary = remember(diaries, day.dateString) {
                    diaries.any { diary ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(diary.timestamp)) == day.dateString
                    }
                }

                val backgroundBrush = if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }

                val textColor = if (isSelected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundBrush)
                        .clickable { onDateSelect(day.dateString) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = day.dayName,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = day.dayOfMonth,
                            fontSize = 18.sp,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // 하단 일기 존재 표시용 도트
                        if (hasDiary) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(5.dp))
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
@Composable
fun TabSelector(
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val tabs = listOf(
                "DIARY" to "📝 다이어리",
                "PLANNER" to "📅 플래너",
                "GOALS" to "🎯 목표",
                "CHAT" to "✨ AI 비서"
            )
            
            tabs.forEach { (tabId, label) ->
                val isSelected = activeTab == tabId
                val itemBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val itemTextColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemBg)
                        .clickable { onTabSelect(tabId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = itemTextColor
                    )
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
    filterByDate: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onSelectDiary: (DiaryEntry) -> Unit,
    onWriteDiary: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit
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

    // 선택된 날짜에 쓴 일기 필터링
    val filteredDiaries = remember(state.diaries, state.selectedDateString, filterByDate) {
        if (filterByDate) {
            state.diaries.filter { diary ->
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(diary.timestamp)) == state.selectedDateString
            }
        } else {
            state.diaries
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
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

        // 감정 빈도 통계 요약 (필터 없이 전체 일기가 다소 있을 때 유용하므로 대시보드로 상단 유지)
        item {
            MoodStatisticsDashboard(state.diaries)
        }

        // 리스트 필터링 옵션 바
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (filterByDate) "$parsedDateText 일기" else "전체 기록들",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "선택일 보기", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = filterByDate,
                        onCheckedChange = onFilterChange,
                        modifier = Modifier.scale(0.8f)
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                            .padding(vertical = 40.dp, horizontal = 24.dp)
                    ) {
                        Text(
                            text = if (filterByDate) {
                                "$parsedDateText 에 기록한 일기가 없습니다.\n오늘 하루의 생각을 먼저 남겨보세요!"
                            } else {
                                "작성된 일기가 없습니다.\n첫 일기를 남겨보세요!"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onWriteDiary,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "오늘의 일기 쓰기", fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            items(filteredDiaries, key = { it.id }) { diary ->
                DiaryListItemCard(diary = diary, onClick = { onSelectDiary(diary) })
            }
        }
        
        // 여백 공간 확보
        item {
            Spacer(modifier = Modifier.height(60.dp))
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
    onAddTask: () -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit
) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 할 일 작성 바
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = onTextChange,
                    placeholder = { Text(text = "$parsedDateText 계획 추가...", fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddTask() }),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onAddTask,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "추가", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 할 일 목록 리스트
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Text(
                    text = "이날의 플래너 계획 (${tasksForDate.size}개)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

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
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(tasksForDate, key = { it.id }) { task ->
                    PlannerTaskItemRow(
                        task = task,
                        onToggle = { onToggleTask(task.id) },
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 커스텀 체크박스 형
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (task.isCompleted) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = task.text,
                fontSize = 14.sp,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )

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
 * 5. [목표 기록] 탭 본문 영역
 */
@Composable
fun GoalsTabContent(
    state: DiaryState,
    newGoalText: String,
    onTextChange: (String) -> Unit,
    onAddGoal: () -> Unit,
    onToggleGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    val totalGoals = state.goals.size
    val completedGoals = state.goals.count { it.isCompleted }
    val progressRatio = if (totalGoals > 0) completedGoals.toFloat() / totalGoals else 0f

    Column(modifier = Modifier.fillMaxSize()) {
        // A. 목표 진행률 데시보드
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "장기 목표 달성 현황",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalGoals > 0) "${totalGoals}개 중 ${completedGoals}개 완료" else "등록된 목표 없음",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progressRatio },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(progressRatio * 100).toInt()}% 완료",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // B. 목표 작성 바
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = newGoalText,
                    onValueChange = onTextChange,
                    placeholder = { Text(text = "새로운 장기 목표 설정하기...", fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddGoal() }),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onAddGoal,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "추가", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // C. 목표 목록
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Text(
                    text = "나의 마일스톤 목표",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (state.goals.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Text(
                            text = "작성된 다이어리 목표가 없습니다.\n나를 성장시키는 매일의 다짐을 기록해 보세요!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(state.goals, key = { it.id }) { goal ->
                    GoalItemRow(
                        goal = goal,
                        onToggle = { onToggleGoal(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

/**
 * 목표 목록 행 아이템
 */
@Composable
fun GoalItemRow(
    goal: Goal,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (goal.isCompleted) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (goal.isCompleted) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (goal.isCompleted) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = goal.text,
                fontSize = 14.sp,
                color = if (goal.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )

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
 * 6. [AI 비서] 챗봇 탭 본문 영역
 */
@Composable
fun ChatTabContent(
    state: DiaryState,
    onSendChat: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 대화가 누적될 때마다 자동으로 스크롤 하단 이동
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 타이틀 및 초기화 버튼
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "온디바이스 RAG AI 비서",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "기록된 일기와 일정을 기억하는 AI 비서입니다.\n오프라인 보안 상태에서 안전하게 답변합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
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
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSendChat(suggestion) }
                        ) {
                            Text(
                                text = "“ $suggestion ”",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🤖", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            val bubbleBg = if (isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                            
                            val bubbleTextColor = if (isUser) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            val bubbleShape = if (isUser) {
                                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                            } else {
                                RoundedCornerShape(topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                            }

                            Card(
                                shape = bubbleShape,
                                colors = CardDefaults.cardColors(containerColor = bubbleBg),
                                modifier = Modifier.widthIn(max = 260.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    color = bubbleTextColor,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 하단 텍스트 전송 바
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(text = "기록된 과거 정보 물어보기...", fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !state.isGeneratingChat) {
                            onSendChat(inputText)
                            inputText = ""
                        }
                    }),
                    modifier = Modifier.weight(1f)
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
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

/**
 * 대시보드 요약 컴포저블: 기록된 일기 속 감정들의 현황을 시각화합니다.
 */
@Composable
fun MoodStatisticsDashboard(diaries: List<DiaryEntry>) {
    val totalCount = diaries.size
    val moodCounts = diaries.groupingBy { it.emotion }.eachCount()

    val joyCount = moodCounts["Joy"] ?: 0
    val calmCount = moodCounts["Calm"] ?: 0
    val sadnessCount = moodCounts["Sadness"] ?: 0
    val anxietyCount = moodCounts["Anxiety"] ?: 0
    val angerCount = moodCounts["Anger"] ?: 0

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "내 마음 통계 현황 (총 ${totalCount}회)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MoodStatBar(label = "😊 기쁨", count = joyCount, total = totalCount, color = Color(0xFFFBC02D))
                MoodStatBar(label = "🌿 평온", count = calmCount, total = totalCount, color = Color(0xFF4CAF50))
                MoodStatBar(label = "😢 슬픔", count = sadnessCount, total = totalCount, color = Color(0xFF2196F3))
                MoodStatBar(label = "😰 불안", count = anxietyCount, total = totalCount, color = Color(0xFF9C27B0))
                MoodStatBar(label = "😡 분노", count = angerCount, total = totalCount, color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun RowScope.MoodStatBar(label: String, count: Int, total: Int, color: Color) {
    val ratio = if (total > 0) count.toFloat() / total else 0f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .height(64.dp)
                .width(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(ratio)
                    .align(Alignment.BottomCenter)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${count}회",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 개별 일기 리스트 아이템 카드 컴포저블
 */
@Composable
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dateText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val (emotionText, emotionColor) = getEmotionUI(diary.emotion)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = emotionColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = emotionText,
                            color = emotionColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = diary.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = previewText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                if (diary.aiAnalysis != null || attachmentCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (diary.aiAnalysis != null) {
                            Text(
                                text = "✨ AI 피드백",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (attachmentCount > 0) {
                            if (diary.aiAnalysis != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "🖼️ 사진 ${attachmentCount}장",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (thumbnailFile != null) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = "일기 썸네일",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
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
