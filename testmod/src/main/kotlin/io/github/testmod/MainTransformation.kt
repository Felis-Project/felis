package io.github.testmod

import felis.asm.AccessModifier
import felis.asm.InjectionPoint
import felis.asm.openMethod
import felis.transformer.ClassContainer
import felis.transformer.Transformation
import org.objectweb.asm.Type
import java.io.PrintStream

object MainTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod("main", Type.VOID_TYPE, Type.getType(Array<String>::class.java)) {
            access(AccessModifier.PUBLIC)
            inject(InjectionPoint.Head) {
                getStatic(locate(System::class), "out", typeOf(PrintStream::class))
                ldc("Hello from testmod")
                invokeVirtual(locate(PrintStream::class), "println", Type.VOID_TYPE, typeOf(String::class))
            }
            inject(InjectionPoint.Return) {
                getStatic(locate(System::class), "out", typeOf(PrintStream::class))
                ldc("Goodbye world")
                invokeVirtual(locate(PrintStream::class), "println", Type.VOID_TYPE, typeOf(String::class))
            }
        }
    }
}
