package org.matt598.CensorPlugin.censor;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.plugins.MessageBusEvent;
import org.example.utils.PermissionChecker;
import java.util.function.Consumer;

// TODO test what happens if a banned image and a banned word are in the same message, mainly check that it doesn't throw an uncaught NullPointerException.
public class CensorListener extends ListenerAdapter {
    private final CensorManager censorManager;
    private final Consumer<MessageBusEvent> sender;

    public CensorListener(CensorManager censorManager, Consumer<MessageBusEvent> sender){
        this.censorManager = censorManager;
        this.sender = sender;
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        // Ignore messages from bots, or users entering quantec commands with manage server.
        if(event.getAuthor().isBot() || (event.getMessage().getContentRaw().startsWith("q!") && PermissionChecker.userHasPermission(event, Permission.MANAGE_SERVER))){
            return;
        }

        // We'll split this off into a thread, since checking large images may take a while.
        new Thread(() -> {
            // Feed message to censor Manager to scan.
            boolean res = this.censorManager.censorWord(event.getGuild().getId(), event.getMessage());
            sender.accept(new MessageBusEvent("censorManager", "censorWdScreen", null));

            // Check for images.
            if(this.censorManager.censorImage(event.getGuild().getId(), event.getMessage(), res)){
                sender.accept(new MessageBusEvent("censorManager", "censorImgRemove", null));
            }
        }).start();
    }
}
