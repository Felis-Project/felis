package io.github.joemama.loader.api.client

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.ClassData
import io.github.joemama.loader.transformer.Transformation

interface ClientEntrypoint {
    fun onClientInit()
}

fun clientApiInit() {
    ModLoader.callEntrypoint("client", ClientEntrypoint::onClientInit)
}

class MinecraftTransformation: Transformation {
    override fun transform(classData: ClassData) {
        TODO("unimplemented")
    }
}