package org.matt598.CensorPlugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.command.Command;
import org.example.plugins.MessageBusEvent;
import org.example.plugins.QuanTecPlugin;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;
import org.matt598.CensorPlugin.censor.CensorListener;
import org.matt598.CensorPlugin.censor.CensorManager;
import org.matt598.CensorPlugin.censor.commands.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.webserver.Client.parseFormData;

/*
    API Endpoints:

        === Censorship ===
        /images/{guildsnowflake}
            GET - returns JSON with a list of images, specifically for each banned image the image's name, whether it's enabled for adaptive filtering, and the number of adaptive images. Additionally, the tolerance pcnt is returned.
            PUT - containing valid JSON with the image's nickname (string) and the image itself encoded in Base64, this adds another image to QuanTec's filter.

        /images/{guild snowflake}/{image nickname}
            GET - Returns an octet stream with the aggregate image.
            DELETE - Unbans the specified image, erasing its adaptive data.
            POST - containing application/x-www-form-urlencoded with a nickname and whether the image should be adaptively filtered, this sets an
                   image's nickname/filename or whether it's enabled for adaptive filtering. Both values must be present, albeit the 'nickname' field
                   can be blank or the same as the current nickname to only set whether the image is adaptively filtered.
        /images/tolerance/{guild snowflake}
            GET - gets the tolerance of the image filter, or how similar an image has to be for it to remove it. 0-100 (percent).
            PUT - updates the tolerance of the image filter.
        /words/{guildsnowflake}
            GET - returns JSON with a list of banned words (case insensitive)
            PUT - containing valid JSON with a list of strings, will add all strings to the list of banned words.
            DELETE - containing valid JSON with a list of strings, deletes any strings in the banned word list from the banned word list.
 */
public class CensorPlugin implements QuanTecPlugin {
    private CensorManager censorManager;
    private Consumer<MessageBusEvent> sender;
    private JDA jda;
    private static final Logger LOGGER = Logger.getLogger("CensorPlugin");

    public CensorPlugin(){
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
    }

    @Override
    public ListenerAdapter getListenerAdapter() {
        return new CensorListener(censorManager, sender);
    }

    @Override
    public void initializePlugin(List<String> subscribedEvents, Consumer<MessageBusEvent> sender) {
        try {
            this.censorManager = CensorManager.getInstance("censor.bin", sender);
        } catch (IOException e){
            // TODO this is broken.
            LOGGER.severe("Censor manager failed to load censor file. This is likely due to using a censor.bin file that is incompatible with the current version of the censor plugin. Try downgrading this plugin (requiring a restart) to fix.");
        }
        this.sender = sender;
    }

    @Override
    public void postInitializePlugin() {
        QuanTecPlugin.super.postInitializePlugin();
    }

    @Override
    public List<Command> getCommands() {
        ArrayList<Command> ret = new ArrayList<>();

        ret.add(new banImage(censorManager));
        ret.add(new banWord(censorManager));
        ret.add(new getBannedImages(censorManager));
        ret.add(new getBannedWords(censorManager));
        ret.add(new getImageTolerance(censorManager));
        ret.add(new setImageName(censorManager));
        ret.add(new setImageTolerance(censorManager));
        ret.add(new unbanImage(censorManager));
        ret.add(new unbanWord(censorManager));

        return ret;
    }

