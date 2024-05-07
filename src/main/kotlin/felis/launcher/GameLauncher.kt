package felis.launcher

fun interface GameLauncher {
    fun instantiate(args: Array<String>): GameInstance
}
