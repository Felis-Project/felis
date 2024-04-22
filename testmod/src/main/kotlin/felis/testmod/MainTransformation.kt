package felis.testmod

import felis.asm.InjectionPoint
import felis.asm.openMethod
import felis.transformer.ClassContainer
import felis.transformer.Transformation
import org.objectweb.asm.Type
import java.io.PrintStream

object MainTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        container.openMethod("main", "([Ljava/lang/String;)V") {
            inject(InjectionPoint.Head) {
                getStatic(locate(System::class), "out", typeOf(PrintStream::class))
                ldc("Hello from testmod")
                invokeVirtual(locate(PrintStream::class), "println", Type.VOID_TYPE, Type.getType(String::class.java))
            }
        }
    }
}