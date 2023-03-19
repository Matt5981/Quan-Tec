package org.example.webserver;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.example.plugins.PluginManager;
import org.example.plugins.QuanTecPlugin;
import org.example.utils.Halter;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/*
    // TODO According to the HTTP/1.1 RFC, servers are NOT allowed to modify their response based on the body of a GET request. Some methods here do that.
    API Endpoints:

        === Information Provider === TODO remove into plugin.
        /users/{snowflake}
            GET - returns the result of getting this user's info from JDA.
        /guilds
            GET - Returns a list of snowflakes corresponding to the servers that QuanTec is a member of, and as such the servers whose emotes and users can be accessed through the /users and /emojis endpoints.
        /guilds/{snowflake}
            GET - Returns the details of a specific guild, specifically the ID, name and server icon.
        /emojis/{snowflake}
            GET - Returns the details of a custom emoji, specifically the ID, name and image link.
        === Control ===
        TODO TEST /core/vers
            GET - Returns JSON with the bot's version.
        TODO TEST /core/plugins
            GET - Returns JSON with a list of plugins currently running on the bot.
        TODO /core/plugins/reload
            GET - Causes QuanTec to reload all plugins.


    API Usage Rules:
        - Any endpoints other than what's specified will return 400.
        - Any endpoints relating to guilds that are used for guilds that Quan-Tec is not in will return 404.
        - Any methods used that aren't explicitly listed above will return 405.
        - All content sent to methods requiring it should be URL encoded, even when specified otherwise (particularly for
          the 'word' endpoints as messages are parsed as URL encoded text when being screened).
        - All API endpoints are provided with no authentication whatsoever. This API is not designed to be opened to the
          internet, and it is the user's responsibility to authenticate attempts to use these.
        - API requests return no headers in some cases. It is the user's responsibility to add these if this is being proxied
          to another application (i.e., the Date, Content-Type and Content-Length headers are all not guaranteed to appear).
 */
public class Client extends Thread {
    private final Socket client;
    private final BlockingQueue<Consumer<JDA>> JDATasks; // TODO deprecated.
    private final String botVers;
    private final Map<String, BiConsumer<Request, OutputStream>> clientEndpoints;
    private final PluginManager pluginManager;
    private final Halter halter;

    private void scheduleNewJDATask(Consumer<JDA> task){
        JDATasks.add(task);
        synchronized (JDATasks) {
            JDATasks.notifyAll();
        }
    }

    public Client(Socket client, BlockingQueue<Consumer<JDA>> JDATasks, String botVers, Map<String, BiConsumer<Request, OutputStream>> clientEndpoints, PluginManager pluginManager, Halter halter){
        this.client = client;
        this.JDATasks = JDATasks;
        this.botVers = botVers;
        this.clientEndpoints = clientEndpoints;
        this.pluginManager = pluginManager;
        this.halter = halter;

        this.start();
    }

