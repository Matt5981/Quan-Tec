package org.matt598.CensorPlugin.censor.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.CensorPlugin.censor.CensorManager;

public class getBannedImages implements Command {
    private final CensorManager censorManager;

    public getBannedImages(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){

        if(censorManager.getBannedImages(event.getGuild().getId()).isEmpty()){
            event.getChannel().sendMessage("**No images are banned on this server!** Use `q!banImage` to ban inappropriate images.").queue();
            return;
        }

        StringBuilder out = new StringBuilder();
        out.append("**The following images are banned in this server (nicknames only):** ");
        for(CensorManager.Image image : censorManager.getBannedImages(event.getGuild().getId())){
            out.append(String.format("`%s`, ", image.getNickname()));
        }
        out.deleteCharAt(out.length()-1);
        out.deleteCharAt(out.length()-1);

        event.getChannel().sendMessage(out.toString()).queue();
    }
}
