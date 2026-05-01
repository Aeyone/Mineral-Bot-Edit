package gg.mineral.bot.standalone.launcher

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.mineral.bot.api.BotAPI
import gg.mineral.bot.api.configuration.BotConfiguration
import gg.mineral.bot.base.client.manager.InstanceManager
import redis.clients.jedis.JedisPool
import java.util.UUID

class RedisManager() {
    data class RedisPacket(
        val type: String,
        val data: JsonObject
    )
    companion object {
        const val CHANNEL: String = "bot-chat"
    }
    val gson = Gson()
    fun accept() {
        val pool = JedisPool(ConfigManager.current.redis.host, ConfigManager.current.redis.port)
        RedisBus(pool, CHANNEL) { msg ->
            println("ACCEPT: $msg")
            val packet = gson.fromJson(msg, RedisPacket::class.java)
            when (packet.type) {
                "add" -> {
                    var config = BotConfiguration()
                    applyJsonToConfig(packet.data, config)
                    BotManager.connect(config)
                }
                "del" -> {
                    val username = packet.data.get("username").asString
                    BotManager.disconnect(username)
                }
                "apply" -> {
                    applyJsonToExistingConfig(packet.data)
                }
            }
        }
    }

    fun applyJsonToExistingConfig(jsonObj: JsonObject): Boolean {
        val config = findConfig(jsonObj) ?: return false
        applyJsonToConfig(jsonObj.withoutIdentityFields(), config)
        return true
    }

    private fun findConfig(jsonObj: JsonObject): BotConfiguration? {
        val uuid = jsonObj.get("uuid")?.takeIf { !it.isJsonNull }?.let {
            runCatching { UUID.fromString(it.asString) }.getOrNull()
        }

        if (uuid != null) {
            val instance = InstanceManager.instances[uuid] ?: InstanceManager.pendingInstances[uuid]
            if (instance != null) return instance.configuration
        }

        val username = jsonObj.get("username")?.takeIf { !it.isJsonNull }?.asString ?: return null
        return (InstanceManager.instances.values + InstanceManager.pendingInstances.values)
            .firstOrNull { it.configuration.username == username || it.configuration.fullUsername == username }
            ?.configuration
    }

    fun applyJsonToConfig(jsonObj: JsonObject, config : BotConfiguration) : BotConfiguration {
        val clazz = config::class.java

        for ((key, value) in jsonObj.entrySet()) {
            try {
                if (value.isJsonNull) continue

                val field = clazz.getDeclaredField(key)
                field.isAccessible = true

                when {
                    field.type == String::class.java -> field.set(config, value.asString)
                    field.type == UUID::class.java -> field.set(config, UUID.fromString(value.asString))
                    field.type == Int::class.java || field.type == java.lang.Integer::class.java -> field.set(config, value.asInt)
                    field.type == Float::class.java || field.type == java.lang.Float::class.java -> field.set(config, value.asFloat)
                    field.type == Double::class.java || field.type == java.lang.Double::class.java -> field.set(config, value.asDouble)
                    field.type == Boolean::class.java || field.type == java.lang.Boolean::class.java -> field.set(config, value.asBoolean)
                    MutableSet::class.java.isAssignableFrom(field.type) && value.isJsonArray -> {
                        val elementType = (field.genericType as? java.lang.reflect.ParameterizedType)
                            ?.actualTypeArguments
                            ?.firstOrNull()

                        if (elementType == UUID::class.java) {
                            val uuids = BotAPI.INSTANCE.collections().newSet<UUID>()
                            value.asJsonArray.forEach {
                                runCatching { UUID.fromString(it.asString) }
                                    .getOrNull()
                                    ?.let(uuids::add)
                            }
                            field.set(config, uuids)
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
        return config
    }

    private fun JsonObject.withoutIdentityFields(): JsonObject {
        return deepCopy().apply {
            remove("username")
            remove("uuid")
        }
    }
}
