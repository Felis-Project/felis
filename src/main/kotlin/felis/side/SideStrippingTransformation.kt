package felis.side

import felis.ModLoader
import felis.transformer.ClassContainer
import felis.transformer.Transformation
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type

object SideStrippingTransformation : Transformation {
    override fun transform(container: ClassContainer) {
        if (ModLoader.isAuditing) return
        // since all classes are passed into this transformation, we better not parse/unparse them
        // if we are bytes, pass through an event-driven stripper
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
        } else { // otherwise, if we are already parsed, pass through a tree driven system
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
