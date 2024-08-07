package felis.meta

import felis.transformer.ContentCollection
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.modules.SerializersModule
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.getIntegerOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.inputStream

typealias Modid = String

class ModDiscoverer {
    companion object {
        const val MOD_META = "felis.mod.toml"
        val metadataToml = Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
            serializersModule = SerializersModule {
                contextual(Path::class, PathSerializer)
            }
        }
    }

    private val internalLibs = mutableListOf<ContentCollection>()
    private var modSet: ModSet? = null
    private val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)
    private val resolver = ModResolver()

    // offered to outsiders as API
    val libs: Iterable<ContentCollection> = this.internalLibs.asIterable()
    val mods: Iterable<Mod>
        get() = this.modSet ?: emptyList()

    fun walkScanner(scanner: Scanner) {
        this.logger.info("mod discovery running for scanner $scanner")
        scanner.offer { this.consider(it) }
    }

    fun registerMod(mod: Mod) = this.resolver.record(mod)

    @Suppress("MemberVisibilityCanBePrivate") // public API
    fun consider(contentCollection: ContentCollection) {
        val result = createMods(contentCollection)

        if (result is ModDiscoveryResult.Mods) result.mods.forEach(this.resolver::record)
        if (result is ModDiscoveryResult.Error) result.failedMods.forEach(Exception::printStackTrace)
        if (result is ModDiscoveryResult.NoMods) this.internalLibs.add(contentCollection)
    }

    fun finish() {
        this.modSet = this.resolver.resolve(modSet)
    }

    private fun createMods(cc: ContentCollection): ModDiscoveryResult {
        if (cc.getContentPath("mods.toml") != null) {
            this.logger.warn("Checked mod $cc contained a mods.toml file that is assumed to use the old metadata schema. Considering it a library.")
            this.logger.warn("To fix this, please change it's name to felis.mod.toml and use schema version 1")
            return ModDiscoveryResult.NoMods
        }
        val paths = cc.getContentPaths(MOD_META)
        if (paths.isEmpty()) return ModDiscoveryResult.NoMods

        val exceptions = mutableListOf<ModDiscoveryError>()
        val mods = mutableListOf<Mod>()
        for (path in paths) {
            val tomlString = try {
                String(path.inputStream().use { it.readAllBytes() })
            } catch (e: Exception) {
                val top = ModDiscoveryError("Problem occured when reading mod candidate : $cc")
                top.initCause(e)
                exceptions += top
                continue
            }

            try {
                val metaToml = metadataToml.parseToTomlTable(tomlString)
                if (metaToml.getIntegerOrNull("schema") != 1L) {
                    exceptions += ModDiscoveryError("$path, must specify a valid schema version. Available versions are: 1")
                    continue
                }
                val metadata = metadataToml.decodeFromString<ModMetadataExtended>(tomlString)
                mods += Mod(cc, metadata)
            } catch (e: SerializationException) {
                val top = ModDiscoveryError("Mod $cc had a malformatted $MOD_META file(at $path)")
                top.initCause(e)
                exceptions += top
            }
        }

        return if (exceptions.isEmpty()) {
            ModDiscoveryResult.Success(mods)
        } else if (mods.isEmpty()) {
            ModDiscoveryResult.Failure(exceptions)
        } else {
            ModDiscoveryResult.PartialMods(mods, exceptions)
        }
    }
}
