package pl.trailpocket

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Xml
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.trailpocket.data.Route
import java.io.File

suspend fun shareGpx(context: Context, route: Route) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val target = File(dir, "trail-${route.id}.gpx")
        target.outputStream().use { output ->
            val xml = Xml.newSerializer().apply { setOutput(output, "UTF-8") }
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "gpx")
            xml.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")
            xml.attribute(null, "version", "1.1")
            xml.attribute(null, "creator", "Trail Pocket")
            route.discovery?.stops?.forEachIndexed { index, point ->
                xml.startTag(null, "wpt").attribute(null, "lat", point.lat.toString()).attribute(null, "lon", point.lon.toString())
                xml.startTag(null, "name").text("Stop ${index + 1}").endTag(null, "name")
                xml.endTag(null, "wpt")
            }
            xml.startTag(null, "trk")
            xml.startTag(null, "name").text(route.name ?: "My route").endTag(null, "name")
            xml.startTag(null, "trkseg")
            route.geometry.coordinates.forEach { (lon, lat) ->
                xml.startTag(null, "trkpt").attribute(null, "lat", lat.toString())
                    .attribute(null, "lon", lon.toString()).endTag(null, "trkpt")
            }
            xml.endTag(null, "trkseg").endTag(null, "trk").endTag(null, "gpx")
            xml.endDocument()
        }
        target
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("GPX route", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share GPX route"))
}
