package org.example;

import org.example.plugins.MessageBusEvent;
import org.example.plugins.QuanTecPlugin;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.*;

public class ExamplePlugin implements QuanTecPlugin {
    private static final Logger LOGGER = Logger.getLogger("ExamplePlugin");

    @Override
    public void initializePlugin(List<String> subscribedEvents, Consumer<MessageBusEvent> sender){

        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$-7s] %4$s %n";

            @Override
            public synchronized String format(LogRecord logRecord){
                return String.format(format, new Date(logRecord.getMillis()), logRecord.getLoggerName(), logRecord.getLevel().getLocalizedName(), logRecord.getMessage());
            }
        });

        handler.setLevel(Level.INFO);
        LOGGER.addHandler(handler);

        LOGGER.info("Hello, World!");
    }

    @Override
    public List<Command> getCommands(){
        return Collections.singletonList(new Command() {

            @Override
            public String getName(){
                return "examplePluginHelloworld";
            }

            @Override
            public void run(MessageReceivedEvent event) {
                event.getChannel().sendMessage("Hello world! I'm speaking from a plugin!").queue();
            }
        });
    }

    @Override
    public Map<String, BiConsumer<Request, OutputStream>> getRESTEndpoints(){
        HashMap<String, BiConsumer<Request, OutputStream>> map = new HashMap<>();
        map.put("example", (request, outStream) -> {
            try {
                outStream.write(Response.okJSON("{\"message\":\"Hello, World! I'm Speaking from JSON in a plugin's REST endpoint! Version 2!\"}").getBytes());
                outStream.flush();
            } catch (IOException e){
                LOGGER.severe("Got IOException writing response to outStream.");
            }
        });

        return map;
    }

    @Override
    public long getVersion() {
        return 1;
    }
}
