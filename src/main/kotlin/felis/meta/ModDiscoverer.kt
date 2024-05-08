package felis.meta

import felis.transformer.JarContentCollection
import felis.util.PerfCounter
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.asSequence

class ModDiscoverer(modPaths: List<Path>) : Iterable<Mod> {
    // TODO: Use Delegates.observable to automatically allow transformer to pick up changes
    private val mods: MutableList<Mod> = mutableListOf()
    val libs = mutableListOf<JarContentCollection>()
    private val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)

    init {
        this.logger.info("mod discovery running for files $modPaths")
        val perfcounter = PerfCounter()
        for (nonExistingModDir in modPaths.filter { it.notExists() }.filter { it.extension.isEmpty() }) {
            nonExistingModDir.createDirectory()
        }

        // behold the limits of my functional programming ability
        val (mods, libs) = modPaths
            .asSequence()
            .flatMap { Files.walk(it).filter(Path::isRegularFile).asSequence() }
            .map { JarContentCollection(it) }
            .fold(Pair(mutableListOf<Mod>(), mutableListOf<JarContentCollection>())) { acc, contentCollection ->
                val (mods, libs) = acc
                perfcounter.timed {
                    Mod.from(contentCollection)
                        .onSuccess { mods.add(it) }
                        .onFailure {
                            when (it) {
                                is SerializationException -> throw ModMetaException(
                                    "mod candidate ${contentCollection.path.name} had a malformatted mods.toml file"
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
