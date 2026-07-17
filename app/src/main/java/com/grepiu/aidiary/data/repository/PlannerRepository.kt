package com.grepiu.aidiary.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 플래너의 목표(Goal) 및 할 일(PlannerTask) 데이터를 로컬 저장소에 영속화하는 레포지토리입니다.
 */
data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class PlannerTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean = false,
    val dateString: String, // 포맷: YYYY-MM-DD
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class PlannerRepository(private val context: Context) {
    private val goalsFile = File(context.filesDir, "goals.json")
    private val tasksFile = File(context.filesDir, "planner_tasks.json")

    /**
     * 목표 리스트를 로드합니다. 데이터가 없으면 초기 웰컴 목표들을 자동 생성합니다.
     */
    fun loadGoals(): List<Goal> {
        if (!goalsFile.exists()) {
            val defaultGoals = listOf(
                Goal(text = "매일 일기 한 장 기록하기 ✍️", isCompleted = false),
                Goal(text = "하루에 한 번 나를 위한 명상 🧘", isCompleted = false),
                Goal(text = "매주 3회 가볍게 운동하기 🏃", isCompleted = false)
            )
            saveGoals(defaultGoals)
            return defaultGoals
        }
        return try {
            val jsonStr = goalsFile.readText()
            if (jsonStr.isBlank()) return emptyList()
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Goal(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    isCompleted = obj.getBoolean("isCompleted"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 목표 리스트를 저장합니다.
     */
    fun saveGoals(goals: List<Goal>) {
        try {
            val arr = JSONArray()
            goals.forEach { goal ->
                val obj = JSONObject().apply {
                    put("id", goal.id)
                    put("text", goal.text)
                    put("isCompleted", goal.isCompleted)
                    put("timestamp", goal.timestamp)
                }
                arr.put(obj)
            }
            goalsFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 할 일 플래너 리스트를 로드합니다. 데이터가 없으면 오늘 날짜의 웰컴 할 일들을 자동 생성합니다.
     */
    fun loadTasks(): List<PlannerTask> {
        if (!tasksFile.exists()) {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val defaultTasks = listOf(
                PlannerTask(text = "오늘의 일기 잊지 않고 쓰기 📝", isCompleted = false, dateString = todayStr),
                PlannerTask(text = "플래너에 할 일 3가지 계획하기 🎯", isCompleted = false, dateString = todayStr),
                PlannerTask(text = "나를 행복하게 한 일 한 가지 떠올리기 ✨", isCompleted = false, dateString = todayStr)
            )
            saveTasks(defaultTasks)
            return defaultTasks
        }
        return try {
            val jsonStr = tasksFile.readText()
            if (jsonStr.isBlank()) return emptyList()
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlannerTask(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    isCompleted = obj.getBoolean("isCompleted"),
                    dateString = obj.getString("dateString"),
                    startTime = if (obj.isNull("startTime")) null else obj.opt("startTime") as? String,
                    endTime = if (obj.isNull("endTime")) null else obj.opt("endTime") as? String,
                    location = if (obj.isNull("location")) null else obj.opt("location") as? String,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 할 일 플래너 리스트를 저장합니다.
     */
    fun saveTasks(tasks: List<PlannerTask>) {
        try {
            val arr = JSONArray()
            tasks.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("text", task.text)
                    put("isCompleted", task.isCompleted)
                    put("dateString", task.dateString)
                    put("startTime", task.startTime ?: JSONObject.NULL)
                    put("endTime", task.endTime ?: JSONObject.NULL)
                    put("location", task.location ?: JSONObject.NULL)
                    put("timestamp", task.timestamp)
                }
                arr.put(obj)
            }
            tasksFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
