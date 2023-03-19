package org.matt598.AnalyticsPlugin.analytics.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.AnalyticsPlugin.analytics.AnalyticsManager;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class analytics implements Command {
    private final AnalyticsManager analyticsManager;
    private final String vers;

    public analytics(AnalyticsManager analyticsManager, String vers){
        this.analyticsManager = analyticsManager;
        this.vers = vers;
    }

    @Override
    public void run(MessageReceivedEvent event){
        event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("QuanTec "+vers)
                .setColor(new Color(0xff, 0x8c, 0x00))
                        .addField("// Analytics", String.format("""
                                Commands Processed: `%d`
                                Messages Screened:  `%d`
                                Messages Removed:   `%d`
                                Images Removed:     `%d`
                                """, analyticsManager.getProcessedCommands(), analyticsManager.getScreenedMessages(), analyticsManager.getRemovedMessages(), analyticsManager.getBlockedImages()), false)
                        .addBlankField(false)
                        .addField("// Fluroginger Certificate Sightings", String.format("""
                                Sightings: `%d`
                                Last Sighting was on %s.
                                """, analyticsManager.getFlurogingerPassAppearances(), new SimpleDateFormat("EEE dd MMM yyyy").format(new Date()) + " at " + new SimpleDateFormat("HH:mm:ss").format(new Date())), false)
                .build()
        ).queue();
    }
}
