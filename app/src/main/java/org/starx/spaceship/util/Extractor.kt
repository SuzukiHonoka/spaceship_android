package org.starx.spaceship.util

class Extractor {
    companion object {
        fun extractPort(ns: String): Int {
            if (ns.isBlank()) {
                throw IllegalArgumentException("Network string cannot be blank")
            }
            
            return try {
                val index = ns.lastIndexOf(':')
                if (index != -1) {
                    val portStr = ns.substring(index + 1)
                    val port = portStr.toInt()
                    if (port !in 1..65535) {
                        throw IllegalArgumentException("Port must be between 1 and 65535, got: $port")
                    }
                    port
                } else {
                    val port = ns.toInt()
                    if (port !in 1..65535) {
                        throw IllegalArgumentException("Port must be between 1 and 65535, got: $port")
                    }
                    port
                }
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid port format in: $ns", e)
            }
        }
    }
}