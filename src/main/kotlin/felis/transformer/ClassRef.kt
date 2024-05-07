package felis.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

sealed interface ClassRef {
    @JvmInline
    value class NodeRef(override val node: ClassNode) : ClassRef {
        override fun nodeRef(): NodeRef = this

        override fun bytesRef(): BytesRef {
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            this.node.accept(writer)
            return BytesRef(writer.toByteArray())
        }

        override val bytes: ByteArray
            get() = throw IllegalAccessException("Nodes shouldn't be asked for bytes")
        override val isNodeRef: Boolean
            get() = true
        override val isBytesRef: Boolean
            get() = false
    }

    @JvmInline
    value class BytesRef(override val bytes: ByteArray) : ClassRef {
        override fun nodeRef(): NodeRef {
            val reader = ClassReader(this.bytes)
            return NodeRef(ClassNode().also { reader.accept(it, ClassReader.EXPAND_FRAMES) })
        }

        override fun bytesRef(): BytesRef = this

        override val node: ClassNode
            get() = throw IllegalAccessException("Bytes shouldn't be asked for a node")
        override val isNodeRef: Boolean
            get() = false
        override val isBytesRef: Boolean
            get() = true
    }

    fun nodeRef(): NodeRef
    fun bytesRef(): BytesRef

    val node: ClassNode
    val bytes: ByteArray

    val isNodeRef: Boolean
    val isBytesRef: Boolean
}

