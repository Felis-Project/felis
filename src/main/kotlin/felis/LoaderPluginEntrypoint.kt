package felis

/**
 * Entrypoint called right after the [ModLoader] object has been initialized for this launch.
 * @see ModLoader.initLoader to see what that entails
 *
 * @author 0xJoeMama
 */
fun interface LoaderPluginEntrypoint {
    fun onLoaderInit()
}
