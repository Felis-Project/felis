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
import java.net.JarURLConnection
import java.net.URL
import java.util.*

interface Transformation {
    fun transform(clazz: ClassNode, name: String)
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

    override fun transform(clazz: ClassNode, name: String) {
        if (this.external.containsKey(name)) {
            for (t in this.external.get(name)) {
                this.logger.info("transforming $name with ${t.value.transformationName}")
                t.value.transform(clazz, name)
            }
        }

        for (t in this.internal) {
            t.transform(clazz, name)
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

    fun getClassNode(name: String): ClassNode? {
        val normalName = name.replace(".", "/") + ".class"
        // getResourceAsStream since mixins require system resources as well
        return this.getResourceAsStream(normalName)?.use {
            val classReader = ClassReader(it)
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES)
            classNode
        }
    }

    // we are given a class that parent loaders couldn't load. It's our turn to load it using the gameJar
    public override fun findClass(name: String): Class<*>? {
        synchronized(this.getClassLoadingLock(name)) {
            val classNode = this.getClassNode(name)

            // TODO: optimize the parsing of every loaded class
            if (classNode != null) {
                ModLoader.transformer.transform(classNode, name)

                // MixinClassWriter properly implements getCommonSuperClass without loading the class (used by COMPUTE_FRAMES)
                // We may need to do that the same using ASM metadata. For now just using Mixin's implementation
                val classWriter = MixinClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                classNode.accept(classWriter)

                return classWriter.toByteArray().let {
                    this.defineClass(name, it, 0, it.size)
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

