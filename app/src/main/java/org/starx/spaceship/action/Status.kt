package org.starx.spaceship.action

enum class Status(val action: String) {
    SERVICE_START("{${Common.pkg}.SERVICE_START}"),
    SERVICE_STOP("{${Common.pkg}.SERVICE_STOP}"),
    SERVICE_OK("{${Common.pkg}.SERVICE_OK}"),
}