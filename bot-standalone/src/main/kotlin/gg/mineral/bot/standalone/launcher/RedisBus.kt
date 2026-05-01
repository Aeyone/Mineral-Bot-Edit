package gg.mineral.bot.standalone.launcher

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub

class RedisBus(
    private val pool: JedisPool,
    private val channel: String,
    private val handler: MessageHandler
) {

    fun interface MessageHandler {
        fun handle(message: String)
    }

    init {
        start()
    }

    fun start() {
        Thread({
            pool.resource.use { jedis ->
                jedis.subscribe(object : JedisPubSub() {
                        override fun onMessage(ch: String, msg: String) {
                            handler.handle(msg)
                        }
                    }, channel
                )
            }
        }, "RedisBus-Subscriber").start()
    }
}