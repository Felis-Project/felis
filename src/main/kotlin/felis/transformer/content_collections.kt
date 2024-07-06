package felis.transformer

import felis.ModLoader
import java.io.InputStream
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

data class JarContentCollection(val path: Path) : ContentCollection {
    private val fs = FileSystems.newFileSystem(this.path)

    override fun getContentUrl(name: String): URL? =
        fs.getPath(name).let { if (Files.exists(it)) it.toUri().toURL() else null }

    override fun getContentPath(path: String): Path? =
        fs.getPath(path).let { if (it.exists()) it else null }

    override fun openStream(name: String): InputStream? =
        fs.getPath(name).let { if (it.exists()) Files.newInputStream(it) else null }

    /**
     * We assume that a jar cannot contain multiple entries so we can safely do this
     */
    override fun getContentUrls(name: String): Collection<URL> =
        this.getContentUrl(name)?.let { listOf(it) } ?: emptyList()
}

data class PathUnionContentCollection(val paths: List<Path>) : ContentCollection {
    override fun getContentUrl(name: String): URL? = this.paths.firstNotNullOfOrNull {
        val out = it / name
        if (out.exists()) out.toUri().toURL() else null
    }

    override fun getContentPath(path: String): Path? = this.paths.firstNotNullOfOrNull {
        val out = it / path
        if (out.exists()) out else null
    }

    override fun openStream(name: String): InputStream? = this.getContentPath(name)?.inputStream()

    override fun getContentUrls(name: String): Collection<URL> = this.paths
        .asSequence()
        .map { it / name }
        .filter { it.exists() }
        .map { it.toUri().toURL() }
        .toCollection(mutableListOf())
}

/**
 * Load content through a ContentCollection
 * Priority is: mods -> game -> libs
 */
// TODO: Make this a class given to the TransformingClassLoader
data object RootContentCollection : ContentCollection {
    override fun getContentUrl(name: String): URL? = this.findPrioritized { it.getContentUrl(name) }
    override fun getContentPath(path: String): Path? = this.findPrioritized { it.getContentPath(path) }
    override fun openStream(name: String): InputStream? = this.findPrioritized { it.openStream(name) }
    override fun getContentUrls(name: String): Collection<URL> = buildList {
        addAll(ModLoader.discoverer.mods.flatMap { it.getContentUrls(name) })
        addAll(ModLoader.discoverer.libs.flatMap { it.getContentUrls(name) })
    }

    private inline fun <T> findPrioritized(getter: (ContentCollection) -> T?) =
        ModLoader.discoverer.mods.firstNotNullOfOrNull(getter)
            ?: ModLoader.discoverer.libs.firstNotNullOfOrNull { getter(it) }
}