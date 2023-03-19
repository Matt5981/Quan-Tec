package org.matt598.CensorPlugin.censor.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.CensorPlugin.censor.CensorManager;

public class getImageTolerance implements Command {
    private final CensorManager censorManager;

    public getImageTolerance(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){
        if(censorManager.getTolerance(event.getGuild().getId()) == null){
            event.getChannel().sendMessage("**Error!** This guild has no set tolerance percentage. Ban an image or set it manually to use this command.").queue();
        } else {
            event.getChannel().sendMessage("Images that are at least "+censorManager.getTolerance(event.getGuild().getId())+"% similar to banned images in this server will be automatically removed.").queue();
        }
    }
}
