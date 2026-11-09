package com.deva.moneymanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// One search result, and one price
data class Hit(val symbol: String, val name: String, val extra: String)
data class Quote(val price: Double, val date: String, val currency: String?)

// True while prices are being refreshed
var refreshingPrices by mutableStateOf(false)

// Live prices.
// Mutual funds: mfapi.in, a free public service that republishes the official AMFI daily NAV.
// Stocks: Yahoo Finance's public web feed. It is free but unofficial and can change or stop,
// so every call fails safely and the app keeps your last value.
object Prices {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // null means the service could not be reached
    suspend fun searchFunds(q: String): List<Hit>? {
        val body = get("https://api.mfapi.in/mf/search?q=${enc(q)}") ?: return null
        return try {
            val a = JSONArray(body)
            (0 until minOf(a.length(), 40)).map {
                val o = a.getJSONObject(it)
                Hit(o.get("schemeCode").toString(), o.getString("schemeName"), "Scheme code " + o.get("schemeCode"))
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchStocks(q: String): List<Hit>? {
        val body = get("https://query1.finance.yahoo.com/v1/finance/search?q=${enc(q)}&quotesCount=15&newsCount=0") ?: return null
        return try {
            val a = JSONObject(body).optJSONArray("quotes") ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("quoteType") in listOf("EQUITY", "ETF") && it.has("symbol") }
                .map {
                    val sym = it.getString("symbol")
                    val nm = it.optString("longname").ifBlank { it.optString("shortname").ifBlank { sym } }
                    Hit(sym, nm, sym + " · " + it.optString("exchDisp").ifBlank { it.optString("exchange") })
                }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun latest(source: String, symbol: String): Quote? = when (source) {
        "mf" -> mfNav(symbol)
        "yahoo" -> yahooPrice(symbol)
        else -> null
    }

    private suspend fun mfNav(code: String): Quote? {
        val body = get("https://api.mfapi.in/mf/${enc(code)}/latest") ?: return null
        return try {
            val d = JSONObject(body).getJSONArray("data").getJSONObject(0)
            val raw = d.getString("date")
            val date = runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd-MM-yyyy")).toString() }.getOrDefault(raw)
            Quote(d.getString("nav").toDouble(), date, "INR")
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun yahooPrice(symbol: String): Quote? {
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/${enc(symbol)}?range=1d&interval=1d") ?: return null
        return try {
            val m = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            Quote(m.getDouble("regularMarketPrice"), LocalDate.now().toString(), m.optString("currency").ifBlank { null })
        } catch (e: Exception) {
            null
        }
    }
}
