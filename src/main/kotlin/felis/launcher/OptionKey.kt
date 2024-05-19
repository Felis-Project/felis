package felis.launcher

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionKey<T>(
    private val name: String,
    private val default: DefaultValue<T> = DefaultValue.NoValue,
    private val creator: (String) -> T
) : ReadOnlyProperty<Any, T> {
    override fun getValue(thisRef: Any, property: KProperty<*>): T =
        System.getProperty(name)?.let(creator) ?: when (default) {
            is DefaultValue.Value -> default.t
            DefaultValue.NoValue -> throw IllegalStateException("Property $name must be specified or have a default value")
        }
}