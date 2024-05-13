package felis.transformer

import felis.ModLoader
import felis.util.PerfCounter
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
 * The normal java classpath isn't used for anything other that ignored packages/classes.
 * You can ignore a class or package using [IgnoreForTransformations]
 *
 * @author 0xJoeMama
 */
class TransformingClassLoader : ClassLoader(getSystemClassLoader()) {
    private val logger: Logger = LoggerFactory.getLogger(TransformingClassLoader::class.java)
    private val classReadPerfCounter =
        PerfCounter("Read {} class containers in {}s, Average read time {}ms", wait = true)
    private val transformationPerfCounter =
        PerfCounter("Transformed {} classes in {}s, Average transformation time {}ms", wait = true)
    private val classLoadPerfCounter =
        PerfCounter("Loaded {} classes in {}s. Average load time was {}ms", wait = true)
    val ignored = IgnoreForTransformations()

    @Suppress("MemberVisibilityCanBePrivate")
    fun getClassData(name: String): ClassContainer? {
        val normalName = name.replace(".", "/") + ".class"
        // getResourceAsStream since mixins require system resources as well
        return this.getResourceAsStream(normalName)?.use { ClassContainer(name, it.readAllBytes()) }
    }

    override fun getResourceAsStream(name: String): InputStream? =
        RootContentCollection.openStream(name) ?: getSystemResourceAsStream(name)

    // TODO: On average twice the amount of time in findClass is used by loadClass. Figure out why that is happening
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        this.classLoadPerfCounter.timed {
            // first see if it's a platform class
            val clazz: Class<*> = try {
                getPlatformClassLoader().loadClass(name)
            } catch (e: ClassNotFoundException) {
                if (this.ignored.isIgnored(name)) {
                    findSystemClass(name)
                } else {
                    var c: Class<*>? = this.findLoadedClass(name)
                    if (c == null) {
                        c = this.findClass(name)
                        if (c == null) {
                            c = this.findSystemClass(name)
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

    // load class and apply defined transformations
    public override fun findClass(name: String): Class<*>? {
        val classData = this.classReadPerfCounter.timed { this.getClassData(name) }
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
                // define class is line amazingly slow. We are talking half our class loading time. So call it only in this rare case
                return this.defineClass(name, bytes, 0, bytes.size)
            }
        }

        return null
    }

    fun defineClass(container: ClassContainer): Class<*> =
        this.defineClass(container.name, container.bytes, 0, container.bytes.size)

    override fun findResource(name: String): URL? {
        val targetUrl = RootContentCollection.getContentUrl(name)
        if (targetUrl != null) return targetUrl

        this.logger.warn("Could not locate resource {}. Here be dragons!!!", name)
        return null
    }

    override fun findResources(name: String): Enumeration<URL> =
        Collections.enumeration(RootContentCollection.getContentUrls(name))

    companion object {
        init {
            registerAsParallelCapable()
        }
    }

    override fun toString(): String = "transforming-class-loader"
}