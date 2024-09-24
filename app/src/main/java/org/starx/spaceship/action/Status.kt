package org.starx.spaceship.action

enum class Status(val action: String) {
    SERVICE_START("{${Common.PACKAGE_NAME}.SERVICE_START}"),
    SERVICE_STOP("{${Common.PACKAGE_NAME}.SERVICE_STOP}"),
    SERVICE_OK("{${Common.PACKAGE_NAME}.SERVICE_OK}"),
}