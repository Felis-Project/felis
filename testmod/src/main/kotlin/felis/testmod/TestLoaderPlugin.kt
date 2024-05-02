package felis.testmod

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.transformer.ClassContainer
import felis.transformer.ClassRef
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object TestLoaderPlugin : LoaderPluginEntrypoint {
    private val logger = LoggerFactory.getLogger(TestLoaderPlugin::class.java)
    override fun onLoaderInit() {
        ModLoader.classLoader.defineClass(ClassContainer("test.class.Class", ClassNode().also {
            it.name = "test/class/Class"
            it.access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL
            it.superName = Type.getInternalName(Any::class.java)
            it.interfaces = listOf()
            it.version = Opcodes.V21

            it.methods.add(MethodNode().also {
                it.name = "test"
                it.desc = Type.getMethodDescriptor(Type.VOID_TYPE)
                it.access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC

                it.instructions.add(InsnList().also {
                    it.add(InsnNode(Opcodes.RETURN))
                })
            })
        }.let { ClassRef.NodeRef(it) }))
        val cls = Class.forName("test.class.Class")
        val invoker = MethodHandles.publicLookup().findStatic(cls, "test", MethodType.methodType(Void.TYPE))
        invoker.invokeExact()

        this.logger.info("Doing stuff")
        ModLoader.transformer.registerTransformation {
            this.logger.info("${it.name} is ${it.bytes.size} bytes in size")
        }
    }
}