package org.starx.spaceship.store

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import org.starx.spaceship.R
import org.starx.spaceship.model.BuiltinRoute
import org.starx.spaceship.model.Configuration
import org.starx.spaceship.model.DNS
import org.starx.spaceship.util.Extractor
import org.starx.spaceship.util.JsonFactory
import org.starx.spaceship.util.Resource

class Settings(private val ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences(ctx.getString(R.string.root_preference_key), Context.MODE_PRIVATE)
    private val edit: SharedPreferences.Editor = sp.edit()

    var profileName: String
        get() = sp.getString(ctx.getString(R.string.profile_name_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.profile_name_key), value).apply()

    var server: String
        get() = sp.getString(ctx.getString(R.string.server_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.server_key), value).apply()

    var serverPort: Int
        get() = sp.getString(ctx.getString(R.string.server_port_key), "0")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.server_port_key), value.toString())
            .apply()

    var userID: String
        get() = sp.getString(ctx.getString(R.string.user_id_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.user_id_key), value).apply()

    var sni: String
        get() = sp.getString(ctx.getString(R.string.server_name_indication_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.server_name_indication_key), value)
            .apply()

    var serviceName: String
        get() = sp.getString(ctx.getString(R.string.server_service_name_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.server_service_name_key), value).apply()

    var mux: Int
        get() = sp.getString(ctx.getString(R.string.server_mux_key), "8")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.server_mux_key), value.toString())
            .apply()

    var buffer: Int
        get() = sp.getString(ctx.getString(R.string.server_buffer_key), "32")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.server_buffer_key), value.toString())
            .apply()

    var dns: String
        get() = sp.getString(ctx.getString(R.string.dns_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.dns_key), value).apply()

    var enableIpv6: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.enable_ipv6_key), false)
        set(value) = edit.putBoolean(ctx.getString(R.string.enable_ipv6_key), value).apply()

    var tls: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.server_tls_key), true)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.server_tls_key), value).apply()
        }

    var bypass: String
        get() = sp.getString(ctx.getString(R.string.server_bypass_key), "lan/cn")!!
        set(value) = edit.putString(ctx.getString(R.string.server_bypass_key), value).apply()

    var advancedRoute: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.server_advanced_route_toggle_key), false)
        set(value) = edit.putBoolean(
            ctx.getString(R.string.server_advanced_route_toggle_key),
            value
        ).apply()

    var socksPort: Int
        get() = sp.getString(ctx.getString(R.string.inbound_socks_port_key), "10818")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.inbound_socks_port_key), value.toString()).apply()

    var httpPort: Int
        get() = sp.getString(ctx.getString(R.string.inbound_http_port_key), "10828")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.inbound_http_port_key), value.toString()).apply()

    var basicAuth: String
        get() = sp.getString(ctx.getString(R.string.inbound_basic_auth_key), "")!!
        set(value) = edit.putString(ctx.getString(R.string.inbound_basic_auth_key), value).apply()

    var allowOther: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.inbound_allow_other_key), false)
        set(value) {
            edit.putBoolean(ctx.getString(R.string.inbound_allow_other_key), value).apply()
        }

    var autoStart: Boolean
        get() = sp.getBoolean(
            ctx.getString(R.string.service_autostart_key),
            false
        )
        set(value) = edit.putBoolean(ctx.getString(R.string.service_autostart_key), value).apply()

    var oom: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.service_restart_on_oom_key), true)
        set(value) = edit.putBoolean(ctx.getString(R.string.service_restart_on_oom_key), value)
            .apply()

    var enableVPN: Boolean
        get() = sp.getBoolean(ctx.getString(R.string.service_enable_vpn_key), false)
        set(value) = edit.putBoolean(ctx.getString(R.string.service_enable_vpn_key), value).apply()

    var idleTimeout: Int
        get() = sp.getString(ctx.getString(R.string.server_idle_timeout_key), "0")!!.toInt()
        set(value) = edit.putString(ctx.getString(R.string.server_idle_timeout_key), value.toString()).apply()

    fun validate(): Boolean {
        return setOf(server, userID).all {  it != ""} && (serverPort in 0..65535)
    }

    fun saveConfiguration(cfg: Configuration) {
        //val outer = Json.decodeFromString(Configuration.serializer(), "")
        val d = cfg.serverAddress.lastIndexOf(':')
        server = cfg.serverAddress.substring(0, d)
        serverPort = cfg.serverAddress.substring(d + 1).toInt()
        sni = cfg.host
        serviceName = cfg.path
        tls = cfg.tls
        mux = cfg.mux
        buffer = cfg.buffer
        userID = cfg.uuid
        socksPort = Extractor.extractPort(cfg.listenSocks)
        httpPort = Extractor.extractPort(cfg.listenHttp)
        dns = cfg.dns.server
        basicAuth = if (cfg.basicAuth == null) "" else cfg.basicAuth.joinToString(separator = "\n")
        idleTimeout = cfg.idleTimeout ?: 0
        //ca = cfg.cas
        //routes = cfg.routes
    }

    private fun toConfiguration(): Configuration {
        val bypassOpt = bypass
        val routes = buildList {
            if (!TextUtils.isEmpty(bypassOpt)) {
                if (bypassOpt.contains("lan")) add(BuiltinRoute.ROUTE_BYPASS_LOCAL_CIDR.route)
                if (bypassOpt.contains("cn")) {
                    add(BuiltinRoute.ROUTE_BYPASS_REGEX.route.apply {
                        src = listOf("\\S*\\.cn")
                    })
                    add(BuiltinRoute.ROUTE_BYPASS_DOMAIN.route.apply {
                        path = "${ctx.filesDir}/${Resource.OPT_ASSET_CHINALIST}"
                    })
                    add(BuiltinRoute.ROUTE_BYPASS_CIDR.route.apply {
                        path = "${ctx.filesDir}/${Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V4}"
                    })
                    if (enableIpv6) {
                        add(BuiltinRoute.ROUTE_BYPASS_CIDR.route.apply {
                            path = "${ctx.filesDir}/${Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V6}"
                        })
                    }
                }
            }
            add(BuiltinRoute.ROUTE_DEFAULT.route)
        }


        val bind = if (allowOther) "0.0.0.0" else "127.0.0.1"
        return Configuration(
            "${server}:${serverPort}",
            sni,
            serviceName,
            tls,
            mux,
            buffer,
            userID,
            "${bind}:${socksPort}",
            "${bind}:${httpPort}",
            if (basicAuth != "") splitBasicAuth(basicAuth) else null,
            DNS(dns),
            enableIpv6,
            listOf("${ctx.filesDir}/${Resource.OPT_ASSET_FAKECA}"),
            routes,
            idleTimeout,
        )
    }

    private fun splitBasicAuth(s: String): List<String> {
        val pattern = "[\n,]"
        val authPattern = "^\\w+:\\w+$".toRegex()
        return s.split(Regex(pattern)).map {
            it.trim()
        }.filter {
            it.isNotEmpty() && it.contains(authPattern)
        }
    }

    fun toJson() = JsonFactory.processor.encodeToString(toConfiguration())
}