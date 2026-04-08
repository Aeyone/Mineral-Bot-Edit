package gg.mineral.bot.standalone;

import com.google.common.collect.ArrayListMultimap;
import gg.mineral.bot.ai.goal.*;
import gg.mineral.bot.ai.goal.type.SelectKitGoal;
import gg.mineral.bot.api.BotAPI;
import gg.mineral.bot.api.configuration.BotConfiguration;
import gg.mineral.bot.base.client.instance.ClientInstance;
import gg.mineral.bot.base.client.manager.InstanceManager;

import java.io.File;
import java.net.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BotManager {

    private static Map<String, UUID> bots = new ConcurrentHashMap<>();

    public static void connect(String username, String host, int port) {
        File runDir = new File("run");
        runDir.mkdirs();

        InstanceManager.getInstances().values()
            .stream()
            .filter( mc -> mc.getSession().getUsername() == username )
            .findFirst()
            .ifPresentOrElse(
                mc -> System.out.println("Player already connected"),
                () -> {
                    BotConfiguration configuration = BotConfiguration.builder()
                        .username(username)
                        .horizontalAimSpeed(0.8f)
                        .verticalAimSpeed(0.8f)
                        .horizontalAimAccuracy(0.6f)
                        .verticalAimAccuracy(0.6f)
                        .horizontalErraticness(0.6f)
                        .averageCps(10f)
                        .latency(30)
                        .sprintResetAccuracy(0.8f)
                        .hitSelectAccuracy(0.6f)
                        .reach(3.0f)
                        .targetSearchRange(64)
                        .pearlCooldown(24)
                        .jumpProbability(0.08f)
                        .wtapProbability(0.8f)
                        .debug(false)
                        .build();

                    ClientInstance instance = new ClientInstance(
                        configuration,
                        800, 600, false, false,
                        runDir,
                        new File(runDir,"assets"),
                        new File(runDir,"resourcepacks"),
                        Proxy.NO_PROXY,
                        "Mineral-Bot-Client", ArrayListMultimap.create(),
                        "1.7.10"
                    );

                    UUID uuid = configuration.getUuid();
                    bots.put(username, uuid);

                    instance.setServer(host, port);
                    InstanceManager.getPendingInstances().put(uuid, instance);
                    instance.run();
                    InstanceManager.getPendingInstances().remove(uuid);
                    InstanceManager.getInstances().put(uuid, instance);
                    apply(username);
                }
            );
    }

    public static void disconnect(String name) {
        UUID uuid = bots.get(name);

        if (uuid == null) {
            System.out.println("Not found " + name + ".");
            return;
        }
        if (BotAPI.INSTANCE.despawn(uuid)) {
            System.out.println("Successfully deleted " + name + ".");
            bots.remove(name);
        } else {
            System.out.println("Unable to deleted " + name + ".");
        }
    }
    public static void apply(String name) {
        ClientInstance instance = InstanceManager.getInstances().get(bots.get(name));
        instance.startGoals(
                new SelectKitGoal(instance),
                new ReplaceArmorGoal(instance),
                new HealSoupGoal(instance),
                new ThrowHealthPotGoal(instance),
                new DrinkPotionGoal(instance),
                new EatGappleGoal(instance),
                new EatFoodGoal(instance),
//                new ThrowDebuffPotGoal(instance),
                new ThrowPearlGoal(instance),
//                new DropEmptyBowlGoal(instance),
                new MeleeCombatGoal(instance)
        );
    }

    public static void deApply(String name) {
        ClientInstance instance = InstanceManager.getInstances().get(bots.get(name));
        instance.clearGoals();
    }

}