package com.kmz.v2raytun.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kmz.v2raytun.R
import com.kmz.v2raytun.ui.MainActivity

/**
 * The tunnel's ongoing notification — mandatory, not decorative: a VPN must run as a
 * foreground service, and Android requires one to be posted promptly after start.
 */
internal object TunnelNotifications {

    private const val CHANNEL_ID = "tunnel"
    private const val NOTIFICATION_ID = 1

    fun startForeground(service: Service, connecting: Boolean, serverName: String?) {
        ensureChannel(service)

        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                service.getString(
                    if (connecting) R.string.tunnel_connecting else R.string.tunnel_connected,
                ),
            )
            .setContentText(serverName)
            .setContentIntent(openAppIntent(service))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (!connecting) {
                    addAction(
                        0,
                        service.getString(R.string.action_disconnect),
                        disconnectIntent(service),
                    )
                }
            }
            .build()

        // specialUse is the documented type for VPNs, which have no dedicated FGS type.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, type)
    }

    fun stopForeground(service: Service) {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tunnel_channel_name),
                // Low: the tunnel's status should be visible, never noisy.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.tunnel_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun disconnectIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            1,
            Intent(context, V2RayVpnService::class.java).setAction(
                V2RayVpnService.ACTION_DISCONNECT,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
