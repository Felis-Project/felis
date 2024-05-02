package felis.transformer

import felis.ModLoader
import felis.PerfCounter
import felis.asm.ClassScope
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URL
import java.util.*

sealed interface ClassRef {
    @JvmInline
    value class NodeRef(override val node: ClassNode) : ClassRef {
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
    value class BytesRef(override val bytes: ByteArray) : ClassRef {
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

data class ClassContainer internal constructor(
    override val name: String,
    private var ref: ClassRef,
    var skip: Boolean = false
) : ClassRef, ClassScope {
    override val internalName by lazy { this.name.replace(".", "/") }

    constructor(name: String, ref: ClassRef) : this(name, ref, false)
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

fun interface Transformation {
    fun transform(container: ClassContainer)

    data class Named(val name: String, val del: Transformation) : Transformation by del
}

class Transformer : Transformation {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)

    // we have to store lazies to allow custom language adapters to work
    private val external: Map<String, List<Lazy<Transformation.Named>>> = ModLoader.discoverer
        .flatMap { it.meta.transforms }
        .groupBy { it.target }
        .mapValues { (_, transformations) ->
            transformations.map {
                lazy {
                    Transformation.Named(
                        it.name,
                        ModLoader.languageAdapter.createInstance<Transformation>(it.path).getOrThrow()
                    )
                }
            }
        }
    private val internal = mutableListOf<Transformation>()

    fun registerTransformation(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(container: ClassContainer) {
        val name = container.name
        if (this.external.containsKey(name)) {
            for (t in this.external.getOrDefault(name, emptyList())) {
                this.logger.info("transforming $name with ${t.value.name}")
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
    private val classLoadPerfCounter = PerfCounter("Loaded {} class data in {}s, Average load time {}ms", wait = true)
    private val transformationPerfCounter =
        PerfCounter("Transformed {} classes in {}s, Average transformation time {}ms", wait = true)

    private val contentCollection: ContentCollection by lazy {
        NestedContentCollection(
            buildList {
                add(ModLoader.gameJar) // needs to be first because minecrafthonk
                addAll(ModLoader.discoverer)
                addAll(ModLoader.discoverer.libs)
            }
        )
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

        return null
    }

    fun defineClass(container: ClassContainer): Class<*> =
        this.defineClass(container.name, container.bytes, 0, container.bytes.size)

    override fun findResource(name: String): URL? {
        val targetUrl = this.contentCollection.getContentUrl(name)
        if (targetUrl != null) return targetUrl

        this.logger.warn("Could not locate resource {}. Here be dragons!!!", name)
        // if no mod jars had it then it doesn't exist in us
        return null
    }

    override fun findResources(name: String): Enumeration<URL> =
        Collections.enumeration(this.contentCollection.getContentUrls(name))

    companion object {
        init {
            registerAsParallelCapable()
        }
    }
}
