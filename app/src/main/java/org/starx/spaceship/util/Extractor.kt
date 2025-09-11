package org.starx.spaceship.util

class Extractor {
    companion object {
        fun extractPort(ns: String): Int {
            require(ns.isNotBlank()) { "Network string cannot be blank" }
            
            return runCatching {
                ns.lastIndexOf(':').takeIf { it != -1 }
                    ?.let { index -> ns.substring(index + 1) }
                    ?: ns
            }.getOrElse { throw IllegalArgumentException("Invalid network string format: $ns") }
                .toIntOrNull()
                ?.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("Port must be between 1 and 65535")
        }
    }
}