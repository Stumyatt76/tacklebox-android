package uk.co.tacklebox.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.time.*
import java.util.concurrent.TimeUnit

data class TripForecastDay(val date:String,val low:Double?,val high:Double?,val wind:Double?,val rainChance:Double?)
data class TripForecast(val fetched:Long,val days:List<TripForecastDay>,val cached:Boolean=false)
object TripForecastService {
    private val client=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
    fun parse(bytes:ByteArray,now:Long=System.currentTimeMillis()):TripForecast {
        val daily=JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject["daily"].asJsonObject
        val dates=daily["time"].asJsonArray
        val keys=listOf("temperature_2m_min","temperature_2m_max","wind_speed_10m_max","precipitation_probability_max")
        require(dates.size() in 1..16 && keys.all { daily[it].asJsonArray.size()==dates.size() }) { "The forecast response is incomplete." }
        fun value(key:String,i:Int):Double?=daily[key].asJsonArray[i].takeUnless { it.isJsonNull }?.asDouble
        return TripForecast(now,dates.mapIndexed { i,date->TripForecastDay(LocalDate.parse(date.asString).toString(),value(keys[0],i),value(keys[1],i),value(keys[2],i),value(keys[3],i)) })
    }
    fun fetch(context:Context,lat:Double,lon:Double,zone:ZoneId):TripForecast {
        val url="https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder().addQueryParameter("latitude",lat.toString()).addQueryParameter("longitude",lon.toString()).addQueryParameter("timezone",zone.id).addQueryParameter("forecast_days","7").addQueryParameter("daily","temperature_2m_min,temperature_2m_max,wind_speed_10m_max,precipitation_probability_max").build()
        val directory=File(context.cacheDir,"trip-forecasts").apply { mkdirs() }
        val file=File(directory,PhotoBackupFormat.digest(url.toString().toByteArray())+".json")
        try {
            val result=client.newCall(Request.Builder().url(url).build()).execute().use { response->
                check(response.isSuccessful) { "The forecast service is unavailable." }
                val bytes=response.body?.byteStream()?.use { stream->
                    val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(true){val n=stream.read(chunk);if(n<0)break;require(buffer.size()+n<1_000_000);buffer.write(chunk,0,n)}
                    buffer.toByteArray()
                } ?: error("The forecast response is empty.")
                parse(bytes)
            }
            runCatching { file.writeText(Gson().toJson(result)) };return result
        } catch(error:Exception) {
            val old=runCatching { Gson().fromJson(file.readText(),TripForecast::class.java) }.getOrNull()
            if(old!=null && System.currentTimeMillis()-old.fetched in 0 until 24*3600*1000)return old.copy(cached=true)
            throw error
        }
    }
}
object TripHistory {
    fun matchingMonth(dates:List<Instant>,date:LocalDate,zone:ZoneId=ZoneId.systemDefault())=dates.count { it.atZone(zone).month==date.month }
}
