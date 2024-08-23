package felis.launcher

import felis.ModLoader
import felis.meta.Mod
import felis.meta.ModMetadataExtended
import felis.transformer.JarContentCollection
import org.objectweb.asm.commons.Method
import org.slf4j.Logger
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

open class GameInstance(
    contentCollection: JarContentCollection,
    meta: ModMetadataExtended,
    private val mainClass: String,
    private val mainMethod: Method,
) : Mod(contentCollection, meta) {
    val path by contentCollection::path

    fun start(logger: Logger, args: Array<String>) {
        logger.info("starting game")
        logger.debug("target game jars: {}", this.path)
        logger.debug("game args: ${args.contentToString()}")

        val mainClass = Class.forName(this.mainClass, true, ModLoader.classLoader)
        // using MethodLookup because technically speaking it's better than reflection
        val mainMethod = MethodHandles.publicLookup().`in`(mainClass).findStatic(
            mainClass,
            this.mainMethod.name,
            MethodType.fromMethodDescriptorString(this.mainMethod.descriptor, ModLoader.classLoader)
        )

        logger.debug("Calling ${this.mainClass}#main")
        // finally call the method
        mainMethod.invoke(args)
    }
}
