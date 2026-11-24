package com.ivangames.zentra

import org.json.JSONArray
import org.json.JSONObject

object ConfigExtra {

    fun build(s: Server): String {
        val p = s.params
        val proxy = JSONObject().put("tag", "proxy").put("protocol", s.protocol)

        if (s.protocol == "shadowsocks") {
            proxy.put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", s.host).put("port", s.port)
                            .put("method", p["method"] ?: "aes-128-gcm").put("password", s.id)
                    )
                )
            )
        } else {
            proxy.put(
                "settings",
                JSONObject().put("version", 2).put("address", s.host).put("port", s.port)
            )
            val tls = JSONObject()
                .put("serverName", p["sni"] ?: s.host)
                .put("alpn", JSONArray(listOf("h3")))
            val pin = p["pinSHA256"]
            if (pin != null) tls.put("pinnedPeerCertSha256", pin.replace(":", "").lowercase())
            val stream = JSONObject()
                .put("network", "hysteria")
                .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", s.id))
                .put("security", "tls")
                .put("tlsSettings", tls)
            if (p["obfs"] == "salamander") {
                stream.put(
                    "udpmasks", JSONArray().put(
                        JSONObject().put("type", "salamander")
                            .put("settings", JSONObject().put("password", p["obfs-password"] ?: ""))
                    )
                )
            }
            proxy.put("streamSettings", stream)
        }

        val tunIn = JSONObject()
            .put("tag", "tun-in")
            .put("port", 0)
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("mtu", 1500))
            .put(
                "sniffing", JSONObject().put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            )

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray(listOf("1.1.1.1", "8.8.8.8"))))
            .put("inbounds", JSONArray().put(tunIn))
            .put(
                "outbounds", JSONArray().put(proxy)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
            .toString()
    }
}
