package org.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.example.command.Command;
import org.example.command.commands.*;
import org.example.utils.Halter;

import java.util.ArrayList;
import java.util.List;

// TODO slash commands instead of old-fashioned prefixed ones.
// TODO map instead of list for storing commands.
public class CommandListener extends ListenerAdapter {
    private final List<Command> commandList;
    private final String vers;
    private final Halter halter;

    public CommandListener(String vers, Halter halter){
        // Make command list.
        this.commandList = new ArrayList<>();
        this.vers = vers;
        this.halter = halter;

        commandList.add(new vers());
    }

    public void addCommand(Command command){
        // TODO a better way of duplicate checking that isn't O(n^2) when adding these initially. Use a hashmap.
        // Check that a command with the same name doesn't already exist.
        commandList.forEach(cmd -> {
            if(cmd.getName().equals(command.getName())){
                throw new IllegalArgumentException("Conflicting command name: command \""+command.getName()+"\" already exists.");
            }
        });

        commandList.add(command);
    }

    public void removeCommand(Command command){
        // This one requires no duplicate checking, just forwarding the parameter to Collections.remove().
        this.commandList.remove(command);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        // Check if sender is a bot or webhook message, on which we can ignore it.
        if(event.getAuthor().isBot() || event.getMessage().isWebhookMessage()){
            return;
        }
        // Check if it starts with our command prefix.
        if(!event.getMessage().getContentRaw().startsWith("q!")){
            return;
        }
        // Check if we're currently reloading plugins, on which we need to halt.
        if(halter.isLocked()){
            try {
                synchronized (halter) {
                    halter.wait();
                }
            } catch (InterruptedException e){
                // Ignored
            }
        }

        // Check if it's the help command, which is special and has to be processed here.
        if(event.getMessage().getContentRaw().equals("q!help")){

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("QuanTec "+vers+" Help Page");
            for(Command command : commandList){
                embedBuilder.addField(command.getName(), command.getHelp(), false);
            }

            event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(embedBuilder.build()).queue());
            return;
        }
        // Conditions satisfied! Check all of our commands to see if the first line is a known command, else send an error.
        for(Command cmd : commandList){
            if(cmd.getName().equals(event.getMessage().getContentRaw().substring(2).split(" ")[0])){
                cmd.run(event);
                return;
            }
        }
        // Not found, send error.
        event.getChannel().sendMessage("Unknown command. Type 'q!help' for a list of commands.").queue();
    }
}
