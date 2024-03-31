package io.github.joemama.loader

import io.github.joemama.loader.meta.ModDiscoverer
import io.github.joemama.loader.mixin.MixinLoaderPlugin
import io.github.joemama.loader.transformer.ClassData
import org.slf4j.LoggerFactory

import java.net.URL
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Path
import java.lang.invoke.MethodType
import java.lang.invoke.MethodHandles

import io.github.joemama.loader.transformer.TransformingClassLoader
import io.github.joemama.loader.transformer.Transformation
import io.github.joemama.loader.transformer.Transformer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.slf4j.Logger
import java.io.PrintStream
import java.util.jar.JarFile

interface LoaderPluginEntrypoint {
    fun onLoaderInit()
}

data class GameJar(val jarLoc: Path) {
    val jarFile: JarFile = JarFile(jarLoc.toFile())
    private val absolutePath by lazy { this.jarLoc.toAbsolutePath() }
    private val jarUri: String by lazy {
        URI.create("jar:${this.absolutePath.toUri().toURL()}!/").toString()
    }

    fun getContentUrl(name: String): URL = URI.create(this.jarUri + name).toURL()
}

object DebugTransformation : Transformation {
    private val logger = LoggerFactory.getLogger(DebugTransformation::class.java)
    override fun transform(classData: ClassData) {
        if (classData.name == "net.minecraft.client.main.Main") {
            val clazz = classData.node
            this.logger.info("Transforming $clazz with DebugTransformation")
            val mainMethod = clazz.methods.first {
                it.name == "main" && it.desc == Type.getMethodDescriptor(
                    Type.VOID_TYPE,
                    Type.getType(Array<String>::class.java)
                )
            }

            val res = InsnList().apply {
                add(
                    FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "java/lang/System",
                        "out",
                        Type.getDescriptor(PrintStream::class.java)
                    )
                )
                add(
                    LdcInsnNode("Hello from Debug Transformation")
                )
                add(
                    MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        Type.getType(PrintStream::class.java).internalName,
                        "println",
                        Type.getMethodDescriptor(
                            Type.VOID_TYPE, Type.getType(String::class.java)
                        )
                    )
                )
            }
            mainMethod.instructions.insert(mainMethod.instructions.get(0), res)
        }
    }
}

object ModLoader {
    private val logger: Logger = LoggerFactory.getLogger(ModLoader::class.java)
    private lateinit var modDir: String
    private lateinit var gameJarPath: String
    lateinit var discoverer: ModDiscoverer
        private set
    lateinit var gameJar: GameJar
        private set
    lateinit var classLoader: TransformingClassLoader
        private set
    lateinit var transformer: Transformer
        private set
    private var debugTransformer: Boolean = false

    fun parseArgs(args: Array<String>): Array<String> {
        var gameJarPath: String? = null
        var modDir: String? = null
        val newArgs = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--print-cp" -> {
                    val cp = System.getProperty("java.class.path").split(":")
                    for (s in cp) {
                        println(s)
                    }
                }

                "--source" -> {
                    if (i + 1 > args.size - 1) throw IllegalArgumentException("Expected a jar file location to be provided")
                    gameJarPath = args[i + 1]
                    i++
                }

                "--mods" -> {
                    if (i + 1 > args.size - 1) throw IllegalArgumentException("Expected a mod directory to be provided")
                    modDir = args[i + 1]
                    i++
                }

                "--debug-t" -> {
                    this.debugTransformer = true
                }

                else -> {
                    newArgs.add(args[i])
                }
            }
            i++
        }

        this.gameJarPath = gameJarPath ?: throw IllegalArgumentException("Provide a source jar with --source")
        this.modDir = modDir ?: throw IllegalArgumentException("Provide a mod directory with --mods")
        return newArgs.toTypedArray()
    }

    fun initLoader() {
        this.logger.info("starting mod loader")
        this.discoverer = ModDiscoverer(this.modDir)
        this.gameJar = GameJar(Paths.get(this.gameJarPath))

        this.classLoader = TransformingClassLoader()

        this.transformer = Transformer()

        this.transformer.apply {
            if (this@ModLoader.debugTransformer) {
                registerInternal(DebugTransformation)
            }
        }

        // TODO: Hardcoded for now
        MixinLoaderPlugin().onLoaderInit()

        this.callEntrypoint("loader_plugin", LoaderPluginEntrypoint::onLoaderInit)
    }

    fun start(owner: String, method: String, desc: String, params: Array<String>) {
        this.logger.info("starting game")
        this.logger.debug("target game jars: ${this.gameJarPath}")
        this.logger.debug("game args: ${params.contentToString()}")

        val mainClass = this.classLoader.loadClass(owner)
        val mainMethod = MethodHandles.lookup().findStatic(
            mainClass,
            method,
            MethodType.fromMethodDescriptorString(desc, null)
        )

        mainMethod.invokeExact(params)
    }

    inline fun <reified T> callEntrypoint(id: String, crossinline method: (T) -> Unit) {
        this.discoverer.mods
            .flatMap { it.meta.entrypoints }
            .filter { it.id == id }
            .map { Class.forName(it.clazz, true, this.classLoader).getDeclaredConstructor().newInstance() as T }
            .forEach { method(it) }
    }
}

fun main(args: Array<String>) {
    val newArgs = ModLoader.parseArgs(args)
    ModLoader.initLoader()
    ModLoader.start(
        owner = "net.minecraft.client.main.Main",
        method = "main",
        desc = Type.getMethodDescriptor(Type.getType(Void.TYPE), Type.getType(Array<String>::class.java)),
        params = newArgs
    )
}
