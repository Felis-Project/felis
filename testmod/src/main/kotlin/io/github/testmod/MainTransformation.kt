package io.github.testmod

import felis.transformer.ClassContainer
import io.github.testmod.asm.AccessModifier
import io.github.testmod.asm.InjectionPoint
import io.github.testmod.asm.openMethod
import felis.transformer.Transformation
import io.github.testmod.asm.ContainerScope
import joptsimple.OptionParser
import joptsimple.OptionSpecBuilder
import org.objectweb.asm.Type
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintStream

object MainTransformation : Transformation {
    @Suppress("unused") // used in bytecode
    val logger: Logger = LoggerFactory.getLogger("MainDebug")
    override fun transform(container: ClassContainer) {
        container.node {
            ContainerScope(it, container.name, container.internalName).openMethod(
                "main",
                Type.VOID_TYPE,
                Type.getType(Array<String>::class.java)
            ) {
                access(AccessModifier.PUBLIC)
                inject(InjectionPoint.Head) {
                    getStatic(locate(System::class), "out", typeOf(PrintStream::class))
                    ldc("Hello from testmod")
                    invokeVirtual(locate(PrintStream::class), "println", Type.VOID_TYPE, typeOf(String::class))
                }
                inject(
                    InjectionPoint.Invoke(
                        typeOf(OptionParser::class),
                        "accepts",
                        typeOf(OptionSpecBuilder::class),
                        typeOf(String::class)
                    )
                ) {
                    dup()
                    getStatic(locate(MainTransformation::class), "INSTANCE", typeOf(MainTransformation::class))
                    invokeVirtual(locate(MainTransformation::class), "getLogger", typeOf(Logger::class))
                    swap()
                    invokeInterface(locate(Logger::class), "info", Type.VOID_TYPE, typeOf(String::class))
                }
            }
        }
    }
}