    @Override
    public void run(){
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));

            while (true){
                Request req = Request.readRequest(reader);
                if(req == null){
                    // Buffer closed while reading, client disconnected. Exit.
                    return;
                }

                // If halted, wait on the halter until it gets notified.
                if(halter.isLocked()){
                    try {
                        synchronized (halter) {
                            halter.wait();
                        }
                    } catch (InterruptedException e){
                        // Ignored
                    }
                }

                // Since there are identifiers in the path, we need to segregate them.
                // We need to work out if it ends with a '/', on which it should be truncated off. We also need to remove any '/'s at the start.
                String path = req.getPath().substring(1);
                if(path.endsWith("/")){
                    path = path.substring(0,path.length()-2);
                }
                // Now, to get the first identifier we simply remove anything after the first '/', if one is present at all.
                if(path.indexOf('/') != -1){
                    path = path.substring(0,path.indexOf('/'));
                }

                switch(path){

                    case "users" -> {
                        if(!req.getMethod().equals("GET")){
                            Response.writeResponse(Response.methodNotAllowed("request to /users must be GET"), writer);
                            continue;
                        }
                        // A bit easier, since this gets to wait for a JDA job to complete.
                        String userID = req.getPath().replaceAll("/users/", "");
                        Object waiter = new Object();
                        final User[] user = new User[1];
                        user[0] = null;
                        scheduleNewJDATask(jda -> {
                            user[0] = jda.getUserById(userID);
                            synchronized (waiter) {
                                waiter.notifyAll();
                            }
                        });

                        try {
                            synchronized (waiter) {
                                waiter.wait();
                            }
                        } catch (InterruptedException e){
                            // Ignored
                        }

                        // Should be done by now, so let's dismantle that user object into some nice compact JSON.
                        if(user[0] != null){
                            User subj = user[0];

                            String respJson = String.format("{\"%s\":{\"name\":\"%s\",\"id\":\"%s\",\"avatarUrl\":\"%s\"}}",
                                    subj.getId(),
                                    subj.getAsTag(),
                                    subj.getIdLong(),
                                    subj.getAvatarUrl()
                            );

                            Response.writeResponse(Response.okJSON(respJson), writer);
                        } else {
                            Response.writeResponse(Response.notFound("JDA returned null"), writer);
                        }
                    }

                    case "guilds" -> {
                        if(!req.getMethod().equals("GET")){
                            Response.writeResponse(Response.methodNotAllowed("Only GET may be used with /guilds"), writer);
                            continue;
                        }
                        // This might be requesting a specific guild's info, so we'll differentiate based on whether the
                        // path is "/guilds/" or "/guilds". Everything else is assumed to be a specific request.
                        if(req.getPath().equals("/guilds/") || req.getPath().equals("/guilds")){
                            // General request. Get all guilds QuanTec is part of, distill into list and return.
                            Object waiter = new Object();
                            List<String> guilds = new ArrayList<>();
                            scheduleNewJDATask(jda -> {
                                List<Guild> guildList = jda.getGuilds();
                                for(Guild guild : guildList){
                                    guilds.add(guild.getId());
                                }
                                synchronized (waiter) {
                                    waiter.notifyAll();
                                }
                            });
                            try {
                                synchronized (waiter) {
                                    waiter.wait();
                                }
                            } catch (InterruptedException e){
                                e.printStackTrace();
                            }

                            StringBuilder json = new StringBuilder();
                            json.append("{\"guilds\":[");
                            for(String val : guilds){
                                json.append("\"").append(val).append("\"").append(",");
                            }
                            if(!guilds.isEmpty()){
                                json.deleteCharAt(json.length()-1);
                            }
                            json.append("]}");

                            Response.writeResponse(Response.okJSON(json.toString()), writer);
                        } else {
                            // Get info regarding specific Guild ID.
                            String idStr = req.getPath();
                            while(idStr.indexOf('/') != -1){
                                idStr = idStr.substring(idStr.indexOf('/') + 1);
                            }

                            // I sure do love unnecessary temp variables for Java's weird lambda rules!
                            final String subjId = idStr;
                            Object waiter = new Object();
                            List<String> ret = new ArrayList<>();
                            scheduleNewJDATask(jda -> {
                                Guild guild = jda.getGuildById(subjId);

                                if(guild != null){
                                    ret.add(String.valueOf(guild.getIdLong()));
                                    ret.add(guild.getName());
                                    ret.add(guild.getIconUrl());
                                }

                                synchronized (waiter) {
                                    waiter.notifyAll();
                                }
                            });
                            try {
                                synchronized (waiter) {
                                    waiter.wait();
                                }
                            } catch (InterruptedException e){
                                e.printStackTrace();
                            }

                            if(ret.isEmpty()){
                                Response.writeResponse(Response.notFound("JDA returned null"), writer);
                            } else {
                                String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"iconURL\":\"%s\"}",
                                        ret.get(0),
                                        ret.get(1),
                                        ret.get(2)
                                );
                                Response.writeResponse(Response.okJSON(json), writer);
                            }
                        }
                    }

                    case "emojis" -> {
                        String idStr = req.getPath();
                        while(idStr.indexOf('/') != -1){
                            idStr = idStr.substring(idStr.indexOf('/') + 1);
                        }

                        // I sure do love unnecessary temp variables for Java's weird lambda rules!
                        final String subjId = idStr;
                        Object waiter = new Object();
                        List<String> ret = new ArrayList<>();
                        scheduleNewJDATask(jda -> {
                            Emoji emoji = jda.getEmojiById(subjId);

                            if(emoji != null){
                                ret.add(subjId);
                                ret.add(emoji.getName());
                                ret.add("https://cdn.discordapp.com/emojis/"+subjId+".png");
                            }

                            synchronized (waiter) {
                                waiter.notifyAll();
                            }
                        });
                        try {
                            synchronized (waiter) {
                                waiter.wait();
                            }
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }

                        if(ret.isEmpty()){
                            Response.writeResponse(Response.notFound("JDA returned null"), writer);
                        } else {
                            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"iconURL\":\"%s\"}",
                                    ret.get(0),
                                    ret.get(1),
                                    ret.get(2)
                            );
                            Response.writeResponse(Response.okJSON(json), writer);
                        }
                    }

                    case "core" -> {
                        switch(req.getPath()){
                            case "/core/vers" -> {
                                if(!req.getMethod().equals("GET")){
                                    Response.writeResponse(Response.methodNotAllowed("Method not allowed, expected GET."), writer);
                                    continue;
                                }
                                Response.writeResponse(Response.okJSON(String.format("{\"vers\":\"%s\"}", botVers)), writer);
                            }

                            case "/core/plugins" -> {
                                if(!req.getMethod().equals("GET")){
                                    Response.writeResponse(Response.methodNotAllowed("Method not allowed, expected GET."), writer);
                                    continue;
                                }
                                // More JSON!
                                StringBuilder json = new StringBuilder();
                                json.append("{\"plugins\":[");
                                for(QuanTecPlugin plugin : pluginManager.getPluginList()){
                                    json.append(String.format("{\"name\":\"%s\",\"vers\":%d},", plugin.getClass().getSimpleName(), plugin.getVersion()));
                                }

                                if(pluginManager.getPluginList().size() != 0){
                                    json.deleteCharAt(json.length()-1);
                                }

                                json.append("]}");

                                Response.writeResponse(Response.okJSON(json.toString()), writer);
                            }

                            case "/core/plugins/reload" -> {
                                pluginManager.reloadPlugins(halter);
                                Response.writeResponse(Response.okNoContent(), writer);
                            }

                            default -> Response.writeResponse(Response.badRequest("Unknown endpoint"), writer);
                        }
                    }

                    default -> {
                        if(clientEndpoints.get(path) != null){
                            clientEndpoints.get(path).accept(req, client.getOutputStream());
                        } else {
                            Response.writeResponse(Response.badRequest("Unknown endpoint"), writer);
                        }
                    }
                }
            }
        } catch (SocketException e){
            // Ignored
        } catch (IOException e){
            System.out.println("[WebServer Client Thread] ERR IOException thrown: "+e.getMessage());
        }
    }

    public static Map<String, String> parseFormData(String data){
        // Parses data sent with the "content-type: application/x-www-form-urlencoded" header present.
        Map<String, String> out = new HashMap<>();
        for(String line : data.split("&")){
            out.put(line.split("=")[0], line.split("=")[1]);
        }

        return out;
    }
}
