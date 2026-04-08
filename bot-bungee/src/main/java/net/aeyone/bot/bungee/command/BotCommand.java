package net.aeyone.bot.bungee.command;

import gg.mineral.bot.api.BotAPI;
import gg.mineral.bot.api.configuration.BotConfiguration;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotCommand extends Command implements TabExecutor {

    private static Map<String, UUID> bots = new ConcurrentHashMap<>();

    public BotCommand() {
        super("Bot", "bungeecord.command.bot");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            if (args[1] == "add") {
                BotConfiguration configuration = BotConfiguration.builder().username(args[2]).build();
                bots.put(args[2], configuration.getUuid());
                BotAPI.INSTANCE.spawn(configuration, "127.0.0.1", 25566);
            }
            if (args[1] == "del") {
                BotAPI.INSTANCE.despawn(bots.get(args[2]));
                bots.remove(args[2]);
            }
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
        return new ArrayList<>();
    }


}
