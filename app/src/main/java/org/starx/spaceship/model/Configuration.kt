package org.starx.spaceship.model

@kotlinx.serialization.Serializable
data class Configuration (
    val server_addr: String,
    val host: String,
    val path: String, // serviceName
    val tls: Boolean,
    val mux: Int, // 0~255
    val buffer: Int, // 1~65535
    val uuid: String,
    val listen_socks: String,
    val listen_http: String,
    val dns: DNS,
    val cas: List<String>? = null,
    val route: List<Route>? = null,
){
    val role="client"
}