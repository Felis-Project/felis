package felis

import felis.language.DelegatingLanguageAdapter
import felis.language.JavaLanguageAdapter
import felis.language.KotlinLanguageAdapter
import felis.language.LanguageAdapter
import felis.launcher.*
import felis.launcher.minecraft.MinecraftLauncher
import felis.meta.*
import felis.side.Side
import felis.side.SideStrippingTransformation
import felis.transformer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
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
     * The **physical** side the loader is running on.
     * Physical side, refers to the jar distribution used.
     * [Side.CLIENT] means the client jar downloaded by the launcher.
     * [Side.SERVER] means the server jar downloaded through the minecraft website
     *
     * @see Side for more info on sides
     * @see SideStrippingTransformation for more info on what a specific [Side] entails
     */
    val side: Side by OptionKey("felis.side") { enumValueOf(it) }
    val mods: List<Path> by OptionKey("felis.mods", DefaultValue.Value(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    val launcher: GameLauncher by OptionKey("felis.launcher", DefaultValue.Value(MinecraftLauncher())) {
        Class.forName(it).getDeclaredConstructor().newInstance() as GameLauncher
    }
    val printClassPath: Boolean by OptionKey("felis.print.cp", DefaultValue.Value(false), String::toBooleanStrict)
    val audit: Path? by OptionKey("felis.audit", DefaultValue.Value(null), Paths::get)
    val printResolutionStages by OptionKey("felis.print.res.stages", DefaultValue.Value(false), String::toBooleanStrict)

    /**
     * A language adapter, that contains all other language adapters.
     * Used when trying to call an entrypoint.
     *
     * @see LanguageAdapter
     */
    var languageAdapter = DelegatingLanguageAdapter(
        KotlinLanguageAdapter,
        JavaLanguageAdapter
    )

    /**
     * The currently running [GameInstance]
     *
     * @see ContentCollection
     */
    val game: GameInstance = this.launcher.instantiate(this.side)

    /**
     * The discoverer created by the loader, loads mods passed through the command line.
     *
     * @see ModDiscoverer for detail of the discovery process
     */
    lateinit var discoverer: ModDiscoverer
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
     * Central location for all resources that can be offered from the launch path.
     *
     * @see ContentCollection
     */
    lateinit var contentCollection: RootContentCollection
        private set

    var isAuditing = this.audit != null

    /**
     * Initialize this loader object.
     *
     * @param args arguments used to start the game up
     */
    fun initLoader(args: Array<String>) {
        this.logger.info("starting mod loader")
        this.discoverer = ModDiscoverer() // the object used to locate and initialize mods

        // run the default scanners
        this.discoverer.walkScanner(ClasspathScanner) // used primarily by development environments
        val directoryScanner = DirectoryScanner(this.mods)
        this.discoverer.walkScanner(directoryScanner) // used primarily by users
        this.discoverer.walkScanner(  // used in both cases, to allow jar inclusion
            JarInJarScanner(listOf(ClasspathScanner, directoryScanner))
        )
        this.discoverer.registerMod(this.game) // register the game
        // resolve all mods detected and create the initial modset
        this.discoverer.finish()
        // tool that transforms classes passed into it using registered Transformations
        this.transformer = Transformer(this.languageAdapter)
        // the transformer needs to be updated on mod changes
        this.discoverer.registerModSetHandler(this.transformer)
        // root location of all accessible loading path files
        this.contentCollection = RootContentCollection(this.discoverer, this.game)
        // the class loader that uses everything in here to work
        this.classLoader = TransformingClassLoader(this.transformer, this.contentCollection)
        this.classLoader.ignored.apply {
            ignorePackage("kotlin")
            ignorePackage("kotlinx")
            ignorePackage("org.objectweb.asm")
            ignorePackage("net.peanuuutz.tomlkt")
            ignorePackage("org.slf4j")
            ignorePackage("org.lwjgl")
            ignorePackage("io.github.z4kn4fein.semver")
            ignorePackage("felis.meta")
            ignorePackage("felis.side")
            ignorePackage("felis.transformer")
            ignorePackage("felis.launcher")
            ignorePackage("felis.language")
            ignorePackageAbsolute("felis")
        }

        // Register built-in transformations of the loader itself
        this.transformer.registerTransformation(SideStrippingTransformation)

        // call all loader plugin entrypoints after we set ourselves up
        this.callEntrypoint("loader_plugin", LoaderPluginEntrypoint::onLoaderInit)

        // the discoverer is done, since after plugins no one else can register scanners or mods
        this.discoverer.finish()
        // print the final mod set.
        val modsetInfo = buildString {
            append("mods currently running: ")
            for (mod in discoverer.mods) {
                appendLine()
                append("- $mod")
            }
        }
        this.logger.info(modsetInfo)

        val audit = this.audit
        if (audit == null) {
            // start the game using the main class from above
            this.game.start(this.logger, args)
        } else {
            // Audit game classes if that is what the user chose
            this.auditTransformations(audit)
        }
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
                        val container = this.transformer.transform(ClassContainer.new(oldBytes, name)) ?: continue

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
    inline fun <reified T, reified R> callEntrypoint(id: String, crossinline method: (T) -> R): List<R> =
        this.discoverer.mods
            .asSequence()
            .flatMap { it.entrypoints }
            .filter { it.id == id }
            .map { this.languageAdapter.createInstance(it.specifier, T::class.java).getOrThrow() }
            .map { method(it) }
            .toList()
}
