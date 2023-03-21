package org.example.webserver;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.example.Main;
import org.example.plugins.PluginManager;
import org.example.plugins.QuanTecPlugin;
import org.example.utils.Halter;
import org.example.utils.PermissionChecker;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.function.BiConsumer;

/*
    // TODO According to the HTTP/1.1 RFC, servers are NOT allowed to modify their response based on the body of a GET request. Some methods here do that.
    API Endpoints:

        === Information Provider === TODO remove into plugin.
        /users/{snowflake}
            GET - returns the result of getting this user's info from JDA.
        /users/manageserver/{snowflake}
            GET - returns, in JSON, a list of guild snowflakes where the provided user has MANAGE_SERVER.
        /guilds
            GET - Returns a list of snowflakes corresponding to the servers that QuanTec is a member of, and as such the servers whose emotes and users can be accessed through the /users and /emojis endpoints.
        /guilds/{snowflake}
            GET - Returns the details of a specific guild, specifically the ID, name and server icon.
        /emojis/{snowflake}
            GET - Returns the details of a custom emoji, specifically the ID, name and image link.
        === Control ===
        /core/vers
            GET - Returns JSON with the bot's version.
        /core/plugins
            GET - Returns JSON with a list of plugins currently running on the bot.
        TODO TEST /core/plugins/reload
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
    private final String botVers;
    private final Map<String, BiConsumer<Request, OutputStream>> clientEndpoints;
    private final PluginManager pluginManager;
    private final Halter halter;
    private final JDA jda;

    public Client(Socket client, JDA jda, Map<String, BiConsumer<Request, OutputStream>> clientEndpoints, PluginManager pluginManager, Halter halter){
        this.client = client;
        this.jda = jda;
        this.botVers = Main.botVers;
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

                        String fmtPath = req.getPath().substring(1);
                        if(fmtPath.split("/").length == 2) {

                            String userID = req.getPath().replaceAll("/users/", "");
                            // Small check here to make sure it's a valid ID.
                            try {
                                Long.parseLong(userID);
                            } catch (NumberFormatException e){
                                Response.writeResponse(Response.badRequest("Invalid snowflake."), writer);
                                continue;
                            }
                            final User user = jda.getUserById(userID);

                            // Should be done by now, so let's dismantle that user object into some nice compact JSON.
                            if (user != null) {
                                String respJson = String.format("{\"%s\":{\"name\":\"%s\",\"id\":\"%s\",\"avatarUrl\":\"%s\"}}",
                                        user.getId(),
                                        user.getAsTag(),
                                        user.getIdLong(),
                                        user.getAvatarUrl()
                                );
                                Response.writeResponse(Response.okJSON(respJson), writer);
                            } else {
                                Response.writeResponse(Response.notFound("JDA returned null"), writer);
                            }
                        } else if(fmtPath.split("/").length == 3 && fmtPath.startsWith("users/manageserver/")) {
                            // Return array of all servers where the user is both a member and has the MANAGE_SERVER permission through any role.
                            String UID = fmtPath.replace("users/manageserver/", "");
                            try {
                                Long.parseLong(UID);
                            } catch (NumberFormatException e){
                                Response.writeResponse(Response.badRequest("Invalid snowflake provided."), writer);
                                return;
                            }

                            // Check if user is known by quantec.
                            if(jda.getUserById(UID) == null){
                                Response.writeResponse(Response.notFound("User not found."), writer);
                                return;
                            }

                            // Since it is known, iterate through all member guilds and add the IDs of them if our user is a part of that guild.
                            List<String> snowflakes = new ArrayList<>();
                            jda.getGuilds().forEach(guild -> {
                                Member member = guild.getMemberById(UID);
                                if(member != null && PermissionChecker.userHasPermission(member, Permission.MANAGE_SERVER)){
                                    snowflakes.add(guild.getId());
                                }
                            });

                            // Assemble JSON and send back.
                            StringBuilder json = new StringBuilder();
                            json.append("{\"guilds\":[");
                            for(String guild : snowflakes){
                                json.append(String.format("\"%s\",", guild));
                            }

                            if(snowflakes.size() != 0){
                                json.deleteCharAt(json.length()-1);
                            }

                            json.append("]}");
                            Response.writeResponse(Response.okJSON(json.toString()), writer);

                        } else {
                            Response.writeResponse(Response.badRequest("Unknown endpoint."), writer);
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
                            List<String> guilds = new ArrayList<>();
                            jda.getGuilds().forEach(guild -> guilds.add(guild.getId()));

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

                            Guild guild = jda.getGuildById(idStr);

                            if(guild == null){
                                Response.writeResponse(Response.notFound("JDA returned null"), writer);
                            } else {
                                String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"iconURL\":\"%s\"}",
                                        guild.getId(),
                                        guild.getName(),
                                        guild.getIconUrl()
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

                        Emoji emoji = jda.getEmojiById(idStr);

                        if(emoji == null){
                            Response.writeResponse(Response.notFound("JDA returned null"), writer);
                        } else {
                            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"iconURL\":\"%s\"}",
                                    idStr,
                                    emoji.getName(),
                                    "https://cdn.discordapp.com/emojis/"+idStr+".png"
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
