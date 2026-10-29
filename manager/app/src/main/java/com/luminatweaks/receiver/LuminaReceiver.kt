package com.luminatweaks.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.luminatweaks.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LuminaReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOTIFY = "com.luminatweaks.ACTION_NOTIFY"
        const val ACTION_RESHOW = "com.luminatweaks.ACTION_RESHOW"

        const val CHANNEL_PROFILE = "lumina_profile"
        const val CHANNEL_SYSTEM = "lumina_system"

        const val GROUP_KEY_SYSTEM = "com.luminatweaks.SYSTEM_EVENTS"
        const val NOTIF_ID_PROFILE = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Automatically self-grant notification permission via root if missing
        ensureNotificationPermission(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels(manager)

        when (intent.action) {
            ACTION_RESHOW -> handleReshow(context, intent, manager)
            ACTION_NOTIFY -> handleNotify(context, intent, manager)
        }
    }

    private fun ensureNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
                    ).waitFor()
                } catch (_: Exception) {
                    // Suppress root shell execution failures
                }
            }
        }
    }

    private fun handleNotify(context: Context, intent: Intent, manager: NotificationManager) {
        // Clear all active notifications if requested
        if (intent.getBooleanExtra("clearall", false)) {
            manager.cancelAll()
            return
        }

        // Show optional floating toast
        val toastText = intent.getStringExtra("toasttext")
        if (!toastText.isNullOrEmpty()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            }
        }

        val title = intent.getStringExtra("notifytitle") ?: "LuminaTweaks"
        val text = intent.getStringExtra("notifytext") ?: return
        val isProfile = intent.getBooleanExtra("is_profile", false)
        val chrono = intent.getBooleanExtra("chrono", false)
        val timeout = intent.getLongExtra("timeout", 0L)

        if (isProfile) {
            postProfileNotification(context, manager, title, text, chrono)
        } else {
            postSystemNotification(context, manager, title, text, timeout)
        }
    }

    private fun postProfileNotification(
        context: Context,
        manager: NotificationManager,
        title: String,
        text: String,
        chrono: Boolean
    ) {
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        // Reshow profile notification after 3 seconds if swiped away
        val deleteIntent = Intent(context, LuminaReceiver::class.java).apply {
            action = ACTION_RESHOW
            putExtra("notifytitle", title)
            putExtra("notifytext", text)
            putExtra("chrono", chrono)
        }

        val pendingDelete = PendingIntent.getBroadcast(
            context,
            NOTIF_ID_PROFILE,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PROFILE)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(pendingDelete)

        if (chrono) {
            builder.setUsesChronometer(true)
        }

        manager.notify(NOTIF_ID_PROFILE, builder.build())
    }

    private fun postSystemNotification(
        context: Context,
        manager: NotificationManager,
        title: String,
        text: String,
        timeout: Long
    ) {
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val notifId = (System.currentTimeMillis() % 10000).toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(largeIcon)
            .setGroup(GROUP_KEY_SYSTEM)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (timeout > 0L) {
            builder.setTimeoutAfter(timeout)
        }

        manager.notify(notifId, builder.build())
    }

    private fun handleReshow(context: Context, intent: Intent, manager: NotificationManager) {
        val title = intent.getStringExtra("notifytitle") ?: "LuminaTweaks"
        val text = intent.getStringExtra("notifytext") ?: return
        val chrono = intent.getBooleanExtra("chrono", false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(3000L)
                postProfileNotification(context, manager, title, text, chrono)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun ensureChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val profileChannel = NotificationChannel(
                CHANNEL_PROFILE,
                "Profile Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active tuning profile indicator"
                setShowBadge(false)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "System Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "System execution events and logs"
            }

            manager.createNotificationChannel(profileChannel)
            manager.createNotificationChannel(systemChannel)
        }
    }
}
