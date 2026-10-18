package de.melvin.tvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    // Wird bei jeder Rückkehr zum Startbildschirm erhöht, damit neu installierte Apps auftauchen
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Zurück-Taste auf dem Startbildschirm ignorieren
            BackHandler(enabled = true) {}
            LauncherScreen(
                refreshKey = refreshKey,
                onLaunch = ::launch,
                onOpenSettings = ::openSettings,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    private fun launch(app: LauncherApp) {
        if (app.installed) {
            val intent = AppRepository.launchIntent(packageManager, app.packageName)
            if (intent != null) {
                try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: ActivityNotFoundException) {
                    // fällt unten auf die Meldung zurück
                }
            }
            Toast.makeText(this, "${app.name} lässt sich nicht starten", Toast.LENGTH_SHORT).show()
        } else {
            openStore(app)
        }
    }

    private fun openStore(app: LauncherApp) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "${app.name} ist nicht installiert", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Einstellungen nicht gefunden", Toast.LENGTH_SHORT).show()
        }
    }
}
