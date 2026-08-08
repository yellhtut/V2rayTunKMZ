package com.kmz.v2raytun.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmz.v2raytun.R
import com.kmz.v2raytun.core.TunnelState
import com.kmz.v2raytun.data.model.Profile

/**
 * Stateful entry point: binds the [HomeViewModel] to the stateless [HomeScreen].
 *
 * The clipboard read happens here, not in the ViewModel: from Android 10 only the focused
 * app may read the clipboard, so it has to be triggered by a UI action.
 */
@Composable
fun HomeRoute(viewModel: HomeViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    // Android's own VPN consent dialog. Granting it is what allows the TUN to be opened.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        viewModel.consumeMessage()
    }

    HomeScreen(
        profiles = profiles,
        selectedId = selectedId,
        tunnelState = tunnelState,
        snackbarHostState = snackbarHostState,
        onImport = { viewModel.importFromClipboard(clipboard.getText()?.text) },
        onSelect = viewModel::select,
        onConnect = { viewModel.connect()?.let(consentLauncher::launch) },
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    profiles: List<Profile>,
    selectedId: Long?,
    tunnelState: TunnelState,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onSelect: (Profile) -> Unit,
    onConnect: () -> Unit,
    onDelete: (Profile) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profiles_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.import_from_clipboard)) },
                    icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                    onClick = onImport,
                )
            }
        },
        bottomBar = {
            if (profiles.isNotEmpty()) {
                ConnectBar(
                    tunnelState = tunnelState,
                    hasSelection = selectedId != null,
                    onConnect = onConnect,
                )
            }
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            EmptyState(onImport = onImport, modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ServerCard(
                        profile = profile,
                        selected = profile.id == selectedId,
                        onSelect = { onSelect(profile) },
                        onDelete = { pendingDelete = profile },
                    )
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        DeleteDialog(
            profile = profile,
            onConfirm = {
                onDelete(profile)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
/**
 * Persistent bottom control. One button that reflects the tunnel rather than the tap: it
 * connects when idle, offers Disconnect once up, and goes inert mid-transition so a second
 * tap can't race the service.
 */
@Composable
private fun ConnectBar(
    tunnelState: TunnelState,
    hasSelection: Boolean,
    onConnect: () -> Unit,
) {
    val connected = tunnelState is TunnelState.Connected
    val label = when (tunnelState) {
        is TunnelState.Connecting -> stringResource(R.string.tunnel_connecting)
        is TunnelState.Disconnecting -> stringResource(R.string.tunnel_disconnecting)
        is TunnelState.Connected -> stringResource(R.string.action_disconnect)
        else -> stringResource(R.string.action_connect)
    }

    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (connected) {
                Text(
                    text = (tunnelState as TunnelState.Connected).profileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onConnect,
                enabled = !tunnelState.isBusy && (hasSelection || connected),
                colors = if (connected) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (tunnelState.isBusy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Rounded.Bolt, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

/** Shown when there are no servers yet: what this screen is, and how to fill it. */
@Composable
private fun EmptyState(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profiles_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profiles_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_from_clipboard))
        }
    }
}

@Composable
private fun DeleteDialog(profile: Profile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_body, profile.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

