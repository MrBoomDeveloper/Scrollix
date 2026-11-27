package com.mrboomdev.scrollix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mrboomdev.scrollix.data.api.ApiManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Raised by the location picker through `requestLocationPermission`; the
    // answer is handed back to the suspend call that asked for it.
    private var pendingLocationPermission: CompletableDeferred<Boolean>? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        pendingLocationPermission?.complete(results.values.any { granted -> granted })
        pendingLocationPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until the cached chat list is on screen; the network
        // sync continues afterwards, so a cold start never flashes an empty
        // list and does not wait for a full dialog download either.
        splash.setKeepOnScreenCondition { !ApiManager.initialDialogsReady.value }

        ApiManager.init()
        enableEdgeToEdge()
        requestLocationPermission = { askForLocationPermission() }
        requestNotificationPermission()
        handleIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        setAppNotificationsForeground(true)
    }

    override fun onStop() {
        setAppNotificationsForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        requestLocationPermission = null
        super.onDestroy()
    }

    /**
     * Shows the system prompt for the location permission. Called from the
     * picker through the shared entry point, so background code never asks.
     */
    private suspend fun askForLocationPermission(): Boolean {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) return true

        val answer = CompletableDeferred<Boolean>()
        pendingLocationPermission = answer

        withContext(Dispatchers.Main) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        return answer.await()
    }

    private fun handleIntent(intent: Intent?) {
        DeepLinkHandler.publish(intent?.data?.toString())
        ChatOpenHandler.publish(
            accountId = intent?.getStringExtra(NotificationExtras.AccountId),
            providerDialogId = intent?.getStringExtra(NotificationExtras.ProviderDialogId)
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
