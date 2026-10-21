package com.example.data.repository

import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.entity.ProjectEntity
import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val subtitleLineDao: SubtitleLineDao
) {

    fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjectsWithCount().map { list ->
            list.map { it.project.toDomain(it.linesCount) }
        }
    }

    suspend fun getProjectById(id: Long): Project? {
        val withCount = projectDao.getProjectByIdWithCount(id) ?: return null
        return withCount.project.toDomain(withCount.linesCount)
    }

    fun observeProjectById(id: Long): Flow<Project?> {
        return projectDao.observeProjectByIdWithCount(id).map { withCount ->
            withCount?.project?.toDomain(withCount.linesCount)
        }
    }

    suspend fun saveProject(project: Project): Long {
        return projectDao.insertProject(ProjectEntity.fromDomain(project))
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(ProjectEntity.fromDomain(project))
    }

    suspend fun updateProgress(id: Long, status: String, progress: Float, currentStep: String) {
        projectDao.updateProgress(id, status, progress, currentStep)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }
}
