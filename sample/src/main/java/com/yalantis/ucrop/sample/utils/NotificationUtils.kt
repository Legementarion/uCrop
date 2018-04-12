package com.yalantis.ucrop.sample.utils

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.yalantis.ucrop.sample.R

class NotificationUtils(context: Context) : ContextWrapper(context) {

    companion object {
        const val ID = "3000"
        const val CHANNEL_NAME = "ucrop_channel"
        private const val DOWNLOAD_NOTIFICATION_ID_DONE = 911
    }

    private var manager: NotificationManager? = null

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        getManager()?.createNotificationChannel(channel)
    }

    private fun getManager(): NotificationManager? {
        if (manager == null) {
            manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return manager
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getChannelNotification(title: String, content: String, intent: Intent): Notification.Builder {
        return Notification.Builder(applicationContext, ID)
                .setContentTitle(title)
                .setContentText(content)
                .setTicker(getString(R.string.notification_image_saved))
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setOngoing(false)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .setAutoCancel(true)
    }

    private fun getNotification(title: String, content: String, intent: Intent): NotificationCompat.Builder {
        return NotificationCompat.Builder(applicationContext)
                .setContentTitle(title)
                .setContentText(content)
                .setTicker(getString(R.string.notification_image_saved))
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setOngoing(false)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .setAutoCancel(true)
    }

    fun sendNotification(title: String, content: String, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = getChannelNotification(title, content, intent).build()
            getManager()?.notify(DOWNLOAD_NOTIFICATION_ID_DONE, notification)
        } else {
            val notification = getNotification(title, content, intent).build()
            getManager()?.notify(DOWNLOAD_NOTIFICATION_ID_DONE, notification)
        }
    }

}