package felis.transformer

import java.io.InputStream
import java.nio.file.Path

/**
 * An object that can be traced by paths. Content refers to any file that can be queried from this object.
 * All implementations of this are required to implement **equals**, **hashcode** and **toString**.
 *
 * @author 0xJoeMama
 */
interface ContentCollection {
    /**
     * Get the [Path] created by [path] inside this [ContentCollection]
     * @param path the path specifying the location of the object we are looking up
     *
     * @return a [Path] instance if the location exists or null otherwise
     */
    fun getContentPath(path: String): Path?

    /**
     * Get all [Path]s matching [path] inside this [ContentCollection]
     *
     * @return a (possibly empty) list of [Path]s to the specified location
     */
    fun getContentPaths(path: String): List<Path>

    /**
     * Apply an action to an [InputStream] inside this [ContentCollection] at the location specified by [name]
     *
     * @param name the location of the content we are looking for
     * @param action a transformation to apply to the stream
     *
     * @return the transformed form of the [InputStream] if it exists, or null otherwise
     */
    fun <R> withStream(name: String, action: (InputStream) -> R): R? = this.openStream(name)?.use(action)

    /**
     * Open an [InputStream] to the location specified by [name]
     *
     * @param name the location of the content
     * @return a valid [InputStream] to the location if it exists or null otherwise
     */
    fun openStream(name: String): InputStream?
}