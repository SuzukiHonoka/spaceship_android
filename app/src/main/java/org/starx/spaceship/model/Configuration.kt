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
    val listen_socks: String,
    val listen_http: String,
    val dns: DNS,
    val ipv6: Boolean,
    val cas: List<String>? = null,
    val route: List<Route>? = null,
) {
    val role = "client"
}