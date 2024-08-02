package felis.meta

import felis.transformer.ContentCollection

/**
 * A representation of a mod as a bundle of [ModMetadata] and a [ContentCollection]
 */
open class Mod(
    private val contentCollection: ContentCollection,
    @Suppress("MemberVisibilityCanBePrivate") // exposed API
    val metadata: ModMetadataExtended
) : ContentCollection by contentCollection, ModMetadataSchemaV1 by metadata {
    override fun toString(): String = "${metadata.modid}: ${metadata.version} (via ${this.contentCollection})"
}
