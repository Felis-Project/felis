package io.github.joemama.loader

import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

// message should have the following form: <text> {}(becomes action count) <text> {}(becomes total time in seconds) <text> {}(becomes average time in milliseconds)
class PerfCounter(private val message: String, wait: Boolean = false) {
    companion object {
        private val logger = LoggerFactory.getLogger(PerfCounter::class.java)
        private val counters: MutableList<PerfCounter> = mutableListOf()
        private val shutdownThread = Thread {
            this.counters.forEach(PerfCounter::printSummary)
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
        var res: T
        this.totalDuration += measureTime {
            res = action()
        }
        this.actionCount++
        return res
    }

    fun printSummary() {
        if (actionCount > 0) {
            val total = this.totalDuration.toDouble(DurationUnit.SECONDS)
            val avg = this.totalDuration.toDouble(DurationUnit.MILLISECONDS) / this.actionCount.toDouble()
            logger.info(message, this.actionCount, total, avg)
        } else {
            logger.error("Not enough actions occured.")
        }
    }
}
