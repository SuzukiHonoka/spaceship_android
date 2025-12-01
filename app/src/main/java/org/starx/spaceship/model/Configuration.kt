package org.starx.spaceship.model

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Configuration(
    @SerialName("server_addr")
    val serverAddress: String,
    val host: String,
    val path: String, // serviceName
    val tls: Boolean,
    val mux: Int, // 0~255
    val buffer: Int, // 1~65535
    val uuid: String,
    @SerialName("listen_socks")
    val listenSocks: String,
    @SerialName("listen_http")
    val listenHttp: String,
    @SerialName("listen_dns")
    val listenDns: String? = null,
    @SerialName("basic_auth")
    val basicAuth: List<String>? = null,
    val dns: DNS,
    val ipv6: Boolean,
    val cas: List<String>? = null,
    val route: List<Route>? = null,
    @SerialName("idle_timeout")
    val idleTimeout: Int?,
) {
    @Suppress("unused")
    val role = "client"
}