package org.starx.spaceship.model

@kotlinx.serialization.Serializable
data class DNS(
    val server: String,
    val type: String = ""
)