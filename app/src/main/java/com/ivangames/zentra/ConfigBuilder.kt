package com.ivangames.zentra

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    fun build(s: Server): String {
        val p = s.params
        var net = p["type"] ?: p["net"] ?: "tcp"
        if (net == "splithttp") net = "xhttp"
        val security = when {
            p["security"] != null -> p["security"]!!
            p["tls"] == "tls" -> "tls"
            s.protocol == "trojan" -> "tls"
            else -> "none"
        }

        val stream = JSONObject().put("network", net).put("security", security)
        if (security == "tls") {
            val tls = JSONObject()
                .put("serverName", p["sni"] ?: p["host"] ?: s.host)
                .put("fingerprint", p["fp"] ?: "chrome")
            val alpn = p["alpn"]
            if (alpn != null) tls.put("alpn", JSONArray(alpn.split(",")))
            stream.put("tlsSettings", tls)
        } else if (security == "reality") {
            stream.put(
                "realitySettings", JSONObject()
                    .put("serverName", p["sni"] ?: s.host)
                    .put("fingerprint", p["fp"] ?: "chrome")
                    .put("publicKey", p["pbk"] ?: "")
                    .put("shortId", p["sid"] ?: "")
                    .put("spiderX", p["spx"] ?: "")
            )
        }

        when (net) {
            "ws" -> stream.put(
                "wsSettings", JSONObject()
                    .put("path", p["path"] ?: "/")
                    .put("headers", JSONObject().put("Host", p["host"] ?: s.host))
            )
            "grpc" -> stream.put(
                "grpcSettings", JSONObject().put("serviceName", p["serviceName"] ?: p["path"] ?: "")
            )
            "xhttp" -> stream.put(
                "xhttpSettings", JSONObject()
                    .put("path", p["path"] ?: "/")
                    .put("host", p["host"] ?: s.host)
                    .put("mode", p["mode"] ?: "auto")
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings", JSONObject()
                    .put("path", p["path"] ?: "/")
                    .put("host", p["host"] ?: s.host)
            )
        }

        val settings = when (s.protocol) {
            "vless" -> JSONObject().put(
                "vnext", JSONArray().put(
                    JSONObject().put("address", s.host).put("port", s.port).put(
                        "users", JSONArray().put(
                            JSONObject().put("id", s.id)
                                .put("encryption", p["encryption"] ?: "none")
                                .put("flow", p["flow"] ?: "")
                        )
                    )
                )
            )
            "vmess" -> JSONObject().put(
                "vnext", JSONArray().put(
                    JSONObject().put("address", s.host).put("port", s.port).put(
                        "users", JSONArray().put(
                            JSONObject().put("id", s.id)
                                .put("alterId", (p["aid"] ?: "0").toIntOrNull() ?: 0)
                                .put("security", "auto")
                        )
                    )
                )
            )
            else -> JSONObject().put(
                "servers", JSONArray().put(
                    JSONObject().put("address", s.host).put("port", s.port).put("password", s.id)
                )
            )
        }

        val proxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", s.protocol)
            .put("settings", settings)
            .put("streamSettings", stream)

        val tunIn = JSONObject()
            .put("tag", "tun-in")
            .put("port", 0)
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("mtu", 1500))
            .put(
                "sniffing", JSONObject().put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            )

        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray(listOf("1.1.1.1", "8.8.8.8"))))
            .put("inbounds", JSONArray().put(tunIn))
            .put(
                "outbounds", JSONArray().put(proxy)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
        return root.toString()
    }
}
