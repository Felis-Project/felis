package io.github.joemama.loader.api.event

import io.github.joemama.loader.api.event.ctx.BreakBlockEventContext
import net.minecraft.world.entity.player.Player

fun interface EventHandler<C> {
    fun handle(ctx: C)
}

open class EventContainer<T> {
    protected val delegates = mutableListOf<EventHandler<T>>()

    open fun register(handler: EventHandler<T>) {
        this.delegates.add(handler)
    }

    open fun fire(ctx: T) {
        for (del in this.delegates) {
            del.handle(ctx)
        }
    }
}

interface CancellableEventContext {
    var isCancelled: Boolean
}

open class CancellableEventContainer<T> : EventContainer<T>() where T : CancellableEventContext {
    override fun fire(ctx: T) {
        for (del in this.delegates) {
            del.handle(ctx)
            if (ctx.isCancelled) return
        }
    }
}

interface PlayerEventContext {
    val player: Player
}

/**
 * Contains all events added by the API.
 * A problem I find when using other loaders is that events are not collected into a centralized location.
 * People need to either guess the game of an event or look through the API jar to find all available events.
 * I don't like that.
 *
 * @author 0xJoeMama
 * @since 2024
 */
object GameEvents {
    /**
     * Fired when a player destroys a block.
     */
    @JvmStatic
    val breakBlock = CancellableEventContainer<BreakBlockEventContext>()

    object PlayerTick {
        /**
         * Fired at the end of the [Player.tick] method
         */
        @JvmStatic
        val end = EventContainer<Player>()

        /**
         * Fired at the beginning of the [Player.tick] method
         */
        @JvmStatic
        val start = EventContainer<Player>()
    }
}