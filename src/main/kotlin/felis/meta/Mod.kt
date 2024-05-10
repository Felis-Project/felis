package felis.meta

import felis.transformer.ContentCollection

open class Mod(private val contentCollection: ContentCollection, val meta: ModMeta) :
    ContentCollection by contentCollection {
    val modid by this.meta::modid
}
