package org.matt598.CensorPlugin.censor.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.example.utils.PermissionChecker;
import org.matt598.CensorPlugin.censor.CensorManager;

import java.util.List;

public class banImage implements Command {
    private final CensorManager censorManager;

    public banImage(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){
        if(!PermissionChecker.userHasPermission(event, Permission.MANAGE_SERVER)) {
            event.getChannel().sendMessage("**Error!** This command requires the `Manage Server` permission.").queue();
        } else {
            List<CensorManager.Image> toBan = CensorManager.getAllImagesFromMessage(event.getMessage());
            if(toBan.size() == 0){
                event.getChannel().sendMessage("**Error!** You need to provide a link to/upload at least 1 image.").queue();
            } else {
                for(CensorManager.Image image : toBan){
                    censorManager.addImage(event.getGuild().getId(), image);
                }
                event.getChannel().sendMessage("Banned "+toBan.size()+" images successfully. Your original message has been deleted to hide embeds.").queue();
                event.getMessage().delete().queue();
            }
        }
    }
}
