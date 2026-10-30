package com.example.data.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.service.SubtitleExtractionService

object NotificationHelper {

    private const val CHANNEL_ID = "subtranslate_processing"
    private const val CHANNEL_NAME = "Processamento de Legendas"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso da extração e tradução de legendas de vídeo"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateProgressNotification(
        context: Context,
        title: String,
        statusText: String,
        progress: Int,
        max: Int = 100
    ) {
        if (!hasNotificationPermission(context)) return

        val isCompleted = progress >= max
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(if (isCompleted) 0 else max, if (isCompleted) 0 else progress, !isCompleted && progress <= 0)
            .setOngoing(!isCompleted)
            .setAutoCancel(isCompleted)
            .setOnlyAlertOnce(true)
        if (!isCompleted) {
            builder.addAction(
                android.R.drawable.ic_delete,
                "Cancelar",
                PendingIntent.getService(
                    context,
                    NOTIFICATION_ID,
                    Intent(context, SubtitleExtractionService::class.java).apply {
                        action = SubtitleExtractionService.ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun buildProcessingNotification(context: Context, title: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("SubTranslate: $title")
            .setContentText("Processando legendas em segundo plano...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Cancelar",
                PendingIntent.getService(
                    context,
                    NOTIFICATION_ID,
                    Intent(context, SubtitleExtractionService::class.java).apply {
                        action = SubtitleExtractionService.ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
