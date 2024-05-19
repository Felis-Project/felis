package felis.transformer

import felis.util.ClassInfoSet
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

class ClassContainer(private val bytes: ByteArray, val name: String) {
    fun interface NodeAction : (ClassNode) -> Unit
    fun interface VisitorFunction : (ClassVisitor) -> ClassVisitor

    val internalName: String = name.replace('.', '/')

    private var nodeActions: MutableList<NodeAction> = mutableListOf()

    // we know we have a visitor since environment stripping
    private var visitorChain: MutableList<VisitorFunction> = ArrayList(1)
    var skip = false

    fun node(action: NodeAction) = this.nodeActions.add(action)

    fun visitor(action: VisitorFunction) = this.visitorChain.add(action)

    private fun runAndWriteNodeActions(node: ClassNode, classInfoSet: ClassInfoSet): ClassWriter {
        val writer = Writer(classInfoSet)
        this.nodeActions.forEach { it.invoke(node) }
        node.accept(writer)
        return writer
    }

    fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray {
        var newBytes = this.bytes

        if (this.visitorChain.isNotEmpty()) {
            val reader = ClassReader(newBytes)
            newBytes = if (this.nodeActions.isNotEmpty()) {
                val node = ClassNode()
                this.walk(reader, node)
                this.runAndWriteNodeActions(node, classInfoSet)
            } else {
                val writer = Writer(classInfoSet)
                this.walk(reader, writer)
                writer
            }.toByteArray()
        } else if (this.nodeActions.isNotEmpty()) {
            val reader = ClassReader(newBytes)
            val node = ClassNode()
            reader.accept(node, ClassReader.EXPAND_FRAMES)
            newBytes = this.runAndWriteNodeActions(node, classInfoSet).toByteArray()
        }

        return newBytes
    }

    fun walk(visitor: ClassVisitor) {
        with(ClassReader(this.bytes)) {
            accept(visitor, ClassReader.EXPAND_FRAMES)
        }
    }

    class Writer(private val classInfoSet: ClassInfoSet) : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String =
            this.classInfoSet.getCommonSuperClass(type1, type2)
    }

    private fun walk(reader: ClassReader, initial: ClassVisitor) =
        this.visitorChain.foldRight(initial) { fn, acc ->
            fn.invoke(acc)
        }.let { reader.accept(it, ClassReader.EXPAND_FRAMES) }
}