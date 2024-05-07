package felis.language

interface LanguageAdapter {
    fun <T> createInstance(specifier: String): Result<T>
}