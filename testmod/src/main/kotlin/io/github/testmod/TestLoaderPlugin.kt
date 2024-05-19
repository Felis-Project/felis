package io.github.testmod

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.transformer.ClassContainer
import felis.transformer.ContentCollection
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.nio.file.Path

object TestLoaderPlugin : LoaderPluginEntrypoint {
    private val logger = LoggerFactory.getLogger(TestLoaderPlugin::class.java)
    override fun onLoaderInit() {
        ModLoader.discoverer.walkScanner { accept ->
            accept(object : ContentCollection {
                override fun getContentUrl(name: String): URL? = null
                override fun getContentPath(path: String): Path? = null
                override fun openStream(name: String): InputStream? = null
                override fun getContentUrls(name: String): Collection<URL> = emptyList()
            })
        }
        val node = ClassNode().apply {
            name = "test/class/Class"
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL
            superName = Type.getInternalName(Any::class.java)
            interfaces = listOf()
            version = Opcodes.V21

            methods.add(MethodNode().apply {
                name = "test"
                desc = Type.getMethodDescriptor(Type.VOID_TYPE)
                access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC

                instructions.add(InsnList().apply {
                    add(InsnNode(Opcodes.RETURN))
                })
            })
        }
        val bytes = with(ClassWriter(ClassWriter.COMPUTE_FRAMES)) {
            node.accept(this)
            toByteArray()
        }
        ModLoader.classLoader.defineClass(ClassContainer(bytes, "test.class.Class"))
        val cls = Class.forName("test.class.Class")
        val invoker = MethodHandles.publicLookup().findStatic(cls, "test", MethodType.methodType(Void.TYPE))
        invoker.invokeExact()

        logger.info("Doing stuff")
//        ModLoader.transformer.registerTransformation {
//            logger.info("${it.name} is being loaded")
//        }
    }
}