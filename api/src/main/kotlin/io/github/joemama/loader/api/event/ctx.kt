package io.github.joemama.loader.api.event

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * Base interface for events which take in a player instance
 */
interface PlayerEventContext<P> where P : Player {
    val player: P
}

/**
 * Base interface for events which take in a world instance
 */
interface LevelEventContext<L> where L : Level {
    val level: L
}

interface BlockEventContext : LevelEventContext<Level> {
    val pos: BlockPos
    val state: BlockState
    val block: Block
}

data class BlockBlockEventContext(
    override val level: Level,
    override val player: Player,
    override val pos: BlockPos,
    override val state: BlockState,
    override val block: Block,
) : BlockEventContext, CancellableEventContext, PlayerEventContext<Player> {
    override var isCancelled: Boolean = false
}