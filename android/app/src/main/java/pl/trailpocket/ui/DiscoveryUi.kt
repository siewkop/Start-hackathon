package pl.trailpocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.trailpocket.PlannerState
import pl.trailpocket.data.Attraction
import pl.trailpocket.data.PlanningMode
import kotlin.math.roundToInt

@Composable
internal fun SketchToolbar(state: PlannerState, modifier: Modifier, onDraw: () -> Unit, onUndo: () -> Unit, onPlan: () -> Unit, showPlan: Boolean = false) {
    Surface(modifier, color = Color.White.copy(alpha = .97f), shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.planningMode == PlanningMode.POINTS) {
                TextButton(onClick = onDraw, enabled = !state.busy) {
                    Icon(Icons.Outlined.Gesture, null, Modifier.size(22.dp))
                    Text("Draw a route", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                }
            } else {
                FilterChip(selected = state.isDrawing, enabled = !state.busy, onClick = onDraw,
                    leadingIcon = { Icon(if (state.isDrawing) Icons.Outlined.Gesture else Icons.Outlined.PanTool, null, Modifier.size(18.dp)) },
                    label = { Text(if (state.isDrawing) "Drawing" else "Draw") })
                IconButton(onClick = onUndo, enabled = state.canUndoSketch && !state.busy) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, "Undo the last section or stop")
                }
                if (showPlan && state.sketch.path.size >= 2 && state.route == null) TextButton(onClick = onPlan, enabled = !state.busy) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Route, null, Modifier.size(20.dp))
                    Text(if (state.busy) "Planning…" else "Plan route", Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttractionSheet(poi: Attraction, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("${poi.categorySymbol()} · ${poi.categoryLabel()}", color = Color(poi.categoryColor()), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(poi.name, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Text("About ${poi.distanceToRouteM.roundToInt()} m from your route.", fontSize = 14.sp)
            if (poi.description.isNotBlank()) Text(poi.description, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            poi.wikipediaUrl()?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) { Text("Read more on Wikipedia ↗") }
            }
            if (poi.selectedForDetour) Text("Your route passes near this place.", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Place information comes from OpenStreetMap. Check details and access before visiting.",
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { uriHandler.openUri(poi.sourceUrl()) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("View on OpenStreetMap")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
