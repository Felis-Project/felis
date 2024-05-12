package felis.meta

import felis.transformer.ContentCollection

fun interface Scanner {
    fun offer(accept: (ContentCollection) -> Unit)
}