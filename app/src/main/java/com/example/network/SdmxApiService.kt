package com.example.network

import android.util.Log
import com.example.data.UserModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SdmxApiService {
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = "sdmx.vip" // force save to sdmx.vip
            val list = cookieStore[host] ?: mutableListOf()
            cookies.forEach { cookie ->
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
            cookieStore[host] = list
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore["sdmx.vip"] ?: emptyList()
        }
        
        fun getCookieString(): String {
            return cookieStore["sdmx.vip"]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .build()

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("referrer", "")
            .add("username", user)
            .add("password", pass)
            .add("login", "")
            .build()
        
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/login")
            .post(formBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36")
            .addHeader("Origin", "https://sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/login")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun createLine(username: String, pass: String, expDate: String, adultos: Boolean): Boolean = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
            .add("action", "line")
            .add("trial", "1")
            .add("bouquets_selected", "")
            .add("username", username)
            .add("password", pass)
            .add("package", "150")
            .add("package_cost", "0")
            .add("package_duration", "24 hours")
            .add("max_connections", "2")
            .add("exp_date", "$expDate 00:00")
            .add("contact", "")
            .add("reseller_notes", "")
            .add("isp_clear", "")
            .add("bouquets_selected[]", "19")
            .add("bouquets_selected[]", "24")
            .add("bouquets_selected[]", "21")
            .add("bouquets_selected[]", "8")
            .add("bouquets_selected[]", "23")
            
        if (adultos) {
            formBuilder.add("bouquets_selected[]", "96")
        }
        
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/post.php?action=line")
            .post(formBuilder.build())
            .addHeader("Accept", "*/*")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Origin", "https://sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/line?trial=1")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun deleteLine(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/api?action=line&sub=delete&user_id=$id")
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/lines?order=0&dir=desc")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun getTableIds(): Map<String, String> = withContext(Dispatchers.IO) {
        val url = "https://sdmx.vip/resellers/table?draw=1&id=lines&filter=&reseller=&start=0&length=-1&order[0][column]=0&order[0][dir]=desc&search[value]=&search[regex]=false&_=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/lines?order=0&dir=desc")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        val map = mutableMapOf<String, String>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext map
                val jsonStr = response.body?.string() ?: return@withContext map
                
                Log.d("SdmxApi", "Table JSON length: ${jsonStr.length}")
                
                val jsonResponse = JSONObject(jsonStr)
                val dataArray = jsonResponse.optJSONArray("data") ?: return@withContext map
                
                val cleanRegex = Regex("<.*?>")
                for (i in 0 until dataArray.length()) {
                    val row = dataArray.optJSONArray(i) ?: continue
                    if (row.length() >= 2) {
                        val rawId = row.optString(0)
                        val rawUsername = row.optString(1)
                        
                        val cleanId = rawId.replace(cleanRegex, "").trim()
                        val cleanUsername = rawUsername.replace(cleanRegex, "").trim()
                        
                        if (cleanId.isNotEmpty() && cleanUsername.isNotEmpty()) {
                            map[cleanUsername] = cleanId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext map
    }
}
