/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class CurrentWeather(@SerializedName("temperature_2m") val temperature:Double?, @SerializedName("wind_speed_10m") val wind:Double?, @SerializedName("surface_pressure") val pressure:Double?, @SerializedName("wind_direction_10m") val windDirection:Double?, val time:String?=null)
data class HourlyWeather(val time:List<String> = emptyList(), @SerializedName("surface_pressure") val pressure:List<Double?> = emptyList())
data class WeatherResponse(val current:CurrentWeather?, val hourly:HourlyWeather?)
data class MarineHourly(val time:List<String> = emptyList(), @SerializedName("wave_height") val waveHeight:List<Double?> = emptyList(), @SerializedName("wave_period") val wavePeriod:List<Double?> = emptyList(), @SerializedName("wave_direction") val waveDirection:List<Double?> = emptyList(), @SerializedName("sea_surface_temperature") val seaTemperature:List<Double?> = emptyList())
data class MarineResponse(val hourly:MarineHourly?)
interface WeatherApi { @GET("v1/forecast") suspend fun current(@Query("latitude") lat:Double,@Query("longitude") lon:Double,@Query("current") current:String="temperature_2m,wind_speed_10m,wind_direction_10m,surface_pressure",@Query("hourly") hourly:String="surface_pressure",@Query("timezone") timezone:String="auto"):WeatherResponse }
interface MarineApi { @GET("v1/marine") suspend fun forecast(@Query("latitude") lat:Double,@Query("longitude") lon:Double,@Query("hourly") hourly:String="wave_height,wave_direction,wave_period,sea_surface_temperature",@Query("forecast_days") days:Int=2,@Query("timezone") timezone:String="auto"):MarineResponse }

object Services {
    // Explicit timeouts: the bare client had none, so a captive portal or a stalled gauge left the Rivers and Tides
    // screens spinning indefinitely with no way back but killing the app.
    val client=OkHttpClient.Builder()
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .build()
    private fun <T> api(url:String,c:Class<T>):T=Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(c)
    val weather:WeatherApi=api("https://api.open-meteo.com/",WeatherApi::class.java)
    val marine:MarineApi=api("https://marine-api.open-meteo.com/",MarineApi::class.java)
    val vision:VisionApi=api("https://api.inaturalist.org/",VisionApi::class.java)
    // Tides call two different hosts (NOAA and WorldTides), so every request passes a full @Url and this base is
    // only here because Retrofit insists on one.
    val tide:TideApi=api("https://api.tidesandcurrents.noaa.gov/",TideApi::class.java)
    val taxa:TaxaApi=api("https://api.inaturalist.org/",TaxaApi::class.java)
    val riverStations:RiverStationsApi=api("https://environment.data.gov.uk/",RiverStationsApi::class.java)
}

// --- Species identification (iNaturalist computer vision) -------------------------------------------------------
// The Log screen offered an "Identify from photo" button whose onClick was empty and had no client behind it at all
// (TB-A-07). This mirrors the iOS SpeciesIDService: multipart upload, bearer token, top suggestions by score.
data class VisionPhoto(@SerializedName("square_url") val squareUrl:String?=null)
data class VisionTaxon(val name:String?=null, @SerializedName("preferred_common_name") val commonName:String?=null, @SerializedName("default_photo") val defaultPhoto:VisionPhoto?=null)
data class VisionScore(@SerializedName("combined_score") val score:Double?=null, @SerializedName("score") val visionScore:Double?=null, val taxon:VisionTaxon?=null)
data class VisionResult(val results:List<VisionScore> = emptyList())
data class SpeciesSuggestion(val scientificName:String, val commonName:String?, val score:Double, val thumbnailUrl:String?=null) {
    val displayName:String get() = commonName ?: scientificName
}

interface VisionApi {
    @Multipart @POST("v1/computervision/score_image")
    suspend fun score(@Header("Authorization") authorization:String, @Part image:MultipartBody.Part, @Part("lat") lat:okhttp3.RequestBody?, @Part("lng") lng:okhttp3.RequestBody?):VisionResult
    @GET("v1/users/me") suspend fun me(@Header("Authorization") authorization:String):ResponseBody
}

class SpeciesIdException(message:String):Exception(message)

/** The five outcomes iOS's `SpeciesIDError` names, with its strings. */
object SpeciesId {
    const val NOT_CONFIGURED="Species identification is not set up yet. Connect iNaturalist in Settings."
    const val UNAUTHORIZED="Please reconnect iNaturalist in Settings. If sign-in succeeds but photo identification is unavailable, provider access may be required."
    const val OFFLINE="Identification is unavailable offline. Try again when you're connected."
    const val NO_RESULTS="iNaturalist couldn't identify this photo. Try a clear side-on photo."
    const val UNREACHABLE="Couldn't reach iNaturalist. Check your connection and try again."

