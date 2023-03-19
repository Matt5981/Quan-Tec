package org.example.command.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;

public class vers implements Command {
    @Override
    public void run(MessageReceivedEvent event){
        event.getChannel().sendMessage("QuanTec version 0.1 indev. Programmed by Matt598, 2023.").queue();
    }
}
