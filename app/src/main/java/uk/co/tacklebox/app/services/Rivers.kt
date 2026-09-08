/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.http.GET
import retrofit2.http.Url
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.max

enum class RiverTrend { RISING, FALLING, STEADY, UNKNOWN }

data class RiverReading(val at: Instant, val value: Double)
data class RiverMeasurement(val value: Double, val unit: String)

data class RiverGauge(
    val id: String,
    val name: String,
    val river: String?,
    val latestLevel: RiverMeasurement?,
    val latestFlow: RiverMeasurement?,
    val trend: RiverTrend,
    val updatedAt: Instant?,
    val distanceKm: Double,
    val sourceLabel: String,
    val history: List<RiverReading>,
    val historyUnit: String?,
)

data class RiverResult(val gauges: List<RiverGauge>, val cached: Boolean)

sealed class RiverError(message: String) : Exception(message) {
    object UnavailableArea : RiverError("River data isn't available for your area yet.")
    object NoGauges : RiverError("No river gauges were found nearby.")
    object Offline : RiverError("River conditions couldn't be updated. Check your connection and try again.")
}

interface RiverStationsApi {
    @GET suspend fun stations(@Url url: String): EAStationsResponse
    @GET suspend fun readings(@Url url: String): EAReadingsResponse
    @GET suspend fun usgs(@Url url: String): UsgsResponse
}

data class EAStationsResponse(val items: List<EAStation> = emptyList())
data class EAStation(
    val stationReference: String?, val label: Any?, val riverName: String?,
    val lat: Double?, val long: Double?, val measures: List<EAMeasure> = emptyList(),
) {
    /** The EA sometimes returns `label` as an array when a station has aliases. */
    val displayName: String get() = when (val l = label) {
        is String -> l
        is List<*> -> l.filterIsInstance<String>().firstOrNull() ?: (stationReference ?: "Gauge")
        else -> stationReference ?: "Gauge"
    }
}
data class EAMeasure(@SerializedName("@id") val id: String?, val parameter: String?, val unitName: String?)
data class EAReadingsResponse(val items: List<EAReadingItem> = emptyList())
data class EAReadingItem(val dateTime: String?, val value: Double?)

data class UsgsResponse(val value: UsgsValue = UsgsValue())
data class UsgsValue(val timeSeries: List<UsgsSeries> = emptyList())
data class UsgsSeries(val sourceInfo: UsgsSource = UsgsSource(), val variable: UsgsVariable = UsgsVariable(), val values: List<UsgsValues> = emptyList())
data class UsgsSource(val siteName: String = "Gauge", val siteCode: List<UsgsCode> = emptyList(), val geoLocation: UsgsGeo = UsgsGeo())
data class UsgsCode(val value: String?)
data class UsgsGeo(val geogLocation: UsgsPoint? = null)
data class UsgsPoint(val latitude: Double?, val longitude: Double?)
data class UsgsVariable(val variableCode: List<UsgsCode> = emptyList(), val unit: UsgsUnit = UsgsUnit())
data class UsgsUnit(val unitCode: String? = null)
data class UsgsValues(val value: List<UsgsPointValue> = emptyList())
data class UsgsPointValue(val value: String?, val dateTime: String?)

/**
 * River gauges, ported from the iOS `RiverService` (feature parity, 2026-09-08).
 *
 * Android called one endpoint — `flood-monitoring/id/readings` — and printed bare numbers with no station name,
 * no units, no trend and no US coverage at all. iOS has named Environment Agency stations with twelve readings of
 * history and a rising/falling/steady trend, and USGS for the contiguous US.
 *
 * The source is chosen by position, as on iOS: Great Britain to the EA, the contiguous US to USGS, anywhere else
 * an honest "not available for your area yet".
 */
object Rivers {
    private const val CACHE_RADIUS_KM = 40.0
    private var cache: Triple<Double, Double, List<RiverGauge>>? = null

    private val api: RiverStationsApi get() = Services.riverStations

