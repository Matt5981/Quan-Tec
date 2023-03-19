package org.matt598.CensorPlugin.censor;

import net.dv8tion.jda.api.entities.Message;
import org.example.plugins.MessageBusEvent;
import org.example.utils.ImageUtils;
import org.example.utils.Response;
import org.example.utils.WebGet;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO Only the map here needs to be serialized, the rest is excessive and prevents updating without resetting the censor data.
public class CensorManager implements Serializable {
    private final Map<String, GuildSet> censorData;
    private transient String filename;
    private transient Consumer<MessageBusEvent> sender;
    private static final String[] ImageExtensions = {
            ".gif",
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    };

    private static class GuildSet implements Serializable {
        private final List<String> bannedWordList;
        private final List<Image> bannedImages;
        private int bannedImageTolerancePcnt;

        public GuildSet() {
            this.bannedWordList = new ArrayList<>();
            this.bannedImages = new LinkedList<>();
            this.bannedImageTolerancePcnt = 95;
        }

        public List<String> getBannedWordList() {
            return bannedWordList;
        }

        public List<Image> getBannedImages() {
            return bannedImages;
        }

        public int getBannedImageTolerancePcnt() {
            return bannedImageTolerancePcnt;
        }

        public void setBannedImageTolerancePcnt(int bannedImageTolerancePcnt) {
            this.bannedImageTolerancePcnt = bannedImageTolerancePcnt;
        }
    }

    public static class Image implements Serializable {
        private String nickname;
        private final List<byte[]> images;
        private boolean isAdaptive;

        public Image(String nickname, List<byte[]> images) {
            this.nickname = nickname;
            this.images = images;
            this.isAdaptive = true;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public boolean isAdaptive() {
            return isAdaptive;
        }

        public void setAdaptive(boolean adaptive) {
            isAdaptive = adaptive;
        }

        public List<byte[]> getImages() {
            return images;
        }
    }

    private CensorManager(String filename, Consumer<MessageBusEvent> sender){
        this.filename = filename;
        this.censorData = new HashMap<>();
        this.sender = sender;

        // Perform initial write-out
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

    public static CensorManager getInstance(String filename, Consumer<MessageBusEvent> sender) throws InvalidClassException {
        // Try to read it in from a file. If we can't, make one and return it.
        try(ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(filename))){
            CensorManager ret = (CensorManager) inputStream.readObject();
            ret.filename = filename;
            ret.sender = sender;
            return ret;
        } catch (FileNotFoundException e){
            return new CensorManager(filename, sender);
        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }
    }

