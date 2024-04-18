package io.github.joemama.loader.api.event

import io.github.joemama.loader.side.OnlyIn
import io.github.joemama.loader.side.Side
import net.minecraft.resources.ResourceLocation

/**
 * Public API for Event Ordering.
 * Used to specify relative order at which event handlers are executed.
 *
 * @author 0xJoeMama
 */
sealed interface Ordering {
    /**
     * Execute the handler marked with [Before] **before** the one refered to by [Before.stage]
     */
    data class Before(val stage: ResourceLocation) : Ordering

    /**
     * Execute the handler marked with [After] **after** the one refered to by [Before.stage]
     */
    data class After(val stage: ResourceLocation) : Ordering
}


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

data class EventInstance<C>(
    val handler: EventHandler<C>,
    val ordering: List<Ordering>,
    val path: ResourceLocation?
) : EventHandler<C> by handler

/**
 * Contains all registered instances of this event.
 * Currently handling is pretty simple.
 *
 * Can be used to [fire] the event and more importantly [register] event handlers on the event.
 *
 * @param T the context of the event that is to be handled
 */
open class EventContainer<T> {
    protected val delegates = mutableListOf<EventInstance<T>>()
    protected var cached = false

    open fun register(
        path: ResourceLocation?,
        vararg ordering: Ordering,
        handler: EventHandler<T>
    ) {
        val instance = EventInstance(
            path = path,
            handler = handler,
            ordering = listOf(*ordering)
        )

        this.delegates.add(instance)
        if (ordering.isNotEmpty()) {
            this.cached = false
        }
    }

    open fun register(handler: EventHandler<T>) = this.register(path = null, handler = handler)

    open fun fire(ctx: T) {
        if (!this.cached) {
            this.sort()
            this.cached = true
        }

        for (del in this.delegates) {
            del.handle(ctx)
        }
    }

    class EventSortError(msg: String) : Exception(msg)

    open fun sort() {
        val delegateMap: Map<ResourceLocation, EventInstance<T>> = this.delegates.fold(hashMapOf()) { acc, instance ->
            if (instance.path in acc)
                throw EventSortError("Event with path ${instance.path} has been registered multiple times")
            if (instance.path != null) {
                acc[instance.path] = instance
            }
            acc
        }

        val ords: Map<ResourceLocation, MutableList<ResourceLocation>> = delegateMap.flatMap { (path, inst) ->
            inst.ordering.map {
                when (it) {
                    is Ordering.Before -> Pair(path, it.stage)
                    is Ordering.After -> Pair(it.stage, path)
                }
            }
        }.fold(hashMapOf()) { acc, (from, to) ->
            acc.getOrPut(from, ::mutableListOf).add(to)
            acc
        }

        if (ords.isEmpty()) return

        val keys = delegateMap.keys.toHashSet()
        val visited = hashSetOf<ResourceLocation>()
        val res = ArrayDeque<EventInstance<T>>()

        fun visit(n: ResourceLocation) {
            if (n !in keys) return
            if (n in visited) throw EventSortError("Cyclic event dependency detected for $n")

            visited += n

            ords[n]?.let {
                for (child in it) {
                    visit(child)
                }
            }

            visited.remove(n)
            keys.remove(n)
            res.addFirst(delegateMap[n]!!) // we got the key from in there so there is no way it's null
        }

        while (keys.isNotEmpty()) {
            val selected = keys.first()
            visit(selected)
        }

        this.delegates.filterTo(res) { it.path == null }
        this.delegates.clear()
        this.delegates.addAll(res)
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

open class MapEventContainer<K, T> : EventContainer<MapEventContainer.JointEventContext<K, T>>() {
    private val events = hashMapOf<K, EventContainer<T>>()

    data class JointEventContext<K, T>(val receiver: K, val ctx: T)

    fun registerForReceiver(
        rec: K,
        path: ResourceLocation? = null,
        vararg ordering: Ordering,
        handler: EventHandler<T>
    ) {
        this.events.getOrPut(rec) { EventContainer() }.register(path = path, ordering = ordering, handler)
    }

    override fun fire(ctx: JointEventContext<K, T>) {
        this.events[ctx.receiver]?.fire(ctx.ctx)
    }
}

/**
 * An subclass of [EventContainer] that is able to handle cancellable events.
 */
open class CancellableEventContainer<T> : EventContainer<T>() where T : CancellableEventContext {
    override fun fire(ctx: T) {
        if (!this.cached) {
            this.sort()
        }

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
        val broken = CancellableEventContainer<BlockBlockEventContext>()
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

object LoaderEvents {
    /**
     * Fired using the key/id of an entrypoint as a receiver, after that entrypoint has been called.
     * Attention: Entrypoints are not added automatically, you need to fire this event for your own entrypoints.
     */
    @JvmStatic
    val entrypointLoaded = MapEventContainer<String, Unit>()
}
