package felis.transformer

import felis.asm.ClassScope
import org.objectweb.asm.tree.ClassNode

data class ClassContainer internal constructor(
    override val name: String,
    private var ref: ClassRef,
    var skip: Boolean = false
) : ClassRef, ClassScope {
    override val internalName by lazy { this.name.replace(".", "/") }

    constructor(name: String, ref: ClassRef) : this(name, ref, false)
    constructor(name: String, bytes: ByteArray) : this(name, ClassRef.BytesRef(bytes))

    // Always call when modifying bytes
    fun newBytes(bytes: ByteArray) {
        this.ref = ClassRef.BytesRef(bytes)
    }

    override fun nodeRef(): ClassRef.NodeRef {
        this.ref = this.ref.nodeRef()
        return this.ref as ClassRef.NodeRef
    }

    override fun bytesRef(): ClassRef.BytesRef {
        this.ref = this.ref.bytesRef()
        return this.ref as ClassRef.BytesRef
    }

    override val node: ClassNode
        get() = this.nodeRef().node
    override val bytes: ByteArray
        get() = this.bytesRef().bytes
    override val isNodeRef: Boolean
        get() = this.ref.isNodeRef
    override val isBytesRef: Boolean
        get() = this.ref.isBytesRef
}