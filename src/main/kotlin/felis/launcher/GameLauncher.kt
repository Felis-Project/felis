package felis.launcher

import felis.side.Side

fun interface GameLauncher {
    fun instantiate(side: Side, args: Array<String>): GameInstance
}
