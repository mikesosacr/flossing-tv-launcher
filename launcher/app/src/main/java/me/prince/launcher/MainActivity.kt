package me.prince.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Window
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

// The first Home after boot goes straight to the Apple TV input
private const val BOOT_WINDOW_MS = 180_000L
private const val XIAOMI_PICTURE_SETTINGS = "com.mitv.settings.PICTURE_SETTINGS"

class MainActivity : ComponentActivity() {
    private var reloadKey by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)
    private var resumes by mutableIntStateOf(0)
    private var launcherLanguage by mutableStateOf("es")
    // False while another app or an HDMI input is in front: the home screen pauses its timers and lets go of images
    private var onScreen by mutableStateOf(false)
    private var firstResume = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadKey++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back on the home screen should do nothing, not exit the launcher
        onBackPressedDispatcher.addCallback(this) {}
        startHotwordService(this)
        launcherLanguage = loadLauncherOptions(this).language

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })

        setContent {
            val systemConfiguration = LocalConfiguration.current
            val localizedConfiguration = remember(launcherLanguage, systemConfiguration) {
                Configuration(systemConfiguration).apply {
                    setLocale(Locale.forLanguageTag(launcherLanguage))
                }
            }
            val localizedContext = remember(launcherLanguage, systemConfiguration) {
                ContextThemeWrapper(this, theme).apply {
                    applyOverrideConfiguration(localizedConfiguration)
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
            ) {
                HomeScreen(
                    reloadKey = reloadKey,
                    homePresses = homePresses,
                    resumes = resumes,
                    visible = onScreen,
                    language = launcherLanguage,
                    onLanguageChange = { launcherLanguage = it },
                    window = window,
                    onUsageSettings = { launch(this, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onLaunch = { if (it.isInput) launchFresh(this, it.launchIntent) else launch(this, it.launchIntent) },
                    onAppInfo = { app ->
                        launch(this, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    },
                    onWifiSettings = { launch(this, Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    onPictureSettings = { launch(this, pictureSettings()) },
                    onSoundSettings = { launch(this, Intent(Settings.ACTION_SOUND_SETTINGS)) },
                    onAndroidSettings = { launch(this, Intent(Settings.ACTION_SETTINGS)) },
                    onUninstall = {
                        launch(this, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                    },
                    // Stremio title links only work on a cold start; plain app launches don't need that
                    onOpenTrending = { if (it.data != null) launchFresh(this, it) else launch(this, it) },
                )
            }
        }
    }

    // Home pressed while already on the home screen: jump back to the first tile
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    override fun onStart() {
        super.onStart()
        onScreen = true
    }

    override fun onResume() {
        super.onResume()
        resumes++
        if (firstResume && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) switchToHdmiDevice(this)
        firstResume = false
    }

    override fun onStop() {
        onScreen = false
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun pictureSettings(): Intent {
        val xiaomi = Intent(XIAOMI_PICTURE_SETTINGS)
        return if (packageManager.resolveActivity(xiaomi, 0) != null) xiaomi else Intent(Settings.ACTION_DISPLAY_SETTINGS)
    }
}
