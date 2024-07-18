package felis.transformer

import java.io.InputStream
import java.nio.file.Path

/**
 * Basically the implementation of what I like to call a "Jar Tree".
 *
 * @author 0xJoeMama
 */
interface ContentCollection {
    fun getContentPath(path: String): Path?
    fun getContentPaths(path: String): List<Path>
    fun <R> withStream(name: String, action: (InputStream) -> R): R? = this.openStream(name)?.use(action)
    fun openStream(name: String): InputStream?
}