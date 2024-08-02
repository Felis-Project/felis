package felis.side

/**
 * Kind of **physical** run side.
 *
 * # Minecraft
 * On run sides: there are 2 kinds of sides, physical and logical sides.
 *
 * Physical sides refer to the kind of jar the game is running from. Physical sides have different classes most of the time. :
 * * Physical server: is the physical side used when running through the game server bundler ot normal server jar. Rendering classes are not included in this side and AWT is enabled.
 * * Physical client: is the physical side used when running through the jar distributed by the vanilla launcher. As far as I know, physical client has all classes physical server has as well as rendering only classes. AWT is disabled.
 *
 * Logical sides refer to the environment in which code is running. For example, code can run on client side and on server side, all that while we are just in physical client. :
 * * Logical server: ticking, serialization, world generation, item manipulation, entity AI, etc. Most game logic runs on logical server and **must** run there for many reasons.
 * * Logical client: logical clients only handle rendering and screen manipulation operations, having any changes to the world itself be sent to the logical server for handling.
 *
 * A physical server only has a logical server(henceforth dedicated server) attached to it.
 * However, a physical client has both a logical client and a logical server.
 * When connected to a multiplayer server, only the logical client is run.
 * When in a singleplayer world however, along with the logical client a server instance is spun up(which we call the integrated server), meaning that a logical server exists as well.
 *
 * @author 0xJoeMama
 * @since March 2024
 */
enum class Side {
    CLIENT,
    SERVER
}
