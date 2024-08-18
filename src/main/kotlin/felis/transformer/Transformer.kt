package felis.transformer

import felis.language.LanguageAdapter
import felis.meta.ModSet
import felis.meta.ModSetUpdateListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Transformer(private val languageAdapter: LanguageAdapter) : Transformation, ModSetUpdateListener {
    private val logger: Logger = LoggerFactory.getLogger(Transformer::class.java)

    @Suppress("MemberVisibilityCanBePrivate")
    val ignored: IgnoreList = IgnoreList()
    private val internal = mutableListOf<Transformation>()
    private val external: MutableMap<String, List<LazyTransformation>> = mutableMapOf()

    fun registerTransformation(t: Transformation) {
        this.internal.add(t)
    }

    override fun transform(container: ClassContainer): ClassContainer? {
        val name = container.name
        if (this.ignored.isIgnored(name)) return container

        val newContainer = this.external[name]?.fold(container as ClassContainer?) { acc, t ->
            this.logger.info("transforming $name with ${t.source.name}")
            if (acc == null) return null
            t.transform(acc)
        } ?: container

        return this.internal.fold(newContainer as ClassContainer?) { acc, fn ->
            if (acc == null) return null
            fn.transform(acc)
        }
    }

    override fun onNewModSet(modSet: ModSet) {
        val transformations = modSet.mods.asSequence().flatMap { it.transformations }.flatMap { t ->
            t.targets.map { Pair(it, LazyTransformation(t, this.languageAdapter)) }
        }.fold(mutableMapOf<String, MutableList<LazyTransformation>>()) { map, (target, t) ->
            map.getOrPut(target) { mutableListOf() }.add(t)
            map
        }

        for ((target, ts) in transformations) {
            val current = this.external.getOrPut(target) { mutableListOf() }
            this.external[target] = ts.filter { !current.contains(it) } + current
        }
    }
}