package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

data class ProjectWithLineCount(
    @Embedded val project: ProjectEntity,
    val linesCount: Int
)

@Dao
interface ProjectDao {

    @Query("""
        SELECT p.*, 
        (SELECT COUNT(*) FROM subtitle_lines WHERE projectId = p.id) AS linesCount 
        FROM projects p 
        ORDER BY p.createdAt DESC
    """)
    fun getAllProjectsWithCount(): Flow<List<ProjectWithLineCount>>

    @Query("""
        SELECT p.*, 
        (SELECT COUNT(*) FROM subtitle_lines WHERE projectId = p.id) AS linesCount 
        FROM projects p 
        WHERE p.id = :id LIMIT 1
    """)
    suspend fun getProjectByIdWithCount(id: Long): ProjectWithLineCount?

    @Query("""
        SELECT p.*, 
        (SELECT COUNT(*) FROM subtitle_lines WHERE projectId = p.id) AS linesCount 
        FROM projects p 
        WHERE p.id = :id LIMIT 1
    """)
    fun observeProjectByIdWithCount(id: Long): Flow<ProjectWithLineCount?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("""
        UPDATE projects
        SET status = :status,
            progress = :progress,
            currentStep = :currentStep,
            errorMessage = CASE WHEN :status = 'ERROR' THEN :currentStep ELSE NULL END
        WHERE id = :id
    """)
    suspend fun updateProgress(id: Long, status: String, progress: Float, currentStep: String)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}
