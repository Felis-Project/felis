package felis.transformer

import java.io.InputStream
import java.net.URL
import java.nio.file.Path

/**
 * Basically the implementation of what I like to call a "Jar Tree".
 *
 * @author 0xJoeMama
 */
interface ContentCollection {
    fun getContentUrl(name: String): URL?
    fun getContentPath(path: String): Path?
    fun <R> withStream(name: String, action: (InputStream) -> R): R? = this.openStream(name)?.use(action)
    fun openStream(name: String): InputStream?
    fun getContentUrls(name: String): Collection<URL>
}