    public static List<Image> getAllImagesFromMessage(Message message){
        // Discord uploads images in two ways: Either they're stored as attachments to the message (i.e. when a user uploads an image file from their computer),
        // or a user links to an image, which if Discord can GET it successfully embeds into what looks like the same image. We need to check for both.

        // First pass: Extract links. Since there's 2 variations of each extension, and 5 extensions supported by Discord, we'll need to do 10 iterations here.
        List<String> imageLinks = new ArrayList<>();
        String messageContent = message.getContentRaw();

        for(String proto : new String[]{"https://", "http://"}){
            for(String extn : ImageExtensions){
                Pattern pattern = Pattern.compile(String.format("(%s).*?(%s)", proto, extn));
                Matcher matcher = pattern.matcher(messageContent);
                while(matcher.find()){
                    imageLinks.add(matcher.group());
                }
            }
        }

        // Next is attachments. If these are present, we need to grab their Discord CDN link and check if it ends with
        // an image extension.
        for(Message.Attachment attachment : message.getAttachments()){
            String url = attachment.getUrl();
            for(String extn : ImageExtensions){
                if(url.toLowerCase().endsWith(extn)){
                    imageLinks.add(url);
                }
            }
        }

        // Now we need to download all of those images.
        List<Response> responses = new ArrayList<>();
        for(String url : imageLinks){
            Response candidate = WebGet.get(url);
            if(candidate == null || candidate.getContent() == null || candidate.getStatusCode() != 200){
                System.out.println("[CensorManager] ERR Attempt to get image at link "+url+" returned a null response." + ((candidate == null) ? "" : " Response was received, albeit with code "+candidate.getStatusCode()+" "+candidate.getStatusMessage()+"."));
            } else {
                responses.add(candidate);
            }
        }

        // Now we have a ton of image files stored in binary. We now need to give them names, which we'll do lazily.
        List<Image> ret = new ArrayList<>();
        int curr_img = 1;
        for(Response response : responses){
            // Stream Byte to byte, because Java.
            byte[] img = new byte[response.getContent().length];
            for (int i = 0; i < img.length; i++) {
                img[i] = response.getContent()[i];
            }

            ret.add(new Image(message.getGuild().getId()+"_"+(curr_img++), new LinkedList<>(Collections.singletonList(img))));
        }

        return ret;
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
                    System.out.println("[CensorManager] WARN unable to create file to write out to. Changes aren't being saved!");
                }
            } catch (IOException ex){
                System.out.println("[CensorManager] WARN unable to write out to file. Changes aren't being saved!");
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    // Word censoring methods

    public void addWord(String guild, String word){
        // TODO replace with addImage's code
        GuildSet subj = censorData.get(guild);
        if(subj != null && !subj.getBannedWordList().contains(word)){
            subj.getBannedWordList().add(word);
            censorData.put(guild, subj); // TODO redundant, expedite above TODO.
            handleMutation();
        } else if(subj == null){
            this.censorData.put(guild, new GuildSet());
            addWord(guild, word);
        }
    }

    public void removeWord(String guild, String word){
        GuildSet subj = censorData.get(guild);
        if(subj != null){
            subj.getBannedWordList().remove(word);
            censorData.put(guild, subj);
            handleMutation();
        }
    }

    public List<String> getBannedWords(String guild){
        GuildSet subj = censorData.get(guild);
        if(subj != null){
            return new ArrayList<>(subj.getBannedWordList());
        } else {
            return new ArrayList<>();
        }
    }

    public String getBannedWordsJSON(String guild){
        GuildSet subj = censorData.get(guild);
        if(subj != null){
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{\"bannedWords\":[");
            for(String word : subj.getBannedWordList()){
                stringBuilder.append(String.format("\"%s\",", word));
            }

            if(!subj.getBannedWordList().isEmpty()){
                stringBuilder.deleteCharAt(stringBuilder.length()-1);
            }
            stringBuilder.append("]}");

            return stringBuilder.toString();
        } else {
            return "{\"bannedWords\":[]}";
        }
    }

    public boolean censorWord(String guild, Message message){
        GuildSet subj = censorData.get(guild);
        if(subj != null){
            for(String banned : subj.getBannedWordList()){
                if(message.getContentRaw().contains(banned)){
                    message.delete().queue();
                    // TODO make this message customizable
                    message.getChannel().sendMessage(String.format("**That wasn't very friendly %s!** You sent a banned word.", message.getAuthor().getAsMention())).queue();
                    sender.accept(new MessageBusEvent("censorManager", "censorWdRemove", null));
                    return true;
                }
            }
        }
        return false;
    }

    // Image censoring methods
    public void addImage(String guild, Image image){
        if(censorData.get(guild) != null){
            // TODO duplicate guard
            censorData.get(guild).getBannedImages().add(image);
            handleMutation();
        } else {
            censorData.put(guild, new GuildSet());
            addImage(guild, image);
        }
        handleMutation();
    }

    public void removeImage(String guild, String imageNickname){
        if(censorData.get(guild) != null){
            if(censorData.get(guild).getBannedImages().removeIf(ele -> ele.getNickname().equals(imageNickname))){
                handleMutation();
            }
        }
    }

    public boolean setImageNickname(String guild, String oldName, String newName){
        if(censorData.get(guild) != null){
            for(Image image : censorData.get(guild).getBannedImages()){
                if(image.getNickname().equals(oldName)){
                    image.setNickname(newName);
                    handleMutation();
                    return true;
                }
            }
        }
        return false;
    }

    public List<Image> getBannedImages(String guild){
        if(censorData.get(guild) != null){
            return censorData.get(guild).getBannedImages();
        }
        return null;
    }

    public void setTolerance(String guild, int tolerance){
        if(!(0 <= tolerance && tolerance <= 100)){
            throw new IllegalArgumentException("Expected 0 <= tolerance <= 100, got "+tolerance+".");
        }

        if(censorData.get(guild) != null){
            censorData.get(guild).setBannedImageTolerancePcnt(tolerance);
        } else {
            censorData.put(guild, new GuildSet());
            setTolerance(guild, tolerance);
        }
    }

    public Integer getTolerance(String guild){
        if(censorData.get(guild) != null){
            return censorData.get(guild).getBannedImageTolerancePcnt();
        } else {
            return null;
        }
    }

    /** <h2>Compile Filter Aggregate</h2>
     * Compiles the aggregate image used to screen images with the <code>censorImage()</code> method.
     * @param guild The snowflake of the guild where the image was banned.
     * @param imageNick The nickname of the image. If there are multiple images with the same nickname in the provided guild, the one added first will be returned.
     * @return A <code>byte[]</code> with the aforementioned aggregate image in <code>png</code> format, or <code>null</code> if the provided guild/image doesn't exist.
     */
    public byte[] compileFilterAggregate(String guild, String imageNick){
        if(censorData.get(guild) != null){
            for(Image image : censorData.get(guild).getBannedImages()){
                if(image.getNickname().equals(imageNick)){
                    return ImageUtils.aggregateImages(image.getImages());
                }
            }
        }
        return null;
    }

    public String getBannedImagesJSON(String guild){
        if(censorData.get(guild) != null){
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{\"bannedImages\":[");
            for(Image image : censorData.get(guild).getBannedImages()){
                stringBuilder.append(String.format("{\"name\":\"%s\",\"adaptive\":\"%s\",\"size\":%d},",
                        image.getNickname(),
                        image.isAdaptive(),
                        image.getImages().size()));
            }
            if(censorData.get(guild).getBannedImages().size() != 0){
                stringBuilder.deleteCharAt(stringBuilder.length()-1);
            }
            stringBuilder.append("]}");

            return stringBuilder.toString();
        }

        return "{\"bannedImages\":[]}";
    }

    public boolean censorImage(String guild, Message message, boolean checkOnly){
        if(censorData.get(guild) != null){
            List<Image> banned = censorData.get(guild).getBannedImages();
            int tolerance = censorData.get(guild).getBannedImageTolerancePcnt();

            for(Image bannedImage : banned){
                for(Image image : getAllImagesFromMessage(message)){
                    if(ImageUtils.compareImages(image.getImages().get(0), ImageUtils.aggregateImages(bannedImage.getImages()), tolerance)){
                        if(bannedImage.isAdaptive()){
                            bannedImage.getImages().add(image.getImages().get(0));
                            handleMutation();
                        }
                        if(!checkOnly){
                            message.delete().queue();
                        }
                        message.getChannel().sendMessage("**Uh oh! That wasn't very friendly "+message.getAuthor().getAsMention()+"!** An image you posted is banned in this server.").queue();
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
