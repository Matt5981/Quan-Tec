package org.matt598.AnalyticsPlugin.analytics;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyticsListener extends ListenerAdapter {
    private final AnalyticsManager analyticsManager;

    public AnalyticsListener(AnalyticsManager analyticsManager){
        this.analyticsManager = analyticsManager;
    }

    private void checkForFlurogingerPass(MessageReceivedEvent event){
        if(event.getMember() != null && (event.getMember().getId().equals("332438399829016577") || event.getMember().getId().equals("300211457805778945")) && event.getMessage().getAttachments().size() != 0){
            // Check attachment hash.
            // preflight checks, we need to be able to write into /tmp, we need our own directory in /tmp for files and we need to be able to create new files there.
            if(new File("/tmp/quantec").exists() && !new File("/tmp/quantec").isDirectory()){
                System.out.println("[AnalyticsListener] ERR /tmp/quantec exists and is a file. Attachment checks will not be run.");
                return;
            }

            if(!new File("/tmp/quantec").exists()){
                if(!new File("/tmp/quantec").mkdir()){
                    System.out.println("[AnalyticsListener] ERR failed to create /tmp/quantec. Attachment checks will not be run.");
                    return;
                }
            }

            // Test file write.
            try {
                if(!new File("/tmp/quantec/probe.tmp").createNewFile()){
                    System.out.println("[AnalyticsListener] ERR failed to create probe file. Attachment checks will not be run.");
                    return;
                } else {
                    new File("/tmp/quantec/probe.tmp").delete();
                }
            } catch (IOException e) {
                System.out.println("[AnalyticsListener] ERR IOException thrown while creating probe file: "+e.getMessage());
                return;
            }

            // Still running? All checks passed. Can continue.

            for(Message.Attachment attachment : event.getMessage().getAttachments()){
                try {
                    File file = new File("/tmp/quantec/fluroginger-pass-chk.tmp");
                    file.createNewFile();
                    attachment.getProxy().downloadToFile(file).thenRun(() -> {
                        try(FileInputStream fileInputStream = new FileInputStream(file)) {
                            // Hash and compare.
                            // I DID NOT WRITE THIS CODE - borrowed from https://howtodoinjava.com/java/java-security/sha-md5-file-checksum-hash/
                            MessageDigest digest = MessageDigest.getInstance("SHA-256");

                            byte[] inBuffer = new byte[1024];
                            int len = 0;

                            while((len = fileInputStream.read(inBuffer)) != -1){
                                digest.update(inBuffer, 0, len);
                            }

                            // Churn hash into Base64, so we can check it against a reference.
                            String digestEncoded = new String(Base64.getEncoder().encode(digest.digest()));
                            if(digestEncoded.equals("7Odpru8mxub6QCTGsP5J5YWz3ajJlMTONd5UF7Jielo=")){
                                analyticsManager.setFlurogingerPassAppearances(analyticsManager.getFlurogingerPassAppearances() + 1);
                            }

                        } catch (IOException | NoSuchAlgorithmException e){
                            // Ignored
                        }
                    });
                } catch (IOException e){
                    // Ignored
                }
            }
        } else if(event.getMember() != null && (event.getMember().getId().equals("332438399829016577") || event.getMember().getId().equals("300211457805778945"))){
            if(event.getMessage().getContentRaw().contains("***REMOVED***")){
                analyticsManager.setFlurogingerPassAppearances(analyticsManager.getFlurogingerPassAppearances() + 1);
            }
        }
    }

    private void screenEmotes(MessageReceivedEvent event){
        // Extract all text from the message's raw content that is enclosed within '<>' brackets. We'll do that with some light (painful) regex.
        List<String> emoteAppearances = new ArrayList<>();
        Pattern emote = Pattern.compile("(?<=<)(.*?)(?=>)");
        Matcher matcher = emote.matcher(event.getMessage().getContentRaw());
        while(matcher.find()){
            emoteAppearances.add(matcher.group());
        }

        // Process each by stripping everything between the two colons. I'd combine these into one regex statement, but
        // lookbehind has to be of fixed length for it to be valid (for whatever reason).
        emoteAppearances.forEach(ele -> emoteAppearances.set(emoteAppearances.indexOf(ele), ele.replaceAll("(:)(.*)(:)", "")));

        // Remove any that have '@' symbols in them (mentions) or '#'s (channel references).
        emoteAppearances.removeIf(ele -> (ele.contains("@") || ele.contains("#")));
        // Remove any that contain the http or https prefixes, since they're embed-blocked images.
        emoteAppearances.removeIf(ele -> (ele.contains("http://") || ele.contains("https://")));
        // Remove any prepended 'a's, since animated emotes have those.
        emoteAppearances.forEach(ele -> {
            if(ele.startsWith("a")){
                emoteAppearances.set(emoteAppearances.indexOf(ele), ele.substring(1));
            }
        });

        for(String emoteStr : emoteAppearances){
            Emoji emoji = event.getJDA().getEmojiById(emoteStr);
            if(emoji != null){
                analyticsManager.registerEmoteAppearance(event.getGuild().getIdLong(), Long.parseLong(emoteStr));
            }
        }
    }
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event){
        // Screen message for anything we want to keep track of with the analytics manager. This is here to unclog the command listener.
        checkForFlurogingerPass(event);

        // Screen message for emotes.
        screenEmotes(event);

        // Check for a text message beginning with 'q!' that isn't a webhook or bot message, meaning it's one of our commands.
        if(event.getMessage().getContentRaw().startsWith("q!") && !event.getAuthor().isBot() && !event.getMessage().isWebhookMessage()){
            analyticsManager.setProcessedCommands(analyticsManager.getProcessedCommands() + 1);
        }
    }
}
