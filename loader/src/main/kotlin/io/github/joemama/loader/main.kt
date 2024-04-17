package io.github.joemama.loader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import io.github.joemama.loader.meta.*
import io.github.joemama.loader.side.Side
import io.github.joemama.loader.side.SideStrippingTransformation
import io.github.joemama.loader.transformer.*
import net.peanuuutz.tomlkt.Toml
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import java.lang.invoke.MethodType
import java.lang.invoke.MethodHandles

import org.objectweb.asm.Type
import org.slf4j.Logger
import java.io.*
import java.util.*
import javax.tools.*

interface LoaderPluginEntrypoint {
    fun onLoaderInit()
}

object ModLoader {
    val toml = Toml {
        ignoreUnknownKeys = true
    }
    private val logger: Logger = LoggerFactory.getLogger(ModLoader::class.java)
    lateinit var languageAdapter: DelegatingLanguageAdapter
        private set
    lateinit var discoverer: ModDiscoverer
        private set
    lateinit var gameJar: JarContentCollection
        private set
    lateinit var classLoader: TransformingClassLoader
        private set
    lateinit var transformer: Transformer
        private set
    lateinit var side: Side
        private set

    fun initLoader(mods: List<String>, sourceJar: String, side: Side, debugTransform: Boolean) {
        this.logger.info("starting mod loader")
        this.side = side // the physical side we are running on
        this.discoverer = ModDiscoverer(mods) // the object used to locate and initialize mods

        // register ourselves as a built-in mod
        this.discoverer.registerMod(
            Mod.from(
                EmptyContentCollection,
                ModLoader::class.java.classLoader
                    .getResourceAsStream("loader.toml")
                    ?.use { it.readAllBytes().decodeToString() }
                    ?: throw FileNotFoundException("Loader loader.toml was not found")
            ).getOrThrow()
        )

        this.gameJar = JarContentCollection(Paths.get(sourceJar)) // the jar the game is located in
        this.languageAdapter = DelegatingLanguageAdapter() // tool used to create instances of abstract objects
        this.transformer = Transformer() // tool that transforms classes passed into it using registered Transformations
        this.classLoader = TransformingClassLoader() // the class loader that uses everything in here to work

        this.languageAdapter.apply {
            registerAdapter(KotlinLanguageAdapter)
            registerAdapter(JavaLanguageAdapter)
        }

        this.transformer.apply {
            if (debugTransform) {
                registerTransformation(DebugTransformation)
            }
            registerTransformation(SideStrippingTransformation)
        }

        this.callEntrypoint("loader_plugin", LoaderPluginEntrypoint::onLoaderInit)
    }

    fun start(owner: String, method: String, desc: String, params: Array<String>) {
        this.logger.info("starting game")
        this.logger.debug("target game jars: {}", this.gameJar.path)
        this.logger.debug("game args: ${params.contentToString()}")
        val sw = StringWriter()
        sw.append("mods currently running: ")
        this.discoverer.forEach {
            sw.appendLine()
            sw.append("- ${it.meta.modid}: ${it.meta.version}")
        }
        this.logger.info(sw.toString())

        val mainClass = this.classLoader.loadClass(owner)
        val mainMethod = MethodHandles.lookup().findStatic(
            mainClass,
            method,
            MethodType.fromMethodDescriptorString(desc, null)
        )

        mainMethod.invokeExact(params)
    }


    inline fun <reified T> callEntrypoint(id: String, crossinline method: (T) -> Unit) {
        this.discoverer
            .asSequence()
            .flatMap { it.meta.entrypoints }
            .filter { it.id == id }
            .map { this.languageAdapter.createInstance<T>(it.path).getOrThrow() }
            .forEach { method(it) }
    }
}

internal class ModLoaderCommand : CliktCommand() {
    private val mods: List<String> by option("--mods")
        .help("All directories and jar files to look for mods in separated by ':'")
        .split(File.pathSeparator)
        .default(emptyList())
    private val gameJarPath: String by option("--source")
        .help("Define the source jar to run the game from")
        .required()
    private val gameArgs: String by option("--args")
        .help("Arguments passed into the main method of the game")
        .default("")
    private val printClassPath: Boolean by option("--print-cp")
        .help("Print the jvm classpath")
        .boolean()
        .default(false)
    private val debugTransformation: Boolean by option("--debug-t")
        .help("Apply a debug transformation at runtime")
        .boolean()
        .default(false)
    private val side: Side by option("--side").enum<Side> { it.name }.required()

    override fun run() {
        if (this.printClassPath) {
            val cp = System.getProperty("java.class.path").split(":")
            for (s in cp) {
                println(s)
            }
        }
        val mainClass = when (this.side) {
            Side.CLIENT -> "net.minecraft.client.main.Main"
            Side.SERVER -> "net.minecraft.server.Main"
        }
        ModLoader.initLoader(
            mods = this.mods,
            sourceJar = this.gameJarPath,
            side = this.side,
            debugTransform = this.debugTransformation
        )
        ModLoader.start(
            owner = mainClass,
            method = "main",
            desc = Type.getMethodDescriptor(Type.getType(Void.TYPE), Type.getType(Array<String>::class.java)),
            params = this.gameArgs.split(" ").toTypedArray()
        )
    }
}

fun main(args: Array<String>) {
    println("Modloader running using arguments: ${args.contentToString()}")
    ModLoaderCommand().main(args)
}
