package pl.trailpocket.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import pl.trailpocket.PlannerState
import pl.trailpocket.PlannerViewModel
import pl.trailpocket.currentLocation
import pl.trailpocket.data.Point
import pl.trailpocket.data.Profile
import pl.trailpocket.data.Route
import pl.trailpocket.shareGpx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailApp(vm: PlannerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val wideNavigation = LocalConfiguration.current.screenWidthDp >= 640
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Route?>(null) }
    var locating by remember { mutableStateOf(false) }
    var mapOnly by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = mapOnly) { mapOnly = false }

    LaunchedEffect(state.error, state.message) {
        val notice = state.error ?: state.message
        if (notice != null) {
            snackbar.showSnackbar(notice, withDismissAction = true)
            vm.clearNotice()
        }
    }
    fun locate() {
        if (locating || state.busy) return
        scope.launch {
            locating = true
            try { vm.useLocation(currentLocation(context)) }
            catch (_: TimeoutCancellationException) { vm.error("Ustalenie lokalizacji trwa za długo. Spróbuj na zewnątrz.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.error(e.message ?: "Nie udało się ustalić lokalizacji.") }
            finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate()
        else vm.error("Brak zgody na lokalizację. Start możesz wskazać na mapie.")
    }
    val onLocate: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val onShare: (Route) -> Unit = { route ->
        scope.launch {
            try { shareGpx(context, route) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { vm.error("Nie udało się udostępnić pliku GPX.") }
        }
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = if (mapOnly) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!mapOnly) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Forest), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Landscape, null, tint = Color(0xFFDFF295), modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("trail pocket", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                }
                IconButton(onClick = { showSettings = true }, enabled = !state.busy) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Ustawienia serwera")
                }
            }
        },
        bottomBar = {
            if (!mapOnly && !wideNavigation) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Map, null) }, label = { Text("Zaplanuj") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1; vm.refresh() },
                    icon = { Icon(Icons.Outlined.Bookmarks, null) }, label = { Text("Moje trasy") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wideNavigation && !mapOnly) NavigationRail(containerColor = Color.White) {
                NavigationRailItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Map, null) }, label = { Text("Zaplanuj") })
                NavigationRailItem(selected = tab == 1, onClick = { tab = 1; vm.refresh() },
                    icon = { Icon(Icons.Outlined.Bookmarks, null) }, label = { Text("Moje trasy") })
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
            if (tab == 0) PlannerScreen(state, vm, locating, onLocate,
                onSearch = { isStart -> vm.selectStart(isStart); showSearch = true },
                onSave = { showSave = true }, onShare = onShare,
                mapOnly = mapOnly, onMapOnlyChange = { mapOnly = it })
            else SavedScreen(state, onOpen = { vm.open(it); tab = 0 }, onDelete = { deleting = it },
                onRefresh = vm::refresh, onPlan = { tab = 0 })
            }
        }
    }

    if (showSearch) {
        SearchSheet(state, onClose = { showSearch = false; vm.dismissSearch() }, onSearch = vm::search,
            onSelect = { vm.chooseSearchResult(it); showSearch = false; vm.dismissSearch() })
    }
    if (showSave) {
        var name by rememberSaveable { mutableStateOf("Moja wycieczka") }
        AlertDialog(onDismissRequest = { showSave = false },
            title = { Text("Zapisz na później") },
            text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Nazwa trasy") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.save(name); showSave = false }, enabled = name.isNotBlank() && !state.busy) { Text("Zapisz trasę") } },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Anuluj") } })
    }
    deleting?.let { route ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Usunąć trasę?") },
            text = { Text(route.name ?: "Moja trasa") },
            confirmButton = { TextButton(onClick = { vm.delete(route); deleting = null }, enabled = !state.busy) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Zostaw") } })
    }
    if (showSettings) SettingsSheet(state, onClose = { showSettings = false }, onSave = vm::updateUrl,
        onCheck = vm::checkConnection)
}