    private enum class Source { EA, USGS }

    private fun source(latitude: Double, longitude: Double): Source? = when {
        latitude in 49.8..59.0 && longitude in -8.7..2.1 -> Source.EA
        latitude in 24.4..49.5 && longitude in -125.0..-66.5 -> Source.USGS
        else -> null
    }

    suspend fun gauges(latitude: Double, longitude: Double): RiverResult {
        val source = source(latitude, longitude) ?: throw RiverError.UnavailableArea
        return try {
            val found = when (source) {
                Source.EA -> environmentAgency(latitude, longitude)
                Source.USGS -> usgs(latitude, longitude)
            }
            if (found.isEmpty()) throw RiverError.NoGauges
            val sorted = found.sortedBy { it.distanceKm }.take(8)
            cache = Triple(latitude, longitude, sorted)
            RiverResult(sorted, cached = false)
        } catch (e: RiverError) {
            // "No gauges nearby" is an answer, not a failure — do not paper over it with stale ones.
            if (e is RiverError.NoGauges) throw e
            nearbyCache(latitude, longitude) ?: throw e
        } catch (e: Exception) {
            nearbyCache(latitude, longitude) ?: throw RiverError.Offline
        }
    }

    private fun nearbyCache(latitude: Double, longitude: Double): RiverResult? =
        cache?.takeIf { Tides.distanceKm(latitude, longitude, it.first, it.second) < CACHE_RADIUS_KM }
            ?.let { RiverResult(it.third, cached = true) }

