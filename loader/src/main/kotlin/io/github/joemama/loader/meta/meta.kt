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
import org.apache.commons.io.file.AccumulatorPathVisitor
import org.slf4j.LoggerFactory

import java.nio.file.*
import kotlin.io.path.name

internal val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)

data class Mod(val contentCollection: ContentCollection, val meta: ModMeta) : ContentCollection by contentCollection {
    companion object {
        fun parse(contentCollection: ContentCollection): Mod {
            val metaToml = contentCollection.withStream("mods.toml") {
                String(it.readAllBytes())
            }!! // all mods must provide a mods.toml file
            val meta = Toml.decodeFromString<ModMeta>(metaToml)
            return Mod(contentCollection, meta)
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

        for (file in this.modPathsSplit) {
            val acc = AccumulatorPathVisitor()
            Files.walkFileTree(file, acc)
            for (candidate in acc.fileList) {
                perfcounter.timed {
                    val contentCollection = JarContentCollection(candidate.toFile())
                    try {
                        val mod = Mod.parse(contentCollection)
                        this.internalMods.add(mod)
                    } catch (e: SerializationException) {
                        throw IllegalArgumentException("Mod candidate ${candidate.name} had a malformatted mods.toml file")
                    } catch (e: Exception) {
                        this.libs.add(contentCollection)
                    }
                }
            }
        }

        perfcounter.printSummary()
    }

    fun registerMod(mod: Mod) = this.internalMods.add(mod)
}

// TODO: change "class" name
@Serializable
data class Entrypoint(
    val id: String,
    @Deprecated(message = "will soon change name") @SerialName("class") val clazz: String
)

@Serializable
data class Transform(
    val name: String,
    val target: String,
    @Deprecated(message = "will soon change name") @SerialName("class") val clazz: String
)

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
