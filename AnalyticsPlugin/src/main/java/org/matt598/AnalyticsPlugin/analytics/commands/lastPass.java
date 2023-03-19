package org.matt598.AnalyticsPlugin.analytics.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.example.command.Command;
import org.matt598.AnalyticsPlugin.analytics.AnalyticsManager;

public class lastPass implements Command {
    private final AnalyticsManager analyticsManager;

    public lastPass(AnalyticsManager analyticsManager){
        this.analyticsManager = analyticsManager;
    }

    @Override
    public void run(MessageReceivedEvent event) {
        long interval = (System.currentTimeMillis()/1000) - analyticsManager.getLastAppearanceOfFlurogingerPass();

        event.getChannel().sendMessage(String.format("It has been %d days, %2d hours, %2d minutes and %2d seconds since I last saw Mr. Vince use his certificate. Since I started keeping track, he has used it %d time(s).",
                interval / 86400,
                (interval % 86400) / 3600,
                ((interval % 86400) % 3600) / 60,
                (((interval % 86400) % 3600) % 60),
                analyticsManager.getFlurogingerPassAppearances()
                )).queue();
    }
}
