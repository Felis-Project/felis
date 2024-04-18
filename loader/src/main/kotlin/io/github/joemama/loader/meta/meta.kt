package io.github.joemama.loader.meta

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.PerfCounter
import io.github.joemama.loader.transformer.ContentCollection
import io.github.joemama.loader.transformer.JarContentCollection
import kotlinx.serialization.*
import net.peanuuutz.tomlkt.TomlTable
import org.slf4j.LoggerFactory

import java.nio.file.*
import kotlin.io.path.name
import kotlin.streams.asSequence

open class ModDiscoveryException(msg: String) : Exception(msg)
class ModMetaException(msg: String) : ModDiscoveryException(msg)
object NotAMod : Throwable() {
    private fun readResolve(): Any = NotAMod
}

data class Mod(val contentCollection: ContentCollection, val meta: ModMeta) : ContentCollection by contentCollection {
    companion object {
        fun from(contentCollection: ContentCollection): Result<Mod> {
            val metaToml = contentCollection.withStream("mods.toml") {
                String(it.readAllBytes())
            } ?: return Result.failure(NotAMod) // all mods must provide a mods.toml
            return from(contentCollection, metaToml)
        }

        fun from(contentCollection: ContentCollection, metaToml: String): Result<Mod> = runCatching {
            val toml = ModLoader.toml.parseToTomlTable(metaToml)
            val meta = ModLoader.toml.decodeFromTomlElement(ModMeta.serializer(), toml)
            meta.toml = toml
            Mod(contentCollection, meta)
        }
    }
}

class ModDiscoverer(modPaths: List<String>) : Iterable<Mod> {
    // TODO: Use Delegates.observable to automatically allow transformer to pick up changes
    private val mods: MutableList<Mod> = mutableListOf()
    val libs = mutableListOf<JarContentCollection>()
    private val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)

    init {
        this.logger.info("mod discovery running for files $modPaths")
        val perfcounter = PerfCounter()

        // behold the limits of my functional programming ability
        val (mods, libs) = modPaths
            .asSequence()
            .map { Paths.get(it) }
            .flatMap { Files.walk(it).asSequence() }
            .map { it.toFile() }
            .map { JarContentCollection(it) }
            .fold(Pair(mutableListOf<Mod>(), mutableListOf<JarContentCollection>())) { acc, contentCollection ->
                val (mods, libs) = acc
                perfcounter.timed {
                    Mod.from(contentCollection)
                        .onSuccess { mods.add(it) }
                        .onFailure {
                            when (it) {
                                is SerializationException -> throw ModMetaException(
                                    "mod candidate ${contentCollection.path.name} had a malformatted loader.toml file"
                                ).initCause(it)

                                is NotAMod -> libs.add(contentCollection)

                                else -> throw ModDiscoveryException(
                                    "encountered an issue while attempting to discover candidate ${contentCollection.path.name}"
                                ).initCause(it)
                            }
                        }
                }
                acc
            }


        this.mods.addAll(mods)
        this.libs.addAll(libs)

        perfcounter.printSummary { _, total, average ->
            this.logger.info("discovered ${mods.size} mods in ${total}s. Average discovery time was ${average}ms")
        }
    }

    fun registerMod(mod: Mod) = this.mods.add(mod)
    override fun iterator(): Iterator<Mod> = this.mods.iterator()
}

@Serializable
data class Entrypoint(
    val id: String,
    val path: String
)

@Serializable
data class Transform(
    val name: String,
    val target: String,
    val path: String
)

@Serializable
data class ModMeta(
    val name: String,
    val modid: String,
    val version: String,
    val description: String = "",
    val entrypoints: List<Entrypoint> = listOf(),
    val transforms: List<Transform> = listOf(),
) {
    @Transient
    var toml: TomlTable = TomlTable.Empty
        internal set
}