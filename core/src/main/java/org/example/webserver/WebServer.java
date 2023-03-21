package org.example.webserver;

import net.dv8tion.jda.api.JDA;
import org.example.plugins.PluginManager;
import org.example.utils.Halter;
import org.example.webserver.utils.Request;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.*;

// A small REST api that allows QuanTec Overwatch to nab data from this.
// It does *some* checks, e.g. it will refuse connections from locations that aren't localhost.
// It's REST compliant but because it's designed to be accessed via proxy (like a database), it doesn't
// include the "Host", "Date" or any other headers other than what's absolutely necessary (e.g., "Content-Type" and "Content-Length").
public class WebServer extends Thread {
    private final Logger logger = Logger.getLogger("WebServer");

    private final JDA jda;
    private final int port;
    private final Map<String, BiConsumer<Request, OutputStream>> pluginEndpoints;
    private final PluginManager pluginManager;
    private final Halter halter;

    public WebServer(JDA jda, int port, PluginManager pluginManager, Halter halter){
        this.jda = jda;
        this.port = port;
        this.pluginManager = pluginManager;
        this.pluginEndpoints = new HashMap<>();
        this.halter = halter;
        logger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$s] [%3$-7s] %4$s %n";

            @Override
            public synchronized String format(LogRecord logRecord){
                return String.format(format, new Date(logRecord.getMillis()), logRecord.getLoggerName(), logRecord.getLevel().getLocalizedName(), logRecord.getMessage());
            }
        });

        consoleHandler.setLevel(Level.INFO);
        logger.addHandler(consoleHandler);

        this.start();
    }

    public Map<String, BiConsumer<Request, OutputStream>> getPluginEndpoints(){
        return this.pluginEndpoints;
    }

    @Override
    public void run(){
        List<Client> clientList = new ArrayList<>();

        ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
        service.schedule(() -> clientList.removeIf(ele -> !ele.isAlive()), 1000, TimeUnit.MILLISECONDS);

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Started server on port "+port+"/tcp.");
            while(true){
                Socket cli = serverSocket.accept();
                if(cli.getInetAddress().isLoopbackAddress()){
                    clientList.add(new Client(cli, jda, pluginEndpoints, pluginManager, this.halter));
                } else {
                    logger.severe("non-local address attempted connection to server, will reject. THIS IS EXTREMELY INSECURE! Address: "+cli.getInetAddress().toString());
                    cli.close();
                }
            }
        } catch (SocketException e){
            logger.warning("SocketException thrown, assuming exit requested and shutting down. If this happens on startup, it's because the port QuanTec is using is likely occupied.");
        } catch (IOException e){
            // Assume shutdown, ignore and exit.
            logger.severe("IOException thrown: "+e.getMessage());
        }
        service.shutdown();
    }
}
