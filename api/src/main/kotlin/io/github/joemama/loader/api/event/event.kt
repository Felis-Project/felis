package io.github.joemama.loader.api.event

import io.github.joemama.loader.side.OnlyIn
import io.github.joemama.loader.side.Side

/**
 * Functional interface responsible for handling an event.
 *
 * The context of an event, is an object of any kind passed into the event as input and/or output parameters.
 * It can contain both mutable and immutable data and any set of methods.
 *
 * @param C the context of this event
 * @author 0xJoeMama
 * @since 2024
 */
fun interface EventHandler<C> {
    fun handle(ctx: C)
}

/**
 * Contains all registered instances of this event.
 * Currently handling is pretty simple.
 *
 * Can be used to [fire] the event and more importantly [register] event handlers on the event.
 *
 * @param T the context of the event that is to be handled
 */
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

/**
 * Base interface for cancellable events.
 */
interface CancellableEventContext {
    /**
     * Whether or not this event has been cancelled by some handler
     */
    var isCancelled: Boolean
}

/**
 * An subclass of [EventContainer] that is able to handle cancellable events.
 */
open class CancellableEventContainer<T> : EventContainer<T>() where T : CancellableEventContext {
    override fun fire(ctx: T) {
        for (del in this.delegates) {
            del.handle(ctx)
            if (ctx.isCancelled) return
        }
    }
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
     * Events related to [Block]s
     */
    object Block {
        /**
         * Fired when a player destroys a block.
         * Hook point is in [net.minecraft.server.level.ServerPlayerGameMode.destroyBlock],
         */
        @JvmField
        val breakBlock = CancellableEventContainer<BreakBlockEventContext>()
    }

    /**
     * Events related to [Player]s
     */
    object Player {
        object Tick {
            /**
             * Fired at the end of the [net.minecraft.world.entity.player.Player.tick] method.
             */
            @JvmField
            val end = EventContainer<net.minecraft.world.entity.player.Player>()

            /**
             * Fired at the beginning of the [net.minecraft.world.entity.player.Player.tick] method.
             */
            @JvmField
            val start = EventContainer<net.minecraft.world.entity.player.Player>()
        }
    }

    object Entity
    object Item
    object Level
    object BlockEntity
    object Chunk

    @OnlyIn(Side.CLIENT)
    object Client

    @OnlyIn(Side.SERVER)
    object Server
}