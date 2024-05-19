package felis.launcher

sealed interface DefaultValue<out T> {
    class Value<out T>(val t: T) : DefaultValue<T>
    data object NoValue : DefaultValue<Nothing>
}
