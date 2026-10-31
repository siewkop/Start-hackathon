package pl.trailpocket.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.trailpocket.PlannerState
import pl.trailpocket.PlannerViewModel
import pl.trailpocket.data.Point
import pl.trailpocket.data.Profile
import pl.trailpocket.data.Route
import pl.trailpocket.data.PlanningMode
import pl.trailpocket.data.Interest
import pl.trailpocket.data.Attraction
import kotlin.math.roundToInt

@Composable
internal fun PlannerScreen(
    state: PlannerState,
    vm: PlannerViewModel,
    locating: Boolean,
    onLocate: () -> Unit,
    onSearch: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: (Route) -> Unit,
    mapOnly: Boolean,
    onMapOnlyChange: (Boolean) -> Unit
) {
    var softMap by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf<MapCommand?>(null) }
    var selectedAttraction by remember { mutableStateOf<Attraction?>(null) }
    val density = LocalDensity.current
    fun send(action: MapAction) { command = MapCommand(action, (command?.version ?: 0) + 1) }

    LaunchedEffect(state.route?.id) {
        if (state.route != null) expanded = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp
        val short = maxHeight < 400.dp
        val panelHeight = with(density) { panelHeightPx.toDp() }
        val mapPadding = when {
            mapOnly -> PaddingValues(0.dp)
            wide -> PaddingValues(start = 344.dp)
            else -> PaddingValues(bottom = panelHeight)
        }
        // Keep the same map instance while panels and full-screen mode change.
        Box(Modifier.fillMaxSize().padding(mapPadding).clipToBounds()) {
            TrailMap(state, Modifier.fillMaxSize(), vm::choose, command,
                onSketch = vm::appendSketch, onStop = vm::addSketchStop, onMessage = vm::error,
                onAttraction = { selectedAttraction = it }, softMap = softMap)
            val safe = if (mapOnly) Modifier.safeDrawingPadding() else Modifier
            Box(safe.fillMaxSize()) {
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    if (mapOnly) {
                        Surface(onClick = { onMapOnlyChange(false) }, shape = RoundedCornerShape(16.dp),
                            color = Color.White, shadowElevation = 3.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.FullscreenExit, null, Modifier.size(22.dp))
                                Text("Planner", Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .96f),
                            shadowElevation = 3.dp, modifier = Modifier.weight(1f)) {
                            Text(when {
                                state.busy -> if (state.planningMode == PlanningMode.SKETCH) "Matching your sketch and finding places…" else "Finding your route…"
                                state.route != null -> "${state.route.profile.label} · route ready"
                                state.isDrawing -> "Draw a line · hold to add a stop"
                                state.planningMode == PlanningMode.SKETCH -> "Move the map or continue drawing"
                                state.selectingStart -> "Tap the map to choose a start"
                                else -> "Tap the map to choose a finish"
                            }, Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        MapIconButton(Icons.Outlined.Fullscreen, "Full-screen map") { onMapOnlyChange(true) }
                    }
                }

                SketchToolbar(state, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 72.dp),
                    onDraw = vm::toggleDrawing, onUndo = vm::undoSketch, onPlan = vm::plan, showPlan = mapOnly)

                val toolModifier = if (short) Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 38.dp)
                    else Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                MapTools(toolModifier, softMap = softMap, onStyle = { softMap = !softMap }, horizontal = short, locating = locating, hasRoute = state.route != null || state.sketch.path.size >= 2,
                    onCountry = { vm.pauseDrawing(); send(MapAction.POLAND) }, onZoomIn = { send(MapAction.ZOOM_IN) },
                    onZoomOut = { send(MapAction.ZOOM_OUT) }, onFit = { send(MapAction.FIT_ROUTE) }, onLocate = onLocate)
                MapAttribution(Modifier.align(Alignment.BottomStart).fillMaxWidth())
            }
        }

        if (!mapOnly) {
            val panelModifier = if (wide) Modifier.align(Alignment.TopStart).width(344.dp).fillMaxHeight().padding(12.dp)
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { panelHeightPx = it.height }
                    .heightIn(max = maxHeight * .62f).animateContentSize()
            PlannerPanel(state, expanded = wide || expanded, wide = wide, modifier = panelModifier,
                onToggle = { expanded = !expanded }, onSelect = vm::selectStart, onSearch = onSearch,
                onSwap = vm::swap, onClear = vm::clear, onProfile = vm::setProfile, onPlan = vm::plan,
                onSave = onSave, onShare = onShare, onPointMode = vm::usePointMode, onSketchMode = vm::toggleDrawing,
                onInterest = vm::setInterest, onExtraRatio = vm::setExtraRatio,
                onNew = vm::newRoute, onRecreation = vm::setRecreation,
                onAttraction = { selectedAttraction = it })
        }
    }
    selectedAttraction?.let { AttractionSheet(it, onClose = { selectedAttraction = null }) }
}

