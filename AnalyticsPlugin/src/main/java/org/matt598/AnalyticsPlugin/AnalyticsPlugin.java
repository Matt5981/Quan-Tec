package org.matt598.AnalyticsPlugin;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.Main;
import org.example.command.Command;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;
import org.matt598.AnalyticsPlugin.analytics.AnalyticsListener;
import org.matt598.AnalyticsPlugin.analytics.AnalyticsManager;
import org.example.plugins.MessageBusEvent;
import org.example.plugins.QuanTecPlugin;
import org.matt598.AnalyticsPlugin.analytics.commands.analytics;
import org.matt598.AnalyticsPlugin.analytics.commands.lastPass;

import java.io.OutputStream;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.example.Main.botVers;

/*
    API METHODS:

    === Analytics ===
        /analytics
            GET - returns all analytics to do with QuanTec.
 */
public class AnalyticsPlugin implements QuanTecPlugin {
    private final AnalyticsManager analyticsManager;
    private Consumer<MessageBusEvent> sender;
    private List<String> subscribedEvents;
    private static final Logger LOGGER = Logger.getLogger("AnalyticsPlugin");

    public AnalyticsPlugin(){

        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$-7s] %4$s %n";

            @Override
            public synchronized String format(LogRecord logRecord){
                return String.format(format, new Date(logRecord.getMillis()), logRecord.getLoggerName(), logRecord.getLevel().getLocalizedName(), logRecord.getMessage());
            }
        });
        LOGGER.addHandler(handler);

        this.analyticsManager = AnalyticsManager.getInstance("analytics.bin");
        if(this.analyticsManager == null){
            LOGGER.severe("AnalyticsManager was null after creating. Analytics will not be saved until this plugin is reset.");
        }
    }

    @Override
    public void initializePlugin(List<String> subscribedEvents, Consumer<MessageBusEvent> sender){
        this.sender = sender;
        this.subscribedEvents = subscribedEvents;
        subscribedEvents.add("censorWdScreen");
        subscribedEvents.add("censorImgRemove");
        subscribedEvents.add("censorWdRemove");
    }

    @Override
    public void postInitializePlugin(){
        sender.accept(new MessageBusEvent("analyticsPlugin", null, this.analyticsManager));
    }

    @Override
    public ListenerAdapter getListenerAdapter(){
        return new AnalyticsListener(this.analyticsManager);
    }

    @Override
    public Map<String, BiConsumer<Request, OutputStream>> getRESTEndpoints() {
        Map<String, BiConsumer<Request, OutputStream>> out = new HashMap<>();

        out.put("analytics", (req, outputStream) -> {
            if(!req.getMethod().equals("GET")){
                Response.writeResponse(Response.methodNotAllowed("request to /analytics must be GET"), outputStream);
                return;
            }
            // Make analytics JSON and send.
            String json = String.format("{\"botVers\":\"%s\",\"processedCommands\":%d,\"screenedMessages\":%d,\"removedMessages\":%d,\"blockedImages\":%d,\"flurogingerPassAppearances\":%d,\"lastPassAppearance\":%d,\"emoteSightings\":%s}",
                    botVers,
                    analyticsManager.getProcessedCommands(),
                    analyticsManager.getScreenedMessages(),
                    analyticsManager.getRemovedMessages(),
                    analyticsManager.getBlockedImages(),
                    analyticsManager.getFlurogingerPassAppearances(),
                    analyticsManager.getLastAppearanceOfFlurogingerPass(),
                    analyticsManager.formatEmoteAppearancesToJSON()
            );
            Response.writeResponse(Response.okJSON(json), outputStream);
        });

        return out;
    }

    @Override
    public List<Command> getCommands(){
        // The analytics manager only has a couple of these.
        ArrayList<Command> ret = new ArrayList<>();
        ret.add(new lastPass(this.analyticsManager));
        ret.add(new analytics(this.analyticsManager, botVers));
        return ret;
    }

    @Override
    public void onMessageBusEvent(MessageBusEvent event) {
        if(event.recipient() != null) {
            switch (event.recipient()) {
                case "censorWdScreen" -> this.analyticsManager.setScreenedMessages(this.analyticsManager.getScreenedMessages() + 1);
                case "censorImgRemove" -> this.analyticsManager.setBlockedImages(this.analyticsManager.getBlockedImages() + 1);
                case "censorWdRemove" -> this.analyticsManager.setRemovedMessages(this.analyticsManager.getRemovedMessages() + 1);
            }
        }
    }

    @Override
    public long getVersion() {
        return 0;
    }
}