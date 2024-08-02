package felis.side

import felis.ModLoader
import felis.transformer.ClassContainer
import felis.transformer.Transformation

/**
 * Internal transformation applied **by default** to all classes.
 * This transformation removes all classes and members annotated with [OnlyIn], whose [OnlyIn.side] value does not match the current [ModLoader.side]
 *
 * @author 0xJoeMama
 */
object SideStrippingTransformation : Transformation {
    override fun transform(container: ClassContainer): ClassContainer? {
        // we don't allow auditing to modify
        if (ModLoader.isAuditing) return container

        // locate strippable objects
        val locator = StripLocator()
        container.walk(locator)
        // if the whole class is removed, we can just skip
        if (locator.skipEntire) {
            return null
        }

        // if no methods or fields are to be removed we can cancel
        if (locator.methods.size == 0 && locator.fields.size == 0) return container
        // otherwise, we visit the class with a stripper
        return container.visitor {
            ClassStripper(it, locator.methods, locator.fields)
        }
    }
}
