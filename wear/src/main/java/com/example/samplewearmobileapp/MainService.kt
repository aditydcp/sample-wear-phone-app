package com.example.samplewearmobileapp

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startForegroundService

class MainService : Service() {
    private val CHANNEL_ID = "MainService"
    private var isServiceRunning = false

    companion object {
        fun startService(context: Context, message: String) {
            val intent = Intent(context, MainService::class.java)
            intent.putExtra("message", message)
            startForegroundService(context, intent)

            val service = getSystemService(context, MainService::class.java)
            service?.isServiceRunning = true
        }

        fun stopService(context: Context) {
            val service = getSystemService(context, MainService::class.java)
            service?.isServiceRunning = false

            val intent = Intent(context, MainService::class.java)
            context.stopService(intent)
        }

        fun isServiceRunning(context: Context) : Boolean {
            val service = getSystemService(context, MainService::class.java)
            return service?.isServiceRunning() ?: false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message")
        createNotificationChannel()
//        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Main Foreground Service Sample App")
            .setContentText("Tracking...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        isServiceRunning = true
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Main Foreground Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun isServiceRunning() : Boolean {
        return isServiceRunning
    }
}