    private suspend fun environmentAgency(latitude: Double, longitude: Double): List<RiverGauge> = coroutineScope {
        fun url(parameter: String) =
            "https://environment.data.gov.uk/flood-monitoring/id/stations" +
                "?lat=${"%.2f".format(latitude)}&long=${"%.2f".format(longitude)}&dist=25&parameter=$parameter"
        val levels = async { runCatching { api.stations(url("level")).items }.getOrDefault(emptyList()) }
        val flows = async { runCatching { api.stations(url("flow")).items }.getOrDefault(emptyList()) }

        // A station can appear under both parameters; merge on its reference so it is listed once with both.
        val merged = LinkedHashMap<String, EAStation>()
        (levels.await() + flows.await()).forEach { st ->
            val key = st.stationReference ?: return@forEach
            val existing = merged[key]
            merged[key] = if (existing == null) st else existing.copy(
                riverName = existing.riverName ?: st.riverName,
                measures = existing.measures + st.measures.filterNot { m -> existing.measures.any { it.id == m.id } })
        }

        val nearest = merged.values
            .filter { it.lat != null && it.long != null }
            .sortedBy { Tides.distanceKm(latitude, longitude, it.lat!!, it.long!!) }
            .take(8)

        nearest.map { station ->
            async {
                val primary = station.measures.firstOrNull { it.parameter?.lowercase() == "level" }
                    ?: station.measures.firstOrNull()
                val history = history(primary)
                val latest = history.lastOrNull()
                if (latest == null) null else {
                    val measurement = RiverMeasurement(latest.value, primary?.unitName.orEmpty())
                    val isLevel = primary?.parameter?.lowercase() == "level"
                    RiverGauge(
                        id = "ea-${station.stationReference}", name = station.displayName, river = station.riverName,
                        latestLevel = if (isLevel) measurement else null,
                        latestFlow = if (isLevel) null else measurement,
                        trend = trend(history), updatedAt = latest.at,
                        distanceKm = Tides.distanceKm(latitude, longitude, station.lat!!, station.long!!),
                        sourceLabel = "Environment Agency", history = history, historyUnit = primary?.unitName)
                }
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun history(measure: EAMeasure?): List<RiverReading> {
        val id = measure?.id?.substringAfterLast('/') ?: return emptyList()
        val url = "https://environment.data.gov.uk/flood-monitoring/id/measures/$id/readings?_sorted&_limit=12"
        return runCatching {
            api.readings(url).items.mapNotNull { r ->
                val at = r.dateTime?.let(::instant) ?: return@mapNotNull null
                val v = r.value ?: return@mapNotNull null
                RiverReading(at, v)
            }.sortedBy { it.at }
        }.getOrDefault(emptyList())
    }

    private suspend fun usgs(latitude: Double, longitude: Double): List<RiverGauge> {
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&parameterCd=00065,00060&siteStatus=active" +
            "&bBox=${"%.4f".format(longitude - 0.3)},${"%.4f".format(latitude - 0.3)}," +
            "${"%.4f".format(longitude + 0.3)},${"%.4f".format(latitude + 0.3)}"
        val response = api.usgs(url)
        data class Site(val name: String, val lat: Double, val lon: Double,
                        var level: Pair<List<RiverReading>, String?>? = null,
                        var flow: Pair<List<RiverReading>, String?>? = null)
        val sites = LinkedHashMap<String, Site>()
        response.value.timeSeries.forEach { series ->
            val point = series.sourceInfo.geoLocation.geogLocation ?: return@forEach
            val siteLat = point.latitude ?: return@forEach
            val siteLon = point.longitude ?: return@forEach
            val code = series.variable.variableCode.firstOrNull()?.value ?: return@forEach
            val key = series.sourceInfo.siteCode.firstOrNull()?.value ?: series.sourceInfo.siteName
            val site = sites.getOrPut(key) { Site(series.sourceInfo.siteName, siteLat, siteLon) }
            val readings = (series.values.firstOrNull()?.value ?: emptyList()).mapNotNull { p ->
                val v = p.value?.toDoubleOrNull() ?: return@mapNotNull null
                val moment = p.dateTime?.let(::instant) ?: return@mapNotNull null
                RiverReading(moment, v)
            }.sortedBy { it.at }
            when (code) {
                "00065" -> site.level = readings to series.variable.unit.unitCode
                "00060" -> site.flow = readings to series.variable.unit.unitCode
            }
        }
        return sites.map { (key, site) ->
            val primary = site.level ?: site.flow
            RiverGauge(
                id = "usgs-$key", name = site.name, river = null,
                latestLevel = site.level?.let { m -> m.first.lastOrNull()?.let { RiverMeasurement(it.value, m.second.orEmpty()) } },
                latestFlow = site.flow?.let { m -> m.first.lastOrNull()?.let { RiverMeasurement(it.value, m.second.orEmpty()) } },
                trend = trend(primary?.first ?: emptyList()),
                updatedAt = listOfNotNull(site.level?.first?.lastOrNull()?.at, site.flow?.first?.lastOrNull()?.at).maxOrNull(),
                distanceKm = Tides.distanceKm(latitude, longitude, site.lat, site.lon),
                sourceLabel = "USGS", history = primary?.first ?: emptyList(), historyUnit = primary?.second)
        }
    }

    /**
     * Rising, falling or steady over roughly the last three hours.
     *
     * The threshold is one per cent of the earlier reading, floored at 0.01, so a river sitting at 4.2 m is not
     * called "rising" because it moved a centimetre, and a small stream is not called steady because it did.
     */
    fun trend(readings: List<RiverReading>): RiverTrend {
        val latest = readings.lastOrNull() ?: return RiverTrend.UNKNOWN
        if (readings.size < 2) return RiverTrend.UNKNOWN
        val target = latest.at.minusSeconds(3 * 3600)
        val earlier = readings.dropLast(1).minByOrNull { abs(it.at.epochSecond - target.epochSecond) }
            ?: return RiverTrend.UNKNOWN
        val difference = latest.value - earlier.value
        val threshold = max(abs(earlier.value) * 0.01, 0.01)
        return when {
            abs(difference) <= threshold -> RiverTrend.STEADY
            difference > 0 -> RiverTrend.RISING
            else -> RiverTrend.FALLING
        }
    }

    private fun instant(value: String): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { Instant.parse(value) }
            .getOrNull()

    internal fun clearCache() { cache = null }
}
