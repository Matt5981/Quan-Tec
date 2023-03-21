package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.listeners.CommandListener;
import org.example.plugins.MessageBus;
import org.example.plugins.MessageBusEvent;
import org.example.plugins.PluginManager;
import org.example.plugins.QuanTecPlugin;
import org.example.utils.Halter;
import org.example.webserver.WebServer;

import java.util.*;
import java.util.logging.*;

// TODO find out why Intellij keeps recreating deleted modules in core.
// TODO remove all System.out.println statements.
public class Main {
    private static final Logger mainLogger = Logger.getLogger("System");

    public static final String botVers = "v4-0.1.0b";
    public static void main(String[] args) {
        new Main(args);
    }

    public Main(String[] args){

        mainLogger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$-7s] %4$s %n";

            @Override
            public synchronized String format(LogRecord logRecord){
                return String.format(format, new Date(logRecord.getMillis()), logRecord.getLoggerName(), logRecord.getLevel().getLocalizedName(), logRecord.getMessage());
            }
        });

        consoleHandler.setLevel(Level.INFO);
        mainLogger.addHandler(consoleHandler);

        final Logger LOGGER = Logger.getLogger("System");

        if(args.length < 1){
            LOGGER.severe("Expected 1 argument, got "+args.length+".");
            return;
        }

        LOGGER.info("Starting QuanTec...");
        LOGGER.info("Beginning plugin discovery and loading...");

        PluginManager pluginManager = PluginManager.getInstance();
        List<QuanTecPlugin> plugins = pluginManager.getPluginList();

        LOGGER.info("Loaded "+plugins.size()+" plugins.");
        LOGGER.info("Starting JDA...");

        // Assemble GateWay intents.
        // We'll add some default ones here to enable the informational endpoints. TODO abstract those into a plugin.
        List<GatewayIntent> gatewayIntents = new ArrayList<>();
        gatewayIntents.add(GatewayIntent.GUILD_MEMBERS);
        gatewayIntents.add(GatewayIntent.DIRECT_MESSAGES);
        gatewayIntents.add(GatewayIntent.GUILD_MESSAGES);
        gatewayIntents.add(GatewayIntent.GUILD_EMOJIS_AND_STICKERS);
        gatewayIntents.add(GatewayIntent.GUILD_PRESENCES);
        gatewayIntents.add(GatewayIntent.MESSAGE_CONTENT);

        // Nested forEach-ing doesn't look particularly pretty, but it gets the job done.
        plugins.forEach(plugin -> {
            if(plugin.getRequiredGatewayIntents() != null){
                plugin.getRequiredGatewayIntents().forEach(intent -> {
                    if(!gatewayIntents.contains(intent)){
                        gatewayIntents.add(intent);
                    }
                });
            }
        });

        // Make JDA.
        JDA jda = JDABuilder
                .create(gatewayIntents)
                .setToken(args[0])
                .setActivity(Activity.watching("my performance on thegaff.dev"))
                .build();

        LOGGER.info("JDA started. Setting up WebServer and command listener...");
        Halter halter = new Halter(false);

        // Command listener. Listens for commands for both QuanTec and its plugins.
        CommandListener commandListener = new CommandListener(botVers, halter);
        jda.addEventListener(commandListener);

        // Start web server.
        WebServer webServer = new WebServer(jda, 8444, botVers, pluginManager, halter);

        LOGGER.info("Command listener set up and WebServer is starting. Initializing plugins...");
        MessageBus messageBus = new MessageBus();

        pluginManager.initializePlugins(jda, messageBus, commandListener, webServer);

        // TODO is this able to be done in ListenerAdapters?
        // Dispatch event to feed the JDA to plugins.
        messageBus.dispatchEvent(new MessageBusEvent("System", null, jda));

        LOGGER.info("Initialization complete. Bot is ready.");
    }
}