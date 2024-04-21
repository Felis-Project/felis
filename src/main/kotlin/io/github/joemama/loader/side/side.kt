package io.github.joemama.loader.side

enum class Side {
    CLIENT,
    SERVER
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class OnlyIn(val side: Side)
