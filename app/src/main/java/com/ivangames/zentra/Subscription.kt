package com.ivangames.zentra

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

object Subscription {

    fun fetch(url: String, done: (List<String>) -> Unit) {
        Thread {
            val result = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Zentra/0.1")
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                conn.disconnect()
                extract(body)
            } catch (e: Exception) {
                emptyList<String>()
            }
            done(result)
        }.start()
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
