package gg.mineral.bot.standalone;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public final class ControlServer {

    private static int port = 5000;

    public static void start() {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                while (true) {
                    Socket socket = server.accept();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream())
                    );
                    String line = reader.readLine();
                    handle(line);
                    socket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Bot-Control-Server");

        t.setDaemon(true);
        t.start();
        System.out.println("Bot-Control-Server started on port: " + port);
    }

    private static void handle(String line) {
        if (line == null || line.isEmpty()) return;

        System.out.println("ACCEPT: " + line);

        String[] split = line.split(" ");
        if (split[0].equalsIgnoreCase("addbot") && split.length == 4) {
            BotManager.connect(split[1], split[2], Integer.parseInt(split[3]));
        }
        if (split[0].equalsIgnoreCase("delbot") && split.length == 2) {
            BotManager.disconnect(split[1]);
        }
        if (split[0].equalsIgnoreCase("applybot") && split.length == 2) {
            BotManager.apply(split[1]);
        }
        if (split[0].equalsIgnoreCase("deapplybot") && split.length == 2) {
            BotManager.deApply(split[1]);
        }
    }
}