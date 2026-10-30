package pl.trailpocket.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.CustomZoomButtonsController
import pl.trailpocket.PlannerState
import pl.trailpocket.data.Point
import pl.trailpocket.data.SketchDraft
import pl.trailpocket.data.PlanningMode
import pl.trailpocket.data.Attraction

internal enum class MapAction { POLAND, FIT_ROUTE, ZOOM_IN, ZOOM_OUT }
internal data class MapCommand(val action: MapAction, val version: Int)
private val PolandBounds = BoundingBox(55.05, 24.35, 48.8, 13.85)

@Composable
internal fun TrailMap(state: PlannerState, modifier: Modifier = Modifier, onTap: (Point) -> Unit,
    command: MapCommand? = null, onSketch: (SketchDraft) -> Unit = {}, onStop: (Int) -> Unit = {},
    onMessage: (String) -> Unit = {}, onAttraction: (Attraction) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentTap by rememberUpdatedState(onTap)
    val currentState by rememberUpdatedState(state)
    val currentSketch by rememberUpdatedState(onSketch)
    val currentStop by rememberUpdatedState(onStop)
    val currentMessage by rememberUpdatedState(onMessage)
    val currentAttraction by rememberUpdatedState(onAttraction)
    val attractionMarkers = remember { mutableListOf<Marker>() }
    var fitTarget by remember { mutableStateOf<MapAction?>(MapAction.POLAND) }
    val map = remember {
        MapView(context).apply {
            setTileSource(XYTileSource("OpenStreetMap", 0, 19, 256, ".png",
                arrayOf("https://tile.openstreetmap.org/"), "© OpenStreetMap contributors"))
            setMultiTouchControls(true)
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(52.0, 19.0))
            setTilesScaledToDpi(true)
        }
    }
    val touch = remember(map) {
        SketchTouchHandler(map, enabled = { currentState.isDrawing && !currentState.busy },
            draft = { currentState.sketch }, commit = { currentSketch(it) }, addStop = { currentStop(it) },
            message = { currentMessage(it) }, onUserTouch = { fitTarget = null })
    }
    val events = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (currentState.planningMode != PlanningMode.POINTS) return false
                currentTap(Point(p.latitude, p.longitude))
                return true
            }
            override fun longPressHelper(p: GeoPoint) = false
        })
    }
    fun fit(action: MapAction) {
        if (map.width <= 0 || map.height <= 0) return
        val bounds = if (action == MapAction.FIT_ROUTE) {
            (currentState.route?.geometry?.coordinates?.map { GeoPoint(it[1], it[0]) }
                ?: currentState.sketch.path.takeIf { it.size >= 2 }?.map { GeoPoint(it.lat, it.lon) })
                ?.let { BoundingBox.fromGeoPoints(it).increaseByScale(1.15f) } ?: PolandBounds
        } else PolandBounds
        val marginDp = if (action == MapAction.POLAND) 16 else 64
        val padding = minOf(marginDp * context.resources.displayMetrics.density, minOf(map.width, map.height) * .12f).toInt()
        map.zoomToBoundingBox(bounds, false, padding, 17.0, null)
    }
    DisposableEffect(map, owner) {
        map.setOnTouchListener(touch)
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?) = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                attractionMarkers.forEach { it.isEnabled = map.zoomLevelDouble >= 12 }
                return false
            }
        }
        map.addMapListener(mapListener)
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                fitTarget?.let { fit(it) }
            }
        }
        map.addOnLayoutChangeListener(layoutListener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            touch.close()
            map.setOnTouchListener(null)
            map.removeMapListener(mapListener)
            map.removeOnLayoutChangeListener(layoutListener)
            owner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(factory = { map }, modifier = modifier)

    LaunchedEffect(state.start, state.end, state.route, state.sketch, state.isDrawing, state.planningMode) {
        map.overlays.clear()
        attractionMarkers.clear()
        map.overlays.add(events)
        if (state.planningMode == PlanningMode.SKETCH && (state.route == null || state.isDrawing) && state.sketch.path.size >= 2) {
            map.overlays.add(Polyline(map).apply {
                setPoints(state.sketch.path.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = android.graphics.Color.rgb(78, 107, 189)
                outlinePaint.strokeWidth = 4f * context.resources.displayMetrics.density
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                setOnClickListener { _, _, _ -> false }
            })
        }
        state.route?.let { route ->
            map.overlays.add(Polyline(map).apply {
                setPoints(route.geometry.coordinates.map { GeoPoint(it[1], it[0]) })
                outlinePaint.color = android.graphics.Color.rgb(36, 95, 67)
                outlinePaint.strokeWidth = 6f * context.resources.displayMetrics.density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            })
        }
        fun marker(point: Point, label: String, color: Int) = Marker(map).apply {
            position = GeoPoint(point.lat, point.lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = markerIcon(context.resources, label, color)
            title = point.name
            setOnMarkerClickListener { _, _ -> true }
        }
        // Keep landmarks legible and always render mandatory points above optional POIs.
        state.route?.discovery?.attractions?.sortedByDescending { it.selectedForDetour }?.take(8)?.forEach { poi ->
            val poiMarker = marker(Point(poi.lat, poi.lon, poi.name), "★", android.graphics.Color.rgb(117, 88, 35)).apply {
                icon = markerIcon(map.resources, "★", android.graphics.Color.rgb(117, 88, 35), 28)
                isEnabled = map.zoomLevelDouble >= 12
                setOnMarkerClickListener { _, _ -> currentAttraction(poi); true }
            }
            attractionMarkers.add(poiMarker)
            map.overlays.add(poiMarker)
        }
        state.start?.let { map.overlays.add(marker(it, "A", android.graphics.Color.rgb(36, 95, 67))) }
        state.end?.let { map.overlays.add(marker(it, "B", android.graphics.Color.rgb(199, 91, 44))) }
        val stops = state.route?.discovery?.stops ?: state.sketch.stopIndices.mapNotNull { state.sketch.path.getOrNull(it) }
        stops.forEachIndexed { index, point ->
            map.overlays.add(marker(point, "${index + 1}", android.graphics.Color.rgb(199, 91, 44)))
        }
        map.invalidate()
    }
    LaunchedEffect(state.isDrawing) {
        if (state.isDrawing) {
            fitTarget = null
            if (map.zoomLevelDouble < 12.5) map.controller.setZoom(13.5)
        }
    }
    LaunchedEffect(state.cameraVersion) {
        val route = state.route
        if (route != null) {
            fitTarget = MapAction.FIT_ROUTE
            map.doOnLayout { fit(MapAction.FIT_ROUTE) }
        } else state.focus?.let {
            fitTarget = null
            map.controller.setZoom(15.0)
            map.controller.animateTo(GeoPoint(it.lat, it.lon))
        } ?: run {
            fitTarget = MapAction.POLAND
            map.doOnLayout { fit(MapAction.POLAND) }
        }
    }
    LaunchedEffect(command) {
        when (command?.action) {
            MapAction.POLAND, MapAction.FIT_ROUTE -> {
                fitTarget = command.action
                map.doOnLayout { fit(command.action) }
            }
            MapAction.ZOOM_IN -> { fitTarget = null; map.controller.zoomIn() }
            MapAction.ZOOM_OUT -> { fitTarget = null; map.controller.zoomOut() }
            null -> Unit
        }
    }
}

internal fun markerIcon(resources: android.content.res.Resources, label: String, color: Int, sizeDp: Int = 38): BitmapDrawable {
    val size = (sizeDp * resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size * .49f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size * .40f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = size * .44f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText(label, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(resources, bitmap)
}
