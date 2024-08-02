package felis.transformer

/**
 * A transformation is a modification to class.
 * To apply a transformation to a specific set of classes use [felis.meta.ModMetadataSchemaV1.transformations] along with an implementation of this interface.
 * For more dynamic capabilities use an implementation of this interface along with the [Transformer.registerTransformation] method.
 *
 * @author 0xJoeMama
 */
fun interface Transformation {
    /**
     * Modifies a class
     *
     * WARNING: The class has probably already been modified by some other [Transformation]. Proceed with caution.
     * @param container the current version of the class
     * @return the modified class or null if this class is to be skipped from being loaded
     * @see ClassContainer
     */
    fun transform(container: ClassContainer): ClassContainer?
    data class Named(val name: String, val del: Transformation) : Transformation by del
}