package gg.mineral.bot.bukkit.plugin.managers.util

import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.JedisPool
import java.util.concurrent.ConcurrentHashMap

class RedisBus(private val pool: JedisPool) {

    private val listeners = ConcurrentHashMap<String, (String) -> Unit>()

    fun publish(channel: String, message: String) {
        pool.resource.use { jedis ->
            jedis.publish(channel, message)
        }
    }

    fun subscribe(channel: String, handler: (String) -> Unit) {
        listeners[channel] = handler

        Thread {
            pool.resource.use { jedis ->
                jedis.subscribe(object : JedisPubSub() {
                    override fun onMessage(ch: String, msg: String) {
                        listeners[ch]?.invoke(msg)
                    }
                }, channel)
            }
        }.start()
    }
}