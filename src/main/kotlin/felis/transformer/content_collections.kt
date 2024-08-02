package felis.transformer

import felis.meta.ModDiscoverer
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class JarContentCollection(val path: Path) : ContentCollection {
    private val fs = FileSystems.newFileSystem(this.path)

    override fun getContentPath(path: String): Path? =
        fs.getPath(path).let { if (it.exists()) it else null }

    override fun openStream(name: String): InputStream? =
        fs.getPath(name).let { if (it.exists()) Files.newInputStream(it) else null }

    /**
     * We assume that a jar cannot contain multiple entries so we can safely do this
     */
    override fun getContentPaths(path: String): List<Path> =
        this.getContentPath(path)?.let { listOf(it) } ?: emptyList()

    override fun toString(): String = "${this.path} in ${this.path.fileSystem}"
}

class PathUnionContentCollection(private val paths: List<Path>) : ContentCollection {
    override fun getContentPath(path: String): Path? = this.paths.firstNotNullOfOrNull {
        val out = it / path
        if (out.exists()) out else null
    }

    override fun openStream(name: String): InputStream? = this.getContentPath(name)?.inputStream()

    override fun getContentPaths(path: String): List<Path> = this.paths
        .asSequence()
        .map { it / path }
        .filter { it.exists() }
        .toList()

    override fun toString(): String = "directories: ${this.paths}"
}

class RootContentCollection(private val discoverer: ModDiscoverer) : URLStreamHandler(), ContentCollection {
    override fun getContentPath(path: String): Path? = this.findPrioritized { it.getContentPath(path) }
    override fun openStream(name: String): InputStream? = this.findPrioritized { it.openStream(name) }
    override fun getContentPaths(path: String): List<Path> = buildList {
        addAll(discoverer.mods.flatMap { it.getContentPaths(path) })
        addAll(discoverer.libs.flatMap { it.getContentPaths(path) })
    }

    private inline fun <T> findPrioritized(getter: (ContentCollection) -> T?) =
        this.discoverer.mods.firstNotNullOfOrNull { getter(it) }
            ?: this.discoverer.libs.firstNotNullOfOrNull { getter(it) }

    override fun openConnection(u: URL): URLConnection = CcUrlConnection(u, this)
}

class CcUrlConnection(url: URL, private val cc: ContentCollection) : URLConnection(url) {
    override fun connect() = Unit
    override fun getDoInput(): Boolean = true
    override fun getInputStream(): InputStream = this.cc.openStream(this.url.path)!!
}