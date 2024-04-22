package felis.testmod

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import org.slf4j.LoggerFactory

object TestLoaderPlugin : LoaderPluginEntrypoint {
    private val logger = LoggerFactory.getLogger(TestLoaderPlugin::class.java)
    override fun onLoaderInit() {
        this.logger.info("Doing stuff")
        ModLoader.transformer.registerTransformation {
            this.logger.info("${it.name} is ${it.bytes.size} bytes in size")
        }
    }
}