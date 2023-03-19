package org.example.webserver.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Request {

    private final String HTTPVers,method,path,content;
    private final Map<String, String> headers;

    private Request(String HTTPVers, String method, String path, String content, Map<String, String> headers){
        this.HTTPVers = HTTPVers;
        this.method = method;
        this.path = path;
        this.content = content;
        this.headers = headers;
    }

    public static Request readRequest(BufferedReader input) throws IOException {
        // Read lines until we read a blank one.
        List<String> lines = new ArrayList<>();
        try {
            String newLine = input.readLine();
            while(!newLine.equals("")){
                lines.add(newLine.toLowerCase());
                newLine = input.readLine();
            }
        } catch (NullPointerException e){
            return null;
        }

        // Check for content-length or chunking.
        int contentLength = 0;
        for(String line : lines){
            if(line.startsWith("content-length: ")){
                contentLength = Integer.parseInt(line.substring(16));
            } else if(line.equals("transfer-encoding: chunked")){
                contentLength = -1;
            }
        }

        StringBuilder bodyBuilder = new StringBuilder();
        String body;

        if(contentLength > 0) {
            for (int i = 0; i < contentLength; i++) {
                bodyBuilder.append((char) input.read());
            }
        } else if(contentLength == -1){
            while(true){
                StringBuilder nums = new StringBuilder();
                char temp = (char)input.read();
                char lastTemp = '0';
                while(true){
                    if(temp == '\n' && lastTemp == '\r'){
                        break;
                    } else {
                        if(!(temp == '\r' || temp == '\n')) {
                            nums.append(temp);
                        }
                        lastTemp = temp;
                        temp = (char)input.read();
                    }
                }
                if(Integer.parseInt(nums.toString(), 16) == 0){
                    // Consume final CRLF and break
                    input.read();
                    input.read();
                    break;
                } else {
                    for (int i = 0; i < Integer.parseInt(nums.toString(), 16); i++) {
                        bodyBuilder.append((char) input.read());
                    }
                    // Consume CRLF and repeat
                    input.read();
                    input.read();
                }
            }
        }

        if(contentLength != 0){
            body = bodyBuilder.toString();
        } else {
            body = null;
        }

        // Get first line and remove to isolate headers.
        String firstLine = lines.get(0);
        lines.remove(firstLine);
        Map<String, String> headers = new HashMap<>();
        for(String line : lines){
            headers.put(line.split(": ")[0], line.split(": ")[1]);
        }

        return new Request(firstLine.split(" ")[2].toUpperCase(), firstLine.split(" ")[0].toUpperCase(), firstLine.split(" ")[1], body, headers);
    }

    public String getHTTPVers() {
        return HTTPVers;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
