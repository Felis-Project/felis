@file:JvmName("ServerApiInit")

package io.github.joemama.loader.api.server

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
import org.objectweb.asm.Type
import java.io.File

@OnlyIn(Side.SERVER)
interface ServerEntrypoint {
    companion object {
        const val KEY = "server"
    }

    fun onClientInit()
}

@Suppress("unused")
@OnlyIn(Side.SERVER)
fun serverApiInit() {
    LoaderApi.logger.info("Calling server entrypoint")
    ModLoader.callEntrypoint(ServerEntrypoint.KEY, ServerEntrypoint::onClientInit)
    LoaderEvents.entrypointLoaded.fire(MapEventContainer.JointEventContext(ServerEntrypoint.KEY, Unit))
}

object MainTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod("main", "([Ljava/lang/String;)V") {
            inject(
                InjectionPoint.Invoke(
                    typeOf(File::class),
                    "<init>",
                    Type.VOID_TYPE,
                    typeOf(String::class),
                    limit = 1
                )
            ) {
                invokeStatic(locate("io.github.joemama.loader.api.server.ServerApiInit"), "serverApiInit")
            }
        }
    }
}