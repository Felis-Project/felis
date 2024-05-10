package felis.meta

import felis.ModLoader
import felis.transformer.ContentCollection
import felis.util.PerfCounter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory

class ModDiscoverer {
    companion object {
        const val MOD_META = "mods.toml"
    }

    private val internalMods = hashMapOf<Modid, Mod>()
    private val internalLibs = mutableListOf<ContentCollection>()
    private val logger = LoggerFactory.getLogger(ModDiscoverer::class.java)
    private val perfcounter = PerfCounter()

    // offered to outsiders as API
    val mods: Iterable<Mod> = this.internalMods.values
    val libs: Iterable<ContentCollection> = this.internalLibs.asIterable()

    fun registerMod(mod: Mod) {
        if (mod.modid in this.internalMods) {
            throw ModDiscoveryError("Mod ${mod.modid} has already been registered")
        }

        this.internalMods[mod.modid] = mod
    }

    fun walkScanner(scanner: Scanner) {
        this.logger.info("mod discovery running for scanner $scanner")
        scanner.offer { this.perfcounter.timed { this.consider(it) } }
    }

    fun consider(contentCollection: ContentCollection) {
        val result = createMods(contentCollection)

        if (result is ModDiscoveryResult.Mods) result.mods.forEach(this::registerMod)
        if (result is ModDiscoveryResult.Error) result.failedMods.forEach(Exception::printStackTrace)
        if (result is ModDiscoveryResult.NoMods) this.internalLibs.add(contentCollection)
    }

    fun finish() {
        this.perfcounter.printSummary { _, total, average ->
            this.logger.info("discovered ${this.internalMods.size} mods in ${total}s. Average discovery time was ${average}ms")
        }
    }

    private fun createMods(cc: ContentCollection): ModDiscoveryResult {
        val urls = cc.getContentUrls(MOD_META)
        if (urls.isEmpty()) return ModDiscoveryResult.NoMods

        val exceptions = mutableListOf<ModDiscoveryError>()
        val mods = mutableListOf<Mod>()
        for (url in urls) {
            val metaToml = try {
                String(url.openStream().use { it.readAllBytes() })
            } catch (e: Exception) {
                val top = ModDiscoveryError("Problem occured when reading mod candidate : $cc")
                top.initCause(e)
                exceptions += top
                continue
            }

            try {
                val meta = ModLoader.toml.decodeFromString<ModMeta>(metaToml)
                mods += Mod(cc, meta)
            } catch (e: SerializationException) {
                val top = ModDiscoveryError("Mod $cc had a malformatted $MOD_META file(at $url)")
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

typealias Modid = String
