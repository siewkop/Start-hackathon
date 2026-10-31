package pl.trailpocket.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import pl.trailpocket.BuildConfig

private class ApiException(message: String) : IOException(message)

class TrailRepository(context: Context) {
    private val prefs = context.getSharedPreferences("trail", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "routes").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        // Discovery can wait for several upstream services before returning a response.
        // The per-call deadline below still limits ordinary requests to 40 seconds.
        .readTimeout(150, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val token = prefs.getString("token", null) ?: ByteArray(32).also {
        SecureRandom().nextBytes(it)
    }.joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("token", it).apply() }

    var baseUrl: String
        get() = prefs.getString("base_url", BuildConfig.DEFAULT_API_URL)!!
        private set(value) { prefs.edit().putString("base_url", value).apply() }

    fun updateUrl(value: String) {
        val parsed = value.trim().toHttpUrlOrNull() ?: error("Enter a valid http:// or https:// address.")
        require(parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null &&
            parsed.username.isEmpty() && parsed.password.isEmpty()) { "Enter only the server address, e.g. http://192.168.1.10:8000." }
        require(BuildConfig.DEBUG || parsed.isHttps) { "The release version requires HTTPS." }
        baseUrl = parsed.toString().trimEnd('/')
    }

    private fun cacheFile(): File {
        // Separate cache for each backend; never show routes from a different server.
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(baseUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$key.json")
    }

    private suspend fun request(path: String, method: String = "GET", body: String? = null, timeoutSeconds: Long = 40): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(baseUrl + path).header("X-Client-Token", token)
            builder.method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType()))
            try {
                val call = client.newCall(builder.build())
                call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
                call.execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (path == "/api/v1/sketch-plans" && response.code == 404) {
                            throw ApiException("Restart the updated backend. This server does not support sketch routes yet.")
                        }
                        val detail = runCatching {
                            json.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.content
                        }.getOrNull()
                        throw ApiException(detail ?: "The server rejected the request (${response.code}). Check your input and try again.")
                    }
                    text
                }
            } catch (e: IOException) {
                if (e is ApiException) throw e
                throw IOException("Cannot connect to the backend. Check the address in settings and start the server.", e)
            }
        }

    suspend fun plan(profile: Profile, points: List<Point>, recreation: Double): Route = json.decodeFromString(
        request("/api/v1/plans", "POST", json.encodeToString(PlanRequest(profile, points, recreation))))

    suspend fun sketch(profile: Profile, draft: SketchDraft, interest: Interest, extraRatio: Double, recreation: Double): Route = json.decodeFromString(
        request("/api/v1/sketch-plans", "POST", json.encodeToString(
            SketchRequest(profile, draft.path, draft.stopIndices, interest, extraRatio, recreation)), timeoutSeconds = 150))

    suspend fun search(query: String): List<Point> {
        val url = (baseUrl + "/api/v1/places").toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query).build()
        return json.decodeFromString(request(url.toString().removePrefix(baseUrl)))
    }

    suspend fun routes(): List<Route> {
        val text = request("/api/v1/routes")
        val routes = json.decodeFromString<List<Route>>(text)
        withContext(Dispatchers.IO) { runCatching { cacheFile().writeText(text) } }
        return routes
    }

    suspend fun cachedRoutes(): List<Route> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<List<Route>>(cacheFile().readText()) }.getOrDefault(emptyList())
    }

    private suspend fun updateCache(routes: List<Route>) = withContext(Dispatchers.IO) {
        runCatching { cacheFile().writeText(json.encodeToString(routes)) }
        Unit
    }

    suspend fun save(route: Route, name: String): Route {
        val saved = json.decodeFromString<Route>(
            request("/api/v1/routes", "POST", json.encodeToString(SaveRequest(route.id, name))))
        updateCache(listOf(saved) + cachedRoutes().filter { it.id != saved.id })
        return saved
    }

    suspend fun delete(route: Route) {
        request("/api/v1/routes/${route.id}", "DELETE")
        updateCache(cachedRoutes().filter { it.id != route.id })
    }

    suspend fun health() { request("/health") }
}
