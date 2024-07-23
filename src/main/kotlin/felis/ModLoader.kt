package felis

import felis.language.DelegatingLanguageAdapter
import felis.language.JavaLanguageAdapter
import felis.language.KotlinLanguageAdapter
import felis.language.LanguageAdapter
import felis.launcher.GameInstance
import felis.launcher.GameLauncher
import felis.meta.*
import felis.side.Side
import felis.side.SideStrippingTransformation
import felis.transformer.*
import org.objectweb.asm.Type
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

/**
 * The central object of the mod loading process.
 * This object is first created at startup, initialized only once through the [main] method
 * and then used for the rest of the loading process.
 *
 * @author 0xJoeMama
 */
@Suppress("MemberVisibilityCanBePrivate")
object ModLoader {
    /**
     * Logger
     */
    private val logger: Logger = LoggerFactory.getLogger(ModLoader::class.java)

    /**
     * A language adapter, that contains all other language adapters.
     * Used when trying to call an entrypoint.
     *
     * @see LanguageAdapter
     */
    lateinit var languageAdapter: DelegatingLanguageAdapter
        private set

    /**
     * The discoverer created by the loader, loads mods passed through the command line.
     *
     * @see ModDiscoverer for detail of the discovery process
     */
    lateinit var discoverer: ModDiscoverer
        private set

    /**
     * A [JarContentCollection] referring to the game jar the loader is running on.
     *
     * @see ContentCollection for more info on [ContentCollection]s
     */
    lateinit var game: GameInstance
        private set

    /**
     * The classloading brain of the whole loader
     *
     * @see TransformingClassLoader
     */
    lateinit var classLoader: TransformingClassLoader
        private set

    /**
     * Central transformation, containing all others as children
     *
     * @see Transformer
     */
    lateinit var transformer: Transformer
        private set

    /**
     * The **physical** side the loader is running on.
     * Physical side, refers to the jar distribution used.
     * [Side.CLIENT] means the client jar downloaded by the launcher.
     * [Side.SERVER] means the server jar downloaded through the minecraft website
     *
     * @see Side for more info on sides
     * @see SideStrippingTransformation for more info on what a specific [Side] entails
     */
    lateinit var side: Side
        private set

    var isAuditing = false
        private set

    /**
     * Initialize this loader object.
     *
     * @param mods a list of all jars this loader will attempt to load as mods. See [ModDiscoverer] for more info on how mods are loaded
     * @param launcher the launcher for the game this loader is running
     * @param side the **physical** side the loader is running on
     * @param audit the path to output audited classes or null if we are not auditing
     */
    fun initLoader(mods: List<Path>, side: Side, launcher: GameLauncher, gameArgs: Array<String>, audit: Path?) {
        this.logger.info("starting mod loader")
        this.side = side // the physical side we are running on
        this.discoverer = ModDiscoverer() // the object used to locate and initialize mods

        // run the default scanners
        this.discoverer.walkScanner(ClasspathScanner) // used primarily by development environments
        val directoryScanner = DirectoryScanner(mods)
        this.discoverer.walkScanner(directoryScanner) // used primarily by users
        this.discoverer.walkScanner(JarInJarScanner(listOf(ClasspathScanner, directoryScanner)))
        // tool used to create instances of abstract objects
        this.languageAdapter = DelegatingLanguageAdapter(
            KotlinLanguageAdapter,
            JavaLanguageAdapter
        )
        this.game = launcher.instantiate(this.side, gameArgs) // create the instance of the game
        this.discoverer.registerMod(this.game) // register the game
        // resolve all mods detected and create the initial modset
        this.discoverer.finish()
        // tool that transforms classes passed into it using registered Transformations
        this.transformer = Transformer(this.discoverer, this.languageAdapter)
        // the class loader that uses everything in here to work
        this.classLoader = TransformingClassLoader(this.transformer, RootContentCollection(this.discoverer))
        this.classLoader.ignored.apply {
            ignorePackage("kotlin")
            ignorePackage("kotlinx")
            ignorePackage("org.objectweb.asm")
            ignorePackage("net.peanuuutz.tomlkt")
            ignorePackage("org.slf4j")
            ignorePackage("org.lwjgl")
            ignorePackage("io.github.z4kn4fein.semver")
            ignorePackageAbsolute("felis")
            ignorePackageAbsolute("felis.meta")
            ignorePackageAbsolute("felis.side")
            ignorePackageAbsolute("felis.transformer")
            ignorePackageAbsolute("felis.launcher")
            ignorePackageAbsolute("felis.language")
            ignorePackageAbsolute("felis.util")
        }

        // Register built-in transformations of the loader itself
        this.transformer.registerTransformation(SideStrippingTransformation)

        // call all loader plugin entrypoints after we set ourselves up
        this.callEntrypoint("loader_plugin", LoaderPluginEntrypoint::onLoaderInit)

        // the discoverer is done, since after plugins no one else can register scanners or mods
        this.discoverer.finish()

        if (audit == null) {
            // start the game using the main class from above
            this.call(
                owner = this.game.mainClass,
                method = "main",
                desc = Type.getMethodDescriptor(Type.getType(Void.TYPE), Type.getType(Array<String>::class.java)),
                params = this.game.args
            )
        } else {
            // Audit game classes if that is what the user chose
            this.auditTransformations(audit)
        }
    }

