package felis

import felis.launcher.DefaultValue
import felis.launcher.OptionKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.measureTimedValue

sealed interface Timer {
    data class Result(val count: Int, val total: Duration) {
        val average: Duration
            get() = this.total / count
    }

    companion object {
        private val timers = ConcurrentHashMap<Timer, (Result) -> Unit>()
        private val showTimers by OptionKey("felis.show.perf", DefaultValue.Value(false), String::toBooleanStrict)
        fun create(name: String) = if (this.showTimers) Impl(name) else NoOp

        init {
            Runtime.getRuntime().addShutdownHook(Thread({ this.timers.forEach(Timer::end) }, "Timers"))
        }

        fun addAuto(timer: Timer, handler: (Result) -> Unit) {
            this.timers[timer] = handler
        }
    }

    private data class Impl(private val name: String) : Timer {
        private var totalTime: Duration = Duration.ZERO
        private var count = 0

        override fun <T> measure(action: () -> T): T {
            val (res, time) = measureTimedValue(action)
            this.totalTime += time
            this.count += 1
            return res
        }

        override fun end(action: (Result) -> Unit) {
            if (this.count > 0) {
                action(Result(count, totalTime))
                this.count = 0
                this.totalTime = Duration.ZERO
            }
        }
    }

    private data object NoOp : Timer {
        override fun <T> measure(action: () -> T): T = action()
        override fun end(action: (Result) -> Unit) {}
    }

    fun <T> measure(action: () -> T): T
    fun end(action: (Result) -> Unit)
}