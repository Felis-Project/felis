package felis.transformer

import felis.language.LanguageAdapter
import felis.meta.ModDiscoverer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Transformer(discoverer: ModDiscoverer, languageAdapter: LanguageAdapter) : Transformation {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)

    // we have to store lazies to allow custom language adapters to work
    private val external: Map<String, List<Lazy<Transformation.Named>>> = createExternal(discoverer, languageAdapter)
    @Suppress("MemberVisibilityCanBePrivate")
    val ignored: IgnoreList = IgnoreList()
    private val internal = mutableListOf<Transformation>()

    fun registerTransformation(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(container: ClassContainer): ClassContainer {
        val name = container.name
        if (this.ignored.isIgnored(name)) return container

        val newContainer = this.external[name]?.fold(container) { acc, lazy ->
            this.logger.info("transforming $name with ${lazy.value.name}")
            lazy.value.transform(acc)
        } ?: container

        return this.internal.fold(newContainer) { acc, fn -> fn.transform(acc) }
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