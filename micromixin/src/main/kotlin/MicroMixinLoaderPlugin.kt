package io.github.joemama.loader.micromixin

import io.github.joemama.loader.LoaderPluginEntrypoint
import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.meta.ModMeta
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import io.github.joemama.loader.transformer.TransformingClassLoader
import net.peanuuutz.tomlkt.asTomlArray
import net.peanuuutz.tomlkt.asTomlTable
import net.peanuuutz.tomlkt.getString
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.stianloader.micromixin.transform.MixinConfig
import org.stianloader.micromixin.transform.MixinTransformer
import org.stianloader.micromixin.transform.api.MixinLoggingFacade
import org.stianloader.micromixin.transform.supertypes.ClassWrapper
import org.stianloader.micromixin.transform.supertypes.ClassWrapperPool

object MicroMixinLoaderPlugin : LoaderPluginEntrypoint {
    data class Mixin(val path: String)

    @Suppress("MemberVisibilityCanBePrivate")
    val ModMeta.mixins: List<Mixin>
        get() = this.toml["mixins"]
            ?.asTomlArray()
            ?.map { it.asTomlTable().getString("path") }
            ?.map { Mixin(it) }
            ?: emptyList()

    private val logger = LoggerFactory.getLogger("MicroMixin")
    private val classWrappers = ClassWrapperPool().also {
        it.addProvider { name, pool ->
            // better than parsing a node at runtime using ASMClassWrapperProvider
            ModLoader.classLoader.getClassData(name)
                ?.bytes
                ?.let(::ClassReader)
                ?.run {
                    ClassWrapper(
                        name,
                        superName,
                        interfaces,
                        access and Opcodes.ACC_INTERFACE != 0,
                        pool
                    )
                }
        }
    }
    private val transformer: MixinTransformer<TransformingClassLoader> =
        MixinTransformer<TransformingClassLoader>({ loader, name ->
            loader.getClassData(name)?.node ?: throw ClassNotFoundException("Class $name was not found")
        }, this.classWrappers).also {
            it.logger = MMLogger(this.logger)
            it.setMergeClassFileVersions(false)
        }

    override fun onLoaderInit() {
        this.logger.info("Initializing Micromixin")
        var configs = 0
        ModLoader.discoverer.flatMap { it.meta.mixins }.map(Mixin::path).forEach { path ->
            ModLoader.classLoader.getResourceAsStream(path)?.use { it.readAllBytes() }?.let {
                transformer.addMixin(ModLoader.classLoader, MixinConfig.fromString(String(it)))
                configs++
            }
        }

        if (configs > 0)
            this.logger.info("Micromixin successfully initialized with $configs configuration${if (configs > 1) "s" else ""}")
        ModLoader.transformer.registerTransformation(MicroMixinTransformation)
    }

    class MMClassWriter : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String = transformer.pool.let {
            it.getCommonSuperClass(it.get(type1), it.get(type2)).name
        }
    }

    object MicroMixinTransformation : Transformation {
        override fun transform(container: ClassContainer) {
            if (transformer.isMixinTarget(container.internalName)) {
                transformer.transform(container.node)
                with(MMClassWriter()) {
                    container.node.accept(this)
                    container.newBytes(this.toByteArray())
                }
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