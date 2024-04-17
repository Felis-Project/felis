@file:JvmName("ClientApiInit")

package io.github.joemama.loader.api.client

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.api.LoaderApi
import io.github.joemama.loader.api.event.LoaderEvents
import io.github.joemama.loader.api.event.MapEventContainer
import io.github.joemama.loader.asm.InjectionPoint
import io.github.joemama.loader.asm.openMethod
import io.github.joemama.loader.side.OnlyIn
import io.github.joemama.loader.side.Side
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import net.minecraft.client.main.GameConfig
import org.objectweb.asm.Type

@OnlyIn(Side.CLIENT)
interface ClientEntrypoint {
    companion object {
        const val KEY = "client"
    }

    fun onClientInit()
}

@Suppress("unused")
@OnlyIn(Side.CLIENT)
fun clientApiInit() {
    LoaderApi.logger.info("Calling client entrypoint")
    ModLoader.callEntrypoint(ClientEntrypoint.KEY, ClientEntrypoint::onClientInit)
    LoaderEvents.entrypointLoaded.fire(MapEventContainer.JointEventContext(ClientEntrypoint.KEY, Unit))
}

object MinecraftTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod("<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(GameConfig::class.java))) {
            inject(
                InjectionPoint.Invoke(
                    Type.getType(Thread::class.java),
                    "currentThread",
                    Type.getType(Thread::class.java),
                    limit = 1
                )
            ) {
                invokeStatic("io/github/joemama/loader/api/client/ClientApiInit", "clientApiInit")
            }
        }
    }
}