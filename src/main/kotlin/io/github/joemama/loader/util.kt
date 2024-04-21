package io.github.joemama.loader

import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

// message should have the following form: <text> {}(becomes action count) <text> {}(becomes total time in seconds) <text> {}(becomes average time in milliseconds)
class PerfCounter(private val message: String = "{} actions in {}s. Average time is {}ms", wait: Boolean = false) {
    companion object {
        private val logger = LoggerFactory.getLogger(PerfCounter::class.java)
        private val counters: MutableList<PerfCounter> = mutableListOf()
        private val shutdownThread = Thread {
            this.counters.forEach {
                it.printSummary { actions, total, average ->
                    logger.info(it.message, actions, total, average)
                }
            }
        }

        init {
            shutdownThread.name = "Waiter"
            Runtime.getRuntime().addShutdownHook(this.shutdownThread)
        }
    }

    init {
        if (wait)
            counters.add(this)
    }

    var totalDuration: Duration = Duration.ZERO
    var actionCount = 0

    inline fun <T> timed(action: () -> T): T {
        val (res, totalDuration) = measureTimedValue(action)
        this.actionCount++
        this.totalDuration += totalDuration
        return res
    }

    fun interface SummaryHandler {
        fun handle(actionCount: Int, total: Double, average: Double)
    }

    fun printSummary(handler: SummaryHandler) {
        if (actionCount > 0) {
            val total = this.totalDuration.toDouble(DurationUnit.SECONDS)
            val avg = this.totalDuration.toDouble(DurationUnit.MILLISECONDS) / this.actionCount.toDouble()
            handler.handle(this.actionCount, total, avg)
        } else {
            logger.error("Not enough actions occured.")
        }
    }
}
