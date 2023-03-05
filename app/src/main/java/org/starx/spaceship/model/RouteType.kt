package org.starx.spaceship.model

enum class RouteType(val type: String) {
    TYPE_EXACT("exact"),
    TYPE_DOMAINS("domains"),
    TYPE_CIDR("cidr"),
    TYPE_REGEX("regex"),
    TYPE_DEFAULT("default")
}