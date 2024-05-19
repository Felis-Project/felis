package felis.util

data class ClassInfo(
    val name: String,
    val superName: String?,
    val interfaces: List<String>,
    val isInterface: Boolean
)
