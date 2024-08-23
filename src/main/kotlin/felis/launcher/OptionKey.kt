package felis.launcher

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionKey<T>(
    private val name: String,
    private val default: DefaultValue<T> = DefaultValue.NoValue,
    private val creator: (String) -> T
) : ReadOnlyProperty<Any, T> {
    override fun getValue(thisRef: Any, property: KProperty<*>): T =
        System.getProperty(this.name)?.let(this.creator) ?: when (this.default) {
            is DefaultValue.Value -> this.default.t
            DefaultValue.NoValue -> throw IllegalStateException("Property ${this.name} must be specified or have a default value")
        }
}