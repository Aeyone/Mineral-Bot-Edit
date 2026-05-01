package gg.mineral.bot.standalone.launcher

import gg.mineral.bot.api.configuration.BotConfiguration
import gg.mineral.bot.api.controls.Key
import gg.mineral.bot.base.client.BotImpl
import gg.mineral.bot.base.client.manager.InstanceManager
import gg.mineral.bot.base.client.tick.GameLoop
import gg.mineral.bot.impl.config.BotGlobalConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.main.Main
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.reader.impl.completer.ArgumentCompleter
import org.jline.reader.impl.completer.NullCompleter
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.io.PrintStream
import java.util.*
import kotlin.system.exitProcess

data class CommandContext(val args: Array<String>, val line: String)

object StandaloneLauncher {

    private val commandRegistry = mutableMapOf<String, (CommandContext) -> Unit>()
    private var running = true

    @JvmStatic
    fun main(args: Array<String>) {
        val runDir = File("run").apply { if (!exists()) mkdirs() }

        if (!BotGlobalConfig.headless) {
            launchMinecraft(runDir, args)
            return
        }

        initCore()
        registerCommands()
        Thread(::consoleLoop, "Console-Thread").start()
    }

    private fun initCore() {
        BotImpl.init()
        Minecraft.init()
        Configurator.setRootLevel(Level.INFO)
        System.setProperty("java.net.preferIPv4Stack", "true")
        GameLoop.start()
        ConfigManager.load()
        RedisManager().accept()

        Runtime.getRuntime().addShutdownHook(Thread({
            running = false
            InstanceManager.instances.values.forEach { it.stopIntegratedServer() }
            println("\nShutting down safely...")
        }, "Shutdown-Hook"))
    }

    private fun registerCommands() {
        commandRegistry["reload"] = {
            ConfigManager.load()
        }


        commandRegistry["stop"] = {
            running = false
        }

        commandRegistry["instances"] = {
            println("Active Instances: ${InstanceManager.instances.size}")
        }

        commandRegistry["debug"] = {
            Configurator.setRootLevel(Level.DEBUG)
        }

        commandRegistry["info"] = {
            Configurator.setRootLevel(Level.INFO)
        }

        commandRegistry["add"] = { ctx ->
            if (ctx.args.size >= 2) BotManager.connect(BotConfiguration(username = ctx.args[1]))
            else println("Usage: add <name>")
        }

        commandRegistry["del"] = { ctx ->
            if (ctx.args.size >= 2) BotManager.disconnect(ctx.args[1])
            else println("Usage: del <name>")
        }

        commandRegistry["players"] = {
            val players = InstanceManager.instances.values.joinToString(", ") { it.session.username }
            println("Players: ${players.ifBlank { "None" }}")
        }

        commandRegistry["presskey"] = { ctx ->
            if (ctx.args.size < 4) {
                println("Usage: presskey <username> <key> <duration>")
            } else {
                val (_, username, keyStr, durationStr) = ctx.args
                try {
                    val keyType = Key.Type.valueOf(keyStr.uppercase())
                    val duration = durationStr.toInt()
                    InstanceManager.instances.values.find { it.session.username == username }
                        ?.keyboard?.pressKey(duration, keyType)
                        ?: println("User $username not found.")
                } catch (e: Exception) {
                    println("Error: Invalid key or duration.")
                }
            }
        }
    }

    private fun consoleLoop() {
        val terminal = TerminalBuilder.builder()
            .system(true)
            .dumb(true)
            .build()

        val completer = AggregateCompleter(
            ArgumentCompleter(
                StringsCompleter { commandRegistry.keys },
                StringsCompleter { InstanceManager.instances.values.map { it.session.username } },
                StringsCompleter { Key.Type.entries.map { it.name } },
                NullCompleter.INSTANCE
            )
        )

        val reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .parser(DefaultParser())
            .variable(LineReader.BELL_STYLE, "none")
            .option(LineReader.Option.AUTO_FRESH_LINE, true)
            .build()


        println("Console ready. Type 'help' or 'stop' to manage.")

        while (running) {
            val line = try {
                reader.readLine("~> ")
            } catch (e: UserInterruptException) {
                continue    // Ctrl+C
            } catch (e: EndOfFileException) {
                break       // Ctrl+D
            }

            if (line.isNullOrBlank()) continue

            val parsedLine = reader.parser.parse(line, 0)
            val args = parsedLine.words().toTypedArray()
            val cmdName = args[0].lowercase()

            val handler = commandRegistry[cmdName]
            if (handler != null) {
                try {
                    handler(CommandContext(args, line))
                } catch (e: Exception) {
                    println("Command execution failed: ${e.message}")
                }
            } else {
                println("Unknown command: $cmdName. Available: ${commandRegistry.keys}")
            }

            if (cmdName == "stop") break
        }

        exitProcess(0)
    }

    private fun launchMinecraft(runDir: File, args: Array<String>) {
        val mcArgs = arrayOf(
            "--version", "Mineral-Bot-Client",
            "--accessToken", "0",
            "--assetIndex", "1.7.10",
            "--userProperties", "{}",
            "--gameDir", runDir.absolutePath,
            "--uuid", UUID.randomUUID().toString(),
            "--assetsDir", File(runDir, "assets").absolutePath
        )
        Main.main(mcArgs + args)
    }
}