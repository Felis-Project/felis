package felis.meta

import felis.transformer.ContentCollection

open class Mod(
    private val contentCollection: ContentCollection,
    val metadata: ModMetadataExtended
) : ContentCollection by contentCollection, ModMetadataSchemaV1 by metadata {
    override fun toString(): String = "${metadata.modid}: ${metadata.version} (via ${this.contentCollection})"
}
