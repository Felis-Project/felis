package io.github.joemama.loader.micromixin

import io.github.joemama.loader.LoaderPluginEntrypoint
import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import io.github.joemama.loader.transformer.TransformingClassLoader
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.stianloader.micromixin.transform.MixinConfig
import org.stianloader.micromixin.transform.MixinTransformer
import org.stianloader.micromixin.transform.api.MixinLoggingFacade
import org.stianloader.micromixin.transform.supertypes.ASMClassWrapperProvider
import org.stianloader.micromixin.transform.supertypes.ClassWrapperPool

class MicroMixinLoaderPlugin : LoaderPluginEntrypoint {
    companion object {
        private val logger = LoggerFactory.getLogger("Micromixin")
        private val classWrappers = ClassWrapperPool(
            listOf(
                object : ASMClassWrapperProvider() {
                    override fun getNode(name: String): ClassNode? = ModLoader.classLoader.getClassData(name)?.node
                }
            )
        )
        private val transformer: MixinTransformer<TransformingClassLoader> =
            MixinTransformer<TransformingClassLoader>({ loader, name ->
                loader.getClassData(name)?.node ?: throw ClassNotFoundException("Invalid mixin class $name")
            }, this.classWrappers).also {
                it.logger = MMLogger(this.logger)
                it.isMergingClassFileVersions
            }
    }

    override fun onLoaderInit() {
        logger.info("Initializing Micromixin")
        ModLoader.discoverer.mods.flatMap { it.meta.mixins }.map { it.path }.forEach { path ->
            ModLoader.classLoader.getResourceAsStream(path)?.use { it.readAllBytes() }?.let {
                transformer.addMixin(ModLoader.classLoader, MixinConfig.fromString(String(it)))
            }
        }
        ModLoader.transformer.registerInternal(MicroMixinTransformation)
    }

    object MicroMixinTransformation : Transformation {
        override fun transform(container: ClassContainer) {
            if (transformer.isMixinTarget(container.name)) {
                transformer.transform(container.node)
            }
        }
    }

    class MMLogger(private val logger: Logger) : MixinLoggingFacade {
        override fun error(clazz: Class<*>?, message: String?, vararg args: Any?) =
            logger.error(message, args)

        override fun info(clazz: Class<*>?, message: String?, vararg args: Any?) =
            logger.info(message, args)

        override fun warn(clazz: Class<*>?, message: String?, vararg args: Any?) =
            logger.warn(message, args)
    }
}