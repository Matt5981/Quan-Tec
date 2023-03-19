package org.example.command;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface Command {
    default String getName(){
        return this.getClass().getSimpleName();
    }

    default String getHelp(){
        return "No help text provided for this command.";
    }

    void run(MessageReceivedEvent event);
}
