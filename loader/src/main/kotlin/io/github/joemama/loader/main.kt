	package io.github.joemama.loader
	
	import com.github.ajalt.clikt.core.CliktCommand
	import com.github.ajalt.clikt.parameters.options.*
	import com.github.ajalt.clikt.parameters.types.boolean
	import com.github.ajalt.clikt.parameters.types.enum
	import io.github.joemama.loader.meta.ModDiscoverer
	import io.github.joemama.loader.mixin.MixinLoaderPlugin
	import io.github.joemama.loader.side.Side
	import io.github.joemama.loader.side.SideStrippingTransformation
	import io.github.joemama.loader.transformer.ClassContainer
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
	    override fun transform(container: ClassContainer) {
	        if (container.name == "net.minecraft.client.main.Main") {
	            val clazz = container.node
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
	    lateinit var discoverer: ModDiscoverer
	        private set
	    lateinit var gameJar: GameJar
	        private set
	    lateinit var classLoader: TransformingClassLoader
	        private set
	    lateinit var transformer: Transformer
	        private set
	    lateinit var side: Side
	
	    fun initLoader(mods: List<String>, sourceJar: String, side: Side, debugTransform: Boolean) {
	        this.logger.info("starting mod loader")
	        this.side = side
	        this.discoverer = ModDiscoverer(mods)
	        this.gameJar = GameJar(Paths.get(sourceJar))
	
	        this.classLoader = TransformingClassLoader()
	
	        this.transformer = Transformer()
	
	        this.transformer.apply {
	            if (debugTransform) {
	                registerInternal(DebugTransformation)
	            }
	            registerInternal(SideStrippingTransformation)
	        }
	
	        // TODO: Hardcoded for now
	        MixinLoaderPlugin().onLoaderInit()
	
	        this.callEntrypoint("loader_plugin", LoaderPluginEntrypoint::onLoaderInit)
	    }
	
	    fun start(owner: String, method: String, desc: String, params: Array<String>) {
	        this.logger.info("starting game")
	        this.logger.debug("target game jars: {}", this.gameJar.jarLoc)
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
	
	class ModLoaderCommand : CliktCommand() {
	    private val mods: List<String> by option("--mods")
	        .help("All directories and jar files to look for mods in separated by ':'")
	        .split(":")
	        .default(emptyList())
	    private val gameJarPath: String by option("--source")
	        .help("Define the source jar to run the game from")
	        .required()
	
	    @Suppress("unused") // TODO: Add lib handling
	    private val libs: List<String> by option("--libs")
	        .help("Define jars included at runtime but not transformed")
	        .split(":")
	        .default(emptyList())
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