package felis

import felis.launcher.FelisLaunchEnvironment
import java.io.File

fun main(args: Array<String>) {
    // print out information related to this launch
    FelisLaunchEnvironment.let { env ->
        env.logger.info("Felis running on ${System.getProperty("java.vendor")}: ${System.getProperty("java.version")} using arguments: ${args.contentToString()}")
        env.logger.info("$env")

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
        audit = FelisLaunchEnvironment.audit,
        gameArgs = args,
    )
}
