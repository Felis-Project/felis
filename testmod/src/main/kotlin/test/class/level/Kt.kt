package test.`class`.level

import felis.LoaderPluginEntrypoint
import org.slf4j.LoggerFactory

class Kt {
    companion object {
        private val logger = LoggerFactory.getLogger(Kt::class.java)

        val testC: LoaderPluginEntrypoint = LoaderPluginEntrypoint {
            logger.info("Lol companion")
        }

        fun testC2() {
            logger.info("Hello wise world companion")
        }
    }

    val test: LoaderPluginEntrypoint = LoaderPluginEntrypoint {
        logger.info("Lol")
    }

    fun test2() {
        logger.info("Hello wise world")
    }
}