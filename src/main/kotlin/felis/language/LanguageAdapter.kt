package felis.language

interface LanguageAdapter {
    fun <T> createInstance(specifier: String, clazz: Class<out T>): Result<T>
}