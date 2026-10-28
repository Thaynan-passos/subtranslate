package com.example.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var processingWakeLock: PowerManager.WakeLock? = null

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
        if (action == ACTION_PAUSE) {
            val projectId = intent?.getLongExtra(EXTRA_PROJECT_ID, 0L)
            if (projectId == runningProjectId) releaseProcessingWakeLock()
            return START_NOT_STICKY
        }
        if (action == ACTION_RESUME) {
            val projectId = intent?.getLongExtra(EXTRA_PROJECT_ID, 0L)
            if (projectId == runningProjectId && processingJob?.isActive == true) {
                acquireProcessingWakeLock()
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

        acquireProcessingWakeLock()
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
                    releaseProcessingWakeLock()
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
        releaseProcessingWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        super.onTimeout(startId, fgsType)
    }

    override fun onDestroy() {
        releaseProcessingWakeLock()
        serviceScope.cancel()
        activeProjectId = null
        super.onDestroy()
    }

    private fun acquireProcessingWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = processingWakeLock ?: powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:SubtitleExtraction"
        ).apply {
            setReferenceCounted(false)
        }.also { processingWakeLock = it }

        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
    }

    private fun releaseProcessingWakeLock() {
        processingWakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val ACTION_START = "com.example.data.service.START"
        const val ACTION_STOP = "com.example.data.service.STOP"
        const val ACTION_PAUSE = "com.example.data.service.PAUSE"
        const val ACTION_RESUME = "com.example.data.service.RESUME"
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

        fun pause(context: Context, projectId: Long) {
            updateWakeLock(context, projectId, ACTION_PAUSE)
        }

        fun resume(context: Context, projectId: Long) {
            updateWakeLock(context, projectId, ACTION_RESUME)
        }

        private fun updateWakeLock(context: Context, projectId: Long, action: String) {
            if (activeProjectId != projectId) return
            val intent = Intent(context, SubtitleExtractionService::class.java).apply {
                this.action = action
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            context.startService(intent)
        }
    }
}