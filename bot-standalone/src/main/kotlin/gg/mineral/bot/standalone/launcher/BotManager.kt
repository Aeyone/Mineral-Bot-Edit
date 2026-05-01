package gg.mineral.bot.standalone.launcher

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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BotManager {
    companion object {
        var bots : MutableMap<String, UUID> = ConcurrentHashMap()

        fun connect(config : BotConfiguration) {
            val username = config.username
            if (bots[username] != null) {
                return
            }
            bots[username] = config.uuid

            val instance = BotAPI.INSTANCE.spawn(
                config,
                ConfigManager.current.bungeeCord.host,
                ConfigManager.current.bungeeCord.port
            )

            instance.onComplete {
                instance.get().startGoals(
                    ReplaceArmorGoal(it),
                    ThrowPearlGoal(it),
                    HealSoupGoal(it),
                    ThrowHealthPotGoal(it),
                    DrinkPotionGoal(it),
                    EatGappleGoal(it),
                    EatFoodGoal(it),
    //                 ThrowDebuffPotGoal(it),
    //                 DropEmptyBowlGoal(it),
                    MeleeCombatGoal(it)
                )
            }
        }

        fun disconnect(username : String) : Boolean {
            val uuid = bots[username]

            if (uuid == null) {
                println("Not found $username.")
                return false
            }

            if (BotAPI.INSTANCE.despawn(uuid)) {
                bots.remove(username)
            }
            return bots[username] == null
        }

    }

}