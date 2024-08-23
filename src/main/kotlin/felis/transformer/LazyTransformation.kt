package felis.transformer

import felis.language.LanguageAdapter
import felis.meta.TransformationMetadata

class LazyTransformation(val source: TransformationMetadata, private val languageAdapter: LanguageAdapter) :
    Transformation {
    val t by lazy {
        this.languageAdapter.createInstance(this.source.specifier, Transformation::class.java).getOrThrow()
    }

    override fun transform(container: ClassContainer): ClassContainer? = this.t.transform(container)
    override fun toString(): String = this.source.toString()
    override fun hashCode(): Int = source.hashCode()
    override fun equals(other: Any?): Boolean = other is LazyTransformation && other.source == this.source
}
