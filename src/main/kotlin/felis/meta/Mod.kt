package felis.meta

import felis.ModLoader
import felis.transformer.ContentCollection

open class Mod(private val contentCollection: ContentCollection, val meta: ModMeta) :
    ContentCollection by contentCollection {
    companion object {
        fun from(contentCollection: ContentCollection): Result<Mod> {
            val metaToml = contentCollection.withStream("mods.toml") {
                String(it.readAllBytes())
            } ?: return Result.failure(NotAMod) // all mods must provide a mods.toml
            return runCatching {
                val toml = ModLoader.toml.parseToTomlTable(metaToml)
                val meta = ModLoader.toml.decodeFromTomlElement(ModMeta.serializer(), toml)
                meta.toml = toml
                Mod(contentCollection, meta)
            }
        }
    }
}
