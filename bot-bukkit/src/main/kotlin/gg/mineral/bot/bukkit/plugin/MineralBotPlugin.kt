package gg.mineral.bot.bukkit.plugin

import gg.mineral.bot.api.BotAPI
import gg.mineral.bot.base.client.manager.InstanceManager
import gg.mineral.bot.base.client.tick.GameLoop
import gg.mineral.bot.bukkit.plugin.command.MineralBotCommand
import gg.mineral.bot.bukkit.plugin.impl.ServerBotImpl
import gg.mineral.bot.bukkit.plugin.managers.RedisManager
import gg.mineral.bot.bukkit.plugin.managers.util.RedisBus
import gg.mineral.bot.impl.thread.ThreadManager.shutdown
import net.minecraft.client.Minecraft
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class MineralBotPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: MineralBotPlugin
        lateinit var redisBus: RedisBus
        var jedisPool : JedisPool? = null
    }

    override fun onLoad() {
        System.setProperty("java.net.preferIPv4Stack", "true")
        ServerBotImpl.init()
        Minecraft.init()
    }

    override fun onEnable() {
        saveDefaultConfig()
        instance = this
        getCommand("mineralbot").executor = MineralBotCommand()
        GameLoop.start()
        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord") // Register a channel to send messages to Bungee

        try {
            val ip = config.getString("Redis.IP") ?: "127.0.0.1"
            val port = config.getInt("Redis.port", 6379)
            val password = config.getString("Redis.Password")
            jedisPool = JedisPool(
                JedisPoolConfig(),
                ip,
                port,
                20000,
                password,
                config.getInt("Redis.DbId", 0)
            )
            redisBus = RedisBus(jedisPool!!)
        } catch (e : Exception) {
            jedisPool = null;
            e.printStackTrace();
            logger.warning("Couldn't connect to a Redis instance at " + config.getString("Redis.IP") + ".");
        }

        RedisManager().accept()
    }

    override fun onDisable() {
        BotAPI.INSTANCE.despawnAll()
        logger.info("Disabling MineralBotPlugin...")

        // Cancel all tasks to prevent memory leaks
        Bukkit.getScheduler().cancelTasks(this)

        // Ensure all instances are removed
        InstanceManager.instances.clear()
        InstanceManager.pendingInstances.clear()

        shutdown()

        ServerBotImpl.destroy()

        jedisPool?.close()
    }
}
