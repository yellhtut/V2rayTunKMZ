package com.kmz.v2raytun.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; the Compose Scaffold handles the insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            V2RayTunTheme {
                HomeRoute(viewModel = viewModel)
            }
        }
    }
}
