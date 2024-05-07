package felis.language

class DelegatingLanguageAdapter : LanguageAdapter, Iterable<LanguageAdapter> {
    private val children: MutableList<LanguageAdapter> = mutableListOf()

    fun registerAdapter(adapter: LanguageAdapter) = this.children.add(adapter)
    override fun <T> createInstance(specifier: String, clazz: Class<out T>): Result<T> = this
        .asSequence()
        .map { it.createInstance(specifier, clazz) }
        .find { it.isSuccess } ?: throw LanguageAdapterException(specifier)

    override fun iterator(): Iterator<LanguageAdapter> = this.children.iterator()
}