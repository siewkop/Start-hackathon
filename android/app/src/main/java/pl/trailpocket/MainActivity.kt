package pl.trailpocket

import android.os.Bundle
import android.content.Context
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.osmdroid.config.Configuration
import pl.trailpocket.ui.TrailApp
import pl.trailpocket.ui.TrailTheme
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // This edition uses English, including Material's built-in accessibility labels.
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocales(LocaleList(Locale.ENGLISH))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = "TrailPocket/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "tiles")
            expirationExtendedDuration = 7L * 24 * 60 * 60 * 1000
            tileDownloadThreads = 2
        }
        enableEdgeToEdge()
        setContent { TrailTheme { TrailApp() } }
    }
}
