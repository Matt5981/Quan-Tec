package org.example.utils;

public class Response {
    private final int statusCode;
    private final String statusMessage;
    private final String mimeType;
    private final Byte[] content;

    public Response(int statusCode, String statusMessage, String mimeType, Byte[] content) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.mimeType = mimeType;
        this.content = content;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Byte[] getContent() {
        return content;
    }

    public static String mimeToExtension(String mime){
        switch(mime){
            case "image/png" -> {
                return ".png";
            }
            case "image/jpeg" -> {
                return ".jpg";
            }
            case "image/webp" -> {
                return ".webp";
            }
            case "image/gif" -> {
                return ".gif";
            }
            default -> {
                return ".bin";
            }
        }
    }
}
