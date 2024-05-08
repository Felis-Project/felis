package felis.language

import felis.ModLoader
import kotlin.reflect.full.*

class LanguageAdapterException(spec: String) :
    IllegalArgumentException("Could not locate class specified by $spec")

object JavaLanguageAdapter : LanguageAdapter {
    override fun <T> createInstance(specifier: String, clazz: Class<out T>): Result<T> = runCatching {
        Class.forName(specifier, true, ModLoader.classLoader).let {
            clazz.cast(it.getDeclaredConstructor().newInstance())
        }
    }
}

object KotlinLanguageAdapter : LanguageAdapter {
    override fun <T> createInstance(specifier: String, clazz: Class<out T>): Result<T> = runCatching {
        val splitName = specifier.split("::")
        val kClass = Class.forName(splitName[0], true, ModLoader.classLoader).kotlin
        when (splitName.size) {
            1 -> {
                clazz.cast(
                    kClass.objectInstance
                        ?: kClass.createInstance()
                        ?: throw LanguageAdapterException(specifier)
                )
            }

            2 -> TODO()
            else -> throw IllegalArgumentException("You cannot have :: move than once in a kotlin specifier")
        }
    }
}