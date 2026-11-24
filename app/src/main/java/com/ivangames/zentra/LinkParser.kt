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
        val low = link.lowercase()
        return try {
            when {
                low.startsWith("vless://") -> parseUri(link, "vless")
                low.startsWith("trojan://") -> parseUri(link, "trojan")
                low.startsWith("vmess://") -> parseVmess(link)
                low.startsWith("ss://") -> parseSs(link)
                low.startsWith("hysteria2://") || low.startsWith("hy2://") -> parseHy2(link)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dec(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun b64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT))
    }

    private fun query(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to dec(it.substringAfter('=')) }

    private fun hostPort(hp: String, defPort: Int): Pair<String, Int> {
        val h = hp.substringBefore('/')
        if (h.startsWith("[")) {
            val host = h.substringAfter('[').substringBefore(']')
            val port = h.substringAfter("]:", "").substringBefore(',').substringBefore('-').toIntOrNull() ?: defPort
            return Pair(host, port)
        }
        val i = h.lastIndexOf(':')
        if (i < 0) return Pair(h, defPort)
        val port = h.substring(i + 1).substringBefore(',').substringBefore('-').toIntOrNull() ?: defPort
        return Pair(h.substring(0, i), port)
    }

    private fun parseUri(link: String, proto: String): Server {
        val body = link.substringAfter("://")
        val name = dec(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val params = query(noFrag.substringAfter('?', ""))
        val main = noFrag.substringBefore('?')
        val id = dec(main.substringBeforeLast('@'))
        val hp = hostPort(main.substringAfterLast('@'), 443)
        return Server(proto, name, hp.first, hp.second, id, params)
    }

    private fun parseVmess(link: String): Server {
        val json = JSONObject(b64(link.substringAfter("://")))
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

    private fun parseSs(link: String): Server {
        val body = link.substringAfter("://")
        val name = dec(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val main = noFrag.substringBefore('?')
        val creds: String
        val hpStr: String
        if (main.contains('@')) {
            val userinfo = main.substringBeforeLast('@')
            creds = if (userinfo.contains(':')) dec(userinfo) else b64(userinfo)
            hpStr = main.substringAfterLast('@')
        } else {
            val d = b64(main)
            creds = d.substringBeforeLast('@')
            hpStr = d.substringAfterLast('@')
        }
        val hp = hostPort(hpStr, 8388)
        return Server(
            "shadowsocks", name, hp.first, hp.second,
            creds.substringAfter(':'),
            mapOf("method" to creds.substringBefore(':'))
        )
    }

    private fun parseHy2(link: String): Server {
        val body = link.substringAfter("://")
        val name = dec(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val params = query(noFrag.substringAfter('?', ""))
        val main = noFrag.substringBefore('?')
        val auth = dec(main.substringBeforeLast('@', ""))
        val hp = hostPort(main.substringAfterLast('@'), 443)
        return Server("hysteria", name, hp.first, hp.second, auth, params)
    }
}
