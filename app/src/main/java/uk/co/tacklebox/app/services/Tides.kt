/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Url
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

enum class TideKind { HIGH, LOW }

/** Where a set of predictions came from. Shown to the angler, because the two have different coverage. */
enum class TideSource(val label: String) { NOAA("NOAA"), WORLD_TIDES("WorldTides") }

data class TideEvent(val time: Instant, val heightMetres: Double, val kind: TideKind)

data class TideResult(val events: List<TideEvent>, val source: TideSource, val cached: Boolean) {
    fun next(from: Instant = Instant.now()): TideEvent? = events.firstOrNull { !it.time.isBefore(from) }
}

sealed class TideError(message: String) : Exception(message) {
    object NotAvailableHere : TideError("Tide predictions aren't available for your area.")
    object Offline : TideError("Tide predictions couldn't be updated. Check your connection and try again.")
}

interface TideApi {
    @GET suspend fun stations(@Url url: String): NoaaStations
    @GET suspend fun predictions(@Url url: String): NoaaPredictions
    @GET suspend fun worldTides(@Url url: String): WorldTidesResponse
}

data class NoaaStations(val stations: List<NoaaStation> = emptyList())
data class NoaaStation(val id: String, val name: String?, val lat: Double, val lng: Double)
data class NoaaPredictions(val predictions: List<NoaaPrediction> = emptyList())
data class NoaaPrediction(@SerializedName("t") val time: String?, @SerializedName("v") val value: String?, val type: String?)
data class WorldTidesResponse(val extremes: List<WorldTideExtreme> = emptyList())
data class WorldTideExtreme(val dt: Long, val height: Double?, val type: String?)

/**
 * Tide predictions, ported from the iOS `TideService` (feature parity, 2026-09-08).
 *
 * Android had none at all: the screen called "Tides & sea" fetched wave height and period from Open-Meteo and
 * nothing else, while the store listing promised coastal tides. iOS has had NOAA for the contiguous US, with
 * WorldTides as an optional key for everywhere else, since before this app shipped.
 *
 * The source is chosen by position, exactly as iOS chooses it: inside the contiguous US, NOAA, which is free and
 * needs no key; outside it, WorldTides if the angler has supplied a key, and otherwise an honest "not available
 * here" rather than an empty screen.
 */
object Tides {
    /** Predictions are valid over a wide area, so a fix a few miles along the coast can reuse the last answer. */
    private const val CACHE_RADIUS_KM = 100.0
    private var cache: Triple<Double, Double, TideResult>? = null

    private val api: TideApi = Services.tide

    fun isContiguousUS(latitude: Double, longitude: Double) =
        latitude in 24.4..49.5 && longitude in -125.0..-66.5

    suspend fun tides(latitude: Double, longitude: Double, worldTidesKey: String = ""): TideResult {
        val key = worldTidesKey.trim()
        val source = when {
            isContiguousUS(latitude, longitude) -> TideSource.NOAA
            key.isNotEmpty() -> TideSource.WORLD_TIDES
            else -> throw TideError.NotAvailableHere
        }
        return try {
            val events = when (source) {
                TideSource.NOAA -> noaa(latitude, longitude)
                TideSource.WORLD_TIDES -> worldTides(latitude, longitude, key)
            }
            if (events.isEmpty()) throw TideError.NotAvailableHere
            val result = TideResult(events.sortedBy { it.time }, source, cached = false)
            cache = Triple(latitude, longitude, result)
            result
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: TideError) {
            nearbyCache(latitude, longitude) ?: throw e
        } catch (e: Exception) {
            nearbyCache(latitude, longitude) ?: throw TideError.Offline
        }
    }

    private fun nearbyCache(latitude: Double, longitude: Double): TideResult? =
        cache?.takeIf { distanceKm(latitude, longitude, it.first, it.second) < CACHE_RADIUS_KM }
            ?.third?.let { TideResult(it.events, it.source, cached = true) }

    /** Nearest tide-prediction station, then today's highs and lows for it. Heights come back in feet. */
    private suspend fun noaa(latitude: Double, longitude: Double): List<TideEvent> {
        val stations = api.stations("https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions")
        val nearest = stations.stations.minByOrNull { distanceKm(latitude, longitude, it.lat, it.lng) }
            ?: throw TideError.NotAvailableHere
        val url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
            "?product=predictions&application=tacklebox&datum=MLLW&station=${nearest.id}" +
            "&time_zone=lst_ldt&units=english&interval=hilo&format=json&date=today"
        return eventsFrom(api.predictions(url).predictions)
    }

    /**
     * NOAA rows to tide events. Separated so the parsing can be tested against a real response rather than one I
     * wrote from memory — heights come back in feet with `units=english`, which is the part worth pinning.
     */
    internal fun eventsFrom(predictions: List<NoaaPrediction>): List<TideEvent> = predictions.mapNotNull { p ->
        val at = p.time?.let(::noaaTime) ?: return@mapNotNull null
        val feet = p.value?.toDoubleOrNull() ?: return@mapNotNull null
        val kind = when (p.type) { "H" -> TideKind.HIGH; "L" -> TideKind.LOW; else -> null } ?: return@mapNotNull null
        TideEvent(at, feet * 0.3048, kind)
    }

    /**
     * Locale-independent: `"%.2f".format(x)` follows the device locale, so a German or French phone sent
     * `lat=50,15` and the only non-US tide source rejected every request.
     */
    internal fun worldTidesUrl(latitude: Double, longitude: Double, key: String): String =
        "https://www.worldtides.info/api/v3?extremes" +
            "&lat=${"%.2f".format(java.util.Locale.US, latitude)}&lon=${"%.2f".format(java.util.Locale.US, longitude)}&key=$key"

    private suspend fun worldTides(latitude: Double, longitude: Double, key: String): List<TideEvent> {
        val url = worldTidesUrl(latitude, longitude, key)
        val today = LocalDate.now()
        return api.worldTides(url).extremes.mapNotNull { e ->
            val kind = when (e.type?.lowercase()) { "high" -> TideKind.HIGH; "low" -> TideKind.LOW; else -> null }
                ?: return@mapNotNull null
            val at = Instant.ofEpochSecond(e.dt)
            // Only today's, as iOS does — a week of extremes on one screen is noise.
            if (at.atZone(ZoneId.systemDefault()).toLocalDate() != today) return@mapNotNull null
            TideEvent(at, e.height ?: 0.0, kind)
        }
    }

    /** NOAA returns local station time with `time_zone=lst_ldt`, so it is parsed in the device zone. */
    private fun noaaTime(value: String): Instant? = try {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            .atZone(ZoneId.systemDefault()).toInstant()
    } catch (_: Exception) { null }

    /** Haversine, in kilometres — used to pick the nearest station and to judge whether the cache still applies. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    /** Test seam: the in-memory cache outlives a single call by design, so tests have to be able to clear it. */
    internal fun clearCache() { cache = null }
}
