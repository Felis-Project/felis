package felis

/**
 * Entrypoint called right after the [ModLoader] object has been initialized for this launch.
 *
 * To register a loader plugin, just add its entrypoint to the [felis.meta.ModMetadataSchemaV1.entrypoints] list, using the 'loader_plugin' id.
 *
 * This allows plugins to modify the [ModLoader] instance as they please.
 * Generally speaking, it is recommended that loader plugins don't have changes made to them often.
 * After initial release they should remain stable and relatively backwards compatible.
 * The reason for this, is because during the 2nd stage of mod resolution, a mod may be replaced by a different version
 * of itself, which may in turn lead to problems if the 2 versions of the plugin are not compatible.
 *
 * @author 0xJoeMama
 * @see ModLoader.initLoader
 */
fun interface LoaderPluginEntrypoint {
    fun onLoaderInit()
}
