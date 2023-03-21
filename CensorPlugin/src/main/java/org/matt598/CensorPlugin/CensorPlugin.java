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
import java.io.InvalidClassException;
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
            GET - returns JSON with a list of images, specifically for each banned image the image's name, whether it's enabled for adaptive filtering, and the number of adaptive images.
            PUT - containing valid JSON with the image's nickname (string) and the image itself encoded in Base64, this adds another image to QuanTec's filter.
            POST - containing application/x-www-form-urlencoded with the image's nickname, whether or not the image should be adaptively filtered, and a new nickname, this sets an
                   image's nickname/filename or whether it's enabled for adaptive filtering. All 4 values must be present, albeit the 'newnickname' field can be blank or the same as 'oldnickname'
                   to not update the image's nickname.
            DELETE - containing an image's nickname in application/x-www-form-urlencoded, this removes an image from QuanTec's filter.
        /images/{guild snowflake}/{image nickname}
            GET - Returns an octet stream with the aggregate image.
        /images/tolerance
            POST - gets the tolerance of the image filter, or how similar an image has to be for it to remove it. 0-100 (percent).
            PUT - updates the tolerance of the image filter.
        /words/{guildsnowflake}
            GET - returns JSON with a list of banned words (case insensitive)
            PUT - containing valid JSON with a list of strings, will add all strings to the list of banned words. TODO use path instead of JSON
            DELETE - containing valid JSON with a list of strings, deletes any strings in the banned word list from the banned word list. TODO path instead of JSON
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
                        // Tolerance or normal images.
                        if(fmtPath.equals("images/tolerance")){
                            if(!(req.getMethod().equals("POST") || req.getMethod().equals("PUT"))){
                                Response.writeResponse(Response.methodNotAllowed("Expected POST or PUT."), writer);
                            } else if(req.getHeaders().get("content-type") == null || !req.getHeaders().get("content-type").equals("application/x-www-form-urlencoded")) {
                                Response.writeResponse(Response.badRequest("Invalid or missing content type, expected application/x-www-form-urlencoded"), writer );
                            } else {
                                // Finally we get to decide what to do!
                                if(req.getMethod().equals("POST")){
                                    // Should contain only one token if we split by '&'.
                                    if(req.getContent() == null){
                                        Response.writeResponse(Response.badRequest("Expected content."), writer);
                                    } else if(req.getContent().split("&").length > 1) {
                                        Response.writeResponse(Response.badRequest("Content invalid."), writer);
                                    } else {
                                        Integer ret = censorManager.getTolerance(req.getContent().split("=")[1]);
                                        if(ret != null){
                                            Response.writeResponse(Response.okJSON(String.format("{\"tolerance\":%d}", ret)), writer);
                                        } else {
                                            Response.writeResponse(Response.notFound("Guild not found."), writer);
                                        }
                                    }
                                } else {
                                    if(req.getContent() == null){
                                        Response.writeResponse(Response.badRequest("Expected content."), writer);
                                    } else if(req.getContent().split("&").length != 2) {
                                        Response.writeResponse(Response.badRequest("Content invalid."), writer);
                                    } else {
                                        try {
                                            String subj = req.getContent().split("&")[0].split("=")[1];
                                            int newTol = Integer.parseInt(req.getContent().split("&")[1].split("=")[1]);
                                            if(!(0 <= newTol && newTol <= 100)){
                                                Response.writeResponse(Response.badRequest("Expected 0 <= new tolerance <= 100."), writer);
                                            } else {
                                                censorManager.setTolerance(subj, newTol);
                                                Response.writeResponse(Response.okNoContent(), writer);
                                            }
                                        } catch (ArrayIndexOutOfBoundsException e){
                                            Response.writeResponse(Response.badRequest("Content invalid."), writer);
                                        }
                                    }
                                }
                            }
                        } else {
                            // Just the 'images' endpoint.
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

                                case "POST" -> {
                                    if(req.getContent() == null){
                                        Response.writeResponse(Response.badRequest("Expected content."), writer);
                                        break;
                                    }

                                    // Try to parse data.
                                    Map<String, String> content = parseFormData(req.getContent());

                                    // Check for the 'oldnickname', 'adaptive' and 'newnickname' keys.
                                    if(!content.containsKey("oldnickname") || !content.containsKey("adaptive") || !content.containsKey("newnickname")){
                                        Response.writeResponse(Response.badRequest("Missing mandatory field."), writer);
                                        break;
                                    }

                                    // check if newnickname is blank.
                                    if(content.get("newnickname").equals("")){
                                        Response.writeResponse(Response.badRequest("newnickname field must not be blank."), writer);
                                        break;
                                    }

                                    // Locate the image in the guild.
                                    CensorManager.Image subj = null;
                                    String guild = fmtPath.split("/")[1];

                                    if(censorManager.getBannedImages(guild) == null){
                                        Response.writeResponse(Response.notFound("Guild not found."), writer);
                                        break;
                                    }

                                    for(CensorManager.Image image : censorManager.getBannedImages(guild)){
                                        if(image.getNickname().equals(content.get("oldnickname"))){
                                            subj = image;
                                            break;
                                        }
                                    }

                                    if(subj == null){
                                        Response.writeResponse(Response.notFound("Image not found."), writer);
                                        break;
                                    }

                                    // Update all fields.
                                    subj.setNickname(content.get("newnickname"));
                                    subj.setAdaptive(Boolean.parseBoolean(content.get("adaptive")));
                                    Response.writeResponse(Response.okNoContent(), writer);
                                }

                                case "DELETE" -> {
                                    // Similar to POST, except with a removeIf call.
                                    if(req.getContent() == null){
                                        Response.writeResponse(Response.badRequest("Expected content."), writer);
                                        break;
                                    }

                                    // Try to parse data.
                                    Map<String, String> content = parseFormData(req.getContent());

                                    // Check for the 'nickname' key.
                                    if(!content.containsKey("nickname")){
                                        Response.writeResponse(Response.badRequest("Missing mandatory field."), writer);
                                        break;
                                    }

                                    // Locate the image in the guild.
                                    String guild = fmtPath.split("/")[1];

                                    if(censorManager.getBannedImages(guild) == null){
                                        Response.writeResponse(Response.notFound("Guild not found."), writer);
                                        break;
                                    }

                                    for(CensorManager.Image image : censorManager.getBannedImages(guild)){
                                        if(image.getNickname().equals(content.get("oldnickname"))){
                                            censorManager.getBannedImages(guild).remove(image);
                                            Response.writeResponse(Response.okNoContent(), writer);
                                            break;
                                        }
                                    }

                                    Response.writeResponse(Response.notFound("Image not found."), writer);
                                }

                                default -> Response.writeResponse(Response.methodNotAllowed("Expected one of GET, PUT, POST, DELETE"), writer);
                            }
                        }

                    }

                    case 3 -> {
                        // Image download request.
                        if(fmtPath.split("/").length != 3){
                            Response.writeResponse(Response.badRequest("Unknown endpoint."), writer);
                        } else {
                            byte[] content = censorManager.compileFilterAggregate(fmtPath.split("/")[1], fmtPath.split("/")[2]);
                            if(content == null){
                                Response.writeResponse(Response.notFound("Not found."), writer);
                            } else {
                                Response.writeFile(content, "image/x-png", writer);
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
                            Pattern guildID = Pattern.compile("(?<=\"guild\":\")(.*?)(?=\")");
                            Matcher gID = guildID.matcher(req.getContent());
                            if(!gID.find()){
                                Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                                break;
                            }

                            String guild = gID.group();
                            // Check if guild exists.
                            if(censorManager.getBannedWords(guild) == null){
                                Response.writeResponse(Response.notFound("Invalid guild."), writer);
                                break;
                            }
                            // Get words. We'll parse it in two steps.
                            Pattern listEx = Pattern.compile("(?<=\\[).*?(?=])");
                            Matcher listM = listEx.matcher(req.getContent());
                            if(!listM.find()){
                                Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                                break;
                            }

                            String list = gID.group();

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
                            Pattern guildID = Pattern.compile("(?<=\"guild\":\")(.*?)(?=\")");
                            Matcher gID = guildID.matcher(req.getContent());
                            if(!gID.find()){
                                Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                                break;
                            }

                            String guild = gID.group();
                            // Check if guild exists.
                            if(censorManager.getBannedWords(guild) == null){
                                Response.writeResponse(Response.notFound("Invalid guild."), writer);
                                break;
                            }
                            // Get words. We'll parse it in two steps.
                            Pattern listEx = Pattern.compile("(?<=\\[).*?(?=])");
                            Matcher listM = listEx.matcher(req.getContent());
                            if(!listM.find()){
                                Response.writeResponse(Response.badRequest("Malformed JSON."), writer);
                                break;
                            }

                            String list = gID.group();

                            Pattern wordEx = Pattern.compile("(?<=\")[^,]*?(?=\")");
                            Matcher words = wordEx.matcher(list);

                            List<String> toCensor = new ArrayList<>();
                            while(words.find()){
                                toCensor.add(words.group());
                            }

                            // Add.
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