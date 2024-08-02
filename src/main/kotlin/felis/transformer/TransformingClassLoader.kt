package felis.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.util.*

/**
 * This class loader misuses(intentional) the classloading delegation model to load most classes.
 * By most I mean both library, mod and game classes, in no specific order.
 *
 * The normal java classpath isn't used for anything other than ignored packages/classes.
 * You can ignore a class or package using [IgnoreList]
 *
 * @param transformer the root transformer used by this class loader
 * @param contentCollection the root content collection
 * @author 0xJoeMama
 */
class TransformingClassLoader(
    private val transformer: Transformer,
    private val contentCollection: RootContentCollection
) : ClassLoader(null) {
    private val logger: Logger = LoggerFactory.getLogger(TransformingClassLoader::class.java)
    val ignored = IgnoreList()
    val classInfoSet = ClassInfoSet { name ->
        this.getResourceAsStream("${name.replace(".", "/")}.class")?.use(::ClassReader)?.let {
            ClassInfoSet.ClassInfo(
                it.className,
                it.superName,
                it.interfaces.toMutableList(),
                it.access and Opcodes.ACC_INTERFACE != 0
            )
        } ?: throw ClassNotFoundException("Class $name was not found in current environment")
    }

    @Suppress("unused") // external API
    fun unmodifiedClassNode(name: String): ClassNode =
        this.getResourceAsStream(name.replace(".", "/") + ".class")
            ?.use(::ClassReader)
            ?.let {
                val node = ClassNode()
                it.accept(node, ClassReader.EXPAND_FRAMES)
                node
            } ?: throw ClassNotFoundException("Class $name could not be found in the current environment")

    override fun getResourceAsStream(name: String): InputStream? =
        this.contentCollection.openStream(name) ?: getSystemResourceAsStream(name)

    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        // first see if it's a platform class
        val clazz: Class<*> = try {
            getPlatformClassLoader().loadClass(name)
        } catch (e: ClassNotFoundException) {
            // then check if it has specifically been ignored
            // this currently only happens for the loader itself as well as the dependencies of the loader
            if (this.ignored.isIgnored(name)) {
                // load it from the system in that case
                this.findSystemClass(name)
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

    /**
     * Attempts to load a class using this class loader.
     * This applies transformations to the class, even if it is not a game class.
     *
     * @param name the class name of the class separated by '.'
     *
     *
     */
    override fun findClass(name: String): Class<*>? {
        // normalize name
        val normalName = name.replace(".", "/") + ".class"
        // Get the container through the RootContentCollection.
        // If we cannot fetch it, this class is not under our jurisdiction(very rare case).
        val container = this.getResourceAsStream(normalName)
            ?.use { ClassContainer.new(it.readAllBytes(), name) }
            ?: return null
        // transform the class using the Transformer
        val newContainer = this.transformer.transform(container)

        // The following code is the implementation of ProtectionDomain
        // I honestly find ProtectionDomain kinda useless so it's been left out(since afaik it doesn't offer any gigantic benefits.
        /*
         val url = this.getResource(normalName)
         val codeSource = CodeSource(url, arrayOf<CodeSigner>())
         val prot = ProtectionDomain(codeSource, Permissions().also { it.add(AllPermission()) })
        */

        // defineClass is like amazingly slow. We are talking half our class loading time. So call it only in this rare case
        return if (!newContainer.skip) this.defineClass(newContainer) else null
    }

    /**
     * Defines a class as per the container parameter
     *
     * @param container the [ClassContainer] with the data of the target class
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun defineClass(container: ClassContainer): Class<*> {
        // this parses the class and resolves all modifications to it(slow)
        val bytes = container.modifiedBytes(this.classInfoSet)
        // this is also slow by default
        return this.defineClass(container.name, bytes, 0, bytes.size, null)
    }

    override fun findResource(name: String): URL? {
        val targetPath = this.contentCollection.getContentPath(name)
        try {
            if (targetPath != null) return targetPath.toUri().toURL()
        } catch (e: MalformedURLException) {
            this.logger.warn("Could not locate resource $name in top level hierarchy. Here be dragons!!!")
        }
        // this is the only place where URLs are handed out
        return URL.of(URI.create("cc:$name"), this.contentCollection)
    }

    override fun findResources(name: String): Enumeration<URL> =
        Collections.enumeration(Collections.singletonList(this.findResource(name)))

    companion object {
        init {
            registerAsParallelCapable()
        }
    }

    override fun toString(): String = "Felis transforming classloader"
}