package net.aeyone.bot.bungee;

import gg.mineral.bot.api.BotAPI;
import gg.mineral.bot.base.client.BotImpl;
import gg.mineral.bot.base.client.tick.GameLoop;
import gg.mineral.bot.impl.thread.ThreadManager;
import net.aeyone.bot.bungee.command.BotCommand;
import net.md_5.bungee.api.plugin.Plugin;
import net.minecraft.client.Minecraft;

import java.util.concurrent.TimeUnit;

public class MineralBotBungeePlugin extends Plugin {
    public static MineralBotBungeePlugin instance;

    @Override
    public void onLoad() {
        BotImpl.init();
        Minecraft.init();
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    @Override
    public void onEnable() {
        instance = this;
        getProxy().getPluginManager().registerCommand(instance, new BotCommand());
    }

    @Override
    public void onDisable() {
        BotAPI.INSTANCE.despawnAll();
        ThreadManager.shutdown();
        instance = null;
    }
}
