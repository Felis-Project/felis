package felis

import felis.launcher.FelisLaunchEnvironment
import java.io.File

fun main(args: Array<String>) {
    // print out information related to this launch
    FelisLaunchEnvironment.let { env ->
        env.logger.info("Felis running on ${System.getProperty("java.vendor")}: ${System.getProperty("java.version")} using arguments: ${args.contentToString()}")
        env.logger.info("Felis environment: mods=${env.mods}, side=${env.side}, launcher=${env.launcher}, print=${env.printClassPath}, audit=${env.audit}")

        // print the classpath
        if (env.printClassPath) {
            val cp = System.getProperty("java.class.path").split(File.pathSeparator)
            env.logger.info("Printing ${cp.size} classpath entries")
            for (s in cp) {
                env.logger.info(s)
            }
        }
    }

    // Initialize the ModLoader instance for this launch
    ModLoader.initLoader(
        side = FelisLaunchEnvironment.side,
        launcher = FelisLaunchEnvironment.launcher,
        mods = FelisLaunchEnvironment.mods,
        gameArgs = args,
        audit = FelisLaunchEnvironment.audit,
    )
}
