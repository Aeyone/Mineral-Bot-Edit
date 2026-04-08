package gg.mineral.bot.standalone.launcher

import gg.mineral.bot.base.client.BotImpl
import gg.mineral.bot.base.client.instance.ClientInstance
import gg.mineral.bot.base.client.manager.InstanceManager
import gg.mineral.bot.base.client.tick.GameLoop
import gg.mineral.bot.impl.config.BotGlobalConfig
import gg.mineral.bot.api.controls.Key
import gg.mineral.bot.impl.thread.ThreadManager
import gg.mineral.bot.standalone.BotManager
import gg.mineral.bot.standalone.ControlServer
import net.minecraft.client.Minecraft
import net.minecraft.client.main.Main
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.system.exitProcess

object StandaloneLauncher {
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val file = File("run")

        if (!file.exists()) file.mkdirs()

        if (!BotGlobalConfig.headless) {
            Main.main(
                concat(
                    arrayOf(
                        "--version",
                        "Mineral-Bot-Client",
                        "--accessToken",
                        "0",
                        "--assetIndex",
                        "1.7.10",
                        "--userProperties",
                        "{}",
                        "--gameDir",
                        file.absolutePath,
                        "--uuid",
                        UUID.randomUUID().toString(),
                        "--assetsDir",
                        File(file, "assets").absolutePath
                    ),
                    args
                )
            )
            return
        }

        BotImpl.init()
        Minecraft.init()

        Configurator.setRootLevel(Level.INFO)
        System.setProperty("java.net.preferIPv4Stack", "true")
        GameLoop.start()
        ControlServer.start();

        Runtime.getRuntime().addShutdownHook(object : Thread("Client Shutdown Thread") {
            override fun run() {
                InstanceManager.instances.values.removeIf { mc: ClientInstance ->
                    mc.stopIntegratedServer()
                    true
                }
            }
        })

        val terminal = TerminalBuilder.builder().build()
        val parser = DefaultParser()
        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .parser(parser)
            .build()
        consoleLoop@ while (true) {
            try {
                val line = reader.readLine("~> ")

                val split = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val commandString = split[0]

                when (commandString) {
                    "stop" -> break@consoleLoop
                    "instances" -> println("Instances: " + InstanceManager.instances.size)
                    "add" -> {
                        if (split.size >= 2) {
                            BotManager.connect(split[1], "127.0.0.1", 25565)
                        }
                    }
                    "del" -> {
                        if (split.size >= 2) {
                            BotManager.disconnect(split[1])
                        }
                    }
                    "players" -> println(
                        "Players: " + InstanceManager.instances.values.stream()
                            .map { mc: ClientInstance -> mc.session.username }
                            .reduce { a: String, b: String -> "$a, $b" }
                            .orElse("None"))
                    "gc" -> System.gc()
                    "debug" -> Configurator.setRootLevel(Level.DEBUG)
                    "info" -> Configurator.setRootLevel(Level.INFO)
                    "presskey" -> {
                        if (split.size < 4) {
                            println("Usage: presskey <username> <key> <duration>")
                        }

                        val username2 = split[1]
                        val key = split[2]
                        val type = Key.Type.valueOf(key)
                        val duration = split[3].toInt()
                        InstanceManager.instances.values
                            .stream()
                            .filter { mc: ClientInstance -> mc.session.username == username2 }.findFirst()
                            .ifPresent { mc: ClientInstance ->
                                mc.keyboard.pressKey(duration, type)
                            }
                    }

                    else -> println("Unknown command: $line")
                }
            } catch (e: UserInterruptException) {
                // Ignore
            } catch (e: EndOfFileException) {
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        println("Shutting down...")
        ThreadManager.shutdown()
        exitProcess(0)
    }

    private fun <T> concat(first: Array<T>, second: Array<T>): Array<T?> {
        val result = first.copyOf(first.size + second.size)
        System.arraycopy(second, 0, result, first.size, second.size)
        return result
    }
}
