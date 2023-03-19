package org.example.utils;

import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public abstract class WebGet {
    public static Response get(@NotNull String url){

        // I sure do love sockets!
        boolean isHttps = url.startsWith("https://");
        String urlNoProto = url.replace(isHttps ? "https://" : "http://", "");
        String domain = urlNoProto.substring(0,urlNoProto.indexOf('/'));
        String path = urlNoProto.substring(urlNoProto.indexOf('/'));

        String req = String.format(
                "GET %s HTTP/1.1\r\n" +
                        "Host: %s\r\n" +
                        "Accept: */*\r\n" +
                        "Connection: keep-alive\r\n" +
                        "\r\n"
        , path, domain);

        try(Socket socket = isHttps ? SSLSocketFactory.getDefault().createSocket(domain, 443) : new Socket(domain, 80)) {
            HTTPInputStreamReader reader = new HTTPInputStreamReader(socket.getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());

            writer.write(req);
            writer.flush();

            List<String> headers = new ArrayList<>();
            String temp = reader.readLine();
            while(!temp.equals("")){
                headers.add(temp.toLowerCase());
                temp = reader.readLine();
            }

            List<Byte> body = new ArrayList<>();
            if(headers.contains("transfer-encoding: chunked")){
                // Pain. Incomparable pain.
                while(true){
                    int nextBlockLen = Integer.parseInt(reader.readLine(), 16);
                    if(nextBlockLen == 0){
                        reader.readLine();
                        break;
                    } else {
                        byte[] nextBlock = new byte[nextBlockLen];
                        reader.read(nextBlock);
                        reader.read(new byte[2]); // Consume CRLF

                        for(byte c : nextBlock){
                            body.add(c);
                        }
                    }
                }
            } else {
                int contentLen = 0;
                for(String header : headers){
                    if(header.startsWith("content-length: ")){
                        contentLen = Integer.parseInt(header.substring(16));
                    }
                }

                if(contentLen == 0){
                    body = null;
                } else {
                    byte[] bdy = new byte[contentLen];
                    int readBytes = 0;
                    for(int i = 0; i < bdy.length; i++){
                        int read = reader.read();
                        if(read == -1){
                            System.out.println("[WebGet] ERR EOF (-1) returned prematurely!");
                        } else {
                            bdy[readBytes++] = (byte) read;
                        }
                    }


                    if(readBytes != contentLen){
                        System.out.println("[WebGet] ERR content-length specified "+contentLen+" bytes, but only read "+readBytes+". Image is likely corrupt!");
                    }

                    // Add to list. TODO Collections.addAll()?
                    for (byte c : bdy) {
                        body.add(c);
                    }
                }
            }

            String mime = "";
            boolean found = false;
            for(String header : headers){
                if(header.startsWith("content-type: ")){
                    mime = header.substring(14);
                    found = true;
                }
            }

            if(!found){
                System.out.println("Did not find content-type header! MIME type will be set as blank.");
            }

            return new Response(Integer.parseInt(headers.get(0).split(" ")[1]), null, mime, body == null ? null : body.toArray(new Byte[0]));
        } catch (IOException | NullPointerException e){
            e.printStackTrace();
            return null;
        }
    }
}
