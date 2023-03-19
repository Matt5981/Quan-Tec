package org.example.plugins;

import net.dv8tion.jda.api.JDA;
import org.example.command.Command;
import org.example.listeners.CommandListener;
import org.example.utils.Halter;
import org.example.webserver.WebServer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class PluginManager {
    private static final String pluginFolderName = "plugins";
    private static final Logger systemLogger = Logger.getLogger("System");
    private final List<QuanTecPlugin> quanTecPlugins;
    private JDA jda;
    private MessageBus messageBus;
    private CommandListener commandListener;
    private WebServer webServer;

    public static PluginManager getInstance(){
        return new PluginManager(loadPlugins());
    }

    private PluginManager(List<QuanTecPlugin> quanTecPlugins){
        this.quanTecPlugins = quanTecPlugins;
    }

    public List<QuanTecPlugin> getPluginList(){
        return this.quanTecPlugins;
    }

    public void initializePlugins(JDA jda, MessageBus messageBus, CommandListener commandListener, WebServer webServer){
        this.jda = jda;
        this.messageBus = messageBus;
        this.commandListener = commandListener;
        this.webServer = webServer;
        // Runs init methods and adds listeners, commands and REST endpoints.
        systemLogger.info("Running plugin initialization methods...");
        this.quanTecPlugins.forEach(plugin -> {
            ArrayList<String> subscribed = new ArrayList<>();
            plugin.initializePlugin(subscribed, messageBus::dispatchEvent);
            messageBus.registerPluginEventSubscriptions(plugin, subscribed);
        });
        systemLogger.info("Running plugin post-initialization methods...");
        this.quanTecPlugins.forEach(QuanTecPlugin::postInitializePlugin);
        systemLogger.info("Adding plugin listeners/endpoints...");

        // Sift through commands for each plugin, using a hashmap to both check if they're duplicates and
        // get the name of both conflicting plugins should we find any. It also keeps the time complexity
        // of it down to O(n) (as we don't reiterate at any point, every command is checked exactly once).
        // We'll add unique commands to a list, which can then be batch-added to the command listener.
        // TODO test adding duplicate commands.
        // TODO test a plugin command containing a reference to another object.
        // TODO is nesting forEach calls bad practice?
        Map<Command, String> pluginCommands = new HashMap<>();
        this.quanTecPlugins.forEach(plugin ->
                plugin.getCommands().forEach(cmdCandidate -> {
                    if(pluginCommands.get(cmdCandidate) == null){
                        pluginCommands.put(cmdCandidate, plugin.getClass().getSimpleName());
                    } else {
                        systemLogger.severe("Command name conflict: \""+plugin.getClass().getSimpleName()+"\" attempted to add command with name \""+cmdCandidate.getName()+"\", already added by \""+pluginCommands.get(cmdCandidate)+"\". Command will not be loaded.");
                    }
                })
        );

        // Add plugin commands.
        pluginCommands.keySet().forEach(cmd -> {
            try {
                commandListener.addCommand(cmd);
            } catch (IllegalArgumentException e){
                systemLogger.severe("Command name conflict: Plugin \""+pluginCommands.get(cmd)+"\" attempted to add command with name \""+cmd.getName()+"\", conflicts with system command. Command will not be loaded.");
            }
        });

        // Add plugin listener adapters.
        for(QuanTecPlugin plugin : this.quanTecPlugins){
            if(plugin.getListenerAdapter() != null){
                jda.addEventListener(plugin.getListenerAdapter());
            }
        }

        // Add REST endpoints. No, making this map accessible with a getter is not bad design. FIXME

        Map<String, QuanTecPlugin> duplicateCheck = new HashMap<>();

        // Prohibit the 'core' endpoint, since those are all the API methods that we add ourselves.
        // Duplicate check. This looks inefficient, but the use of hashmaps here means it only iterates once, just
        // over the combined set of REST endpoints from each plugin, resulting in O(n). TODO unit test for this.
        QuanTecPlugin systemReserver = () -> 0L;
        duplicateCheck.put("core", systemReserver);

        for(QuanTecPlugin plugin : this.quanTecPlugins){
            for(String RESTroute : plugin.getRESTEndpoints().keySet()){
                if(duplicateCheck.get(RESTroute) == null){
                    duplicateCheck.put(RESTroute, plugin);
                    webServer.getPluginEndpoints().put(RESTroute, plugin.getRESTEndpoints().get(RESTroute));
                } else {
                    systemLogger.severe("Endpoint conflict: Plugin \""+plugin.getClass().getSimpleName()+"\" attempted to register endpoint \""+RESTroute+"\", already registered by other plugin \""+(duplicateCheck.get(RESTroute) == systemReserver ? "System" : duplicateCheck.get(RESTroute).getClass().getSimpleName())+"\". Conflicting endpoint will not be added.");
                }
            }
        }

        systemLogger.info("Plugin initialization complete.");
    }

    public void reloadPlugins(Halter halter){
        systemLogger.info("Plugin reload requested. Locking web server and command listener and reloading.");
        halter.setLocked(true);

        // Get the new set of plugins to load.
        List<QuanTecPlugin> newPlugins = loadPlugins();

        // For each plugin we currently have in memory, check whether they have a plugin with the same classname as them in
        // our new plugin list. If they have the same name, compare the version numbers. If the version numbers are the same,
        // do not modify them. If the version number of the new one is <= the old one, ignore, else add to initialization queue.
        // TODO check time complexity, which is O(n) i think.
        Map<String, QuanTecPlugin> classNameMap = new HashMap<>();
        List<String> newClassNames = new ArrayList<>();
        this.quanTecPlugins.forEach(plugin -> classNameMap.put(plugin.getClass().getName(), plugin));

        // If a plugin is not present at all, add it to a removal list and remove it from the plugin list.
        List<QuanTecPlugin> deletionQueue = new ArrayList<>();
        List<QuanTecPlugin> initializationQueue = new ArrayList<>();

        for(QuanTecPlugin plugin : newPlugins){
            newClassNames.add(plugin.getClass().getName());

            if(classNameMap.get(plugin.getClass().getName()) != null){
                // Check version numbers.
                if(plugin.getVersion() > classNameMap.get(plugin.getClass().getName()).getVersion()){
                    deletionQueue.add(classNameMap.get(plugin.getClass().getName()));
                    initializationQueue.add(plugin);
                }
            }
        }

        this.quanTecPlugins.forEach(plugin -> {
            if(!newClassNames.contains(plugin.getClass().getName())){
                deletionQueue.add(plugin);
            }
        });

        // We now have two queues, a list of plugins to delete (will be done first),
        // and a list of plugins to initialize.

        // We'll start with the deletion queue.
        // remove their listeners, commands and REST endpoints, then remove them. This will remove
        // all references to these objects, which should let the garbage collector grab them. This
        // won't happen however if any of the deletion plugins spawn threads that haven't died,
        // but it should work in most cases.
        for(QuanTecPlugin plugin : deletionQueue){
            if(plugin.getListenerAdapter() != null){
                jda.removeEventListener(plugin.getListenerAdapter());
            }

            for(Command command : plugin.getCommands()){
                commandListener.removeCommand(command);
            }

            for(String key : plugin.getRESTEndpoints().keySet()){
                webServer.getPluginEndpoints().remove(key);
            }

            this.quanTecPlugins.remove(plugin);
        }

        // Now the initialization queue. These need to be initialized by running their init and
        // post-init methods, then adding their listener adapters, commands and REST endpoints.

        // The multiple loops here technically can be combined into one, but because all plugins expect
        // their init methods to be run at the same time, and their post init methods after all other
        // plugins have run their first init method, we have to use three loops here.

        for(QuanTecPlugin plugin : initializationQueue){
            List<String> subscribedEvents = new ArrayList<>();
            plugin.initializePlugin(subscribedEvents, messageBus::dispatchEvent);
            messageBus.registerPluginEventSubscriptions(plugin, subscribedEvents);
        }

        initializationQueue.forEach(QuanTecPlugin::postInitializePlugin);

        for(QuanTecPlugin plugin : initializationQueue){

            if(plugin.getListenerAdapter() != null){
                jda.addEventListener(plugin.getListenerAdapter());
            }

            for(Command command : plugin.getCommands()){
                try {
                    commandListener.addCommand(command);
                } catch (IllegalArgumentException e){
                    systemLogger.severe(String.format("Loading new/updated plugin \"%s\"'s command \"%s\" threw an IllegalArgumentException, likely because it already exists. Please restart QuanTec to see the conflict's source. This command will not be loaded.", plugin.getClass().getSimpleName(), command.getName()));
                }
            }

            for(String RESTkey : plugin.getRESTEndpoints().keySet()){
                if(webServer.getPluginEndpoints().get(RESTkey) != null){
                    systemLogger.severe(String.format("Loading new/updated plugin \"%s\"'s REST endpoint \"%s\" already exists. Please restart QuanTec to see the conflict's source. This endpoint will not be loaded.", plugin.getClass().getSimpleName(), RESTkey));
                } else {
                    webServer.getPluginEndpoints().put(RESTkey, plugin.getRESTEndpoints().get(RESTkey));
                }
            }

            this.quanTecPlugins.add(plugin);
        }

        halter.setLocked(false);
        synchronized (halter) {
            halter.notifyAll();
        }

        systemLogger.info("Plugin reload complete. QuanTec is currently running "+this.quanTecPlugins.size()+" plugins.");
    }

    private static List<QuanTecPlugin> loadPlugins(){
        // Check for the existence of the plugin folder.
        File pluginFolderPtr = new File(pluginFolderName);
        List<String> filenames = new ArrayList<>();
        if(pluginFolderPtr.exists() && pluginFolderPtr.isDirectory()){
            // Get files in folder, adding any that end with '.class' to our filenames list.
            try(Stream<Path> fileStream = Files.walk(Paths.get(pluginFolderName))) {
                fileStream.
                        filter(candidate -> !Files.isDirectory(candidate) && candidate.getFileName().toString().endsWith(".jar"))
                        .forEach(file -> filenames.add(file.getFileName().toString()));
            } catch (IOException e){
                systemLogger.severe("IOException thrown while listing files in plugin directory. No plugins will be loaded.");
                return new ArrayList<>();
            }
        } else {
            // If the folder doesn't exist, make it. If the folder is instead a file, warn. In either case, return an empty list.
            if(!pluginFolderPtr.exists()){
                if(!pluginFolderPtr.mkdir()){
                    systemLogger.severe("Unable to create plugin directory. No plugins will be loaded.");
                    return new ArrayList<>();
                }
            } else {
                systemLogger.severe("Plugin directory is a file. No plugins will be loaded.");
                return new ArrayList<>();
            }
        }

        // Now actually load them. Since we're using .jars, we don't need to worry about naming, since once we know
        // the filename, the classes themselves are contained as entries in the jar.
        List<Class> toCast = new ArrayList<>();

        for(String jarfile : filenames){
            try(JarFile jarFile = new JarFile(pluginFolderPtr.getPath() + "/" + jarfile)) {

                // We'll assemble the URL here so the ugly string concatenation is less noticable.
                String url = "jar:file:" + pluginFolderPtr.getPath() + "/" + jarfile + "!/";

                // This is mostly unnecessary, but Intellij puts an ugly warning here otherwise since we're using an auto-closable
                // resource that throws exceptions.
                // TODO test removing any files with a dollar-sign in their name.
                try (URLClassLoader classLoader = new URLClassLoader(new URL[] { new URL(url) })) {
                    jarFile.entries().asIterator().forEachRemaining(entry -> {
                        if(entry.isDirectory() || !entry.getName().endsWith(".class")){
                            return;
                        }
                        try {
                            toCast.add(classLoader.loadClass(entry.getName().substring(0,entry.getName().length()-6).replace("/", ".")));
                        } catch (ClassNotFoundException e) {
                            systemLogger.severe("ClassNotFoundException thrown while loading plugin \""+jarfile+"\". This plugin will not be loaded.");
                        }
                    });
                }
            } catch (IOException e){
                systemLogger.severe("IOException thrown while loading plugin \""+jarfile+"\". This plugin will not be loaded.");
                e.printStackTrace();
            }
        }

        // Now that we have a bunch of classes, we can try to cast them to plugins.
        List<QuanTecPlugin> ret = new ArrayList<>();
        for(Class<?> candidate : toCast){
            try {
                if(Arrays.stream(candidate.getInterfaces()).toList().contains(QuanTecPlugin.class)){
                    ret.add((QuanTecPlugin) candidate.getDeclaredConstructor().newInstance());
                }
            } catch (Exception e){
                systemLogger.severe("Exception thrown while loading plugin with name "+candidate.getName()+", skipping. If this is a plugin, it may be corrupt. Exception message: "+e.getClass().getSimpleName()+" "+e.getMessage());
            }
        }
        return ret;
    }
}
