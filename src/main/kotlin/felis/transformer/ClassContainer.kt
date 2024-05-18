package felis.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

class ClassContainer(private val bytes: ByteArray, val name: String) {
    fun interface NodeAction : (ClassNode) -> Unit
    fun interface VisitorFunction : (ClassVisitor) -> ClassVisitor

    val internalName: String
        get() = name.replace('.', '/')

    private var nodeActions: MutableList<NodeAction> = mutableListOf()
    // we know we have a visitor since environment stripping
    private var visitorChain: MutableList<VisitorFunction> = ArrayList(1)
    var skip = false
    // TODO: Class hierarchy information for COMPUTE_FRAMES through current class + class readers
    private val classWriter: ClassWriter
        get() = ClassWriter(0)


    fun node(action: NodeAction) = this.nodeActions.add(action)

    fun visitor(action: VisitorFunction) = this.visitorChain.add(action)

    private fun runNodeActions(node: ClassNode): ClassWriter {
        val writer = this.classWriter
        this.nodeActions.forEach { it.invoke(node) }
        node.accept(writer)
        return writer
    }

    fun modifiedBytes(): ByteArray {
        var newBytes = bytes

        if (this.visitorChain.isNotEmpty()) {
            val reader = ClassReader(newBytes)
            newBytes = if (this.nodeActions.isNotEmpty()) {
                val node = ClassNode()
                this.walk(reader, node)
                this.runNodeActions(node)
            } else {
                val writer = this.classWriter
                this.walk(reader, writer)
                writer
            }.toByteArray()
        } else if (this.nodeActions.isNotEmpty()) {
            val reader = ClassReader(newBytes)
            val node = ClassNode()
            reader.accept(node, ClassReader.EXPAND_FRAMES)
            // TODO: ClassWrapper api
            newBytes = this.runNodeActions(node).toByteArray()
        }

        return newBytes
    }

    fun walk(visitor: ClassVisitor) {
        with(ClassReader(this.bytes)) {
            accept(visitor, ClassReader.EXPAND_FRAMES)
        }
    }

    private fun walk(reader: ClassReader, initial: ClassVisitor) =
        this.visitorChain.foldRight(initial) { fn, acc ->
            fn.invoke(acc)
        }.let { reader.accept(it, ClassReader.EXPAND_FRAMES) }
}