package org.matt598.CensorPlugin.censor.commands;


import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.CensorPlugin.censor.CensorManager;

public class getBannedWords implements Command {
    private final CensorManager censorManager;

    public getBannedWords(CensorManager censorManager){
        this.censorManager = censorManager;
    }

    @Override
    public void run(MessageReceivedEvent event){

        if(censorManager.getBannedWords(event.getGuild().getId()) == null || censorManager.getBannedWords(event.getGuild().getId()).isEmpty()){
            event.getChannel().sendMessage("**No words are banned on this server!** Use `q!banWord` to ban inappropriate words.").queue();
            return;
        }

        StringBuilder out = new StringBuilder();
        out.append("**The following words are banned in this server:** ");
        for(String word : censorManager.getBannedWords(event.getGuild().getId())){
            out.append(String.format("`%s`, ", word));
        }
        out.deleteCharAt(out.length()-1);
        out.deleteCharAt(out.length()-1);

        event.getChannel().sendMessage(out.toString()).queue();
    }
}
