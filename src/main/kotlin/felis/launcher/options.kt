package felis.launcher

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface OptionScope {
    fun <T> option(name: String, default: DefaultValue<T> = noValue(), creator: (String) -> T): OptionKey<T> =
        OptionKey(name, default, creator)

    fun noValue(): DefaultValue<Nothing> = DefaultValue.NoValue
    fun <T> default(t: T): DefaultValue<T> = DefaultValue.Value(t)
}

class OptionKey<T>(
    private val name: String,
    private val default: DefaultValue<T> = DefaultValue.NoValue,
    private val creator: (String) -> T
) : ReadOnlyProperty<Any, T> {
    override fun getValue(thisRef: Any, property: KProperty<*>): T =
        System.getProperty(name)?.let(creator) ?: when (default) {
            DefaultValue.NoValue -> throw IllegalStateException("Property $name must be specified or have a default value")
            is DefaultValue.Value -> default.t
        }
}

sealed class DefaultValue<out T> {
    class Value<out T>(val t: T) : DefaultValue<T>()
    data object NoValue : DefaultValue<Nothing>()
}
