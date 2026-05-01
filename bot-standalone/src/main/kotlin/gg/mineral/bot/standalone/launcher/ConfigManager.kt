package gg.mineral.bot.standalone.launcher

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.introspector.BeanAccess
import java.io.File

object ConfigManager {
    class BotConfig {
        var redis: RedisConfig = RedisConfig()
        var bungeeCord: BungeeConfig = BungeeConfig()

        class RedisConfig {
            var host: String = "127.0.0.1"
            var port: Int = 6379
        }

        class BungeeConfig {
            var host: String = "127.0.0.1"
            var port: Int = 25565
        }
    }

    private val configFile = File("config.yml")
    private val yaml = Yaml().apply {
        setBeanAccess(BeanAccess.FIELD)
    }

    var current: BotConfig = BotConfig()

    fun load() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()

            val input = javaClass.getResourceAsStream("/config.yml")
                ?: error("Default config.yml not found in resources")

            input.use { ins ->
                configFile.outputStream().use { out ->
                    ins.copyTo(out)
                }
            }

            println("[Config] Default config.yml created from resources")
        }

        configFile.inputStream().use { input ->
            val loaded = yaml.loadAs(input, BotConfig::class.java)
            current = loaded ?: BotConfig()
        }
    }
}