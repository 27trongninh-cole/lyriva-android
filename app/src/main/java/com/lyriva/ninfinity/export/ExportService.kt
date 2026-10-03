package com.lyriva.ninfinity.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lyriva.ninfinity.MainActivity
import com.lyriva.ninfinity.data.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Chạy việc xuất MP4 trong foreground service để tắt màn hình hoặc chuyển app vẫn xuất tiếp. */
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val ACTION_CANCEL = "com.lyriva.ninfinity.CANCEL_EXPORT"
        private const val CHANNEL = "export"
        private const val NOTIF_ID = 42

        @Volatile
        private var active = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            ExportState.cancelRequested = true
            return START_NOT_STICKY
        }
        if (active) return START_NOT_STICKY
        active = true
        ensureChannel()
        startForeground(NOTIF_ID, notif("Đang chuẩn bị…", 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch { runExport() }
        return START_NOT_STICKY
    }

    private fun runExport() {
        try {
            val p = ProjectStore(applicationContext).load()
            val uri = VideoExporter.export(
                applicationContext, p,
                { prog, msg ->
                    ExportState.progress = prog
                    ExportState.message = msg
                    update(msg, (prog * 100).toInt())
                },
                { ExportState.cancelRequested }
            )
            ExportState.resultUri = uri.toString()
            ExportState.progress = 1f
            ExportState.message = "Đã lưu vào Movies/LYRIVA"
            ExportState.done = true
        } catch (e: ExportCancelled) {
            ExportState.message = "Đã hủy xuất video"
        } catch (e: Throwable) {
            ExportState.error = e.message ?: e.javaClass.simpleName
        } finally {
            ExportState.running = false
            active = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Xuất video", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notif(text: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("LYRIVA đang xuất video")
            .setContentText(text)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Hủy", cancel)
            .build()
    }

    private fun update(text: String, percent: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(text, percent))
    }
}