    @Override
    public Map<String, BiConsumer<Request, OutputStream>> getRESTEndpoints() {
        Map<String, BiConsumer<Request, OutputStream>> RESTmappings = new HashMap<>();
        RESTmappings.put("images", (req, writer) -> {

            // Check if it's got anything else in the path. If it does it's either a tolerance request or an
            // image download request.
            String fmtPath = req.getPath();
            if(fmtPath.charAt(0) == '/'){
                fmtPath = fmtPath.substring(1);
            }

            switch(fmtPath.split("/").length){
                case 2 -> {
                    // Just the 'images' endpoint. We'll need to check the snowflake here to make sure it's valid before proceeding.
                    try {
                        Long.parseLong(fmtPath.split("/")[1]);
                    } catch (NumberFormatException e){
                        Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                        return;
                    }

                    switch(req.getMethod()){
                        case "GET" -> Response.writeResponse(Response.okJSON(censorManager.getBannedImagesJSON(fmtPath.split("/")[1])), writer);

                        case "PUT" -> {
                            if(req.getContent() == null || req.getHeaders().get("content-type") == null || !req.getHeaders().get("content-type").equals("application/json")){
                                Response.writeResponse(Response.badRequest("Empty/Invalid content."), writer);
                                break;
                            }

                            // JSON should have three fields, "nickname", "adaptive" and "content".
                            // TODO a better way of this, it's messy.
                            String nick, guild, content;
                            Pattern regex = Pattern.compile("(?<=\")[^:,]*?(?=\")");
                            Matcher json = regex.matcher(req.getContent());

                            if(!json.find()){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            if(!json.group().equals("nickname")){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            if(!json.find()){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            // group should be "nickname".
                            nick = json.group();

                            if(!json.find()){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            if(!json.group().equals("content")){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            if(!json.find()){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            // group should be the base64 encoded image.
                            content = json.group();


                            // Process. Name must not be blank and content
                            // must be valid Base64.
                            if(nick.equals("")){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            byte[] img;

                            try {
                                img = Base64.getDecoder().decode(content.getBytes());
                            } catch (IllegalArgumentException e){
                                Response.writeResponse(Response.badRequest("Malformed content."), writer);
                                break;
                            }

                            ArrayList<byte[]> retImg = new ArrayList<>();

                            retImg.add(img);

                            guild = fmtPath.split("/")[1];

                            // Make sure we're actually in the guild we're adding to.
                            boolean found = false;
                            List<Guild> guilds = jda.getGuilds();

                            for(Guild guild1 : guilds){
                                if(guild1.getId().equals(guild)){
                                    found = true;
                                    break;
                                }
                            }

                            if(!found){
                                Response.writeResponse(Response.notFound("Guild not found."), writer);
                                break;
                            }

                            // Add and return 204.
                            censorManager.addImage(guild, new CensorManager.Image(nick, retImg));
                            Response.writeResponse(Response.okNoContent(), writer);
                        }

                        default -> Response.writeResponse(Response.methodNotAllowed("Expected GET or PUT"), writer);
                    }
                }

                case 3 -> {
                    if(fmtPath.startsWith("images/tolerance")){
                        if(!(req.getMethod().equals("GET") || req.getMethod().equals("PUT"))){
                            Response.writeResponse(Response.methodNotAllowed("Expected POST or PUT."), writer);
                            return;
                        }

                        // Check snowflake.
                        try {
                            Long.parseLong(fmtPath.split("/")[2]);
                        } catch (NumberFormatException e){
                            Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                            return;
                        }

                        // Finally we get to decide what to do!
                        if(req.getMethod().equals("GET")){
                            // Parse snowflake
                            Integer ret = censorManager.getTolerance(fmtPath.split("/")[2]);
                            Response.writeResponse(Response.okJSON(String.format("{\"tolerance\":%d}", Objects.requireNonNullElse(ret, 95))), writer);
                        } else {
                            if(req.getContent() == null){
                                Response.writeResponse(Response.badRequest("Expected content."), writer);
                            } else if(req.getHeaders().get("content-type") == null || !req.getHeaders().get("content-type").equals("application/x-www-form-urlencoded") || req.getContent().split("=").length != 2) {
                                Response.writeResponse(Response.badRequest("Content invalid."), writer);
                            } else {
                                int newTol = Integer.parseInt(req.getContent().split("=")[1]);
                                if(!(0 <= newTol && newTol <= 100)){
                                    Response.writeResponse(Response.badRequest("Expected 0 <= new tolerance <= 100."), writer);
                                } else {
                                    censorManager.setTolerance(fmtPath.split("/")[2], newTol);
                                    Response.writeResponse(Response.okNoContent(), writer);
                                }
                            }
                        }
                    } else {
                        // Any method here is '/images' followed by a snowflake, followed by a string, so we'll check the snowflake here to save time.
                        try {
                            Long.parseLong(fmtPath.split("/")[1]);
                        } catch (NumberFormatException e) {
                            Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                            return;
                        }

                        switch (req.getMethod()) {
                            case "GET" -> {
                                // Image download request.
                                byte[] content = censorManager.compileFilterAggregate(fmtPath.split("/")[1], fmtPath.split("/")[2]);
                                if (content == null) {
                                    Response.writeResponse(Response.notFound("Not found."), writer);
                                } else {
                                    Response.writeFile(content, "image/x-png", writer);
                                }

                            }

                            case "DELETE" -> {
                                censorManager.removeImage(fmtPath.split("/")[1], fmtPath.split("/")[2]);
                                Response.writeResponse(Response.okNoContent(), writer);
                            }

                            case "POST" -> {
                                // There'll be content in this one which we need to dig through.
                                if (req.getContent() == null) {
                                    Response.writeResponse(Response.badRequest("Expected content."), writer);
                                    return;
                                }

                                // Try to parse data.
                                Map<String, String> content = parseFormData(req.getContent());

                                // Check for the 'adaptive' and 'nickname' keys.
                                if (!content.containsKey("adaptive") || !content.containsKey("nickname")) {
                                    Response.writeResponse(Response.badRequest("Missing mandatory field."), writer);
                                    break;
                                }

                                // check if nickname is blank.
                                if (content.get("nickname").equals("")) {
                                    Response.writeResponse(Response.badRequest("nickname field must not be blank."), writer);
                                    break;
                                }

                                String guild = fmtPath.split("/")[1], oldNick = fmtPath.split("/")[2];

                                // Boolean.parseBoolean never throws. It will however return false for improper values, but as that isn't
                                // destructive we'll (implicitly) permit it here.
                                if (censorManager.setImageAdaptive(guild, oldNick, Boolean.parseBoolean(content.get("adaptive"))) && censorManager.setImageNickname(guild, oldNick, content.get("nickname"))) {
                                    Response.writeResponse(Response.okNoContent(), writer);
                                } else {
                                    Response.writeResponse(Response.notFound("Not found."), writer);
                                }
                            }

                            default -> Response.writeResponse(Response.methodNotAllowed("Expected GET, POST or DELETE."), writer);
                        }
                    }
                }

                default -> Response.writeResponse(Response.badRequest("Unknown endpoint."), writer);
            }


        });

        RESTmappings.put("words", (req, writer) -> {
            switch(req.getMethod()){
                case "GET" -> {
                    String subj = req.getPath().replace("/words/", "");
                    // Return list of banned words from censorManager.
                    Response.writeResponse(Response.okJSON(censorManager.getBannedWordsJSON(subj)), writer);
                }

                case "PUT" -> {
                    if(req.getHeaders().get("content-type") == null || !req.getHeaders().get("content-type").equals("application/json")){
                        Response.writeResponse(Response.badRequest("Content type invalid, expected application/json."), writer);
                    } else if(req.getContent() == null) {
                        Response.writeResponse(Response.badRequest("Content must not be empty."), writer);
                    } else {
                        // Get each string from the submitted JSON, then add each to the list.
                        if(req.getPath().split("/").length != 3){
                            Response.writeResponse(Response.badRequest("Malformed URL. DEBUG got "+req.getPath().split("/").length+" args."), writer);
                            return;
                        }

                        String guild = req.getPath().substring(req.getPath().lastIndexOf('/')+1);

                        try {
                            Long.parseLong(guild);
                        } catch (NumberFormatException e){
                            Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                            return;
                        }

                        // Check if guild exists.
                        if(censorManager.getBannedWords(guild) == null){
                            Response.writeResponse(Response.notFound("Invalid guild."), writer);
                            return;
                        }

                        // Get words. We'll parse it in two steps.
                        Pattern listEx = Pattern.compile("(?<=\\[).*?(?=])");
                        Matcher listM = listEx.matcher(req.getContent());
                        if(!listM.find()){
                            Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                            return;
                        }

                        String list = listM.group();

                        Pattern wordEx = Pattern.compile("(?<=\")[^,]*?(?=\")");
                        Matcher words = wordEx.matcher(list);

                        List<String> toCensor = new ArrayList<>();
                        while(words.find()){
                            toCensor.add(words.group());
                        }

                        // Add.
                        for(String word : toCensor){
                            censorManager.addWord(guild, word);
                        }

                        Response.writeResponse(Response.okNoContent(), writer);
                    }
                }

                case "DELETE" -> {
                    if(!req.getHeaders().get("content-type").equals("application/json")){
                        Response.writeResponse(Response.badRequest("Content type invalid, expected application/json."), writer);
                    } else if(req.getContent() == null) {
                        Response.writeResponse(Response.badRequest("Content must not be empty."), writer);
                    } else {
                        // Get each string from the submitted JSON, then add each to the list.
                        if(req.getPath().split("/").length != 3){
                            Response.writeResponse(Response.badRequest("Malformed URL. DEBUG got "+req.getPath().split("/").length+" args."), writer);
                            return;
                        }

                        String guild = req.getPath().substring(req.getPath().lastIndexOf('/')+1);

                        try {
                            Long.parseLong(guild);
                        } catch (NumberFormatException e){
                            Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                            return;
                        }

                        // Check if guild exists.
                        if(censorManager.getBannedWords(guild) == null){
                            Response.writeResponse(Response.notFound("Invalid guild."), writer);
                            return;
                        }
                        // Get words. We'll parse it in two steps.
                        Pattern listEx = Pattern.compile("(?<=\\[).*?(?=])");
                        Matcher listM = listEx.matcher(req.getContent());
                        if(!listM.find()){
                            Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                            return;
                        }

                        String list = listM.group();

                        Pattern wordEx = Pattern.compile("(?<=\")[^,]*?(?=\")");
                        Matcher words = wordEx.matcher(list);

                        List<String> toCensor = new ArrayList<>();
                        while(words.find()){
                            toCensor.add(words.group());
                        }

                        // delete.
                        for(String word : toCensor){
                            censorManager.removeWord(guild, word);
                        }

                        Response.writeResponse(Response.okNoContent(), writer);
                    }
                }

                default -> Response.writeResponse(Response.methodNotAllowed("Method not allowed."), writer);
            }
        });
        return RESTmappings;
    }

    @Override
    public void onMessageBusEvent(MessageBusEvent event) {
        if(event.content() instanceof JDA){
            this.jda = (JDA)event.content();
        }
    }

    @Override
    public long getVersion() {
        return 0;
    }
}