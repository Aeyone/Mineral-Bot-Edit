package gg.mineral.bot.bukkit.plugin.managers

import gg.mineral.bot.ai.goal.DrinkPotionGoal
import gg.mineral.bot.ai.goal.EatFoodGoal
import gg.mineral.bot.ai.goal.EatGappleGoal
import gg.mineral.bot.ai.goal.HealSoupGoal
import gg.mineral.bot.ai.goal.MeleeCombatGoal
import gg.mineral.bot.ai.goal.ReplaceArmorGoal
import gg.mineral.bot.ai.goal.ThrowHealthPotGoal
import gg.mineral.bot.ai.goal.ThrowPearlGoal
import gg.mineral.bot.ai.goal.type.SelectKitGoal
import gg.mineral.bot.api.BotAPI
import gg.mineral.bot.api.configuration.BotConfiguration
import gg.mineral.bot.api.world.ServerWorld
import gg.mineral.bot.bukkit.plugin.MineralBotPlugin
import org.bukkit.Bukkit
import org.bukkit.World
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import gg.mineral.bot.api.math.ServerLocation

class BotManager {
    companion object {
        var bots : MutableMap<String, UUID> = ConcurrentHashMap()

        fun connect(config : BotConfiguration) : Boolean {
            val username = config.username
            if (bots[username] != null) {
                return false
            }
            val instance = BotAPI.INSTANCE.spawn(
                    config,
                    object : ServerLocation {
                        override val x: Double get() = 0.0
                        override val y: Double get() = 100.0
                        override val z: Double get() = 0.0
                        override val yaw: Float get() = 0.0F
                        override val pitch: Float get() = 0.0F
                        override val world: ServerWorld<*>
                            get() = object : ServerWorld<World> {
                                override val handle get() = Bukkit.getWorlds()[0]
                            }
                    }
                )

            instance.onComplete {
                instance.get().startGoals(
                    SelectKitGoal(it),
                    ReplaceArmorGoal(it),
                    HealSoupGoal(it),
                    ThrowHealthPotGoal(it),
                    DrinkPotionGoal(it),
                    EatGappleGoal(it),
                    EatFoodGoal(it),
    //                 ThrowDebuffPotGoal(it),
                    ThrowPearlGoal(it),
    //                 DropEmptyBowlGoal(it),
                    MeleeCombatGoal(it)
                )
                var player = Bukkit.getPlayer(username)
                bots[username] = player.uniqueId
                Bukkit.getLogger().info("player: " + player.name)

                Bukkit.getScheduler().runTaskAsynchronously(MineralBotPlugin.instance) {
                    try {
                        val out = ByteArrayOutputStream()
                        val data =  DataOutputStream(out)

                        data.writeUTF("Connect")
                        data.writeUTF("Practice")

                        player.sendPluginMessage(MineralBotPlugin.instance, "BungeeCord", out.toByteArray());
                    } catch (e : IOException) {
                        e.printStackTrace()
                        disconnect(username)
                    }
                }
            }
            return bots[username] != null
        }

        fun disconnect(username : String) : Boolean {
            val uuid = bots[username] ?: return true
            if (BotAPI.INSTANCE.despawn(uuid)) {
                bots.remove(username)
            }
            return bots[username] == null
        }

    }

}