package io.github.joemama.loader.side

import io.github.joemama.loader.ModLoader
import io.github.joemama.loader.transformer.ClassContainer
import io.github.joemama.loader.transformer.Transformation
import org.objectweb.asm.*
import org.objectweb.asm.commons.Method

class ClassStripper(del: ClassVisitor, private val methods: List<Method>, private val fields: List<String>) :
    ClassVisitor(Opcodes.ASM9, del) {
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? = if (Method(name, descriptor) in this.methods) null else super.visitMethod(
        access,
        name,
        descriptor,
        signature,
        exceptions
    )

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? = if (name in this.fields) null else super.visitField(access, name, descriptor, signature, value)
}

class StripLocator : ClassVisitor(Opcodes.ASM9) {
    var skipEntire = false
    val methods = mutableListOf<Method>()
    val fields = mutableListOf<String>()
    override fun visitAnnotation(
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? = if (descriptor == Type.getDescriptor(OnlyIn::class.java)) {
        OnlyInDetector { this.skipEntire = true }
    } else super.visitAnnotation(descriptor, visible)

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor = object : MethodVisitor(this.api) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
            if (descriptor == Type.getDescriptor(OnlyIn::class.java)) OnlyInDetector {
                this@StripLocator.methods += Method(name, descriptor)
            } else super.visitAnnotation(descriptor, visible)
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor = object : FieldVisitor(this.api) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? =
            if (descriptor == Type.getDescriptor(OnlyIn::class.java)) OnlyInDetector {
                this@StripLocator.fields += name
            } else super.visitAnnotation(descriptor, visible)
    }

    class OnlyInDetector(val action: () -> Unit) : AnnotationVisitor(Opcodes.ASM9) {
        override fun visitEnum(name: String, descriptor: String, value: String) {
            if (name == "side" && descriptor == Type.getDescriptor(Side::class.java)) {
                val side = enumValueOf<Side>(value)
                if (side != ModLoader.side) this.action()
            }
        }
    }
}

object SideStrippingTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        // since all classes are passed into this transformation, we better not parse/unparse them
        if (container.isBytesRef) {
            val locator = StripLocator()
            val reader = ClassReader(container.bytes)
            reader.accept(locator, 0)
            if (locator.skipEntire) {
                container.skip = true
                return
            }

            if (locator.methods.size == 0 && locator.fields.size == 0) return

            val writer = ClassWriter(0)
            val stripper = ClassStripper(writer, locator.methods, locator.fields)

            reader.accept(stripper, 0)

            container.newBytes(writer.toByteArray())
        } else {
            container.node.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
                @Suppress("UNCHECKED_CAST")
                val value = (it.values[1] as Array<String>)[1]
                val side = enumValueOf<Side>(value)
                if (side != ModLoader.side) container.skip = true
            }

            if (container.skip) return

            container.node.methods.removeIf { method ->
                method.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
                    @Suppress("UNCHECKED_CAST")
                    val value = (it.values[1] as Array<String>)[1]
                    val side = enumValueOf<Side>(value)
                    side != ModLoader.side
                } ?: false
            }

            container.node.fields.removeIf { field ->
                field.visibleAnnotations?.find { it.desc == Type.getDescriptor(OnlyIn::class.java) }?.let {
                    @Suppress("UNCHECKED_CAST")
                    val value = (it.values[1] as Array<String>)[1]
                    val side = enumValueOf<Side>(value)
                    side != ModLoader.side
                } ?: false
            }
        }
    }
}