    /** Sends the cover photo and, when known, the rounded position — iNaturalist ranks by range as well as by looks. */
    suspend fun identify(bytes:ByteArray, token:String, latitude:Double?=null, longitude:Double?=null):List<SpeciesSuggestion> {
        if (token.isBlank()) throw SpeciesIdException(NOT_CONFIGURED)
        val part = MultipartBody.Part.createFormData("image","catch.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        fun field(value:Double?) = value?.let { "%.2f".format(java.util.Locale.US, it).toRequestBody("text/plain".toMediaType()) }
        val result = try { Services.vision.score(bearer(token), part, field(latitude), field(longitude)) }
        catch (e:kotlinx.coroutines.CancellationException) { throw e }
        catch (e:Exception) { throw SpeciesIdException(describe(e)) }
        val suggestions = suggestionsFrom(result)
        if (suggestions.isEmpty()) throw SpeciesIdException(NO_RESULTS)
        return suggestions
    }

    /** Which iOS message a failed request maps to: 401/403 → reconnect, no network → offline, anything else → unreachable. */
    fun describe(e:Throwable):String = when {
        e is HttpException && (e.code()==401 || e.code()==403) -> UNAUTHORIZED
        e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.NoRouteToHostException -> OFFLINE
        else -> UNREACHABLE
    }

    /** The top five by score, each with its name, common name and square thumbnail. Scores are clamped to 0–100. */
    fun suggestionsFrom(result:VisionResult):List<SpeciesSuggestion> = result.results.mapNotNull { r ->
        val name = r.taxon?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val raw = r.score ?: r.visionScore ?: return@mapNotNull null
        SpeciesSuggestion(name, r.taxon.commonName, raw.coerceIn(0.0, 100.0), r.taxon.defaultPhoto?.squareUrl)
    }.sortedByDescending { it.score }.take(5)

    /** `GET /v1/users/me` with the token: 200 connected, 401/403 invalid, anything else unreachable — as iOS tests it. */
    suspend fun validateToken(token:String):uk.co.tacklebox.app.DataServiceTestResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url("https://api.inaturalist.org/v1/users/me").header("Authorization", bearer(token)).build()
            Services.client.newCall(request).execute().use { classifyINaturalist(it.code) }
        } catch (e:kotlinx.coroutines.CancellationException) { throw e }
        catch (_:Exception) { uk.co.tacklebox.app.DataServiceTestResult.Unreachable(INATURALIST_UNREACHABLE) }
    }
    const val INATURALIST_UNREACHABLE="Couldn't reach iNaturalist. Try again later."
    fun classifyINaturalist(code:Int):uk.co.tacklebox.app.DataServiceTestResult = when (code) {
        200 -> uk.co.tacklebox.app.DataServiceTestResult.Connected
        401, 403 -> uk.co.tacklebox.app.DataServiceTestResult.Invalid
        else -> uk.co.tacklebox.app.DataServiceTestResult.Unreachable(INATURALIST_UNREACHABLE)
    }

    private fun bearer(token:String) = if (token.startsWith("Bearer ",ignoreCase=true)) token else "Bearer $token"
}

/**
 * Which way the barometer is going, ported from the iOS `ConditionsService` (feature parity, 2026-09-08).
 *
 * Android had a `pressureTrend` column and an importer that filled it, but nothing that ever computed one — so the
 * field showed only on journals imported from iOS. It needs the hourly pressure series, which is why the weather
 * request now asks for it.
 *
 * Three hours back is the window iOS uses, and one hectopascal the threshold. Anglers read a falling glass as the
 * fish coming on, so the wording matters more than the precision.
 */
object PressureTrend {
    fun of(current: Double?, hourly: HourlyWeather?, now: String?): String {
        if (current == null || hourly == null || hourly.time.isEmpty()) return "Steady"
        val currentIndex = now?.let { t -> hourly.time.indexOfLast { it <= t } }?.takeIf { it >= 0 }
            ?: hourly.time.lastIndex
        val earlierIndex = maxOf(0, currentIndex - 3)
        val earlier = hourly.pressure.getOrNull(earlierIndex) ?: return "Steady"
        val change = current - earlier
        return when {
            change >= 1 -> "Rising"
            change <= -1 -> "Falling"
            else -> "Steady"
        }
    }
}

/**
 * Where "now" falls in Open-Meteo's hourly series. The series starts at local midnight, so taking the first eight
 * entries showed this morning's sea state all afternoon.
 */
object MarineHours {
    fun firstFromNow(times: List<String>, now: LocalDateTime = LocalDateTime.now()): Int {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        val index = times.indexOfFirst { t -> runCatching { !LocalDateTime.parse(t).isBefore(hour) }.getOrDefault(false) }
        return if (index < 0) 0 else index
    }
}
