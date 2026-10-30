package pl.trailpocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.osmdroid.config.Configuration
import pl.trailpocket.ui.TrailApp
import pl.trailpocket.ui.TrailTheme
import java.io.File

class MainActivity : ComponentActivity() {
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
