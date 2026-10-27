package com.example.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.example.data.util.NotificationHelper
import com.example.SubTranslateApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

class SubtitleExtractionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processingJob: kotlinx.coroutines.Job? = null
    private var lastStartId = 0
    private var runningProjectId: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val action = intent?.action
        if (action == ACTION_STOP) {
            processingJob?.cancel(CancellationException("Cancelado pelo usuário"))
            if (processingJob?.isCompleted != false) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(lastStartId)
            }
            return START_NOT_STICKY
        }

        val projectId = intent?.getLongExtra(EXTRA_PROJECT_ID, 0L) ?: 0L
        val projectTitle = intent?.getStringExtra(EXTRA_PROJECT_TITLE) ?: "Vídeo"
        NotificationHelper.createNotificationChannel(this)
        val notification = NotificationHelper.buildProcessingNotification(this, projectTitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        if (processingJob?.isActive == true) {
            if (runningProjectId != projectId) {
                serviceScope.launch {
                    val message = "Outra extração está em andamento. Tente novamente quando ela terminar."
                    (application as SubTranslateApp).container.projectRepository.updateProgress(
                        projectId,
                        com.example.domain.model.ProjectStatus.ERROR.name,
                        0f,
                        message
                    )
                }
            }
            return START_NOT_STICKY
        }

        runningProjectId = projectId
        activeProjectId = projectId

        val job = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var completedSuccessfully = false
            val container = (application as SubTranslateApp).container
            try {
                val project = container.projectRepository.getProjectById(projectId)
                    ?: throw IllegalStateException("Projeto não encontrado.")
                val settings = container.settingsRepository.settings.first()
                completedSuccessfully = container.extractionPipeline.execute(project, settings).isSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "Erro ao iniciar a extração do vídeo."
                withContext(NonCancellable) {
                    container.projectRepository.updateProgress(
                        projectId,
                        com.example.domain.model.ProjectStatus.ERROR.name,
                        0f,
                        message
                    )
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (completedSuccessfully && NotificationHelper.hasNotificationPermission(this@SubtitleExtractionService)) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        NotificationHelper.cancelNotification(this@SubtitleExtractionService)
                    }
                    processingJob = null
                    runningProjectId = null
                    activeProjectId = null
                    stopSelf(lastStartId)
                }
            }
        }
        processingJob = job

        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        processingJob?.cancel(CancellationException("Tempo limite do serviço de processamento excedido"))
        stopSelf(startId)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        activeProjectId = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.data.service.START"
        const val ACTION_STOP = "com.example.data.service.STOP"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_PROJECT_TITLE = "extra_project_title"

        @Volatile
        var activeProjectId: Long? = null
            private set

        fun start(context: Context, projectId: Long, projectTitle: String) {
            val intent = Intent(context, SubtitleExtractionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PROJECT_TITLE, projectTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SubtitleExtractionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}