package org.starx.spaceship.util

import kotlinx.serialization.json.Json

class JsonFactory {
    companion object {
        val processor = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true // Handle future configuration versions gracefully
            isLenient = true // Allow more flexible JSON parsing
            coerceInputValues = true // Handle type mismatches gracefully
        }
    }
}