package felis.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

sealed class ClassContainer(protected val bytes: ByteArray, val name: String) {
    companion object {
        fun new(bytes: ByteArray, name: String): ClassContainer = Bytes(bytes, name)
    }

    fun interface NodeAction : (ClassNode) -> Unit
    fun interface VisitorFunction : (ClassVisitor) -> ClassVisitor

    class Bytes internal constructor(bytes: ByteArray, name: String) : ClassContainer(bytes, name) {
        override fun node(op: NodeAction): ClassContainer =
            NodeChain(mutableListOf(op), this.bytes, this.name)

        override fun visitor(op: VisitorFunction): ClassContainer =
            VisistorChain(mutableListOf(op), this.bytes, this.name)

        override fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray = this.bytes
    }

    class NodeChain internal constructor(
        private val ops: MutableList<NodeAction>,
        bytes: ByteArray,
        name: String
    ) : ClassContainer(bytes, name) {
        override fun node(op: NodeAction): ClassContainer {
            this.ops.add(op)
            return this
        }

        override fun visitor(op: VisitorFunction): ClassContainer =
            MixedChain(this.bytes, this.name, this.ops, mutableListOf(op))

        override fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray {
            val reader = ClassReader(this.bytes)
            val node = ClassNode()
            reader.accept(node, ClassReader.EXPAND_FRAMES)

            val writer = Writer(classInfoSet)
            this.ops.forEach { it.invoke(node) }
            node.accept(writer)
            return writer.toByteArray()
        }
    }

    class VisistorChain internal constructor(
        private val ops: MutableList<VisitorFunction>,
        bytes: ByteArray,
        name: String
    ) : ClassContainer(bytes, name) {
        override fun node(op: NodeAction): ClassContainer =
            MixedChain(this.bytes, this.name, mutableListOf(op), this.ops)

        override fun visitor(op: VisitorFunction): ClassContainer {
            this.ops.add(op)
            return this
        }

        override fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray {
            val reader = ClassReader(this.bytes)
            val writer = Writer(classInfoSet)
            this.walk(reader, writer)
            return writer.toByteArray()
        }

        private fun walk(reader: ClassReader, initial: ClassVisitor) =
            this.ops.foldRight(initial) { fn, acc -> fn.invoke(acc) }
                .let { reader.accept(it, ClassReader.EXPAND_FRAMES) }
    }

    class MixedChain internal constructor(
        bytes: ByteArray,
        name: String,
        private val nodeOps: MutableList<NodeAction>,
        private val visitorOps: MutableList<VisitorFunction>,
    ) : ClassContainer(bytes, name) {
        override fun node(op: NodeAction): ClassContainer {
            this.nodeOps.add(op)
            return this
        }

        override fun visitor(op: VisitorFunction): ClassContainer {
            this.visitorOps.add(op)
            return this
        }

        override fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray {
            val reader = ClassReader(this.bytes)
            val node = ClassNode()
            this.visitorOps.foldRight(node as ClassVisitor) { fn, acc -> fn.invoke(acc) }
                .let { reader.accept(it, ClassReader.EXPAND_FRAMES) }

            val writer = Writer(classInfoSet)
            this.nodeOps.forEach { it.invoke(node) }
            node.accept(writer)
            return writer.toByteArray()
        }
    }

    class Writer(private val classInfoSet: ClassInfoSet) : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String =
            this.classInfoSet.getCommonSuperClass(type1, type2)
    }

    val internalName: String
        get() = this.name.replace(".", "/")

    var skip: Boolean = false

    abstract fun node(op: NodeAction): ClassContainer
    abstract fun visitor(op: VisitorFunction): ClassContainer
    abstract fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray

    fun walk(visitor: ClassVisitor, opts: Int = ClassReader.EXPAND_FRAMES) = with(ClassReader(this.bytes)) {
        accept(visitor, opts)
    }
}