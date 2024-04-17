package io.github.joemama.loader.meta

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.PerfCounter
import io.github.joemama.loader.transformer.ContentCollection
import io.github.joemama.loader.transformer.JarContentCollection
import kotlinx.serialization.*
import net.peanuuutz.tomlkt.TomlTable
import org.apache.commons.io.file.AccumulatorPathVisitor
import org.slf4j.LoggerFactory

import java.nio.file.*
import kotlin.io.path.name

internal val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)

class ModMetaException(msg: String) : Exception(msg)
object NotAMod : Throwable() {
    private fun readResolve(): Any = NotAMod
}

data class Mod(val contentCollection: ContentCollection, val meta: ModMeta) : ContentCollection by contentCollection {
    companion object {
        fun from(contentCollection: ContentCollection): Result<Mod> {
            val metaToml = contentCollection.withStream("mods.toml") {
                String(it.readAllBytes())
            } ?: return Result.failure(NotAMod) // all mods must provide a mods.toml file
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
    private val modPathsSplit = modPaths.map { Paths.get(it) }
    private val mods: MutableList<Mod> = mutableListOf()
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
                    Mod.from(contentCollection)
                        .onSuccess { this.mods.add(it) }
                        .onFailure {
                            when (it) {
                                is SerializationException -> {
                                    val us =
                                        ModMetaException("Mod candidate ${candidate.name} had a malformatted mods.toml file")
                                    us.initCause(it)
                                    throw us
                                }

                                is NotAMod -> this.libs.add(contentCollection)
                                else -> throw it
                            }
                        }
                }
            }
        }

        perfcounter.printSummary()
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