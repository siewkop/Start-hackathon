package pl.trailpocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.trailpocket.data.Point
import pl.trailpocket.data.Profile
import pl.trailpocket.data.Route
import pl.trailpocket.data.TrailRepository
import pl.trailpocket.data.PlanningMode
import pl.trailpocket.data.SketchDraft
import pl.trailpocket.data.Interest

data class PlannerState(
    val start: Point? = null,
    val end: Point? = null,
    val profile: Profile = Profile.HIKE,
    val selectingStart: Boolean = true,
    val route: Route? = null,
    val isSaved: Boolean = false,
    val saved: List<Route> = emptyList(),
    val places: List<Point> = emptyList(),
    val busy: Boolean = false,
    val searching: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val cached: Boolean = false,
    val baseUrl: String = "",
    val focus: Point? = null,
    val cameraVersion: Int = 0,
    val planningMode: PlanningMode = PlanningMode.POINTS,
    val isDrawing: Boolean = false,
    val sketch: SketchDraft = SketchDraft(),
    val canUndoSketch: Boolean = false,
    val interest: Interest = Interest.BALANCED,
    val maxExtraRatio: Double = .25,
    val recreation: Double = .67
)

class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrailRepository(application)
    private val mutable = MutableStateFlow(PlannerState(baseUrl = repository.baseUrl))
    val state = mutable.asStateFlow()
    private var planJob: Job? = null
    private var searchJob: Job? = null
    private var operationJob: Job? = null
    private var refreshJob: Job? = null
    private val sketchHistory = ArrayDeque<SketchDraft>()

    init { refresh() }

    fun selectStart(value: Boolean) { mutable.update { it.copy(selectingStart = value, places = emptyList()) } }

    fun choose(point: Point) {
        if (mutable.value.busy || mutable.value.planningMode != PlanningMode.POINTS) return
        planJob?.cancel()
        mutable.update {
            if (it.selectingStart) it.copy(start = point, selectingStart = false, route = null, isSaved = false,
                places = emptyList(), error = null)
            else it.copy(end = point, route = null, isSaved = false, places = emptyList(), error = null)
        }
    }

    fun chooseSearchResult(point: Point) {
        choose(point)
        mutable.update { it.copy(focus = point, cameraVersion = it.cameraVersion + 1,
            route = if (it.planningMode == PlanningMode.SKETCH) null else it.route,
            isDrawing = false, isSaved = false) }
    }

    fun toggleDrawing() {
        if (mutable.value.busy) return
        mutable.update {
            val draft = it.sketch
            it.copy(planningMode = PlanningMode.SKETCH, isDrawing = !it.isDrawing, route = null, isSaved = false,
                start = draft.path.firstOrNull(), end = if (draft.path.size >= 2) draft.path.last() else null, error = null)
        }
    }

    fun pauseDrawing() { mutable.update { it.copy(isDrawing = false) } }

    fun usePointMode() {
        if (mutable.value.busy) return
        mutable.update { it.copy(planningMode = PlanningMode.POINTS, isDrawing = false, route = null, isSaved = false) }
    }

    private fun commitDraft(draft: SketchDraft) {
        if (mutable.value.busy) return
        if (draft.path.size > 2000 || draft.stopIndices.size > 8) {
            error("Szkic może mieć maksymalnie 8 postojów. Skróć szkic lub cofnij ostatni fragment.")
            return
        }
        sketchHistory.addLast(mutable.value.sketch)
        if (sketchHistory.size > 30) sketchHistory.removeFirst()
        mutable.update { it.copy(sketch = draft, start = draft.path.firstOrNull(),
            end = if (draft.path.size >= 2) draft.path.last() else null, route = null, isSaved = false,
            canUndoSketch = true, planningMode = PlanningMode.SKETCH, error = null) }
    }

    fun appendSketch(stroke: SketchDraft) {
        if (stroke.path.isEmpty()) return
        val old = mutable.value.sketch
        commitDraft(SketchDraft(old.path + stroke.path, (old.stopIndices + stroke.stopIndices.map { it + old.path.size }).distinct().sorted()))
    }

    fun addSketchStop(index: Int) {
        val old = mutable.value.sketch
        if (index !in old.path.indices || index in old.stopIndices) return
        commitDraft(old.copy(stopIndices = (old.stopIndices + index).sorted()))
    }

    fun undoSketch() {
        if (mutable.value.busy || sketchHistory.isEmpty()) return
        val draft = sketchHistory.removeLast()
        mutable.update { it.copy(sketch = draft, start = draft.path.firstOrNull(),
            end = if (draft.path.size >= 2) draft.path.last() else null,
            route = null, isSaved = false, canUndoSketch = sketchHistory.isNotEmpty()) }
    }

    fun setInterest(interest: Interest) {
        if (!mutable.value.busy) mutable.update { it.copy(interest = interest, route = null, isSaved = false) }
    }

    fun setExtraRatio(ratio: Double) {
        if (!mutable.value.busy) mutable.update { it.copy(maxExtraRatio = ratio.coerceIn(0.0, .4), route = null, isSaved = false) }
    }

    fun setProfile(profile: Profile) {
        if (mutable.value.busy || mutable.value.profile == profile) return
        mutable.update { it.copy(profile = profile, route = null, isSaved = false) }
    }

    fun swap() {
        if (mutable.value.busy) return
        if (mutable.value.planningMode == PlanningMode.SKETCH) {
            val draft = mutable.value.sketch
            commitDraft(SketchDraft(draft.path.reversed(), draft.stopIndices.map { draft.path.lastIndex - it }.sorted()))
            return
        }
        mutable.update { it.copy(start = it.end, end = it.start, route = null, isSaved = false) }
    }

    fun newRoute() {
        if (mutable.value.busy) return
        clear()
        mutable.update { it.copy(isDrawing = false, focus = null, message = null) }
    }

    fun setRecreation(value: Double) {
        if (!mutable.value.busy) mutable.update { it.copy(recreation = value.coerceIn(0.0, 1.0), route = null, isSaved = false) }
    }

    fun clear() {
        if (mutable.value.busy) return
        planJob?.cancel()
        searchJob?.cancel()
        sketchHistory.clear()
        mutable.update { it.copy(start = null, end = null, route = null, isSaved = false,
            selectingStart = true, places = emptyList(), searching = false, error = null,
            sketch = SketchDraft(), canUndoSketch = false) }
    }

    fun plan() {
        val snapshot = mutable.value
        if (snapshot.planningMode == PlanningMode.SKETCH) { planSketch(); return }
        val start = snapshot.start ?: return
        val end = snapshot.end ?: return
        if (snapshot.busy) return
        if (start.lat == end.lat && start.lon == end.lon) {
            error("Wybierz różne punkty startu i celu.")
            return
        }
        planJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val route = repository.plan(snapshot.profile, listOf(start, end), snapshot.recreation)
                mutable.update { it.copy(route = route, isSaved = false, cameraVersion = it.cameraVersion + 1) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error(e.message ?: "Nie udało się wyznaczyć trasy.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private fun planSketch() {
        val snapshot = mutable.value
        if (snapshot.busy || snapshot.sketch.path.size < 2) return
        planJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, isDrawing = false, error = null) }
            try {
                val route = repository.sketch(snapshot.profile, snapshot.sketch, snapshot.interest, snapshot.maxExtraRatio, snapshot.recreation)
                mutable.update { it.copy(route = route, isSaved = false, cameraVersion = it.cameraVersion + 1) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error(e.message ?: "Nie udało się dopasować szkicu do ścieżek.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun search(query: String) {
        if (query.trim().length < 2 || mutable.value.busy) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutable.update { it.copy(searching = true, places = emptyList(), error = null) }
            try {
                val places = repositorySearch(query)
                mutable.update { it.copy(places = places) }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e.message ?: "Wyszukiwanie nie powiodło się.") }
            finally { mutable.update { it.copy(searching = false) } }
        }
    }

    private suspend fun repositorySearch(query: String): List<Point> {
        val results = repository.search(query.trim())
        if (results.isEmpty()) mutable.update { it.copy(message = "Brak wyników. Spróbuj podać miejscowość.") }
        return results
    }

    fun dismissSearch() {
        searchJob?.cancel()
        mutable.update { it.copy(places = emptyList(), searching = false) }
    }

    fun save(name: String) {
        val route = mutable.value.route ?: return
        if (name.isBlank() || mutable.value.busy || mutable.value.isSaved) return
        refreshJob?.cancel()
        operationJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val saved = repository.save(route, name.trim())
                mutable.update { it.copy(route = saved, isSaved = true, message = "Trasa zapisana") }
                loadRoutes()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error(e.message ?: "Nie udało się zapisać trasy.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun refresh() {
        if (mutable.value.busy) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { loadRoutes() }
    }

    private suspend fun loadRoutes() {
        mutable.update { it.copy(refreshing = true) }
        try {
            val routes = repository.routes()
            mutable.update { it.copy(saved = routes, cached = false) }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            val cache = repository.cachedRoutes()
            mutable.update { it.copy(saved = cache, cached = true) }
        } finally { mutable.update { it.copy(refreshing = false) } }
    }

    fun open(route: Route) {
        if (mutable.value.busy) return
        sketchHistory.clear()
        mutable.update { it.copy(start = route.points.first(), end = route.points.last(), profile = route.profile,
            route = route, recreation = route.recreation, isSaved = true, places = emptyList(), error = null, cameraVersion = it.cameraVersion + 1,
            planningMode = if (route.discovery == null) PlanningMode.POINTS else PlanningMode.SKETCH,
            sketch = route.discovery?.let { d -> SketchDraft(d.sketch, d.stopIndices) } ?: SketchDraft(),
            interest = route.discovery?.interest ?: it.interest, isDrawing = false, canUndoSketch = false) }
    }

    fun delete(route: Route) {
        if (mutable.value.busy) return
        refreshJob?.cancel()
        operationJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                repository.delete(route)
                mutable.update { it.copy(message = "Trasa usunięta",
                    route = if (it.route?.id == route.id) null else it.route,
                    isSaved = if (it.route?.id == route.id) false else it.isSaved) }
                loadRoutes()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error(e.message ?: "Nie udało się usunąć trasy.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun updateUrl(value: String) {
        if (mutable.value.busy) return
        runCatching { repository.updateUrl(value) }.onSuccess {
            operationJob?.cancel()
            refreshJob?.cancel()
            planJob?.cancel()
            searchJob?.cancel()
            sketchHistory.clear()
            mutable.value = PlannerState(baseUrl = repository.baseUrl, message = "Adres serwera zapisany")
            refresh()
        }.onFailure { error(it.message ?: "Nieprawidłowy adres.") }
    }

    fun checkConnection() {
        if (mutable.value.busy) return
        operationJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try {
                repository.health()
                mutable.update { it.copy(message = "Backend odpowiada poprawnie") }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error(e.message ?: "Brak połączenia.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun useLocation(point: Point) {
        if (mutable.value.busy) return
        if (mutable.value.planningMode == PlanningMode.SKETCH) {
            mutable.update { it.copy(focus = point, route = null, isSaved = false, isDrawing = false,
                cameraVersion = it.cameraVersion + 1, message = "Rysuj w wybranej okolicy. Przytrzymaj palec, aby dodać postój.") }
            return
        }
        mutable.update { it.copy(start = point, route = null, isSaved = false, selectingStart = false,
            focus = point, cameraVersion = it.cameraVersion + 1, message = "Twoja lokalizacja ustawiona jako start") }
    }

    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun clearNotice() { mutable.update { it.copy(error = null, message = null) } }
}
