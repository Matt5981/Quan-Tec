package org.example.plugins;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.command.Command;
import org.example.webserver.utils.Request;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** <h1>QuanTec Plugin</h1>
 * This is the interface representing a generic plugin for QuanTec. Each of these methods can be optionally overridden to
 * add functionality to the bot. To create a plugin, create a class implementing this interface, implement the desired methods,
 * then compile it to a <code>.class</code> file and place it inside the <code>plugins</code> folder. QuanTec should
 * be restarted to then reload plugins, albeit if it becomes absolutely necessary that no downtime is experienced, QuanTec can
 * be reloaded via <code>PUT</code> request to the <code>/system/reload</code> endpoint.
 */
public interface QuanTecPlugin {

    /** <h2>getListenerAdapter</h2>
     * Adds a listener adapter to QuanTec, allowing the plugin to listen for JDA events. This may use objects set up in
     * the init method. <b>Note:</b> Please do not use this for declaring commands, as it overrides QuanTec's conflict checking. Please declare all commands in the <code>getCommands</code> method.
     * @return A ListenerAdapter which will be added to the JDA instance used by QuanTec.
     */
    default ListenerAdapter getListenerAdapter() {
        return null;
    }

    /** <h2>getRequiredGatewayIntents</h2>
     * Adds the specified gateway intents to QuanTec on JDA instance creation. This runs after the init method, and as such
     * can be populated by that method if gateway intents are dynamic. Duplicate intents will be removed, so assume no intents
     * are present.
     * @return A list of gateway intents to add to QuanTec.
     */
    default List<GatewayIntent> getRequiredGatewayIntents() {
        return null;
    }

    /** <h2>initializePlugin</h2>
     * An init method run by QuanTec. These are all run directly after plugin discovery,
     * but before creating the JDA instance or starting the REST WebServer, so ensure that all necessary setup work (connecting to another API,
     * opening/parsing files, starting threads, etc) is completed in this method. The logger passed is a child logger to the main one, and it is
     * advisable to save this to a class field for later use if logging is required.
     * @param subscribedEvents A reference to a list of strings corresponding to 'recipient' strings for message bus events. You can add strings during this event to 'subscribe' to events sent by that sender. System events (those sent from the main thread) cannot be subscribed to, as they are sent to plugins regardless.
     * @param sender A reference to a consumer method that acts as the 'send' function. If you are planning to send events via this plugin, you should save this to a field where it can be accessed by listeners and commands.
     */
    default void initializePlugin(List<String> subscribedEvents, Consumer<MessageBusEvent> sender) {
    }

    /** <h2>postInitializePlugin</h2>
     * This is a secondary initialization method, called after all plugins have run their 'initializePlugin' methods. The
     * objective of this is to allow plugins to transmit initialization data to each other, as plugins running code in this
     * message can send messages.
     */
    default void postInitializePlugin(){
    }

    /** <h2>getCommands</h2>
     * A list of command objects for QuanTec to screen for when evaluating commands. Note that if two commands are added with the same name, QuanTec
     * will reject one of them.
     * @return A list of command objects as above.
     */
    default List<Command> getCommands(){
        return null;
    }

    /** <h2>getRESTEndpoints</h2>
     * A set of REST endpoint methods as a map, wherein the key is the 'root' node (e.g., using "images" for this string would redirect all REST queries to "/images" and "/images/*")
     * and the value is a function reacting to the request via the provided output stream.
     * @return As above.
     */
    default Map<String, BiConsumer<Request, OutputStream>> getRESTEndpoints() {
        return null;
    }

    /** <h2>onMessageBusEvent</h2>
     * This method allows your plugin to react to events sent via the message bus. To receive these events, you need to subscribe to them via the list provided in the initialization method
     * (except for system events, which are sent regardless).
     * @param event An event sent by another plugin or QuanTec itself.
     */
    default void onMessageBusEvent(final MessageBusEvent event){}

    /** <h2>getVersion</h2>
     * This should return the version of your plugin. It is mandatory, as the plugin loader uses it to assess whether to reset your plugin when plugin reloading is requested. Classes loaded with
     * a version number greater than the presently loaded version will be loaded.
     * @return A version number, similar to a serialVersionUID.
     */
    long getVersion();
}
