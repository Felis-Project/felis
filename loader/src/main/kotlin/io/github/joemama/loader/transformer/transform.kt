package io.github.joemama.loader.transformer

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.PerfCounter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URL
import java.util.*

sealed interface ClassRef {
    @JvmInline
    value class NodeRef internal constructor(override val node: ClassNode) : ClassRef {
        override fun nodeRef(): NodeRef = this

        override fun bytesRef(): BytesRef {
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            this.node.accept(writer)
            return BytesRef(writer.toByteArray())
        }

        override val bytes: ByteArray
            get() = throw IllegalAccessException("Nodes shouldn't be asked for bytes")
        override val isNodeRef: Boolean
            get() = true
        override val isBytesRef: Boolean
            get() = false
    }

    @JvmInline
    value class BytesRef internal constructor(override val bytes: ByteArray) : ClassRef {
        override fun nodeRef(): NodeRef {
            val reader = ClassReader(this.bytes)
            return NodeRef(ClassNode().also { reader.accept(it, ClassReader.EXPAND_FRAMES) })
        }

        override fun bytesRef(): BytesRef = this
        override val node: ClassNode
            get() = throw IllegalAccessException("Bytes shouldn't be asked for a node")
        override val isNodeRef: Boolean
            get() = false
        override val isBytesRef: Boolean
            get() = true
    }

    fun nodeRef(): NodeRef
    fun bytesRef(): BytesRef
    val node: ClassNode
    val bytes: ByteArray

    val isNodeRef: Boolean
    val isBytesRef: Boolean
}

data class ClassContainer(val name: String, private var ref: ClassRef, var skip: Boolean = false) : ClassRef {
    val internalName by lazy { this.name.replace(".", "/") }

    constructor(name: String, bytes: ByteArray) : this(name, ClassRef.BytesRef(bytes))

    // Always call when modifying bytes
    fun newBytes(bytes: ByteArray) {
        this.ref = ClassRef.BytesRef(bytes)
    }

    override fun nodeRef(): ClassRef.NodeRef {
        this.ref = this.ref.nodeRef()
        return this.ref as ClassRef.NodeRef
    }

    override fun bytesRef(): ClassRef.BytesRef {
        this.ref = this.ref.bytesRef()
        return this.ref as ClassRef.BytesRef
    }

    override val node: ClassNode
        get() = this.nodeRef().node
    override val bytes: ByteArray
        get() = this.bytesRef().bytes
    override val isNodeRef: Boolean
        get() = this.ref.isNodeRef
    override val isBytesRef: Boolean
        get() = this.ref.isBytesRef
}

interface Transformation {
    fun transform(container: ClassContainer)
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

    override fun transform(container: ClassContainer) {
        val name = container.name
        if (this.external.containsKey(name)) {
            for (t in this.external.get(name)) {
                this.logger.info("transforming $name with ${t.value.transformationName}")
                t.value.transform(container)
                if (container.skip) {
                    return
                }
            }
        }

        for (t in this.internal) {
            t.transform(container)

            if (container.skip) {
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

    private val contentCollection: ContentCollection by lazy {
        object : NestedContentCollection {
            override val children: Iterable<ContentCollection>
                get() = mutableListOf<ContentCollection>(ModLoader.gameJar).also { it.addAll(ModLoader.discoverer.mods) }
                    .also { it.addAll(ModLoader.discoverer.libs) }
        }
    }

    fun getClassData(name: String): ClassContainer? {
        val normalName = name.replace(".", "/") + ".class"
        // getResourceAsStream since mixins require system resources as well
        return this.getResourceAsStream(normalName)?.use { ClassContainer(name, it.readAllBytes()) }
    }

    override fun getResourceAsStream(name: String): InputStream? =
        this.contentCollection.openStream(name) ?: super.getResourceAsStream(name)

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

    override fun findResource(name: String): URL? {
        val targetUrl = this.contentCollection.getContentUrl(name)
        if (targetUrl != null) return targetUrl

        this.logger.warn("Could not locate resource {}. Here be dragons!!!", name)
        // if no mod jars had it then it doesn't exist in us
        return null
    }

    override fun findResources(name: String): Enumeration<URL> {
        val urls = mutableListOf<URL>()
        var targetUrl = ModLoader.gameJar.getContentUrl(name)
        if (targetUrl != null) urls.add(targetUrl)

        for (mod in ModLoader.discoverer.mods) {
            targetUrl = mod.getContentUrl(name)

            if (targetUrl != null) urls.add(targetUrl)
        }

        return Collections.enumeration(urls)
    }

    companion object {
        init {
            registerAsParallelCapable()
        }
    }
}

