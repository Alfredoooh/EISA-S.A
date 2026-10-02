package com.appao

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar

object WeatherHelper {

    data class Prov(val n: String, val lat: Double, val lon: Double)

    private val provinces = listOf(
        Prov("Cabinda", -5.55, 12.19), Prov("Zaire", -6.13, 12.37),
        Prov("Uíge", -7.61, 15.06), Prov("Bengo", -8.58, 13.42),
        Prov("Luanda", -8.84, 13.23), Prov("Cuanza Norte", -9.30, 14.91),
        Prov("Cuanza Sul", -10.55, 14.95), Prov("Malanje", -9.54, 16.34),
        Prov("Lunda Norte", -7.38, 20.42), Prov("Lunda Sul", -10.83, 19.55),
        Prov("Moxico", -11.78, 19.91), Prov("Bié", -12.38, 17.55),
        Prov("Huambo", -12.77, 15.74), Prov("Benguela", -12.57, 13.40),
        Prov("Namibe", -15.19, 12.15), Prov("Huíla", -14.91, 13.49),
        Prov("Cunene", -16.10, 15.74)
    )

    data class Result(val icon: String, val temp: Int, val prov: String)

    private val client = OkHttpClient()

    fun nearest(lat: Double, lon: Double): Prov {
        var best = provinces[0]; var d = Double.MAX_VALUE
        for (p in provinces) {
            val dd = (p.lat - lat) * (p.lat - lat) + (p.lon - lon) * (p.lon - lon)
            if (dd < d) { d = dd; best = p }
        }
        return best
    }

    fun iconFor(code: Int, night: Boolean): String = when {
        code == 0 -> if (night) "night" else "sunny"
        code in 1..2 -> if (night) "partly_cloudly_night" else "partly_cloudly"
        code == 3 -> "cloudly"
        code == 45 || code == 48 -> "mist"
        (code in 51..67) || (code in 80..82) -> if (night) "rain_night" else "rain"
        (code in 71..77) || code == 85 || code == 86 -> if (night) "snow_night" else "snow"
        code in 95..99 -> if (night) "thunder_night" else "thunder"
        else -> "cloudly"
    }

    suspend fun fetch(ctx: Context, lat: Double, lon: Double): Result? = withContext(Dispatchers.IO) {
        try {
            val p = nearest(lat, lon)
            val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=Africa%2FLuanda"
            val req = Request.Builder().url(u).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return@withContext null }
            val cur = JSONObject(body).optJSONObject("current") ?: return@withContext null
            val t = Math.round(cur.optDouble("temperature_2m")).toInt()
            val code = cur.optInt("weather_code")
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val night = hour < 6 || hour >= 19
            Result(iconFor(code, night), t, p.n)
        } catch (e: Exception) { null }
    }
}
