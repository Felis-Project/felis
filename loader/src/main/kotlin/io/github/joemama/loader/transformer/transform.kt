package io.github.joemama.loader.transformer

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import io.github.joemama.loader.ModLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongepowered.asm.transformers.MixinClassWriter
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import java.util.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

interface Transformation {
    fun transform(classData: ClassData)
}

data class SimpleTransformation(
    val transformation: Transformation,
    val transformationName: String
) : Transformation by transformation

class Transformer : Transformation {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)
    private val external: Multimap<String, Lazy<SimpleTransformation>> = MultimapBuilder.ListMultimapBuilder
        .hashKeys()
        .arrayListValues()
        .build()
    private val internal = mutableListOf<Transformation>()

    init {
        for ((name, target, clazz) in ModLoader.discoverer.mods.flatMap { it.meta.transforms }) {
            this.external.put(
                target,
                lazy {
                    SimpleTransformation(
                        Class.forName(clazz, true, ModLoader.classLoader)
                            .getDeclaredConstructor()
                            .newInstance() as Transformation,
                        name
                    )
                }
            )
        }
    }

    fun registerInternal(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(classData: ClassData) {
        val name = classData.name
        if (this.external.containsKey(name)) {
            for (t in this.external.get(name)) {
                this.logger.info("transforming $name with ${t.value.transformationName}")
                t.value.transform(classData)
                if (classData.skip) {
                    return
                }
            }
        }

        for (t in this.internal) {
            t.transform(classData)

            if (classData.skip) {
                return
            }
        }
    }
}

/**
 * This class loader uses the classloading delegation model to:
 * 1. Use the classpath for loader/minecraft dependencies
 * 2. Load the contents of mods and the game itself through their jars at runtime
 * 3. Apply transformations to game classes
 *
 * @author 0xJoeMama
 */
class TransformingClassLoader : ClassLoader(getSystemClassLoader()) {
    private val logger: Logger = LoggerFactory.getLogger(TransformingClassLoader::class.java)
    private val classLoadPerfCounter = PerfCounter("Loaded {} class data in {}s, Average load time {}ms")
    private val transformationPerfCounter =
        PerfCounter("Transformed {} classes in {}s, Average transformation time {}ms")

    fun getClassData(name: String): ClassData? {
        val normalName = name.replace(".", "/") + ".class"
        // getResourceAsStream since mixins require system resources as well
        return this.getResourceAsStream(normalName)?.use { ClassData(it.readAllBytes(), name) }
    }

    override fun getResourceAsStream(name: String): InputStream? {
        val mcRes = ModLoader.gameJar.jarFile.getJarEntry(name)
        if (mcRes != null) return ModLoader.gameJar.jarFile.getInputStream(mcRes)

        for (mod in ModLoader.discoverer.mods) {
            val entry = mod.jar.getJarEntry(name) ?: continue
            return mod.jar.getInputStream(entry)
        }

        return super.getResourceAsStream(name)
    }

    // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
    public override fun findClass(name: String): Class<*>? {
        synchronized(this.getClassLoadingLock(name)) {
            val classData = this.classLoadPerfCounter.timed { this.getClassData(name) }
            // TODO: optimize the parsing of every loaded class
            if (classData != null) {
                val bytes = this.transformationPerfCounter.timed {
                    ModLoader.transformer.transform(classData)
                    if (!classData.skip) {
                        classData.bytes
                    } else {
                        null
                    }
                }

                if (bytes != null) {
                    return this.defineClass(name, bytes, 0, bytes.size)
                }
            }
        }

        return super.findClass(name)
    }

    private fun tryResourceUrl(url: URL): URL? {
        try {
            val jarCon = url.openConnection() as JarURLConnection
            jarCon.jarEntry
            return url
        } catch (e: Exception) {
            return null
        }
    }

    override fun findResource(name: String): URL? {
        var targetUrl = this.tryResourceUrl(ModLoader.gameJar.getContentUrl(name))
        if (targetUrl != null) return targetUrl

        // if not a game class, attempt to load it from mod jars
        for (mod in ModLoader.discoverer.mods) {
            targetUrl = this.tryResourceUrl(mod.getContentUrl(name))

            if (targetUrl != null) return targetUrl
        }

        this.logger.warn("Could not locate resource {}. Here be dragons!!!", name)
        // if no mod jars had it then it doesn't exist in us
        return null
    }

    override fun findResources(name: String): Enumeration<URL> {
        val urls = mutableListOf<URL>()
        var targetUrl = this.tryResourceUrl(ModLoader.gameJar.getContentUrl(name))
        if (targetUrl != null) urls.add(targetUrl)

        for (mod in ModLoader.discoverer.mods) {
            targetUrl = this.tryResourceUrl(mod.getContentUrl(name))

            if (targetUrl != null) urls.add(targetUrl)
        }

        return Collections.enumeration(urls)

    }

    fun isClassLoaded(name: String): Boolean = synchronized(this.getClassLoadingLock(name)) {
        this.findLoadedClass(name) != null
    }

    companion object {
        init {
            registerAsParallelCapable()
        }
    }
}

class ClassData(initialBytes: ByteArray, val name: String, var skip: Boolean = false) {
    private var internalBytes: ByteArray? = initialBytes
        get() {
            if (field != null) return field
            val writer = MixinClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
            this.internalNode!!.accept(writer)
            this.internalNode = null
            return writer.toByteArray()
        }
    private var internalNode: ClassNode? = null
        get() {
            if (field != null) return field
            val reader = ClassReader(this.internalBytes!!)
            field = ClassNode()
            reader.accept(field, ClassReader.EXPAND_FRAMES)
            this.internalBytes = null
            return field
        }

    var bytes: ByteArray?
        get() = this.internalBytes
        set(bytes) {
            this.internalBytes = bytes
        }
    val node
        get() = this.internalNode!!
}

// message should have the following form: <text> {}(becomes action count) <text> {}(becomes total time in seconds) <text> {}(becomes average time in milliseconds)
class PerfCounter(private val message: String) {
    companion object {
        private val logger = LoggerFactory.getLogger(PerfCounter::class.java)
        private val counters: MutableList<PerfCounter> = mutableListOf()
        private val shutdownThread = Thread {
            this.counters.forEach(PerfCounter::printSummary)
        }

        init {
            shutdownThread.name = "Waiter"
            Runtime.getRuntime().addShutdownHook(this.shutdownThread)
        }
    }

    init {
        counters.add(this)
    }

    var totalDuration: Duration = Duration.ZERO
    var actionCount = 0

    inline fun <T> timed(action: () -> T): T {
        var res: T
        this.totalDuration += measureTime {
            res = action()
        }
        this.actionCount++
        return res
    }

    fun printSummary() {
        if (actionCount > 0) {
            val total = this.totalDuration.toDouble(DurationUnit.SECONDS)
            val avg = this.totalDuration.toDouble(DurationUnit.MILLISECONDS) / this.actionCount.toDouble()
            logger.info(message, this.actionCount, total, avg)
        } else {
            logger.error("Not enough actions occured.")
        }
    }
}