package org.matt598.CensorPlugin.censor.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.CensorPlugin.censor.CensorManager;

public class setImageName implements Command {
    private final CensorManager censorManager;

    public setImageName(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){
        if(event.getMessage().getContentRaw().split(" ").length < 3){
            event.getChannel().sendMessage("**Error!** This command requires 2 arguments.").queue();
        } else {
            if(censorManager.setImageNickname(event.getGuild().getId(), event.getMessage().getContentRaw().split(" ")[1], event.getMessage().getContentRaw().split(" ")[2])){
                event.getChannel().sendMessage("Image renamed successfully." + (event.getMessage().getContentRaw().split(" ").length > 3 ? " **Warning:** More then 3 arguments detected. The new name of the image will be whichever word directly followed the old image name." : "")).queue();
            } else {
                event.getChannel().sendMessage("Image not found. No changes have been made.").queue();
            }
        }
    }
}
