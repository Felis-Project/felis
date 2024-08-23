package felis.language

import felis.ModLoader
import java.lang.reflect.Proxy
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmName

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
                        ?: throw IllegalArgumentException("Could not obtain an instance of class $specifier")
                )
            }

            2 -> {
                val instance = kClass.objectInstance
                    ?: kClass.createInstance()
                    ?: throw IllegalStateException("Cannot obtain an instance of class ${kClass.jvmName}")

                val member = kClass.members.find { it.name == splitName[1] }

                if (member != null) {
                    this.buildProxy(member, instance, clazz)
                } else {
                    val companionMember = kClass.companionObject?.members?.find { it.name == splitName[1] }
                        ?: throw IllegalArgumentException("Could not locate member ${splitName[1]} in $kClass")
                    this.buildProxy(companionMember, kClass.companionObjectInstance!!, clazz)
                }
            }

            else -> throw IllegalArgumentException("You cannot have :: more than once in a kotlin specifier")
        }
    }

    private fun <T> buildProxy(member: KCallable<*>, instance: Any, clazz: Class<out T>): T = when (member) {
        is KProperty -> clazz.cast(member.getter.call(instance))
        is KFunction -> {
            val proxy = Proxy.newProxyInstance(ModLoader.classLoader, arrayOf(clazz)) { _, method, args ->
                if (method.parameterCount == member.parameters.size - 1 &&
                    method.parameters.indices.all { method.parameters[it].type == member.parameters[it].type.javaType }
                ) {
                    if (args == null) member.call(instance) else member.call(instance, *args)
                }
            }
            clazz.cast(proxy)
        }

        else -> throw IllegalStateException("Kotlin language adapters only support functions and properties")
    }
}