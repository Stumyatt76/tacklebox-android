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

data class CurrentWeather(@SerializedName("temperature_2m") val temperature:Double?, @SerializedName("wind_speed_10m") val wind:Double?, @SerializedName("surface_pressure") val pressure:Double?, @SerializedName("wind_direction_10m") val windDirection:Double?)
data class WeatherResponse(val current:CurrentWeather?)
data class MarineHourly(val time:List<String> = emptyList(), @SerializedName("wave_height") val waveHeight:List<Double?> = emptyList(), @SerializedName("wave_period") val wavePeriod:List<Double?> = emptyList())
data class MarineResponse(val hourly:MarineHourly?)
interface WeatherApi { @GET("v1/forecast") suspend fun current(@Query("latitude") lat:Double,@Query("longitude") lon:Double,@Query("current") current:String="temperature_2m,wind_speed_10m,wind_direction_10m,surface_pressure"):WeatherResponse }
interface MarineApi { @GET("v1/marine") suspend fun forecast(@Query("latitude") lat:Double,@Query("longitude") lon:Double,@Query("hourly") hourly:String="wave_height,wave_period",@Query("forecast_days") days:Int=2):MarineResponse }
data class RiverItems(val items:List<RiverReading> = emptyList())
data class RiverReading(val dateTime:String?=null,val value:Double?=null,val measure:String?=null)
interface RiverApi { @GET("flood-monitoring/id/readings") suspend fun readings(@Query("lat") lat:Double,@Query("long") lon:Double,@Query("dist") distance:Int=20,@Query("_limit") limit:Int=20,@Query("latest") latest:String=""):RiverItems }

object Services {
    // Explicit timeouts: the bare client had none, so a captive portal or a stalled gauge left the Rivers and Tides
    // screens spinning indefinitely with no way back but killing the app.
    private val client=OkHttpClient.Builder()
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .build()
    private fun <T> api(url:String,c:Class<T>):T=Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(c)
    val weather:WeatherApi=api("https://api.open-meteo.com/",WeatherApi::class.java)
    val marine:MarineApi=api("https://marine-api.open-meteo.com/",MarineApi::class.java)
    val river:RiverApi=api("https://environment.data.gov.uk/",RiverApi::class.java)
    val vision:VisionApi=api("https://api.inaturalist.org/",VisionApi::class.java)
}

// --- Species identification (iNaturalist computer vision) -------------------------------------------------------
// The Log screen offered an "Identify from photo" button whose onClick was empty and had no client behind it at all
// (TB-A-07). This mirrors the iOS SpeciesIDService: multipart upload, bearer token, top suggestions by score.
data class VisionTaxon(val name:String?=null, @SerializedName("preferred_common_name") val commonName:String?=null)
data class VisionScore(@SerializedName("combined_score") val score:Double?=null, val taxon:VisionTaxon?=null)
data class VisionResult(val results:List<VisionScore> = emptyList())
data class SpeciesSuggestion(val scientificName:String, val commonName:String?, val score:Double)

interface VisionApi {
    @Multipart @POST("v1/computervision/score_image")
    suspend fun score(@Header("Authorization") authorization:String, @Part image:MultipartBody.Part):VisionResult
    @GET("v1/users/me") suspend fun me(@Header("Authorization") authorization:String):ResponseBody
}

class SpeciesIdException(message:String):Exception(message)

object SpeciesId {
    // The iNaturalist token is a short-lived JWT (about 24 hours), so an expired token is the common case, not an
    // edge case. Say so plainly rather than surfacing a raw HTTP error.
    private const val EXPIRED="That species-ID token has expired. iNaturalist tokens last about a day — paste a fresh one in Settings."

    suspend fun identify(bytes:ByteArray, token:String):List<SpeciesSuggestion> {
        if (token.isBlank()) throw SpeciesIdException("Add an iNaturalist token in Settings to identify from a photo.")
        val part = MultipartBody.Part.createFormData("image","catch.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        val result = try { Services.vision.score(bearer(token), part) }
        catch (e:HttpException) { throw SpeciesIdException(if (e.code()==401||e.code()==403) EXPIRED else "Couldn’t reach the identification service. Try again.") }
        catch (e:Exception) { throw SpeciesIdException("Couldn’t reach the identification service. Try again.") }
        return result.results.mapNotNull { r ->
            val name = r.taxon?.name ?: return@mapNotNull null
            SpeciesSuggestion(name, r.taxon.commonName, r.score ?: 0.0)
        }.sortedByDescending { it.score }.take(5)
    }

    suspend fun validate(token:String):Boolean = try { Services.vision.me(bearer(token)); true } catch (_:Exception) { false }

    private fun bearer(token:String) = if (token.startsWith("Bearer ",ignoreCase=true)) token else "Bearer $token"
}
