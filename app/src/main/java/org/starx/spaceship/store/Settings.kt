package org.starx.spaceship.store

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import kotlinx.serialization.encodeToString
import org.starx.spaceship.R
import org.starx.spaceship.model.BuiltinRoute
import org.starx.spaceship.model.Configuration
import org.starx.spaceship.model.DNS
import org.starx.spaceship.model.Route
import org.starx.spaceship.util.JsonFactory

class Settings(private val ctx: Context) {
    private var sp: SharedPreferences =
        ctx.getSharedPreferences(ctx.getString(R.string.root_preference_key), Context.MODE_PRIVATE)
    private var edit: SharedPreferences.Editor = sp.edit()
    
    var profileName: String
        get() = sp.getString(ctx.getString(R.string.profile_name_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.profile_name_key), value).apply()
        }

    var server: String
        get() = sp.getString(ctx.getString(R.string.server_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.server_key), value).apply()
        }

    var serverPort: Int
        get() = sp.getString(ctx.getString(R.string.server_port_key), "0")!!.toInt()
        set(value) {
            edit.putString(ctx.getString(R.string.server_port_key), value.toString()).apply()
        }

    var userID: String
        get() = sp.getString(ctx.getString(R.string.user_id_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.user_id_key), value).apply()
        }

    var sni: String
        get() = sp.getString(ctx.getString(R.string.server_name_indication_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.server_name_indication_key), value).apply()
        }

    var serviceName: String
        get() = sp.getString(ctx.getString(R.string.server_service_name_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.server_service_name_key), value).apply()
        }

    var mux: Int
        get() = sp.getString(ctx.getString(R.string.server_mux_key), "8")!!.toInt()
        set(value) {
            edit.putString(ctx.getString(R.string.server_mux_key), value.toString()).apply()
        }

    var buffer: Int
        get() = sp.getString(ctx.getString(R.string.server_buffer_key), "32")!!.toInt()
        set(value) {
            edit.putString(ctx.getString(R.string.server_buffer_key), value.toString()).apply()
        }

    var dns: String
        get() = sp.getString(ctx.getString(R.string.dns_key), "")!!
        set(value) {
            edit.putString(ctx.getString(R.string.dns_key), value).apply()
        }

    var tls: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.server_tls_key), true)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.server_tls_key), value).apply()
        }

    var bypass: String
        get() = sp.getString(ctx.getString(R.string.server_bypass_key), "lan/cn")!!
        set(value) {
            edit.putString(ctx.getString(R.string.server_bypass_key), value).apply()
        }

    var advancedRoute: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.server_advanced_route_toggle_key), false)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.server_advanced_route_toggle_key), value).apply()
        }

    var socks: String
        get() = sp.getString(ctx.getString(R.string.inbound_socks_key), "127.0.0.1:10818")!!
        set(value) {
            edit.putString(ctx.getString(R.string.inbound_socks_key), value).apply()
        }

    var http: String
        get() = sp.getString(ctx.getString(R.string.inbound_http_key), "127.0.0.1:10828")!!
        set(value) {
            edit.putString(ctx.getString(R.string.inbound_http_key), value).apply()
        }

    var autoStart: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.service_autostart_key),
            false)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.service_autostart_key), value).apply()
        }

    var oom: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.service_restart_on_oom_key), true)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.service_restart_on_oom_key), value).apply()
        }

    fun validate():Boolean {
        if (server == "" || serverPort == 0 || userID == "") return false
        return true
    }

    fun saveConfiguration(cfg: Configuration){
        //val outer = Json.decodeFromString(Configuration.serializer(), "")
        val d = cfg.server_addr.lastIndexOf(':')
        server = cfg.server_addr.substring(0, d)
        serverPort = cfg.server_addr.substring(d+1).toInt()
        sni = cfg.host
        serviceName = cfg.path
        tls = cfg.tls
        mux = cfg.mux
        buffer = cfg.buffer
        userID = cfg.uuid
        socks = cfg.listen_socks
        http = cfg.listen_http
        dns = cfg.dns.server
        //ca = cfg.cas
        //routes = cfg.routes
    }

    private fun toConfiguration(): Configuration{
        val bypassOpt = bypass
        val routes =  mutableListOf<Route>()
        if (!TextUtils.isEmpty(bypassOpt)) {
            if (bypassOpt.contains("lan")) routes.add(BuiltinRoute.ROUTE_BYPASS_LOCAL_CIDR.route)
            if (bypassOpt.contains("cn")) {
                routes.add(BuiltinRoute.ROUTE_BYPASS_DOMAINS.route.apply {
                    src = listOf("cn")
                    path = "${ctx.filesDir}/chinalist.txt"
                })
                routes.add(BuiltinRoute.ROUTE_BYPASS_CIDR.route.apply {
                    path = "${ctx.filesDir}/cn.zone"
                })
            }
        }
        routes.add(BuiltinRoute.ROUTE_DEFAULT.route)
        return Configuration(
            "${server}:${serverPort}",
            sni,
            serviceName,
            tls,
            mux,
            buffer,
            userID,
            socks,
            http,
            DNS(dns),
            listOf("${ctx.filesDir.absolutePath}/fakeca.pem"),
            routes
          )
    }

    fun toJson() = JsonFactory.processor.encodeToString(toConfiguration())
}