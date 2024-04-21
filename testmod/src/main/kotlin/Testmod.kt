package felis.testmod

import io.github.joemama.loader.api.CommonEntrypoint
import io.github.joemama.loader.api.event.GameEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Testmod : CommonEntrypoint {
    private val logger: Logger = LoggerFactory.getLogger(Testmod::class.java)
    override fun onInit() {
        this.logger.info("Initialize me baby")
        GameEvents.Player.Tick.end.register { player ->
            this.logger.info(player.mainHandItem.toString())
        }
    }
}