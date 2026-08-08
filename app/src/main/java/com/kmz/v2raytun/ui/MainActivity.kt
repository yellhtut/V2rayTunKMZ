package com.kmz.v2raytun.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.kmz.v2raytun.ui.home.HomeRoute
import com.kmz.v2raytun.ui.home.HomeViewModel
import com.kmz.v2raytun.ui.theme.V2RayTunTheme

/**
 * The single activity that hosts the whole Compose UI.
 *
 * There is only one screen for now, so navigation lives inside Compose rather than in
 * multiple activities. The [HomeViewModel] is scoped to this activity so it survives
 * configuration changes, and is built through its factory because it needs the
 * [android.app.Application] to reach the database.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory(application)
    }

    /**
     * Result is ignored: the tunnel runs either way, the user just won't see its notification.
     * Denying is a reasonable choice, so it isn't worth nagging over.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; the Compose Scaffold handles the insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            V2RayTunTheme {
                HomeRoute(viewModel = viewModel)
            }
        }
    }

    /** From Android 13 the foreground-service notification is hidden without this grant. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
