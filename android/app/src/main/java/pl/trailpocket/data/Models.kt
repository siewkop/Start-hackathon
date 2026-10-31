package pl.trailpocket.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class Point(val lat: Double, val lon: Double, val name: String = "Punkt na mapie") {
    fun coordinates() = String.format(Locale.US, "%.5f, %.5f", lat, lon)
}

@Serializable
enum class Profile(val label: String) {
    @SerialName("hike") HIKE("Pieszo"),
    @SerialName("bike") BIKE("Rowerem")
}

@Serializable
data class Geometry(val type: String = "LineString", val coordinates: List<List<Double>>)

enum class PlanningMode { POINTS, SKETCH }

@Serializable
enum class Interest(val label: String) {
    @SerialName("balanced") BALANCED("Miks"),
    @SerialName("nature") NATURE("Przyroda"),
    @SerialName("culture") CULTURE("Historia")
}

@Serializable
data class SketchDraft(val path: List<Point> = emptyList(), @SerialName("stop_indices") val stopIndices: List<Int> = emptyList())

@Serializable
data class SketchRequest(val profile: Profile, val path: List<Point>,
    @SerialName("stop_indices") val stopIndices: List<Int>, val interest: Interest,
    @SerialName("max_extra_ratio") val maxExtraRatio: Double = .25, val recreation: Double = 0.67)

@Serializable
data class Attraction(val id: String, val lat: Double, val lon: Double, val name: String, val category: String,
    val kind: String = "", val description: String = "", val wikipedia: String = "",
    @SerialName("distance_to_route_m") val distanceToRouteM: Double = 0.0,
    @SerialName("selected_for_detour") val selectedForDetour: Boolean = false) {
    fun categoryLabel() = when (category) { "nature" -> "Przyroda"; "view" -> "Punkt widokowy"; "art" -> "Sztuka"; "museum" -> "Muzeum"; else -> "Historia" }
    fun categoryColor(): Int = when (category) {
        "nature" -> 0xFF1976B9.toInt()
        "view" -> 0xFFD18416.toInt()
        "art" -> 0xFFAD407D.toInt()
        "museum" -> 0xFF7255AD.toInt()
        else -> 0xFF865B3B.toInt()
    }
    fun categorySymbol() = when (category) { "nature" -> "N"; "view" -> "W"; "art" -> "S"; "museum" -> "M"; else -> "H" }
    fun wikipediaUrl(): String? {
        val parts = wikipedia.split(":", limit = 2)
        if (parts.size != 2 || !parts[0].matches(Regex("[a-z]{2,12}")) || parts[1].isBlank()) return null
        return "https://${parts[0]}.wikipedia.org/wiki/" + java.net.URLEncoder.encode(parts[1].replace(' ', '_'), "UTF-8").replace("+", "%20")
    }
    fun sourceUrl() = "https://www.openstreetmap.org/$id"
}

@Serializable
data class Discovery(val sketch: List<Point>, val stops: List<Point>,
    @SerialName("stop_indices") val stopIndices: List<Int>, val attractions: List<Attraction>, val interest: Interest,
    @SerialName("considered_variants") val consideredVariants: Int,
    @SerialName("baseline_distance_m") val baselineDistanceM: Double,
    @SerialName("extra_distance_m") val extraDistanceM: Double,
    @SerialName("ai_used") val aiUsed: Boolean = false, val explanation: String, val warnings: List<String> = emptyList())

@Serializable
data class Route(
    val id: String,
    val name: String? = null,
    val profile: Profile,
    val points: List<Point>,
    val geometry: Geometry,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("created_at") val createdAt: String,
    val recreation: Double = 0.0,
    @SerialName("routing_engine") val routingEngine: String = "osrm",
    @SerialName("routing_warning") val routingWarning: String? = null,
    val discovery: Discovery? = null
) {
    fun distanceLabel() = if (distanceM < 1000) "${distanceM.roundToInt()} m"
        else String.format(Locale.forLanguageTag("pl"), "%.1f km", distanceM / 1000)
    fun durationLabel(): String {
        val minutes = (durationS / 60).roundToInt().coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }
}

@Serializable
data class PlanRequest(val profile: Profile, val points: List<Point>, val recreation: Double = 0.67)

@Serializable
data class SaveRequest(@SerialName("plan_id") val planId: String, val name: String)
