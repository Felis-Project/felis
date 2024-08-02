package felis.meta

import felis.transformer.ContentCollection

/**
 * A [Scanner] is an object with is able to locate [ContentCollection] instances.
 *
 * Must be registered to the [ModDiscoverer] using the [ModDiscoverer.walkScanner] method when using this for mod discovery.
 *
 * @author 0xJoeMama
 * @since May 2024
 */
fun interface Scanner {
    /**
     * Offers all [ContentCollection]s this [Scanner] can locate using the [accept] method
     *
     * @param accept consumes [ContentCollection] instances
     */
    fun offer(accept: (ContentCollection) -> Unit)
}