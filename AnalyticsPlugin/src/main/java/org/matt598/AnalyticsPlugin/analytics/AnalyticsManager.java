package org.matt598.AnalyticsPlugin.analytics;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsManager implements Serializable {
    public static final long SerialVersionUID = 1L;

    private long processedCommands;
    private long screenedMessages;
    private long removedMessages;
    private long blockedImages;

    // Funnies.
    private long flurogingerPassAppearances;
    private long lastAppearanceOfFlurogingerPass;
    private final Map<Long,Map<Long,Long>> emoteTracking;

    // Trackers.
    private transient String filename;

    private AnalyticsManager(String filename){
        this.processedCommands = 0;
        this.screenedMessages = 0;
        this.removedMessages = 0;
        this.blockedImages = 0;
        this.lastAppearanceOfFlurogingerPass = 0;
        this.flurogingerPassAppearances = 0;
        this.emoteTracking = new HashMap<>();
        this.filename = filename;

        // Write out for first time if this is being called. Don't do this if we can't create the file.
        try {
            if(new File(filename).createNewFile()){
                ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(filename));
                outputStream.writeObject(this);
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public static AnalyticsManager getInstance(String filename){
        // Try to read it in from a file. If we can't, make one and return it.
        try(ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(filename))){
            AnalyticsManager ret = (AnalyticsManager) inputStream.readObject();
            ret.filename = filename;
            return ret;
        } catch (FileNotFoundException e){
            return new AnalyticsManager(filename);
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }
    }

    private void handleMutation(){
        // Write out. It definitely exists by now.
        try {
            File probe = new File(filename);
            if(probe.exists()){
                if(!probe.delete()){
                    throw new IOException("filename exists and couldn't be deleted.");
                }
            }
            if(!probe.createNewFile()){
                throw new IOException("Couldn't replace filename.");
            }
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(filename));
            outputStream.writeObject(this);
            outputStream.flush();
            outputStream.close();
        } catch (FileNotFoundException e){
            // Try to create it and rerun the method. If that throws an exception or returns false, warn and return.
            try {
                if(new File(filename).createNewFile()){
                    handleMutation();
                } else {
                    System.out.println("[AnalyticsManager] WARN unable to create file to write out to. Changes aren't being saved!");
                }
            } catch (IOException ex){
                System.out.println("[AnalyticsManager] WARN unable to write out to file. Changes aren't being saved!");
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public long getProcessedCommands() {
        return processedCommands;
    }

    public void setProcessedCommands(long processedCommands) {
        this.processedCommands = processedCommands;
        handleMutation();
    }

    public long getScreenedMessages() {
        return screenedMessages;
    }

    public void setScreenedMessages(long screenedMessages) {
        this.screenedMessages = screenedMessages;
        handleMutation();
    }

    public long getRemovedMessages() {
        return removedMessages;
    }

    public void setRemovedMessages(long removedMessages) {
        this.removedMessages = removedMessages;
        handleMutation();
    }

    public long getBlockedImages() {
        return blockedImages;
    }

    public void setBlockedImages(long blockedImages) {
        this.blockedImages = blockedImages;
        handleMutation();
    }

    public long getFlurogingerPassAppearances() {
        return flurogingerPassAppearances;
    }

    public long getLastAppearanceOfFlurogingerPass(){
        return lastAppearanceOfFlurogingerPass;
    }

    public void setFlurogingerPassAppearances(long flurogingerPassAppearances) {
        this.flurogingerPassAppearances = flurogingerPassAppearances;
        this.lastAppearanceOfFlurogingerPass = System.currentTimeMillis()/1000;
        handleMutation();
    }

    public void registerEmoteAppearance(long guildID, long emoteID){
        if(emoteTracking.get(guildID) == null){
            emoteTracking.put(guildID, new HashMap<>());
            // If the map for that guild previously didn't exist, then
            // the emote entry couldn't have existed in the non-existent hashmap.
            emoteTracking.get(guildID).put(emoteID, 1L);
        } else {
            if(emoteTracking.get(guildID).get(emoteID) == null){
                emoteTracking.get(guildID).put(emoteID, 1L);
            } else {
                emoteTracking.get(guildID).put(emoteID, emoteTracking.get(guildID).get(emoteID) + 1);
            }
        }
        handleMutation();
    }

    public String formatEmoteAppearancesToJSON(){
        StringBuilder json = new StringBuilder();
        json.append("{\"guilds\":{");
        for(Map.Entry<Long, Map<Long, Long>> entry : emoteTracking.entrySet()){
            json.append("\"").append(entry.getKey()).append("\": {");
            for(Map.Entry<Long, Long> emote : entry.getValue().entrySet()){
                json.append("\"").append(emote.getKey()).append("\": ").append(emote.getValue()).append(",");
            }
            json.deleteCharAt(json.length()-1);
            json.append("},");
        }
        // If the map is empty, append 'null' instead.
        json.deleteCharAt(json.length() - 1);
        if(emoteTracking.isEmpty()){
            json.append("\"null\"");
        } else {
            json.append("}");
        }
        json.append("}");

        return json.toString();
    }
}
