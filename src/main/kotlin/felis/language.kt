package felis

import kotlin.reflect.full.createInstance

interface LanguageAdapter {
    fun <T> createInstance(specifier: String): Result<T>
}

class EntrypointException(spec: String) : IllegalArgumentException("Could not locate entrypoint specified by $spec")

class DelegatingLanguageAdapter : LanguageAdapter, Iterable<LanguageAdapter> {
    private val children: MutableList<LanguageAdapter> = mutableListOf()

    fun registerAdapter(adapter: LanguageAdapter) = this.children.add(adapter)
    override fun <T> createInstance(specifier: String): Result<T> = this
        .asSequence()
        .map { it.createInstance<T>(specifier) }
        .first { it.isSuccess }

    override fun iterator(): Iterator<LanguageAdapter> = this.children.iterator()
}

object JavaLanguageAdapter : LanguageAdapter {
    @Suppress("UNCHECKED_CAST") // fuck you type safety
    override fun <T> createInstance(specifier: String): Result<T> = runCatching {
        Class.forName(specifier, true, ModLoader.classLoader).let {
            it.getDeclaredConstructor().newInstance() as T
        }
    }
}

object KotlinLanguageAdapter : LanguageAdapter {
    @Suppress("UNCHECKED_CAST")
    override fun <T> createInstance(specifier: String): Result<T> = runCatching {
        val splitName = specifier.split("::")
        if (splitName.size == 1) {
            val kClass = Class.forName(specifier, true, ModLoader.classLoader).kotlin
            kClass.objectInstance as? T
                ?: kClass.createInstance() as? T
                ?: throw EntrypointException(specifier)
        } else {
            TODO("Method/property references are not implemented yet")
        }
    }
}