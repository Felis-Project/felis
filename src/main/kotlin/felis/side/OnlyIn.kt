package felis.side

/**
 * Annotation that removes a specific class, field or method(henceforth excludables) from a specific run side.
 *
 * Excludables marked with the [OnlyIn] annotation are completely stripped from the environment.
 * This means access to stripped classes leads to [ClassNotFoundException], for methods [NoSuchMethodException] and for fields [NoSuchFieldException].
 *
 * @see Side
 * @author 0xJoeMama
 * @since 2024
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnlyIn(val side: Side)
