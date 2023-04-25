package org.starx.spaceship.model

@kotlinx.serialization.Serializable
data class Route(
    val type: String,
    val dst: String,
    var src: List<String>? = null,
    var path: String? = null,
)

enum class BuiltinRoute(val route: Route) {
    ROUTE_BYPASS_DOMAIN(
        Route(
            RouteType.TYPE_DOMAIN.type,
            TransportType.TYPE_DIRECT.type,
        )
    ),
    ROUTE_BYPASS_EXACT(
        Route(
            RouteType.TYPE_EXACT.type,
            TransportType.TYPE_DIRECT.type,
        )
    ),
    ROUTE_BYPASS_CIDR(
        Route(
            RouteType.TYPE_CIDR.type,
            TransportType.TYPE_DIRECT.type,
        )
    ),
    ROUTE_BYPASS_REGEX(
        Route(
            RouteType.TYPE_REGEX.type,
            TransportType.TYPE_DIRECT.type,
        )
    ),
    ROUTE_BYPASS_LOCAL_CIDR(
        Route(
            RouteType.TYPE_CIDR.type,
            TransportType.TYPE_DIRECT.type,
            listOf(
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16"
            )
        )
    ),
    ROUTE_DEFAULT(
        Route(
            RouteType.TYPE_DEFAULT.type,
            TransportType.TYPE_PROXY.type
        )
    )
}