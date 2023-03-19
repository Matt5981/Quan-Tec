package org.matt598.CensorPlugin.censor.commands;

import org.example.command.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.utils.PermissionChecker;
import org.matt598.CensorPlugin.censor.CensorManager;

import java.util.ArrayList;
import java.util.List;

public class unbanImage implements Command {
    private final CensorManager censorManager;

    public unbanImage(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){
        // Has arguments.
        if(event.getMessage().getContentRaw().split(" ").length < 2){
            event.getChannel().sendMessage("**Error!** This command requires at least 1 argument.").queue();
        } else if(!PermissionChecker.userHasPermission(event, Permission.MANAGE_SERVER)) {
            event.getChannel().sendMessage("**Error!** This command requires the `Manage Server` permission.").queue();
        } else {
            List<String> toRemove = new ArrayList<>(List.of(event.getMessage().getContentRaw().substring(event.getMessage().getContentRaw().indexOf(' ') + 1).split(" ")));

            StringBuilder responseString = new StringBuilder();
            responseString.append("**Unbanned the images with the following names:** ");

            for(String candidate : toRemove){
                censorManager.removeImage(event.getGuild().getId(), candidate);
                responseString.append(String.format("`%s`,  ", candidate));
            }
            responseString.deleteCharAt(responseString.length()-1);
            responseString.deleteCharAt(responseString.length()-1);

            event.getChannel().sendMessage(responseString.toString()).queue();
        }
    }
}
