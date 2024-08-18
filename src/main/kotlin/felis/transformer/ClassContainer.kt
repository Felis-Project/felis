package felis.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

/**
 * A boxed version of a class, disallowing direct access to bytes.
 * We disallow acces to bytes, since it allows us to skip class verification,
 * because the only way the class can be modified is through asm API, which in itself is generally safe.
 *
 * One only has to interact with the top level superclass [ClassContainer] and not with the implementations.
 *
 * @author 0xJoeMama
 */
sealed class ClassContainer(protected val bytes: ByteArray, val name: String) {
    companion object {
        /**
         * Create a new instance of [ClassContainer]
         * @param bytes a JVM ClassFile spec compatible byte array
         * @param name the JVM name of the class(dot separated, no special characters except $, no .class)
         *
         * @return a [ClassContainer] instance
         */
        fun new(bytes: ByteArray, name: String): ClassContainer = Bytes(bytes, name)
    }

    // only useful for Java
    /**
     * Modify a [ClassNode] instance
     */
    fun interface NodeAction : (ClassNode) -> Unit

    /**
     * Append a [ClassVisitor] on a visitor chain, as explained in [ClassContainer.visitor] using the provided instance of [ClassVisitor] as the delegate for the chain to work.
     */
    fun interface VisitorFunction : (ClassVisitor) -> ClassVisitor

    private class Bytes(bytes: ByteArray, name: String) : ClassContainer(bytes, name) {
        override fun node(op: NodeAction): ClassContainer =
            NodeChain(mutableListOf(op), this.bytes, this.name)

        override fun visitor(op: VisitorFunction): ClassContainer =
            VisistorChain(mutableListOf(op), this.bytes, this.name)

        override fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray = this.bytes
    }

    private class NodeChain(
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

    private class VisistorChain(
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

    /**
     * A [ClassWriter] that operates using the [ClassInfoSet] for safely handling class inheritance instead of classloading classes.
     * Technically speaking it is almost always better to use this to deserialize a class.
     *
     * @author 0xJoeMama
     * @since May 2024
     */
    open class Writer(private val classInfoSet: ClassInfoSet) : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(type1: String, type2: String): String =
            this.classInfoSet.getCommonSuperClass(type1, type2)
    }

    /**
     * The internal name of this class.
     */
    val internalName: String
        get() = this.name.replace(".", "/")

    /**
     * Modify this class by getting a [ClassNode] and modifying it as a delegate.
     */
    abstract fun node(op: NodeAction): ClassContainer

    /**
     * Append a new [ClassVisitor] on the current chain.
     *
     * Visitor chains consist of [ClassVisitor] who delegate action to each other. The initial [ClassVisitor] of the chain is an instance of [Writer] or a [ClassNode].
     * Visitor chain modifications **always** happen before node modifications.
     *
     * @param op a function creating a [ClassVisitor] to be append to the current chain
     */
    abstract fun visitor(op: VisitorFunction): ClassContainer

    /**
     * Modify the [bytes] of this class and return the modified version, on the class environment created by [ClassInfoSet]
     *
     * @param classInfoSet an instance of [ClassInfoSet] to denote the class environment
     * @return the modified version of the current class
     */
    abstract fun modifiedBytes(classInfoSet: ClassInfoSet): ByteArray

    /**
     * Visit this class with [visitor] using [opts].
     * This visits the initial version of the class **not the current one**, because modifications are done lazily.
     *
     * @param visitor the [ClassVisitor] to visit
     * @param opts options to pass into [ClassReader.accept]
     */
    fun walk(visitor: ClassVisitor, opts: Int = ClassReader.EXPAND_FRAMES) = with(ClassReader(this.bytes)) {
        accept(visitor, opts)
    }

    override fun toString(): String = this.name
}