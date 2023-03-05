package org.starx.spaceship.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

class JsonFactory {
    companion object{
        @OptIn(ExperimentalSerializationApi::class)
        val processor = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}