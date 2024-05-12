package felis.meta

import felis.transformer.ContentCollection
import felis.transformer.JarContentCollection
import java.nio.file.Path
import kotlin.io.path.*

data class DirectoryScanner(private val paths: Iterable<Path>) : Scanner {
    @OptIn(ExperimentalPathApi::class)
    override fun offer(accept: (ContentCollection) -> Unit) {
        for (modCandidate in paths) {
            modCandidate.createDirectories()
            modCandidate.walk()
                .map(::JarContentCollection)
                .forEach { accept(it) }
        }
    }
}