package uk.co.tacklebox.app.services

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.*
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
    private val client=OkHttpClient.Builder().build()
    private fun <T> api(url:String,c:Class<T>):T=Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(c)
    val weather:WeatherApi=api("https://api.open-meteo.com/",WeatherApi::class.java)
    val marine:MarineApi=api("https://marine-api.open-meteo.com/",MarineApi::class.java)
    val river:RiverApi=api("https://environment.data.gov.uk/",RiverApi::class.java)
}

data class BiteWindow(val label:String,val start:LocalTime,val end:LocalTime,val major:Boolean)
data class SolunarDay(val sunrise:LocalTime,val sunset:LocalTime,val moonrise:LocalTime,val rating:Int,val moonPhase:String,val windows:List<BiteWindow>)
object Astronomy {
    fun calculate(date:LocalDate=LocalDate.now(), latitude:Double=52.5, longitude:Double=-1.5):SolunarDay {
        val n=date.dayOfYear; val lat=Math.toRadians(latitude)
        val decl=0.409*sin(2*PI*n/365-1.39); val hour=acos((-0.01454-sin(lat)*sin(decl))/(cos(lat)*cos(decl))).coerceIn(0.0,PI)
        val daylight=hour*24/PI; val noon=12.0-longitude/15.0
        fun time(h:Double)=LocalTime.of(((h%24+24)%24).toInt(),((((h%1)+1)%1)*60).toInt())
        val rise=time(noon-daylight/2); val set=time(noon+daylight/2)
        val age=((date.toEpochDay()+4)%29.53059+29.53059)%29.53059; val moonHour=(age/29.53059*24+6)%24
        val moonrise=time(moonHour); val opposite=time(moonHour+12); val phase=when { age<2->"New moon"; age<7.4->"Waxing crescent"; age<9.5->"First quarter"; age<14.8->"Waxing gibbous"; age<17->"Full moon"; age<22.1->"Waning gibbous"; age<24.3->"Last quarter"; else->"Waning crescent" }
        fun window(label:String,center:LocalTime,mins:Long,major:Boolean)=BiteWindow(label,center.minusMinutes(mins),center.plusMinutes(mins),major)
        val windows=listOf(window("Moon overhead",moonrise,60,true),window("Moon underfoot",opposite,60,true),window("Dawn",rise,35,false),window("Dusk",set,35,false)).sortedBy{it.start}
        val rating=(2 + if(age<2||abs(age-14.8)<2) 2 else 1 + if(daylight in 10.0..15.0) 1 else 0).coerceAtMost(5)
        return SolunarDay(rise,set,moonrise,rating,phase,windows)
    }
}
