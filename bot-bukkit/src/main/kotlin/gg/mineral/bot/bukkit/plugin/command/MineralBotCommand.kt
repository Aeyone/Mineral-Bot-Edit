package gg.mineral.bot.bukkit.plugin.command

import gg.mineral.bot.api.BotAPI
import gg.mineral.bot.api.math.ServerLocation
import gg.mineral.bot.api.configuration.BotConfiguration
import gg.mineral.bot.api.entity.living.player.skin.Skins
import gg.mineral.bot.api.world.ServerWorld
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MineralBotCommand : CommandExecutor {
    private var debugEnabled = false

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("op")) {
            return false
        }

        if (args.isEmpty()) {
            // TODO: list commands
            return false
        }

        if (args[0].equals("debug", ignoreCase = true)) {
            if (debugEnabled) {
                Configurator.setRootLevel(Level.INFO)
                debugEnabled = false
                sender.sendMessage("MineralBot debug mode disabled.")
            } else {
                Configurator.setRootLevel(Level.DEBUG)
                debugEnabled = true
                sender.sendMessage("MineralBot debug mode enabled.")
            }
        }

        if (args[0].equals("add", ignoreCase = true)) {
            if (sender is Player) {
                val location = sender.location
                val instance = BotAPI.INSTANCE.spawn(
                    BotConfiguration(
                        username = args[1],
                        skin = Skins.MINERAL_GREEN,
                        horizontalAimSpeed = 0.3f,
                        verticalAimSpeed = 0.3f,
                        horizontalAimAccuracy = 0.3f,
                        verticalAimAccuracy = 0.25f,
                        horizontalErraticness = 0.3f,
                        averageCps = 5f,
                        latency = 50,
                        sprintResetAccuracy = 0.25f,
                        hitSelectAccuracy = 0.0f
                    ), object : ServerLocation {
                        override val x: Double get() = location.x
                        override val y: Double get() = location.y + 1
                        override val z: Double get() = location.z
                        override val yaw: Float get() = location.yaw
                        override val pitch: Float get() = location.pitch
                        override val world: ServerWorld<*>
                            get() = object : ServerWorld<World> {
                                override val handle get() = location.world
                            }
                    }
                )
                instance.onComplete {
                    Bukkit.getLogger().info("Complete")
                }
                instance.onError {
                    Bukkit.getLogger().info("Error")
                }

            }
        }

        // TODO: gui for managing logs, settings, etc.
        // TODO: item for selecting which bot to debug
        return true
    }
}
