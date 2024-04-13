package io.github.joemama.loader.side

@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class OnlyIn(val side: Side)
