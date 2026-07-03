package com.example.taskmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL ORDER BY isCompleted ASC, dueDate IS NULL, dueDate ASC, createdAt DESC")
    fun getTopLevelTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAt ASC")
    fun getSubtasks(parentId: Long): Flow<List<Task>>

    @Query(
        """
        SELECT parentTaskId, COUNT(*) as total, SUM(CASE WHEN isCompleted THEN 1 ELSE 0 END) as completedCount
        FROM tasks
        WHERE parentTaskId IS NOT NULL
        GROUP BY parentTaskId
        """
    )
    fun getSubtaskCounts(): Flow<List<SubtaskCount>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubtasksOf(parentId: Long)
}
