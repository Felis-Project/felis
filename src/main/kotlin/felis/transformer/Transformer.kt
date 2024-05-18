package felis.transformer

import felis.ModLoader
import felis.language.LanguageAdapter
import felis.meta.ModDiscoverer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Transformer(discoverer: ModDiscoverer, languageAdapter: LanguageAdapter) : Transformation {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)
    // we have to store lazies to allow custom language adapters to work
    private val external: Map<String, List<Lazy<Transformation.Named>>> = createExternal(discoverer, languageAdapter)
    val ignored: IgnoreList = IgnoreList()
    private val internal = mutableListOf<Transformation>()

    fun registerTransformation(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(container: ClassContainer) {
        val name = container.name

        if (this.ignored.isIgnored(name)) return
        if (this.external.containsKey(name)) {
            for (t in this.external.getOrDefault(name, emptyList())) {
                this.logger.info("transforming $name with ${t.value.name}")
                t.value.transform(container)
                if (container.skip) {
                    return
                }
            }
        }

        for (t in this.internal) {
            t.transform(container)
            if (container.skip) {
                return
            }
        }
    }

    private fun createExternal(
        discoverer: ModDiscoverer,
        languageAdapter: LanguageAdapter
    ): Map<String, List<Lazy<Transformation.Named>>> {
        // FIXME: This kind of only allows for creating mods that are builtin not registered ones
        val res = hashMapOf<String, MutableList<Lazy<Transformation.Named>>>()
        for (transformation in discoverer.mods.flatMap { it.transformations }) {
            val lazyTransformation = lazy {
                Transformation.Named(
                    transformation.name,
                    languageAdapter.createInstance(
                        transformation.specifier,
                        Transformation::class.java
                    ).getOrThrow()
                )
            }
            for (target in transformation.targets) {
                res.getOrPut(target, ::mutableListOf).add(lazyTransformation)
            }
        }

        return res.toMap()
    }
}