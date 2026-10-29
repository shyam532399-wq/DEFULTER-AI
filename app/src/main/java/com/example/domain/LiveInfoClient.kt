package com.example.domain

import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LiveInfoResult(
    val success: Boolean,
    val spokenSummary: String,
    val boardTitle: String? = null,
    val boardCategory: String? = null,
    val boardMarkdown: String? = null
)

class LiveInfoClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(cityQuery: String): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val cleanCity = cityQuery.trim().ifBlank { "Mumbai" }
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
                .build()

            val geoJson = httpClient.newCall(geoReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "Weather server se connect nahi ho paya, Boss.")
                JSONObject(resp.body?.string().orEmpty())
            }

            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext LiveInfoResult(false, "Boss, '$cleanCity' location nahi mili.")
            }

            val first = results.getJSONObject(0)
            val name = first.optString("name", cleanCity)
            val country = first.optString("country", "")
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
            val wJson = httpClient.newCall(Request.Builder().url(weatherUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "Live mausam data nahi mila, Boss.")
                JSONObject(resp.body?.string().orEmpty())
            }

            val current = wJson.getJSONObject("current")
            val temp = current.optDouble("temperature_2m", 0.0)
            val feels = current.optDouble("apparent_temperature", temp)
            val humidity = current.optInt("relative_humidity_2m", 0)
            val wind = current.optDouble("wind_speed_10m", 0.0)
            val code = current.optInt("weather_code", 0)
            val desc = weatherCodeHinglish(code)

            LiveInfoResult(
                success = true,
                spokenSummary = "$name me abhi ${temp}°C hai ($desc), feels like ${feels}°C aur hawa ${wind} km/h hai, Boss.",
                boardTitle = "Live Weather — $name, $country",
                boardCategory = "note",
                boardMarkdown = buildString {
                    appendLine("# Mausam Update: $name ($country)")
                    appendLine("- **Temperature:** ${temp}°C (Feels like ${feels}°C)")
                    appendLine("- **Condition:** $desc")
                    appendLine("- **Humidity:** $humidity%")
                    appendLine("- **Wind Speed:** $wind km/h")
                }
            )
        } catch (e: Exception) {
            LiveInfoResult(false, "Boss, abhi live weather laane me network error aaya.")
        }
    }

    suspend fun fetchCryptoPrice(query: String): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val clean = query.trim().lowercase(Locale.ROOT).ifBlank { "bitcoin" }
            val coinId = when (clean) {
                "btc", "bitcoin" -> "bitcoin"
                "eth", "ethereum" -> "ethereum"
                "sol", "solana" -> "solana"
                "doge", "dogecoin" -> "dogecoin"
                "xrp", "ripple" -> "ripple"
                else -> {
                    val searchUrl = "https://api.coingecko.com/api/v3/search?query=${URLEncoder.encode(clean, "UTF-8")}"
                    httpClient.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val coins = if (resp.isSuccessful && body.isNotBlank()) {
                            JSONObject(body).optJSONArray("coins")
                        } else null
                        if (coins != null && coins.length() > 0) {
                            coins.getJSONObject(0).optString("id", clean)
                        } else clean
                    }
                }
            }

            val priceUrl = "https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=inr,usd&include_24hr_change=true"
            val pJson = httpClient.newCall(Request.Builder().url(priceUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "Market rate server busy hai, Boss.")
                JSONObject(resp.body?.string().orEmpty())
            }

            if (!pJson.has(coinId)) {
                return@withContext LiveInfoResult(false, "Boss, '$query' ka live crypto rate nahi mila.")
            }
            val data = pJson.getJSONObject(coinId)
            val inr = data.optDouble("inr", 0.0)
            val usd = data.optDouble("usd", 0.0)
            val chg = data.optDouble("inr_24h_change", 0.0)
            val chgFormatted = String.format(Locale.US, "%+.2f%%", chg)
            val titleCoin = coinId.replaceFirstChar { it.uppercase() }

            // Strict Section 5 Market Rule: Hinglish 2 lines, NO buy/sell or target advice, rest on Board.
            LiveInfoResult(
                success = true,
                spokenSummary = "$titleCoin abhi ₹${String.format(Locale.US, "%,.0f", inr)} ($${String.format(Locale.US, "%,.2f", usd)}) pe hai, 24h badlaav $chgFormatted raha. Poora snapshot Board pe rakh diya hai, Boss.",
                boardTitle = "$titleCoin Market Snapshot",
                boardCategory = "market",
                boardMarkdown = buildString {
                    appendLine("# $titleCoin Live Market Snapshot")
                    appendLine("- **INR Rate:** ₹${String.format(Locale.US, "%,.2f", inr)}")
                    appendLine("- **USD Rate:** $${String.format(Locale.US, "%,.2f", usd)}")
                    appendLine("- **24h Change:** $chgFormatted")
                    appendLine()
                    appendLine("_Note: Ye sirf live market data hai, koi buy/sell salah nahi._")
                }
            )
        } catch (_: Exception) {
            LiveInfoResult(false, "Boss, live crypto rate abhi fetch nahi ho paya.")
        }
    }

    suspend fun fetchForexRate(fromCurrency: String, toCurrency: String): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val base = fromCurrency.trim().uppercase(Locale.ROOT).ifBlank { "USD" }
            val target = toCurrency.trim().uppercase(Locale.ROOT).ifBlank { "INR" }
            val url = "https://api.frankfurter.dev/v1/latest?base=$base&symbols=$target"
            val json = httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "Forex rate nahi mil saka, Boss.")
                JSONObject(resp.body?.string().orEmpty())
            }
            val rates = json.optJSONObject("rates")
            val rate = rates?.optDouble(target, 0.0) ?: 0.0
            if (rate <= 0.0) {
                return@withContext LiveInfoResult(false, "Boss, $base se $target ka rate nahi mila.")
            }
            val date = json.optString("date", "latest")
            LiveInfoResult(
                success = true,
                spokenSummary = "1 $base abhi ${String.format(Locale.US, "%.2f", rate)} $target chal raha hai, Boss.",
                boardTitle = "Forex: $base/$target",
                boardCategory = "market",
                boardMarkdown = "# Forex Rate ($date)\n- **1 $base** = **${String.format(Locale.US, "%.4f", rate)} $target**\n- _Sirf reference data, koi trading salah nahi._"
            )
        } catch (_: Exception) {
            LiveInfoResult(false, "Forex network check fail hua, Boss.")
        }
    }

    suspend fun fetchFreshNews(): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val idsReq = Request.Builder()
                .url("https://hacker-news.firebaseio.com/v0/topstories.json")
                .build()
            val idsArr = httpClient.newCall(idsReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "News feed abhi reachable nahi hai, Boss.")
                JSONArray(resp.body?.string().orEmpty())
            }
            val headlines = mutableListOf<Pair<String, Boolean>>()
            val count = minOf(3, idsArr.length())
            val sevenDaysAgoEpoch = Instant.now().minus(7, ChronoUnit.DAYS).epochSecond

            for (i in 0 until count) {
                val id = idsArr.optInt(i)
                val itemUrl = "https://hacker-news.firebaseio.com/v0/item/$id.json"
                httpClient.newCall(Request.Builder().url(itemUrl).build()).execute().use { r ->
                    if (r.isSuccessful) {
                        val obj = JSONObject(r.body?.string().orEmpty())
                        val title = obj.optString("title", "")
                        val timeSec = obj.optLong("time", Instant.now().epochSecond)
                        val isOld = timeSec < sevenDaysAgoEpoch
                        if (title.isNotBlank()) {
                            headlines.add(title to isOld)
                        }
                    }
                }
            }
            if (headlines.isEmpty()) {
                return@withContext LiveInfoResult(false, "Abhi koi fresh khabar nahi mili, Boss.")
            }
            // Section 5 News Rule: Sirf 2-3 sabse fresh; >7 days old marked "purana"
            val spoken = headlines.take(2).joinToString(" | ") { (t, old) ->
                if (old) "(Purana) $t" else t
            }
            val boardMd = buildString {
                appendLine("# Aaj Ki Top Fresh Khabrein")
                headlines.forEachIndexed { idx, (t, old) ->
                    val tag = if (old) " _(Purana — 7+ din)_" else " _(Fresh)_"
                    appendLine("${idx + 1}. $t$tag")
                }
            }
            LiveInfoResult(
                success = true,
                spokenSummary = "Boss, top fresh khabrein: $spoken.",
                boardTitle = "Fresh Tech & Global News",
                boardCategory = "research",
                boardMarkdown = boardMd
            )
        } catch (_: Exception) {
            LiveInfoResult(false, "Khabrein fetch karne me network dikkat aayi, Boss.")
        }
    }

    private fun weatherCodeHinglish(code: Int): String = when (code) {
        0 -> "saaf aasmaan"
        1, 2, 3 -> "halke baadal"
        45, 48 -> "kohra / fog"
        51, 53, 55, 61, 63, 65 -> "baarish"
        71, 73, 75 -> "barafbaari"
        80, 81, 82 -> "tez baarish ke jhonke"
        95, 96, 99 -> "aandhi-toofan aur bijli"
        else -> "normal mausam"
    }
}
