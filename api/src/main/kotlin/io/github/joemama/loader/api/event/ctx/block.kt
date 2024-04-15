package io.github.joemama.loader.api.event.ctx

import io.github.joemama.loader.api.event.CancellableEventContext
import io.github.joemama.loader.api.event.PlayerEventContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

interface BlockEventContext {
    val level: Level
    val pos: BlockPos
    val state: BlockState
    val block: Block
}

data class BreakBlockEventContext(
    override val level: Level,
    override val player: Player,
    override val pos: BlockPos,
    override val state: BlockState,
    override val block: Block,
) : BlockEventContext, CancellableEventContext, PlayerEventContext {
    override var isCancelled: Boolean = false
}