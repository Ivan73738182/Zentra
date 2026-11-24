package com.ivangames.zentra

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

data class SubInfo(val used: Long, val total: Long, val expire: Long)
data class SubResult(val links: List<String>, val info: SubInfo?)

object Subscription {

    fun fetch(url: String, done: (SubResult) -> Unit) {
        Thread {
            val res = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Zentra/0.1")
                val header = conn.getHeaderField("subscription-userinfo")
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                conn.disconnect()
                SubResult(extract(body), parseInfo(header))
            } catch (e: Exception) {
                SubResult(emptyList(), null)
            }
            done(res)
        }.start()
    }

    private fun parseInfo(h: String?): SubInfo? {
        if (h == null) return null
        val m = h.split(";").map { it.trim() }.filter { it.contains("=") }
            .associate {
                it.substringBefore("=").trim() to
                    (it.substringAfter("=").trim().toDoubleOrNull()?.toLong() ?: 0L)
            }
        val used = (m["upload"] ?: 0L) + (m["download"] ?: 0L)
        return SubInfo(used, m["total"] ?: 0L, m["expire"] ?: 0L)
    }

    private fun decode(text: String): String {
        return try {
            String(Base64.decode(text, Base64.DEFAULT))
        } catch (e: Exception) {
            try {
                String(Base64.decode(text, Base64.URL_SAFE))
            } catch (e2: Exception) {
                text
            }
        }
    }

    private fun extract(body: String): List<String> {
        val text = if (body.contains("://")) body else decode(body)
        return text.lines().map { it.trim() }.filter { LinkParser.parse(it) != null }
    }
}
