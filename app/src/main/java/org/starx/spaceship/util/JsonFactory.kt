package org.starx.spaceship.util

import kotlinx.serialization.json.Json

class JsonFactory {
    companion object {
        val processor = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}