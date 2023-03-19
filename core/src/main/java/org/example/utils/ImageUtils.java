package org.example.utils;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** <h1>ImageUtils</h1>
 * A range of methods for assisting with the manipulation of images.
 */
public class ImageUtils {

    /** <h2>Compare Images</h2>
     * This method takes two images and returns a boolean as to whether they're at least <code>similarityThreshold</code> percent similar.
     * If two images of different sizes are provided, the larger image will be resized to fit the smaller one.
     * @param imageFileOne The first image to compare.
     * @param imageFileTwo The second image to compare.
     * @param similarityThreshold A percentage value (0-100), to which the return value will be evaluated against, with <code>true</code> being returned if the images are at least {this value} percent similar. If <code>0</code>, this method will always return <code>true</code>. If <code>100</code>, this method will only return <code>true</code> if the images are identical.
     * @return <code>true</code> if the images are at least <code>similarityThreshold</code> percent similar, else false.
     */
    public static boolean compareImages(byte[] imageFileOne, byte[] imageFileTwo, int similarityThreshold){
        BufferedImage imgOne;
        BufferedImage imgTwo;
        try {
            imgOne = ImageIO.read(new ByteArrayInputStream(imageFileOne));
            imgTwo = ImageIO.read(new ByteArrayInputStream(imageFileTwo));
        } catch(IOException e){
            e.printStackTrace();
            return false;
        }
        // Figure out the larger of the two for each axis. TODO collate these five lines.
        BufferedImage largerX, largerY, smallerX, smallerY;
        largerX = imgOne.getWidth() > imgTwo.getWidth() ? imgOne : imgTwo;
        smallerX = largerX == imgOne ? imgTwo : imgOne;
        largerY = imgOne.getHeight() > imgTwo.getHeight() ? imgOne : imgTwo;
        smallerY = largerY == imgOne ? imgTwo : imgOne;

        // Perform compare.
        int redDiffTotal = 0, greenDiffTotal = 0, blueDiffTotal = 0, pix = 0;
        Color c1, c2;

        for(int x = 0; x < largerX.getWidth(); x++){
            int lesserXCoord = (int) Math.floor(x/(double)largerX.getWidth() * smallerX.getWidth());
            for(int y = 0; y < largerY.getHeight(); y++){
                int lesserYCoord = (int) Math.floor(y/(double)largerY.getHeight() * smallerY.getHeight());
                // This is a bit CPU-heavy since the GC has to remove potentially millions of color objects
                // from memory, but this doesn't run in the main thread so it shouldn't cause too much upset.
                int imgOneX,imgOneY,imgTwoX,imgTwoY;
                if(smallerX == imgOne){
                    imgOneX = lesserXCoord;
                    imgTwoX = x;
                } else {
                    imgOneX = x;
                    imgTwoX = lesserXCoord;
                }
                if(smallerY == imgOne){
                    imgOneY = lesserYCoord;
                    imgTwoY = y;
                } else {
                    imgOneY = y;
                    imgTwoY = lesserYCoord;
                }

                c1 = new Color(imgOne.getRGB(imgOneX,imgOneY));
                c2 = new Color(imgTwo.getRGB(imgTwoX,imgTwoY));
                // Add difference in RGB values to totals and increment pix.
                redDiffTotal += Math.abs(c1.getRed()-c2.getRed());
                greenDiffTotal += Math.abs(c1.getGreen()-c2.getGreen());
                blueDiffTotal += Math.abs(c1.getBlue()-c2.getBlue());
                pix++;
            }
        }
        // Average totals out to get the average color difference.
        int avgColorDiff = ((redDiffTotal/pix)+(greenDiffTotal/pix)+(blueDiffTotal/pix))/3;
        // This results in a value between 0 and 255. We convert our threshold here to what the difference should be
        // under, then return the result of comparing the two.
        int similarity = (int) (100 - ((avgColorDiff / 255.0) * 100));

        // Finally, we return.
        System.out.println("[DEBUG] Average color diff was "+avgColorDiff+", similarity "+similarity+"/"+similarityThreshold);
        return (similarity >= similarityThreshold);

    }

    /** <h2>Aggregate Images</h2>
     * Literally 'takes the average' of a set of images by averaging the color values across all of their pixels.
     * @param images A list of byte arrays containing image files in any format supported by <code>ImageIO.read()</code>
     * @return A byte[] corresponding to a png of the aggregated images. Width and Height will be equal to whatever the widest image's width is and whatever the highest image's height was respectively.
     */
    public static byte[] aggregateImages(List<byte[]> images){
        if(images.size() == 0){
            return null;
        }

        try {
            // Make many buffered images
            BufferedImage[] toAggregate = new BufferedImage[images.size()];
            int[] toAggregateXCoords = new int[images.size()];
            int[] toAggregateYCoords = new int[images.size()];
            for (int i = 0; i < toAggregate.length; i++) {
                toAggregate[i] = ImageIO.read(new ByteArrayInputStream(images.get(i)));
            }

            // Work out which of those images has the highest height, and which has the longest length.
            BufferedImage largestY = toAggregate[0];
            for(BufferedImage image : toAggregate){
                if(image.getHeight() > largestY.getHeight()){
                    largestY = image;
                }
            }
            BufferedImage largestX = toAggregate[0];
            for(BufferedImage image : toAggregate){
                if(image.getWidth() > largestX.getWidth()){
                    largestX = image;
                }
            }

            BufferedImage out = new BufferedImage(largestX.getWidth(), largestY.getHeight(), BufferedImage.TYPE_INT_RGB);
            for(int x = 0; x < largestX.getWidth(); x++){
                for (int i = 0; i < toAggregate.length; i++) {
                    toAggregateXCoords[i] = (int)(((double)x/largestX.getWidth()) * toAggregate[i].getWidth());
                }
                for(int y = 0; y < largestY.getHeight(); y++){
                    for(int i = 0; i < toAggregate.length; i++){
                        toAggregateYCoords[i] = (int)(((double)y/largestY.getHeight()) * toAggregate[i].getHeight());
                    }

                    // Grab ALL of those colors from all of the different images, take the average and set the pixel of out to whatever the result is.
                    Color[] cols = new Color[toAggregate.length];
                    for (int i = 0; i < toAggregate.length; i++) {
                        cols[i] = new Color(toAggregate[i].getRGB(toAggregateXCoords[i], toAggregateYCoords[i]));
                    }

                    int len = 0;
                    long red = 0,grn = 0,blu = 0;
                    for(Color color : cols){
                        len++;
                        red += color.getRed();
                        grn += color.getGreen();
                        blu += color.getBlue();
                    }

                    red /= len;
                    grn /= len;
                    blu /= len;

                    Color aggregate = new Color(red / 255.0F, grn / 255.0F, blu / 255.0F);

                    out.setRGB(x, y, aggregate.getRGB());
                }
            }


            ByteArrayOutputStream file = new ByteArrayOutputStream();
            ImageIO.write(out, "png", file);

            return file.toByteArray();

        } catch (IOException e){
            e.printStackTrace();
            return null;
        }

    }
}