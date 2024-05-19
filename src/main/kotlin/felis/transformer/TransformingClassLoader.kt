package felis.transformer

import felis.util.ClassInfo
import felis.util.ClassInfoSet
import felis.util.PerfCounter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URL
import java.util.*

/**
 * This class loader misuses the classloading delegation model to:
 * 1. Load mod classes
 * 2. Load game classes
 * 3. Load library classes
 * The normal java classpath isn't used for anything other than ignored packages/classes.
 * You can ignore a class or package using [IgnoreList]
 *
 * @param transformer the root transformer used by this class loader
 * @param contentCollection the root content collection
 * @author 0xJoeMama
 */
class TransformingClassLoader(
    private val transformer: Transformer,
    private val contentCollection: ContentCollection
) : ClassLoader(null) {
    private val logger: Logger = LoggerFactory.getLogger(TransformingClassLoader::class.java)
    private val classLoadPerfCounter = PerfCounter("Loaded {} classes in {}s. Average load time was {}ms", wait = true)
    val ignored = IgnoreList()
    val classInfoSet = ClassInfoSet { name ->
        val internal = name.replace('.', '/')
        this.getResourceAsStream("$internal.class")?.use(::ClassReader)?.let {
            ClassInfo(
                it.className,
                it.superName,
                it.interfaces.toMutableList(),
                it.access and Opcodes.ACC_INTERFACE != 0
            )
        } ?: throw ClassNotFoundException("Class $name was not found in current environment")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun readContainer(name: String): ClassContainer? {
        val normalName = name.replace(".", "/") + ".class"
        return this.getResourceAsStream(normalName)?.use { ClassContainer(it.readAllBytes(), name) }
    }

    override fun getResourceAsStream(name: String): InputStream? =
        this.contentCollection.openStream(name) ?: getSystemResourceAsStream(name)

    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        this.classLoadPerfCounter.timed {
            // first see if it's a platform class
            val clazz: Class<*> = try {
                getPlatformClassLoader().loadClass(name)
            } catch (e: ClassNotFoundException) {
                // then check if it has specifically been ignored
                // this currently only happens for the loader itself as well as the dependencies of the loader
                if (this.ignored.isIgnored(name)) {
                    // load it from the system in that case
                    findSystemClass(name)
                } else {
                    // otherwise check if we have already loaded it
                    var c: Class<*>? = this.findLoadedClass(name)
                    if (c == null) {
                        // if not try to load it now
                        c = this.findClass(name)
                        if (c == null) {
                            // if we can't relay to system
                            c = this.findSystemClass(name)
                            // if system can't find it :concern:
                            if (c == null) throw ClassNotFoundException("Couldn't find class $name")
                        }
                    }
                    c
                }
            }

            if (resolve) resolveClass(clazz)
            clazz
        }
    }

    /**
     * Attempts to load a class using this class loader.
     * This applies transformations to the class, even if it is not a game class.
     *
     * @param name the class name of the class separated by '.'
     */
    override fun findClass(name: String): Class<*>? {
        // we attempt to read a container, if we can't we know nothing about the class
        val classData = this.readContainer(name) ?: return null

        this.transformer.transform(classData)
        if (!classData.skip) {
            return this.defineClass(classData)
        }

        return null
    }

    /**
     * Defines a class as provided by the container parameter
     *
     * @param container the [ClassContainer] with the data of the target class
     */
    fun defineClass(container: ClassContainer): Class<*> {
        // defineClass is like amazingly slow. We are talking half our class loading time. So call it only in this rare case
        // TODO: Setup CodeSource/ProtectionDomain
        val bytes =
            container.modifiedBytes(this.classInfoSet) // this parses the class and resolves all modifications to it
        return this.defineClass(name, bytes, 0, bytes.size, null)
    }

    override fun findResource(name: String): URL? {
        val targetUrl = this.contentCollection.getContentUrl(name)
        if (targetUrl != null) return targetUrl
        this.logger.warn("Could not locate resource {}. Here be dragons!!!", name)
        return null
    }

    override fun findResources(name: String): Enumeration<URL> =
        Collections.enumeration(this.contentCollection.getContentUrls(name))

    companion object {
        init {
            registerAsParallelCapable()
        }
    }

    override fun toString(): String = "transforming-class-loader"
}