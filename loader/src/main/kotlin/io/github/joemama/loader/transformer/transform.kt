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
	
	    fun getClassData(name: String): ClassContainer? {
	        val normalName = name.replace(".", "/") + ".class"
	        // getResourceAsStream since mixins require system resources as well
	        return this.getResourceAsStream(normalName)?.use { ClassContainer(name, it.readAllBytes()) }
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
	
	sealed interface ClassRef {
	    @JvmInline
	    value class NodeRef internal constructor(override val node: ClassNode) : ClassRef {
	        override fun nodeRef(): NodeRef = this
	
	        override fun bytesRef(): BytesRef {
	            val writer = MixinClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
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