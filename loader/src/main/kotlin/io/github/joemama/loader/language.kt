package io.github.joemama.loader

import kotlin.reflect.full.createInstance

interface LanguageAdapter {
    fun <T> createInstance(name: String): Result<T>
}

class DelegatingLanguageAdapter : LanguageAdapter, Iterable<LanguageAdapter> {
    private val children: MutableList<LanguageAdapter> = mutableListOf()

    fun registerAdapter(adapter: LanguageAdapter) = this.children.add(adapter)
    override fun <T> createInstance(name: String): Result<T> = this
        .asSequence()
        .map { it.createInstance<T>(name) }
        .first { it.isSuccess }

    override fun iterator(): Iterator<LanguageAdapter> = this.children.iterator()
}

object JavaLanguageAdapter : LanguageAdapter {
    @Suppress("UNCHECKED_CAST") // allow unchecked because yes
    override fun <T> createInstance(name: String): Result<T> = runCatching {
        Class.forName(name, true, ModLoader.classLoader).let {
            it.getDeclaredConstructor().newInstance() as T
        }
    }
}

object KotlinLanguageAdapter : LanguageAdapter {
    @Suppress("UNCHECKED_CAST")
    override fun <T> createInstance(name: String): Result<T> = runCatching {
        val kClass = Class.forName(name, true, ModLoader.classLoader).kotlin
        kClass.objectInstance as? T
            ?: kClass.createInstance() as? T
            ?: throw ClassNotFoundException("Could not locate class $name")
    }
}