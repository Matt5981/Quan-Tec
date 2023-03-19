package org.matt598.CensorPlugin.censor.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.example.utils.PermissionChecker;
import org.matt598.CensorPlugin.censor.CensorManager;

public class setImageTolerance implements Command {
    private final CensorManager censorManager;

    public setImageTolerance(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){
        if(!PermissionChecker.userHasPermission(event, Permission.MANAGE_SERVER)){
            event.getChannel().sendMessage("**Error!** This command requires the `Manage Server` permission.").queue();
        } else if(event.getMessage().getContentRaw().split(" ").length < 2){
            event.getChannel().sendMessage("**Error!** You need to provide a number to set the tolerance level.").queue();
        } else {
            try {
                censorManager.setTolerance(event.getGuild().getId(), Integer.parseInt(event.getMessage().getContentRaw().split(" ")[1]));
                event.getChannel().sendMessage("Successfully set image similarity tolerance. Images that are at least "+censorManager.getTolerance(event.getGuild().getId())+"% similar to banned images will be automatically removed.").queue();
            } catch (NumberFormatException e){
                event.getChannel().sendMessage("**Error!** Provided number was invalid, enter a percentage between 0-100.").queue();
            }
        }
    }
}
