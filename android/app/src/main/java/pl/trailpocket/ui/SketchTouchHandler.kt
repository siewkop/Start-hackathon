package pl.trailpocket.ui

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Point as PixelPoint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pl.trailpocket.data.Point
import pl.trailpocket.data.SketchDraft
import kotlin.math.hypot

/** Draw with one finger; hand two-finger gestures back to the map. A dwell creates a stop. */
internal class SketchTouchHandler(
    private val map: MapView,
    private val enabled: () -> Boolean,
    private val draft: () -> SketchDraft,
    private val commit: (SketchDraft) -> Unit,
    private val addStop: (Int) -> Unit,
    private val message: (String) -> Unit,
    private val onUserTouch: () -> Unit
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private val density = map.resources.displayMetrics.density
    private val points = mutableListOf<Point>()
    private val stops = mutableListOf<Int>()
    private val dots = mutableListOf<Marker>()
    private var line: Polyline? = null
    private var navigating = false
    private var rejected = false
    private var stopOnly: Int? = null
    private var markedExisting = false
    private var holdX = 0f
    private var holdY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hold: Runnable? = null
    private var limitReported = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onUserTouch()
        if (!enabled()) {
            close()
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            return false
        }
        if (event.pointerCount > 1 || navigating) {
            if (!navigating) finish(commitStroke = points.size >= 2, cancelEvent = null)
            navigating = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                close()
                rejected = false
                limitReported = false
                if (map.zoomLevelDouble < 11.5) {
                    message("Use + to zoom in and draw more precisely.")
                    rejected = true
                    return true
                }
                // Give the native detector its down event so a second finger can still start a pinch.
                map.onTouchEvent(event)
                val old = draft().path
                if (old.isNotEmpty()) {
                    val pixel = PixelPoint()
                    fun gap(p: Point): Float {
                        map.projection.toPixels(GeoPoint(p.lat, p.lon), pixel)
                        return hypot(pixel.x - event.x, pixel.y - event.y)
                    }
                    if (gap(old.last()) > 56 * density) {
                        val nearest = old.indices.minByOrNull { gap(old[it]) }!!
                        if (gap(old[nearest]) < 28 * density) stopOnly = nearest
                        else {
                            rejected = true
                            message("Continue from the end of your sketch. Hold on the existing line to add a stop.")
                            return true
                        }
                    }
                }
                lastX = event.x
                lastY = event.y
                if (stopOnly == null) {
                    points.add(geo(event.x, event.y))
                    line = Polyline(map).apply {
                        outlinePaint.color = Color.rgb(78, 107, 189)
                        outlinePaint.strokeWidth = 4 * density
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(9 * density, 6 * density), 0f)
                        setOnClickListener { _, _, _ -> false }
                    }.also { map.overlays.add(it) }
                }
                armHold(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (rejected) return true
                if (hypot(event.x - holdX, event.y - holdY) > 12 * density) {
                    if (stopOnly != null) {
                        rejected = true
                        cancelHold()
                        message("Hold on the line to add a stop. Start a new section at the end of your sketch.")
                        return true
                    }
                    armHold(event.x, event.y)
                }
                if (stopOnly == null && hypot(event.x - lastX, event.y - lastY) >= 4 * density) {
                    append(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!rejected && stopOnly == null && points.isNotEmpty() && hypot(event.x - lastX, event.y - lastY) >= density) {
                    append(event.x, event.y)
                }
                view.performClick()
                finish(commitStroke = !rejected, cancelEvent = event)
            }
            MotionEvent.ACTION_CANCEL -> finish(commitStroke = false, cancelEvent = event)
        }
        return true
    }

    private fun geo(x: Float, y: Float): Point = map.projection.fromPixels(x.toInt(), y.toInt()).let {
        Point(it.latitude, it.longitude, "Sketch")
    }

    private fun append(x: Float, y: Float) {
        if (draft().path.size + points.size >= 2000) {
            if (!limitReported) message("Your sketch has reached the detail limit. Finish drawing or undo the last section.")
            limitReported = true
            return
        }
        points.add(geo(x, y))
        lastX = x
        lastY = y
        line?.setPoints(points.map { GeoPoint(it.lat, it.lon) })
        map.invalidate()
    }

    private fun armHold(x: Float, y: Float) {
        cancelHold()
        holdX = x
        holdY = y
        hold = Runnable {
            if (!enabled() || rejected) return@Runnable
            val existing = stopOnly
            if (existing != null && (existing in draft().stopIndices || markedExisting)) return@Runnable
            if (draft().stopIndices.size + stops.size >= 8) {
                message("You can add up to 8 stops.")
                return@Runnable
            }
            val point = if (existing != null) {
                markedExisting = true
                draft().path[existing]
            } else {
                if (points.isEmpty()) return@Runnable
                val index = points.lastIndex
                if (index in stops) return@Runnable
                stops.add(index)
                points[index]
            }
            dots.add(Marker(map).apply {
                position = GeoPoint(point.lat, point.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = markerIcon(map.resources, "Ⅱ", Color.rgb(199, 91, 44))
                title = "Planned stop"
            }.also { map.overlays.add(it) })
            map.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            map.invalidate()
        }.also { handler.postDelayed(it, 700) }
    }

    private fun cancelHold() { hold?.let { handler.removeCallbacks(it) }; hold = null }

    private fun finish(commitStroke: Boolean, cancelEvent: MotionEvent?) {
        cancelHold()
        val stroke = SketchDraft(points.toList(), stops.toList())
        val oldStop = if (commitStroke && markedExisting) stopOnly else null
        close()
        if (cancelEvent != null) {
            val cancel = MotionEvent.obtain(cancelEvent).apply { action = MotionEvent.ACTION_CANCEL }
            map.onTouchEvent(cancel)
            cancel.recycle()
        }
        if (commitStroke) {
            if (oldStop != null) addStop(oldStop)
            else if (stroke.path.size >= 2 || stroke.stopIndices.isNotEmpty()) commit(stroke)
        }
    }

    fun close() {
        cancelHold()
        line?.let { map.overlays.remove(it) }
        dots.forEach { map.overlays.remove(it) }
        line = null
        dots.clear()
        points.clear()
        stops.clear()
        stopOnly = null
        markedExisting = false
    }
}
