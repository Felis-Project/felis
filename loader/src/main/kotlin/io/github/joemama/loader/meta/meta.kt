package io.github.joemama.loader.meta

import io.github.joemama.loader.PerfCounter
import io.github.joemama.loader.transformer.ContentCollection
import io.github.joemama.loader.transformer.JarContentCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable
import org.slf4j.LoggerFactory

import java.io.File

import java.io.FileFilter
import java.nio.file.Paths

internal val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)

data class Mod(val contentCollection: ContentCollection, val meta: ModMeta) : ContentCollection by contentCollection {
    companion object {
        fun parse(file: File): Mod {
            val modContentCollection = JarContentCollection(file)
            val metaToml = modContentCollection.withStream("mods.toml") {
                String(it.readAllBytes())
            }!! // all mods must provide a mods.toml file
            val meta = Toml.decodeFromString<ModMeta>(metaToml)
            return Mod(modContentCollection, meta)
        }
    }
}

class ModDiscoverer(modPaths: List<String>) {
    private val modPathsSplit = modPaths.map { Paths.get(it) }
    private val internalMods: MutableList<Mod> = mutableListOf()
    val mods: Iterable<Mod>
        get() = this.internalMods
    val libs = mutableListOf<JarContentCollection>()

    init {
        logger.info("mod discovery running for files $modPaths")
        val perfcounter = PerfCounter("discovered {} mods in {}s. Average mod load time was {}ms")

        for (file in this.modPathsSplit.map { it.toFile() }) {
            if (file.isDirectory()) {
                file.mkdirs()

                for (jarfile in file.listFiles(FileFilter { !it.isDirectory() })!!) {
                    perfcounter.timed {
                        try {
                            this.internalMods.add(Mod.parse(jarfile))
                        } catch (e: SerializationException) {
                            throw IllegalArgumentException("File ${jarfile.name} has a malformatted mods.toml file")
                        } catch (e: Exception) {
                            this.libs.add(JarContentCollection(jarfile))
                        }
                    }
                }
            } else {
                perfcounter.timed {
                    try {
                        this.internalMods.add(Mod.parse(file))
                    } catch (e: SerializationException) {
                        throw IllegalArgumentException("File ${file.name} has a malformatted mods.toml file")
                    } catch (e: Exception) {
                        this.libs.add(JarContentCollection(file))
                    }
                }
            }
        }

        perfcounter.printSummary()
    }

    fun registerMod(mod: Mod) = this.internalMods.add(mod)
}

@Serializable
data class Entrypoint(val id: String, @SerialName("class") val clazz: String)

@Serializable
data class Transform(val name: String, val target: String, @SerialName("class") val clazz: String)

@Serializable
data class Mixin(val path: String)

// TODO: Find what other info we should hold
// TODO: Allow for others to get data from the mods.toml file
@Serializable
data class ModMeta(
    val name: String,
    val version: String,
    val description: String = "",
    val entrypoints: List<Entrypoint> = listOf(),
    val modid: String,
    val transforms: List<Transform> = listOf(),
    val mixins: List<Mixin> = listOf(),
    val extra: TomlTable = TomlTable.Empty
)
