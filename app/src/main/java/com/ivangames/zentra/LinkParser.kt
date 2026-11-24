package com.ivangames.zentra

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

data class Server(
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val id: String,
    val params: Map<String, String>
)

object LinkParser {

    fun parse(raw: String): Server? {
        val link = raw.trim()
        return try {
            when {
                link.startsWith("vless://") -> parseUri(link, "vless")
                link.startsWith("trojan://") -> parseUri(link, "trojan")
                link.startsWith("vmess://") -> parseVmess(link)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun parseUri(link: String, proto: String): Server {
        val body = link.removePrefix("$proto://")
        val name = dec(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val query = noFrag.substringAfter('?', "")
        val main = noFrag.substringBefore('?')
        val id = main.substringBefore('@')
        val hostPort = main.substringAfter('@')
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').trimEnd('/').toInt()
        val params = query.split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to dec(it.substringAfter('=')) }
        return Server(proto, name, host, port, id, params)
    }

    private fun parseVmess(link: String): Server {
        val body = link.removePrefix("vmess://").trim()
        val json = JSONObject(String(Base64.decode(body, Base64.DEFAULT)))
        val params = mutableMapOf<String, String>()
        for (key in listOf("net", "tls", "path", "host", "sni", "aid")) {
            val v = json.optString(key, "")
            if (v.isNotEmpty()) params[key] = v
        }
        return Server(
            "vmess",
            json.optString("ps", ""),
            json.getString("add"),
            json.get("port").toString().toInt(),
            json.getString("id"),
            params
        )
    }
}
