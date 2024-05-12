package felis.launcher

import felis.meta.Mod
import felis.meta.ModMetadataExtended
import felis.transformer.JarContentCollection

class GameInstance(
    contentCollection: JarContentCollection,
    meta: ModMetadataExtended,
    val mainClass: String,
    val args: Array<String>
) : Mod(contentCollection, meta) {
    val path by contentCollection::path
}