@Composable
private fun PlannerPanel(
    state: PlannerState, expanded: Boolean, wide: Boolean, modifier: Modifier,
    onToggle: () -> Unit, onSelect: (Boolean) -> Unit, onSearch: (Boolean) -> Unit,
    onSwap: () -> Unit, onClear: () -> Unit, onProfile: (Profile) -> Unit, onPlan: () -> Unit,
    onSave: () -> Unit, onShare: (Route) -> Unit, onPointMode: () -> Unit, onSketchMode: () -> Unit,
    onNew: () -> Unit, onRecreation: (Double) -> Unit,
    onInterest: (Interest) -> Unit, onExtraRatio: (Double) -> Unit, onAttraction: (Attraction) -> Unit
) {
    Surface(modifier, color = Color.White, shadowElevation = 8.dp,
        shape = if (wide) RoundedCornerShape(22.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !wide, onClick = onToggle).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.route?.name ?: when {
                        state.route != null -> "Your route is ready"
                        state.planningMode == PlanningMode.SKETCH -> "Draw your next adventure"
                        state.start == null -> "Where to today?"
                        state.end == null -> "Now choose a finish"
                        else -> "Ready to go"
                    }, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.route?.let { "${it.distanceLabel()} · ${it.durationLabel()} · ${it.profile.label}" }
                        ?: if (state.planningMode == PlanningMode.SKETCH) "${state.profile.label} · ${state.sketch.stopIndices.size} ${if (state.sketch.stopIndices.size == 1) "stop" else "stops"}"
                        else "${state.profile.label} · ${listOfNotNull(state.start, state.end).size}/2 points",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!wide) IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        if (expanded) "Collapse the planner" else "Expand the planner")
                }
            }
            if (state.route != null) TextButton(onClick = onNew, enabled = !state.busy) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("New route", Modifier.padding(start = 6.dp))
            }
            if (expanded) {
                Column(Modifier.weight(1f, fill = wide).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.planningMode == PlanningMode.POINTS, onClick = onPointMode,
                            enabled = !state.busy, label = { Text("Two points") })
                        FilterChip(selected = state.planningMode == PlanningMode.SKETCH,
                            onClick = { if (state.planningMode != PlanningMode.SKETCH) onSketchMode() },
                            enabled = !state.busy, label = { Text("Sketch") })
                    }
                    if (state.planningMode == PlanningMode.POINTS) {
                    EndpointRow("A", "Start", state.start, state.selectingStart, !state.busy,
                        onClick = { onSelect(true) }, onSearch = { onSearch(true) })
                    EndpointRow("B", "Finish", state.end, !state.selectingStart, !state.busy,
                        onClick = { onSelect(false) }, onSearch = { onSearch(false) })
                    } else {
                        Surface(color = Moss, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Draw the general route. Hold your finger to add a stop. Use two fingers to move and zoom the map.",
                                Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Interest.entries.forEach { interest ->
                                FilterChip(selected = state.interest == interest, onClick = { onInterest(interest) },
                                    enabled = !state.busy, label = { Text(interest.label, fontSize = 12.sp) })
                            }
                        }
                        Text("For interesting places: up to ${(state.maxExtraRatio * 100).roundToInt()}% extra distance", fontSize = 12.sp)
                        Slider(value = state.maxExtraRatio.toFloat(), onValueChange = { onExtraRatio(it.toDouble()) },
                            valueRange = 0f..0.4f, steps = 7, enabled = !state.busy)
                    }
                    Text(if (state.recreation == 0.0) "Style: direct" else if (state.recreation < .5) "Style: quiet paths" else if (state.profile == Profile.HIKE) "Style: leisurely walk" else "Style: leisurely ride", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(value = state.recreation.toFloat(), onValueChange = { onRecreation(it.toDouble()) },
                        valueRange = 0f..1f, steps = 2, enabled = !state.busy)
                    Text("Direct → quiet paths → leisurely", fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Profile.entries.forEach { profile ->
                            FilterChip(selected = state.profile == profile, enabled = !state.busy,
                                onClick = { onProfile(profile) }, label = { Text(profile.label) },
                                leadingIcon = { Icon(profileIcon(profile), null, Modifier.size(18.dp)) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSwap, enabled = !state.busy && (state.start != null || state.end != null)) {
                            Icon(Icons.Outlined.SwapVert, null, Modifier.size(18.dp))
                            Text("Swap", Modifier.padding(start = 4.dp))
                        }
                        TextButton(onClick = onClear, enabled = !state.busy && (state.start != null || state.end != null)) { Text("Clear") }
                    }
                    state.route?.routingWarning?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                    state.route?.discovery?.let { discovery ->
                        Text(if (discovery.aiUsed) "Why this route · GPT-assisted" else "Why this route", fontWeight = FontWeight.Bold)
                        Text(discovery.explanation, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                        Text("Estimated time excludes stops.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        discovery.warnings.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                        if (discovery.attractions.isNotEmpty()) {
                            Text("Selected for you · ${discovery.attractions.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            discovery.attractions.forEach { poi ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAttraction(poi) }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(poi.categoryColor())), contentAlignment = Alignment.Center) {
                                        Text(poi.categorySymbol(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(poi.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("${poi.categoryLabel()} · about ${poi.distanceToRouteM.roundToInt()} m from the route", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val route = state.route
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (route == null) {
                    val canPlan = if (state.planningMode == PlanningMode.SKETCH) state.sketch.path.size >= 2 else state.start != null && state.end != null
                    Button(onClick = onPlan, enabled = canPlan && !state.busy,
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Outlined.Route, null, Modifier.size(20.dp)); Text(if (state.planningMode == PlanningMode.SKETCH) "Find a scenic route" else "Find a route", Modifier.padding(start = 8.dp), fontSize = 13.sp) }
                    }
                    if (!expanded) OutlinedIconButton(onClick = { onSearch(state.selectingStart) },
                        enabled = !state.busy, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Search, "Search for a start or finish")
                    }
                } else {
                    Button(onClick = onSave, enabled = !state.busy && !state.isSaved,
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(if (state.isSaved) Icons.Outlined.Check else Icons.Outlined.BookmarkAdd, null, Modifier.size(20.dp))
                        Text(if (state.isSaved) "Saved" else "Save route", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { onShare(route) }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Share, "Export GPX", Modifier.size(18.dp)); Text("GPX", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(letter: String, label: String, point: Point?, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (selected) Paper else Color.Transparent).clickable(enabled = enabled, onClick = onClick)
        .padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (letter == "A") Forest else Color(0xFFC75B2C)), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(point?.let { if (it.name == "Map point") it.coordinates() else it.name } ?: "Choose on the map",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSearch, enabled = enabled) { Icon(Icons.Outlined.Search, "Search: $label", Modifier.size(20.dp)) }
    }
}

@Composable
private fun MapTools(modifier: Modifier, softMap: Boolean, onStyle: () -> Unit, horizontal: Boolean, locating: Boolean, hasRoute: Boolean,
    onCountry: () -> Unit, onZoomIn: () -> Unit, onZoomOut: () -> Unit, onFit: () -> Unit, onLocate: () -> Unit) {
    val content: @Composable () -> Unit = {
        Surface(onClick = onCountry, color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp) {
            Text("Poland", Modifier.padding(horizontal = 12.dp, vertical = 14.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
            if (horizontal) Row {
                IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, "Zoom in") }
                IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, "Zoom out") }
            } else Column {
                IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, "Zoom in") }
                HorizontalDivider(Modifier.width(48.dp), color = Moss)
                IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, "Zoom out") }
            }
        }
        Surface(onClick = onStyle, color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp) {
            Text(if (softMap) "Light" else "OSM", Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (hasRoute) MapIconButton(Icons.Outlined.CenterFocusStrong, "Show the whole route", onFit)
        Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
            IconButton(onClick = onLocate, enabled = !locating) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.MyLocation, "Start from my location", tint = Forest)
            }
        }
    }
    if (horizontal) Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    else Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) { content() }
}

@Composable
private fun MapIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = Forest) }
    }
}

@Composable
private fun MapAttribution(modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Surface(modifier, color = Color.White.copy(alpha = .92f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("© OpenStreetMap contributors", fontSize = 10.sp,
                modifier = Modifier.clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") })
            Text("Improve the map ↗", fontSize = 10.sp, color = Forest,
                modifier = Modifier.clickable { uriHandler.openUri("https://www.openstreetmap.org/fixthemap") })
        }
    }
}
