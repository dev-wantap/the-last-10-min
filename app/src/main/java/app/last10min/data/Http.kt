package app.last10min.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class Http(private val baseUrl: String) {
    val enabled: Boolean get() = baseUrl.isNotBlank()

    fun get(path: String): String = request("GET", path, null)

    fun post(path: String, json: String): String = request("POST", path, json)

    private fun request(method: String, path: String, body: String?): String {
        if (!enabled) throw IOException("서버 미설정")
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code $text")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
