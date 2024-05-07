package felis.language

import felis.ModLoader
import kotlin.reflect.full.createInstance

class LanguageAdapterException(spec: String) :
    IllegalArgumentException("Could not locate entrypoint specified by $spec")

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
                ?: throw LanguageAdapterException(specifier)
        } else {
            TODO("Method/property references are not implemented yet")
        }
    }
}