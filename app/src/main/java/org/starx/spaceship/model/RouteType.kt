package org.starx.spaceship.model

enum class RouteType(val type: String) {
    TYPE_EXACT("exact"),
    TYPE_DOMAIN("domain"),
    TYPE_CIDR("cidr"),
    TYPE_REGEX("regex"),
    TYPE_DEFAULT("default")
}