    /**
     * Run a specific *main* method as specified by the Java Standard.
     *
     * @param owner the name of the class that contains the method
     * @param method the name of the method. Usually this would be `main`
     * @param desc the descriptor of the method. Usually this would be `([Ljava/lang/String;)V`
     * @param params the arguments passed into the method
     */
    fun call(owner: String, method: String, desc: String, params: Array<String>) {
        this.logger.info("starting game")
        this.logger.debug("target game jars: {}", this.game.path)
        this.logger.debug("game args: ${params.contentToString()}")

        // create a mod list.
        val sw = StringWriter()
        sw.append("mods currently running: ")
        this.discoverer.mods.forEach {
            sw.appendLine()
            sw.append("- $it")
        }
        this.logger.info(sw.toString())

        val mainClass = this.classLoader.loadClass(owner)
        // using MethodLookup because technically speaking it's better that reflection
        val mainMethod = MethodHandles.publicLookup().`in`(mainClass).findStatic(
            mainClass,
            method,
            MethodType.fromMethodDescriptorString(desc, null)
        )

        this.logger.debug("Calling $owner#main")
        // finally call the method
        mainMethod.invoke(params)
    }

    /**
     * Force all game classes to be transformed.
     * Goes through all classes of the [ModLoader.game] reads them, and then applies all registered [Transformation]s to them.
     * Outputs classes in the specified jar file.
     *
     * @param outputPath the path to a jar where transformed classes will be output
     */
    private fun auditTransformations(outputPath: Path) {
        this.isAuditing = true
        this.logger.warn("Auditing game jar ${this.game.path}")
        FileSystems.newFileSystem(outputPath.createParentDirectories(), mapOf("create" to "true")).use { outputJar ->
            FileSystems.newFileSystem(this.game.path).use { gameJar ->
                for (file in Files.walk(gameJar.getPath("/"))) {
                    if (file.extension == "class") {
                        val name = file.pathString.substring(1).replace("/", ".").removeSuffix(".class")
                        val oldBytes = Files.newInputStream(file).use { it.readBytes() }
                        val container = ClassContainer(oldBytes, name)
                        this.transformer.transform(container)

                        if (container.skip) continue

                        val output = outputJar.getPath("/").resolve(file.pathString)
                        this.logger.trace("Auditing $name")

                        output.createParentDirectories()
                        Files.newOutputStream(
                            output,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE
                        ).use { it.write(container.modifiedBytes(this.classLoader.classInfoSet)) }
                    } else if (!file.isDirectory()) {
                        val output = outputJar.getPath("/").resolve(file.pathString)
                        output.createParentDirectories()
                        Files.copy(
                            file,
                            output,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES
                        )
                    }
                }
            }
        }
    }

    /**
     * Call the entrypoint specified by the given id.
     * Entrypoint IDs don't need to be specifically registered.
     * Entrypoints need to be registered in the [ModMetadataSchemaV1.entrypoints] list.
     *
     * @see ModMetadataSchemaV1 for the mod metadata schema
     *
     * @param T the type of the entrypoint
     * @param R the return type of the entrypoint method call
     * @param id the id of the entrypoint
     * @param method the method to run on objects of the [T] type
     *
     * @return a list with the results of calling all the entrypoints
     */
    @Suppress("MemberVisibilityCanBePrivate")
    inline fun <reified T, reified R> callEntrypoint(id: String, crossinline method: (T) -> R): List<R> =
        this.discoverer.mods
            .asSequence()
            .flatMap { it.entrypoints }
            .filter { it.id == id }
            .map { this.languageAdapter.createInstance(it.specifier, T::class.java).getOrThrow() }
            .map { method(it) }
            .toList()
}
