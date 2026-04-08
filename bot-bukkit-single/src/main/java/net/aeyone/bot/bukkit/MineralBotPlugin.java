package net.aeyone.bot.bukkit;

import gg.mineral.bot.api.BotAPI;
import gg.mineral.bot.base.client.BotImpl;
import gg.mineral.bot.base.client.tick.GameLoop;
import gg.mineral.bot.impl.thread.ThreadManager;
import net.minecraft.client.Minecraft;
import org.bukkit.plugin.java.JavaPlugin;

public class MineralBotPlugin extends JavaPlugin {

    public static MineralBotPlugin instance;

    @Override
    public void onLoad() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        BotImpl.init();
        Minecraft.init();
    }

    @Override
    public void onEnable() {
        instance = this;
        GameLoop.start();
    }

    @Override
    public void onDisable() {
        BotAPI.INSTANCE.despawnAll();
        ThreadManager.shutdown();
    }

}
