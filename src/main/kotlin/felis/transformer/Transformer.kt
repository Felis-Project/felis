package felis.transformer

import felis.ModLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Transformer : Transformation {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)

    // we have to store lazies to allow custom language adapters to work
    private val external: Map<String, List<Lazy<Transformation.Named>>> = ModLoader.discoverer
        .flatMap { it.meta.transforms }
        .groupBy { it.target }
        .mapValues { (_, transformations) ->
            transformations.map {
                lazy {
                    Transformation.Named(
                        it.name,
                        ModLoader.languageAdapter.createInstance(it.path, Transformation::class.java).getOrThrow()
                    )
                }
            }
        }
    private val internal = mutableListOf<Transformation>()

    fun registerTransformation(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(container: ClassContainer) {
        val name = container.name
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
}