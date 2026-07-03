package com.example.taskmanager.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val topLevelTasks: Flow<List<Task>> = taskDao.getTopLevelTasks()
    val subtaskCounts: Flow<List<SubtaskCount>> = taskDao.getSubtaskCounts()

    fun subtasksOf(parentId: Long): Flow<List<Task>> = taskDao.getSubtasks(parentId)

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun getAllTasksOnce(): List<Task> = taskDao.getAllTasksOnce()

    suspend fun upsertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) {
        taskDao.deleteSubtasksOf(task.id)
        taskDao.deleteTask(task)
    }

    suspend fun setCompleted(task: Task, completed: Boolean) =
        taskDao.updateTask(task.copy(isCompleted = completed))
}
