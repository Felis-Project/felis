@file:JvmName("ApiInit")

package io.github.joemama.loader.api

import io.github.joemama.loader.ModLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface CommonEntrypoint {
    fun onInit()
}

val logger: Logger = LoggerFactory.getLogger("Loader API")

@Suppress("unused")
fun apiInit() {
    ModLoader.callEntrypoint("common", CommonEntrypoint::onInit)
}

