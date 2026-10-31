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
                onAttraction = { selectedAttraction = it })
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
                                Text("Planer", Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .96f),
                            shadowElevation = 3.dp, modifier = Modifier.weight(1f)) {
                            Text(when {
                                state.busy -> if (state.planningMode == PlanningMode.SKETCH) "Dopasowuję szkic i szukam atrakcji…" else "Wyznaczam trasę…"
                                state.route != null -> "${state.route.profile.label} · trasa gotowa"
                                state.isDrawing -> "Rysuj linię · przytrzymaj na postój"
                                state.planningMode == PlanningMode.SKETCH -> "Przesuń mapę lub wróć do rysowania"
                                state.selectingStart -> "Dotknij mapy, aby wybrać start"
                                else -> "Dotknij mapy, aby wybrać cel"
                            }, Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        MapIconButton(Icons.Outlined.Fullscreen, "Mapa na pełnym ekranie") { onMapOnlyChange(true) }
                    }
                }

                SketchToolbar(state, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 72.dp),
                    onDraw = vm::toggleDrawing, onUndo = vm::undoSketch, onPlan = vm::plan, showPlan = mapOnly)

                val toolModifier = if (short) Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 38.dp)
                    else Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                MapTools(toolModifier, horizontal = short, locating = locating, hasRoute = state.route != null || state.sketch.path.size >= 2,
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
                        state.route != null -> "Twoja trasa jest gotowa"
                        state.planningMode == PlanningMode.SKETCH -> "Narysuj swoją przygodę"
                        state.start == null -> "Dokąd dziś?"
                        state.end == null -> "Teraz wybierz cel"
                        else -> "Możesz ruszać w drogę"
                    }, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.route?.let { "${it.distanceLabel()} · ${it.durationLabel()} · ${it.profile.label}" }
                        ?: if (state.planningMode == PlanningMode.SKETCH) "${state.profile.label} · ${state.sketch.stopIndices.size} postojów"
                        else "${state.profile.label} · ${listOfNotNull(state.start, state.end).size}/2 punktów",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!wide) IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        if (expanded) "Zwiń panel planowania" else "Rozwiń panel planowania")
                }
            }
            if (state.route != null) TextButton(onClick = onNew, enabled = !state.busy) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("Nowa trasa", Modifier.padding(start = 6.dp))
            }
            if (expanded) {
                Column(Modifier.weight(1f, fill = wide).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.planningMode == PlanningMode.POINTS, onClick = onPointMode,
                            enabled = !state.busy, label = { Text("Dwa punkty") })
                        FilterChip(selected = state.planningMode == PlanningMode.SKETCH,
                            onClick = { if (state.planningMode != PlanningMode.SKETCH) onSketchMode() },
                            enabled = !state.busy, label = { Text("Rysunek") })
                    }
                    if (state.planningMode == PlanningMode.POINTS) {
                    EndpointRow("A", "Start", state.start, state.selectingStart, !state.busy,
                        onClick = { onSelect(true) }, onSearch = { onSearch(true) })
                    EndpointRow("B", "Cel", state.end, !state.selectingStart, !state.busy,
                        onClick = { onSelect(false) }, onSearch = { onSearch(false) })
                    } else {
                        Surface(color = Moss, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Narysuj ogólny przebieg trasy. Przytrzymaj palec, aby zostawić kropkę postoju. Dwoma palcami możesz przesuwać i powiększać mapę.",
                                Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Interest.entries.forEach { interest ->
                                FilterChip(selected = state.interest == interest, onClick = { onInterest(interest) },
                                    enabled = !state.busy, label = { Text(interest.label, fontSize = 12.sp) })
                            }
                        }
                        Text("Na ciekawsze miejsca: do ${(state.maxExtraRatio * 100).roundToInt()}% dodatkowej drogi", fontSize = 12.sp)
                        Slider(value = state.maxExtraRatio.toFloat(), onValueChange = { onExtraRatio(it.toDouble()) },
                            valueRange = 0f..0.4f, steps = 7, enabled = !state.busy)
                    }
                    Text(if (state.recreation == 0.0) "Styl: bezpośrednio" else if (state.recreation < .5) "Styl: spokojne ścieżki" else if (state.profile == Profile.HIKE) "Styl: spacer rekreacyjny" else "Styl: rower rekreacyjny", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(value = state.recreation.toFloat(), onValueChange = { onRecreation(it.toDouble()) },
                        valueRange = 0f..1f, steps = 2, enabled = !state.busy)
                    Text("Bezpośrednio → spokojne ścieżki → rekreacyjnie", fontSize = 11.sp)
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
                            Text("Zamień", Modifier.padding(start = 4.dp))
                        }
                        TextButton(onClick = onClear, enabled = !state.busy && (state.start != null || state.end != null)) { Text("Wyczyść") }
                    }
                    state.route?.routingWarning?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                    state.route?.discovery?.let { discovery ->
                        Text(if (discovery.aiUsed) "Dlaczego ta trasa · wsparta GPT" else "Dlaczego ta trasa", fontWeight = FontWeight.Bold)
                        Text(discovery.explanation, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                        Text("Czas przejścia nie obejmuje postojów.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        discovery.warnings.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                        if (discovery.attractions.isNotEmpty()) {
                            Text("Wybrane dla Ciebie · ${discovery.attractions.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            discovery.attractions.forEach { poi ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAttraction(poi) }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(poi.categoryColor())), contentAlignment = Alignment.Center) {
                                        Text(poi.categorySymbol(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(poi.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("${poi.categoryLabel()} · ok. ${poi.distanceToRouteM.roundToInt()} m od trasy", fontSize = 11.sp,
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
                        else { Icon(Icons.Outlined.Route, null, Modifier.size(20.dp)); Text(if (state.planningMode == PlanningMode.SKETCH) "Znajdź ciekawą trasę" else "Wyznacz trasę", Modifier.padding(start = 8.dp), fontSize = 13.sp) }
                    }
                    if (!expanded) OutlinedIconButton(onClick = { onSearch(state.selectingStart) },
                        enabled = !state.busy, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Search, "Wyszukaj start lub cel")
                    }
                } else {
                    Button(onClick = onSave, enabled = !state.busy && !state.isSaved,
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(if (state.isSaved) Icons.Outlined.Check else Icons.Outlined.BookmarkAdd, null, Modifier.size(20.dp))
                        Text(if (state.isSaved) "Zapisana" else "Zapisz trasę", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { onShare(route) }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Share, "Eksport GPX", Modifier.size(18.dp)); Text("GPX", Modifier.padding(start = 6.dp))
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
            Text(point?.let { if (it.name == "Punkt na mapie") it.coordinates() else it.name } ?: "Wybierz na mapie",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSearch, enabled = enabled) { Icon(Icons.Outlined.Search, "Wyszukaj: $label", Modifier.size(20.dp)) }
    }
}

@Composable
private fun MapTools(modifier: Modifier, horizontal: Boolean, locating: Boolean, hasRoute: Boolean,
    onCountry: () -> Unit, onZoomIn: () -> Unit, onZoomOut: () -> Unit, onFit: () -> Unit, onLocate: () -> Unit) {
    val content: @Composable () -> Unit = {
        Surface(onClick = onCountry, color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp) {
            Text("Polska", Modifier.padding(horizontal = 12.dp, vertical = 14.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
            if (horizontal) Row {
                IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, "Przybliż mapę") }
                IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, "Oddal mapę") }
            } else Column {
                IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, "Przybliż mapę") }
                HorizontalDivider(Modifier.width(48.dp), color = Moss)
                IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, "Oddal mapę") }
            }
        }
        if (hasRoute) MapIconButton(Icons.Outlined.CenterFocusStrong, "Pokaż całą trasę", onFit)
        Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
            IconButton(onClick = onLocate, enabled = !locating) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.MyLocation, "Użyj mojej lokalizacji jako startu", tint = Forest)
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
            Text("Popraw mapę ↗", fontSize = 10.sp, color = Forest,
                modifier = Modifier.clickable { uriHandler.openUri("https://www.openstreetmap.org/fixthemap") })
        }
    }
}