internal fun profileIcon(profile: Profile): ImageVector = if (profile == Profile.HIKE) Icons.Outlined.Hiking else Icons.AutoMirrored.Outlined.DirectionsBike

@Composable
private fun SavedScreen(state: PlannerState, onOpen: (Route) -> Unit, onDelete: (Route) -> Unit,
    onRefresh: () -> Unit, onPlan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Drogi, do których wrócisz", fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                Text("${state.saved.size} zapisanych tras", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Odśwież trasy") }
        }
        if (state.cached) {
            Surface(color = Moss, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Brak połączenia z serwerem. Pokazujemy ostatnio pobrane trasy. Mapy wymagają internetu.",
                    Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }
        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        if (state.saved.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Moss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Explore, null, Modifier.size(42.dp), tint = Forest)
                }
                Text("Tu zaczyna się kolekcja przygód", Modifier.padding(top = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Zaplanuj pierwszą trasę i zapisz ją na później.", Modifier.padding(top = 8.dp, bottom = 20.dp),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onPlan) { Text("Zaplanuj wycieczkę") }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(state.saved, key = { it.id }) { route ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
                    onClick = { if (!state.busy) onOpen(route) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Moss), contentAlignment = Alignment.Center) {
                            Icon(profileIcon(route.profile), null, tint = Forest)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(route.name ?: "Moja trasa", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${route.distanceLabel()} · ${route.durationLabel()}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(route.profile.label, fontSize = 10.sp, color = Forest)
                        }
                        IconButton(onClick = { onDelete(route) }, enabled = !state.busy) {
                            Icon(Icons.Outlined.DeleteOutline, "Usuń ${route.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(state: PlannerState, onClose: () -> Unit, onSearch: (String) -> Unit, onSelect: (Point) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text(if (state.selectingStart) "Skąd ruszasz?" else "Dokąd idziesz?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Znajdź miejscowość, adres lub miejsce.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            OutlinedTextField(query, onValueChange = { if (it.length <= 160) query = it }, singleLine = true,
                label = { Text("np. Łazienki Królewskie") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch(query) }))
            Button(onClick = { keyboard?.hide(); onSearch(query) }, enabled = query.trim().length >= 2 && !state.searching,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Szukaj miejsca") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)) }
            state.places.forEach { point ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(point) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = Forest)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(point.name, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(point.coordinates(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
                HorizontalDivider(color = Moss)
            }
            Text("Wyniki: Photon · dane © OpenStreetMap contributors", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: PlannerState, onClose: () -> Unit, onSave: (String) -> Unit, onCheck: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(state.baseUrl) }
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text("Połącz swoją bazę", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Adres backendu", fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            OutlinedTextField(url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://192.168.1.10:8000") }, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onSave(url) }))
            Text("Emulator: http://10.0.2.2:8000\nTelefon przez USB: http://127.0.0.1:8000 po adb reverse\nWi-Fi: adres IP komputera, port 8000.",
                fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)) }
            state.message?.let { Text(it, color = Forest, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(onClick = { keyboard?.hide(); onSave(url) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("Zapisz adres") }
            OutlinedButton(onClick = onCheck, enabled = !state.busy && url.trim().trimEnd('/') == state.baseUrl,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Sprawdź zapisane połączenie")
            }
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Moss)
            Text("Trail Pocket ${pl.trailpocket.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text("Planowanie online, rysowanie tras, postoje i GPX. Lokalizacja jest pobierana po naciśnięciu przycisku. Szkic i punkty trasy trafiają do backendu, FOSSGIS i Overpass; wyszukiwania do Photon. Jeśli backend ma włączony GPT, nazwy i kategorie znalezionych miejsc są przekazywane OpenAI do rankingu. Klucz pozostaje na serwerze.",
                fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) { Text("Dane map i licencje ↗") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
