package io.github.joemama.loader.mixin

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.tree.ClassNode
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.logging.ILogger
import org.spongepowered.asm.logging.LoggerAdapterAbstract
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.MixinEnvironment.Phase
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.service.*
import org.spongepowered.asm.util.ReEntranceLock
import java.io.InputStream
import java.net.URL

object MixinTransformation : Transformation {
    override fun transform(clazz: ClassNode, name: String) {
        if (Mixin.transformer.transformClass(Mixin.environment, name, clazz)) {
            ModLoader.logger.debug("transformed {} with mixin", clazz.name)
        }
    }
}

class MixinLogger : LoggerAdapterAbstract("mixin") {
    private val logger = LoggerFactory.getLogger(Mixin::class.java)
    private fun matchLevel(level: org.spongepowered.asm.logging.Level): Level = when (level) {
        org.spongepowered.asm.logging.Level.DEBUG -> Level.DEBUG
        org.spongepowered.asm.logging.Level.WARN -> Level.WARN
        org.spongepowered.asm.logging.Level.INFO -> Level.INFO
        org.spongepowered.asm.logging.Level.TRACE -> Level.TRACE
        org.spongepowered.asm.logging.Level.ERROR -> Level.ERROR
        else -> throw IllegalArgumentException("Invalid logging level")
    }

    override fun getType(): String = "slf4j Logger"

    override fun catching(p0: org.spongepowered.asm.logging.Level, p1: Throwable) {
        this.throwing(p1)
    }

    override fun log(p0: org.spongepowered.asm.logging.Level, p1: String, vararg p2: Any) {
        this.logger.atLevel(this.matchLevel(p0)).log(p1, *p2)
    }

    override fun log(p0: org.spongepowered.asm.logging.Level, p1: String, p2: Throwable) {
        this.logger.atLevel(this.matchLevel(p0)).log(p1)
        this.logger.atLevel(this.matchLevel(p0)).log(p2.toString())
    }

    override fun <T : Throwable> throwing(t: T): T {
        this.warn("Throwing {}: {}", t::class.java.getName(), t.message, t)
        return t
    }
}

class Mixin : IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    companion object {
        // beware of local global property
        internal lateinit var transformer: IMixinTransformer
        internal lateinit var environment: MixinEnvironment
        fun initMixins() {
            // initialize mixins
            MixinBootstrap.init()

            // pass in configs
            for (cfg in ModLoader.discoverer.mods.flatMap { it.meta.mixins }.map { it.path }) {
                Mixins.addConfiguration(cfg)
            }

            // move to the default phase
            environment = MixinEnvironment.getEnvironment(Phase.DEFAULT)
        }
    }

    private val lock = ReEntranceLock(1)

    // TODO: Change when we get a legit name
    override fun getName(): String = "ModLoader"

    // TODO: change once we get legit side handling
    override fun getSideName(): String = "CLIENT"
    override fun isValid(): Boolean = true
    override fun getClassProvider(): IClassProvider = this
    override fun getBytecodeProvider(): IClassBytecodeProvider = this
    override fun getTransformerProvider(): ITransformerProvider = this
    override fun getClassTracker(): IClassTracker = this
    override fun getAuditTrail(): IMixinAuditTrail? = null
    override fun getPlatformAgents(): Collection<String> =
        listOf("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault")

    override fun getPrimaryContainer(): IContainerHandle =
        ContainerHandleURI(ModLoader::class.java.protectionDomain.codeSource.location.toURI())

    override fun getResourceAsStream(name: String): InputStream? = ModLoader.classLoader.getResourceAsStream(name)
    override fun prepare() = Unit
    override fun getInitialPhase(): Phase = Phase.PREINIT
    override fun init() = Unit
    override fun beginPhase() = Unit
    override fun checkEnv(o: Any) = Unit
    override fun getReEntranceLock(): ReEntranceLock = this.lock
    override fun getMixinContainers(): Collection<IContainerHandle> = listOf()
    override fun getMinCompatibilityLevel(): MixinEnvironment.CompatibilityLevel =
        MixinEnvironment.CompatibilityLevel.JAVA_8

    override fun getMaxCompatibilityLevel(): MixinEnvironment.CompatibilityLevel =
        MixinEnvironment.CompatibilityLevel.JAVA_17

    override fun getLogger(name: String): ILogger = MixinLogger()

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java")
    override fun getClassPath(): Array<out URL> = arrayOf()
    override fun findClass(name: String): Class<*>? = ModLoader.classLoader.findClass(name)
    override fun findClass(name: String, resolve: Boolean): Class<*>? =
        Class.forName(name, resolve, ModLoader.classLoader)

    override fun findAgentClass(name: String, resolve: Boolean): Class<*> =
        Class.forName(name, resolve, ModLoader::class.java.classLoader)

    override fun getClassNode(name: String): ClassNode? = this.getClassNode(name, true)

    // runTransformers means nothing in our case since we always run transformers before Mixin application
    override fun getClassNode(name: String, runTransformers: Boolean): ClassNode? =
        ModLoader.classLoader.getClassNode(name)

    override fun getTransformers(): Collection<ITransformer> = listOf()
    override fun getDelegatedTransformers(): Collection<ITransformer> = listOf()
    override fun addTransformerExclusion(name: String) = Unit
    override fun registerInvalidClass(name: String) = Unit
    override fun isClassLoaded(name: String): Boolean = ModLoader.classLoader.isClassLoaded(name)
    override fun getClassRestrictions(name: String): String = ""

    override fun offer(internal: IMixinInternal) {
        if (internal is IMixinTransformerFactory) {
            transformer = internal.createTransformer()
        }
    }
}

class MixinBootstrap : IMixinServiceBootstrap {
    override fun getName(): String = "ModLoaderBootstrap"
    override fun getServiceClassName(): String = "io.github.joemama.loader.mixin.Mixin"
    override fun bootstrap() = Unit
}

data class PropertyKey(val key: String) : IPropertyKey

class GlobalPropertyService : IGlobalPropertyService {
    private val props: MutableMap<String, Any?> = mutableMapOf()
    override fun resolveKey(key: String): IPropertyKey = PropertyKey(key)

    // safe since we trust mixins to keep types properly stored
    @Suppress("unchecked_cast")
    override fun <T> getProperty(key: IPropertyKey): T? = this.props[(key as PropertyKey).key] as T
    override fun <T> getProperty(key: IPropertyKey, default: T?): T? = this.getProperty(key) ?: default
    override fun getPropertyString(key: IPropertyKey, default: String): String =
        this.getProperty(key, default).toString()

    override fun setProperty(key: IPropertyKey, value: Any?) {
        this.props[(key as PropertyKey).key] = value
    }
}
