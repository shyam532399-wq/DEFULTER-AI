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

data class HomeWeatherSnapshot(
    val isLoaded: Boolean = false,
    val tempCelsius: Int = 0,
    val conditionLabel: String = "No data",
    val humidityPercent: Int = 0,
    val locationName: String = ""
)

class LiveInfoClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private fun jsonRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "MayaPhoneAgent/1.0 (Android)")
        .header("Accept", "application/json")
        .build()

    suspend fun fetchWeather(cityQuery: String): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val cleanCity = cityQuery.trim().ifBlank { "Mumbai" }
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val geoReq = jsonRequest("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")

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
            val wJson = httpClient.newCall(jsonRequest(weatherUrl)).execute().use { resp ->
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
        } catch (_: Exception) {
            LiveInfoResult(false, "Boss, abhi live weather laane me network error aaya.")
        }
    }

    /**
     * Section 3.7 & Section 5 Stocks Rule:
     * Fetches live NSE/BSE & global stock/index quotes via Yahoo Finance public chart API.
     * Strictly 2 Hinglish lines spoken, full snapshot on Board, ZERO buy/sell/target advice.
     */
    suspend fun fetchStockQuote(query: String): LiveInfoResult = withContext(Dispatchers.IO) {
        try {
            val raw = query.trim().ifBlank { "RELIANCE.NS" }
            val resolvedSymbol = resolveStockTicker(raw)
            val encodedSym = URLEncoder.encode(resolvedSymbol, "UTF-8")
            val chartUrl = "https://query1.finance.yahoo.com/v8/finance/chart/$encodedSym?interval=1d&range=5d"

            val root = httpClient.newCall(jsonRequest(chartUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "Boss, '$raw' ka stock data abhi fetch nahi ho saka.")
                JSONObject(resp.body?.string().orEmpty())
            }

            val resultArr = root.optJSONObject("chart")?.optJSONArray("result")
            if (resultArr == null || resultArr.length() == 0) {
                return@withContext LiveInfoResult(false, "Boss, '$raw' ticker market feed me nahi mila.")
            }

            val meta = resultArr.getJSONObject(0).getJSONObject("meta")
            val symbol = meta.optString("symbol", resolvedSymbol)
            val shortName = meta.optString("shortName", symbol).ifBlank { symbol }
            val currency = meta.optString("currency", "INR")
            val price = meta.optDouble("regularMarketPrice", 0.0)
            val prevClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
            val dayHigh = meta.optDouble("regularMarketDayHigh", price)
            val dayLow = meta.optDouble("regularMarketDayLow", price)

            if (price <= 0.0) {
                return@withContext LiveInfoResult(false, "Boss, '$symbol' ka live rate abhi uplabdh nahi hai.")
            }

            val diff = price - prevClose
            val pct = if (prevClose > 0.0) (diff / prevClose) * 100.0 else 0.0
            val curPrefix = if (currency.equals("INR", ignoreCase = true)) "₹" else "$currency "
            val pctStr = String.format(Locale.US, "%+.2f%%", pct)
            val priceStr = String.format(Locale.US, "%,.2f", price)

            LiveInfoResult(
                success = true,
                spokenSummary = "$shortName ($symbol) abhi $curPrefix$priceStr pe hai ($pctStr badlaav). Poora snapshot Board pe rakh diya hai, Boss.",
                boardTitle = "Stock Snapshot — $shortName ($symbol)",
                boardCategory = "market",
                boardMarkdown = buildString {
                    appendLine("# $shortName ($symbol) — Live Quote")
                    appendLine("- **Current Price:** $curPrefix$priceStr")
                    appendLine("- **Previous Close:** $curPrefix${String.format(Locale.US, "%,.2f", prevClose)}")
                    appendLine("- **Change:** ${String.format(Locale.US, "%+.2f", diff)} ($pctStr)")
                    appendLine("- **Day Range:** $curPrefix${String.format(Locale.US, "%,.2f", dayLow)} – $curPrefix${String.format(Locale.US, "%,.2f", dayHigh)}")
                    appendLine()
                    appendLine("_Note: Ye sirf live market data hai — koi kharidne/bechne ya target price ki salah nahi._")
                }
            )
        } catch (_: Exception) {
            LiveInfoResult(false, "Boss, stock rate laane me network dikkat aayi.")
        }
    }

    private fun resolveStockTicker(raw: String): String {
        val upper = raw.trim().uppercase(Locale.ROOT)
        val shortcuts = mapOf(
            "RELIANCE" to "RELIANCE.NS",
            "TCS" to "TCS.NS",
            "INFY" to "INFY.NS",
            "INFOSYS" to "INFY.NS",
            "HDFC" to "HDFCBANK.NS",
            "HDFCBANK" to "HDFCBANK.NS",
            "HDFC BANK" to "HDFCBANK.NS",
            "ICICI" to "ICICIBANK.NS",
            "SBIN" to "SBIN.NS",
            "SBI" to "SBIN.NS",
            "TATAMOTORS" to "TATAMOTORS.NS",
            "TATA MOTORS" to "TATAMOTORS.NS",
            "ZOMATO" to "ETERNAL.NS",
            "NIFTY" to "^NSEI",
            "NIFTY 50" to "^NSEI",
            "SENSEX" to "^BSESN"
        )
        shortcuts[upper]?.let { return it }
        if (upper.contains(".") || upper.startsWith("^")) return upper

        return try {
            val searchUrl = "https://query2.finance.yahoo.com/v1/finance/search?q=${URLEncoder.encode(raw, "UTF-8")}&quotesCount=3&newsCount=0"
            httpClient.newCall(jsonRequest(searchUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return upper
                val quotes = JSONObject(resp.body?.string().orEmpty()).optJSONArray("quotes")
                if (quotes != null && quotes.length() > 0) {
                    quotes.getJSONObject(0).optString("symbol", upper)
                } else upper
            }
        } catch (_: Exception) {
            upper
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
                    httpClient.newCall(jsonRequest(searchUrl)).execute().use { resp ->
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
            val pJson = httpClient.newCall(jsonRequest(priceUrl)).execute().use { resp ->
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
            val json = httpClient.newCall(jsonRequest(url)).execute().use { resp ->
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
            val idsArr = httpClient.newCall(jsonRequest("https://hacker-news.firebaseio.com/v0/topstories.json")).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "News feed abhi reachable nahi hai, Boss.")
                JSONArray(resp.body?.string().orEmpty())
            }
            val headlines = mutableListOf<Pair<String, Boolean>>()
            val count = minOf(3, idsArr.length())
            val sevenDaysAgoEpoch = Instant.now().minus(7, ChronoUnit.DAYS).epochSecond

            for (i in 0 until count) {
                val id = idsArr.optInt(i)
                val itemUrl = "https://hacker-news.firebaseio.com/v0/item/$id.json"
                httpClient.newCall(jsonRequest(itemUrl)).execute().use { r ->
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

    /**
     * Section 3.6 Live Web Search (`search_web`) using DuckDuckGo Instant Answer + Wikipedia REST API.
     */
    suspend fun searchWebLive(query: String): LiveInfoResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext LiveInfoResult(false, "Search topic batayein, Boss.")
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            var abstractText = ""
            var heading = clean
            val relatedBullets = mutableListOf<String>()

            httpClient.newCall(jsonRequest(ddgUrl)).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    abstractText = obj.optString("AbstractText", "").trim()
                    heading = obj.optString("Heading", clean).ifBlank { clean }
                    val related = obj.optJSONArray("RelatedTopics")
                    if (related != null) {
                        for (i in 0 until minOf(4, related.length())) {
                            val item = related.optJSONObject(i)
                            val txt = item?.optString("Text", "").orEmpty()
                            if (txt.isNotBlank()) relatedBullets.add(txt)
                        }
                    }
                }
            }

            if (abstractText.isBlank()) {
                val wikiSearchUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=1&namespace=0&format=json"
                var bestTitle = ""
                httpClient.newCall(jsonRequest(wikiSearchUrl)).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string().orEmpty())
                        val titles = arr.optJSONArray(1)
                        bestTitle = titles?.optString(0, "").orEmpty()
                    }
                }
                if (bestTitle.isNotBlank()) {
                    val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(bestTitle.replace(" ", "_"), "UTF-8")}"
                    httpClient.newCall(jsonRequest(summaryUrl)).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val sObj = JSONObject(resp.body?.string().orEmpty())
                            heading = sObj.optString("title", bestTitle)
                            abstractText = sObj.optString("extract", "").trim()
                        }
                    }
                }
            }

            if (abstractText.isBlank() && relatedBullets.isEmpty()) {
                return@withContext LiveInfoResult(false, "Boss, '$clean' par seedha web abstract nahi mila.")
            }

            val firstTwoSentences = (abstractText.ifBlank { relatedBullets.first() })
                .split(Regex("(?<=[.!?])\\s+"))
                .take(2)
                .joinToString(" ")
                .take(240)

            val md = buildString {
                appendLine("# Web Lookup: $heading")
                if (abstractText.isNotBlank()) {
                    appendLine(abstractText)
                    appendLine()
                }
                if (relatedBullets.isNotEmpty()) {
                    appendLine("## Key Points")
                    relatedBullets.forEach { appendLine("- $it") }
                }
            }

            LiveInfoResult(
                success = true,
                spokenSummary = "$firstTwoSentences Poora note Board pe rakh diya hai, Boss.",
                boardTitle = "Web Search — $heading",
                boardCategory = "research",
                boardMarkdown = md
            )
        } catch (_: Exception) {
            LiveInfoResult(false, "Web search me network dikkat aayi, Boss.")
        }
    }

    /**
     * Section 3.6 `read_url` — Fetches webpage HTML and extracts untrusted plain text safely.
     */
    suspend fun fetchUrlCleanText(rawUrl: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl.trim()
            } else {
                "https://${rawUrl.trim()}"
            }
            val req = Request.Builder()
                .url(normalized)
                .header("User-Agent", "Mozilla/5.0 (Android; MayaPhoneAgent/1.0)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val html = resp.body?.string().orEmpty()
                val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val cleanText = html
                    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(6_000)
                if (cleanText.isBlank()) error("Page me readable text nahi mila.")
                (titleMatch.ifBlank { normalized }) to cleanText
            }
        }
    }

    /**
     * Section 3.10 Coding & Dev — Real GitHub REST API (`github_repo_overview`, `github_list_repos`, `github_actions_status`).
     */
    suspend fun fetchGitHubInfo(query: String): LiveInfoResult = withContext(Dispatchers.IO) {
        val clean = query.trim().removePrefix("https://github.com/").trim('/')
        if (clean.isBlank()) return@withContext LiveInfoResult(false, "GitHub username ya owner/repo batayein, Boss.")

        try {
            if (clean.contains("/") && !clean.contains(" ")) {
                val parts = clean.split("/")
                val owner = parts[0]
                val repo = parts[1]
                val repoReq = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo")
                    .header("User-Agent", "MayaPhoneAgent/1.0")
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val repoObj = httpClient.newCall(repoReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "GitHub pe '$owner/$repo' public repo nahi mila, Boss.")
                    JSONObject(resp.body?.string().orEmpty())
                }

                val stars = repoObj.optInt("stargazers_count", 0)
                val issues = repoObj.optInt("open_issues_count", 0)
                val lang = repoObj.optString("language", "Code")
                val desc = repoObj.optString("description", "")

                var latestRunStatus = "No recent workflow runs"
                val runsReq = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=3")
                    .header("User-Agent", "MayaPhoneAgent/1.0")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                httpClient.newCall(runsReq).execute().use { r ->
                    if (r.isSuccessful) {
                        val runs = JSONObject(r.body?.string().orEmpty()).optJSONArray("workflow_runs")
                        if (runs != null && runs.length() > 0) {
                            val top = runs.getJSONObject(0)
                            val name = top.optString("name", "Workflow")
                            val status = top.optString("status", "")
                            val conclusion = top.optString("conclusion", "").ifBlank { status }
                            latestRunStatus = "$name: $conclusion"
                        }
                    }
                }

                val md = buildString {
                    appendLine("# GitHub Repo: $owner/$repo")
                    if (desc.isNotBlank()) appendLine("> $desc\n")
                    appendLine("- **Language:** $lang")
                    appendLine("- **Stars:** $stars | **Open Issues/PRs:** $issues")
                    appendLine("- **Latest GitHub Actions:** $latestRunStatus")
                }

                return@withContext LiveInfoResult(
                    success = true,
                    spokenSummary = "$owner/$repo ($lang) me $stars stars, $issues open issues hain, aur latest Action '$latestRunStatus' hai. Overview Board pe hai, Boss.",
                    boardTitle = "GitHub — $owner/$repo",
                    boardCategory = "research",
                    boardMarkdown = md
                )
            } else {
                // User repos or keyword search
                val singleToken = !clean.contains(" ")
                val url = if (singleToken) {
                    "https://api.github.com/users/${URLEncoder.encode(clean, "UTF-8")}/repos?sort=updated&per_page=5"
                } else {
                    "https://api.github.com/search/repositories?q=${URLEncoder.encode(clean, "UTF-8")}&per_page=5"
                }
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MayaPhoneAgent/1.0")
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val reposList = mutableListOf<String>()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext LiveInfoResult(false, "GitHub se '$clean' ki jankari nahi mili, Boss.")
                    val body = resp.body?.string().orEmpty()
                    val arr = if (singleToken) JSONArray(body) else JSONObject(body).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until minOf(5, arr.length())) {
                        val item = arr.getJSONObject(i)
                        val fullName = item.optString("full_name", "")
                        val st = item.optInt("stargazers_count", 0)
                        if (fullName.isNotBlank()) reposList.add("$fullName (★$st)")
                    }
                }

                if (reposList.isEmpty()) {
                    return@withContext LiveInfoResult(false, "GitHub pe '$clean' ke liye koi public repo nahi mila, Boss.")
                }

                return@withContext LiveInfoResult(
                    success = true,
                    spokenSummary = "GitHub pe top repos mile: ${reposList.take(2).joinToString(", ")}. Poori list Board pe rakh di hai, Boss.",
                    boardTitle = "GitHub Repos — $clean",
                    boardCategory = "research",
                    boardMarkdown = "# GitHub Repositories ($clean)\n" + reposList.joinToString("\n") { "- **$it**" }
                )
            }
        } catch (_: Exception) {
            LiveInfoResult(false, "GitHub API call me network error aaya, Boss.")
        }
    }

    suspend fun fetchHomeWeatherCard(
        latitude: Double? = null,
        longitude: Double? = null,
        fallbackCity: String = "New Delhi"
    ): HomeWeatherSnapshot = withContext(Dispatchers.IO) {
        try {
            var resolvedLat = latitude
            var resolvedLon = longitude
            var resolvedCity = fallbackCity

            if (resolvedLat == null || resolvedLon == null) {
                val encoded = URLEncoder.encode(fallbackCity.trim().ifBlank { "New Delhi" }, "UTF-8")
                val geoReq = jsonRequest(
                    "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
                )
                httpClient.newCall(geoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val results = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            resolvedLat = first.optDouble("latitude", 28.6139)
                            resolvedLon = first.optDouble("longitude", 77.2090)
                            resolvedCity = first.optString("name", fallbackCity)
                        }
                    }
                }
            }

            val lat = resolvedLat ?: 28.6139
            val lon = resolvedLon ?: 77.2090
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code"

            val wJson = httpClient.newCall(jsonRequest(weatherUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext HomeWeatherSnapshot()
                JSONObject(resp.body?.string().orEmpty())
            }

            val current = wJson.optJSONObject("current") ?: return@withContext HomeWeatherSnapshot()
            val tempRounded = kotlin.math.round(current.optDouble("temperature_2m", 0.0)).toInt()
            val humidity = current.optInt("relative_humidity_2m", 0).coerceIn(0, 100)
            val code = current.optInt("weather_code", 0)

            HomeWeatherSnapshot(
                isLoaded = true,
                tempCelsius = tempRounded,
                conditionLabel = weatherCodeEnglishShort(code),
                humidityPercent = humidity,
                locationName = resolvedCity
            )
        } catch (_: Exception) {
            HomeWeatherSnapshot()
        }
    }

    private fun weatherCodeEnglishShort(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Clear"
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
