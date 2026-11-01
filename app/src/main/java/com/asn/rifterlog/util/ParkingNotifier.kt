package com.asn.rifterlog.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.asn.rifterlog.MainActivity
import com.asn.rifterlog.R
import com.asn.rifterlog.data.ParkedLocation

object ParkingNotifier {
    private const val CHANNEL_ID = "rifter_parking_channel"
    private const val NOTIF_ID = 2002

    fun showParkedNotification(context: Context, parked: ParkedLocation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rifter Park Bildirimleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Aracı park ettiğinizde konum ve yol tarifi bildirimi"
        }
        nm.createNotificationChannel(channel)

        val timeStr = ParkingLocationHelper.formatTimestamp(parked.timestampMillis)

        // Google Haritalar yaya yol tarifi intent'i
        val mapUri = Uri.parse("google.navigation:q=${parked.latitude},${parked.longitude}&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val mapPendingIntent = PendingIntent.getActivity(
            context,
            101,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Uygulamayı açma intent'i
        val appIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            102,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📍 Rifter Park Edildi ($timeStr)")
            .setContentText(parked.address)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${parked.address}\n\nArabanıza yürüyerek dönmek için 'Arabama Götür' butonuna dokunun.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(false)
            .setContentIntent(mapPendingIntent)
            .addAction(
                android.R.drawable.ic_dialog_map,
                "🚶 Arabama Götür",
                mapPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_info_details,
                "Uygulamayı Aç",
                appPendingIntent
            )
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIF_ID)
    }
}

