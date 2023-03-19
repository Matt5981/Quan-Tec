package org.example.webserver.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class Response {
    // Shorthand. TODO reorder the generic requests in order of code (i.e., 200 at first)
    public static void writeResponse(String response, PrintWriter writer){
        writer.print(response);
        writer.flush();
    }

    public static void writeResponse(String response, OutputStream outputStream){
        try {
            outputStream.write(response.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e){
            // FIXME ignored, should be logged.
        }

    }

    public static void writeFile(byte[] file, String mimeType, OutputStream outputStream){
        try {
            outputStream.write(("HTTP/1.1 200 OK\r\n" +
                    "Content-Length: "+file.length+"\r\n" +
                    "Content-Type: "+mimeType+"\r\n" +
                    "Content-Disposition: attachment; filename=\"download.png\"\r\n" +
                    "\r\n").getBytes(StandardCharsets.US_ASCII));

            outputStream.write(file);
            outputStream.flush();
        } catch (IOException e){
            throw new RuntimeException("IOException thrown by outputStream: "+e.getMessage());
        }

    }
    public static String badRequest(String reason){
        String content = "{\"error\":\"" + reason + "\"}";
        return "HTTP/1.1 400 Bad Request\r\n" +
                "Content-Length: "+content.length()+"\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                content;
    }

    public static String notFound(String reason){
        String content = "{\"error\":\"" + reason + "\"}";
        return "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: "+content.length()+"\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                content;
    }

    public static String methodNotAllowed(String reason){
        String content = "{\"error\":\"" + reason + "\"}";
        return "HTTP/1.1 405 Method Not Allowed\r\n" +
                "Content-Length: "+content.length()+"\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                content;
    }

    public static String okJSON(String json){
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + json.length() + "\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" +
                json;
    }

    public static String okNoContent(){
        return "HTTP/1.1 204 No Content\r\n\r\n";
    }
}
