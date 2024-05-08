package felis.transformer

fun interface Transformation {
    fun transform(container: ClassContainer)
    data class Named(val name: String, val del: Transformation) : Transformation by del
}