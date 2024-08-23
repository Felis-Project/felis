package felis

import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    // print out information related to this launch
    ModLoader.let {
        val logger = LoggerFactory.getLogger("Launch Environment")
        logger.info("Felis running on ${System.getProperty("java.vendor")}: ${System.getProperty("java.version")} using arguments: ${args.contentToString()}")
        logger.info("Felis environment: mods=${it.mods}, side=${it.side}, launcher=${it.launcher}, print-cp=${it.printClassPath}, print-res=${it.printResolutionStages}, audit=${it.audit}")

        // print the classpath
        if (it.printClassPath) {
            val cp = System.getProperty("java.class.path").split(File.pathSeparator)
            logger.info("Printing ${cp.size} classpath entries")
            for (s in cp) {
                logger.info(s)
            }
        }
    }

    // Initialize the ModLoader instance for this launch
    ModLoader.initLoader(args)
}
