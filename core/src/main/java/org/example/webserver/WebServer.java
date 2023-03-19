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
import java.util.function.Consumer;
import java.util.logging.*;

// A small REST api that allows QuanTec Overwatch to nab data from this.
// It does *some* checks, e.g. it will refuse connections from locations that aren't localhost
// It's also (temporarily) an actual REST api, since it uses endpoints instead of command strings.
public class WebServer extends Thread {
    private final Logger logger = Logger.getLogger("WebServer");

    private final JDA jda;

    private final BlockingQueue<Consumer<JDA>> JDATasks; // TODO deprecated.
    private final int port;
    private final String botVers;
    private final Map<String, BiConsumer<Request, OutputStream>> pluginEndpoints;
    private final PluginManager pluginManager;
    private final Halter halter;

    public WebServer(JDA jda, int port, String botVers, PluginManager pluginManager, Halter halter){
        this.jda = jda;
        this.JDATasks = new ArrayBlockingQueue<>(4096);
        this.port = port;
        this.botVers = botVers;
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
        // It's a blocking queue for a reason! We're going to run (in another thread) *another* thread to run JDA
        // tasks. This makes sure the number of threads accessing JDA stays at 2 (main and this one), and doesn't
        // blow out when we have multiple users.
        Thread JDATaskExecutor = new Thread(() -> {
            try {
                while(true){
                    synchronized (JDATasks) {
                        JDATasks.wait();
                    }
                    while(JDATasks.peek() != null){
                        JDATasks.poll().accept(jda);
                    }
                }
            } catch (InterruptedException e){
                // Ignored
                logger.warning("JDATaskExecutor interrupted!");
            }
        });
        JDATaskExecutor.start();

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Started server on port "+port+"/tcp.");
            while(true){
                Socket cli = serverSocket.accept();
                if(cli.getInetAddress().isLoopbackAddress()){
                    clientList.add(new Client(cli, JDATasks, botVers, pluginEndpoints, pluginManager, this.halter));
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
