	package io.github.joemama.loader.meta
	
	import kotlinx.serialization.SerialName
	import kotlinx.serialization.Serializable
	import kotlinx.serialization.decodeFromString
	import net.peanuuutz.tomlkt.Toml
	import org.slf4j.LoggerFactory
	
	import java.io.File
	
	import java.io.FileFilter
	import java.util.jar.JarFile
	import java.nio.file.Paths
	import java.net.URL
	import java.net.URI
	
	internal val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)
	
	data class Mod(val jar: JarFile, val meta: ModMeta) {
	    companion object {
	        fun parse(file: File): Mod? = try {
	            val modJar = JarFile(file)
	            val modMeta = modJar.getJarEntry("mods.toml")
	            val metaToml = modJar.getInputStream(modMeta)!!.use {
	                val mf = String(it.readAllBytes())
	                mf
	            }
	
	            try {
	                val meta = Toml.decodeFromString<ModMeta>(metaToml)
	                Mod(modJar, meta)
	            } catch (e: Exception) {
	                logger.error("File ${file.name} had a malformatted mods.toml file")
	                e.printStackTrace()
	                null
	            }
	        } catch (e: Exception) {
	            logger.error("file ${file.name} could not be parsed as a mod file: ${e.message}")
	            e.printStackTrace()
	            null
	        }
	    }
	
	    private val path by lazy {
	        Paths.get(jar.name).toAbsolutePath()
	    }
	    private val url: String by lazy {
	        URI("jar:" + this.path.toUri().toString() + "!/").toString()
	    }
	
	    fun getContentUrl(name: String): URL = URI.create(this.url + name).toURL()
	}
	
	class ModDiscoverer(modPaths: List<String>) {
	    private val modPathsSplit = modPaths.map { Paths.get(it) }
	    val mods: List<Mod>
	
	    init {
	        logger.info("mod discovery running for files $modPaths")
	        val modList = mutableListOf<Mod>()
	
	        for (file in this.modPathsSplit.map { it.toFile() }) {
	            if (file.isDirectory()) {
	                file.mkdirs()
	                modList.addAll(
	                    file.listFiles(FileFilter { !it.isDirectory() })?.mapNotNull { Mod.parse(it) }
	                        ?: throw IllegalStateException("For some reason we got a null result")
	                )
	            } else {
	                Mod.parse(file)?.let { modList.add(it) }
	            }
	        }
	
	        this.mods = modList
	
	        logger.info("discovered ${mods.size} mod files")
	    }
	}
	
	@Serializable
	data class Entrypoint(val id: String, @SerialName("class") val clazz: String)
	
	@Serializable
	data class Transform(val name: String, val target: String, @SerialName("class") val clazz: String)
	
	@Serializable
	data class Mixin(val path: String)
	
	// TODO: Find what other info we should hold
	@Serializable
	data class ModMeta(
	    val name: String,
	    val version: String,
	    val description: String = "",
	    val entrypoints: List<Entrypoint> = listOf(),
	    val modid: String,
	    val transforms: List<Transform> = listOf(),
	    val mixins: List<Mixin> = listOf()
	)
