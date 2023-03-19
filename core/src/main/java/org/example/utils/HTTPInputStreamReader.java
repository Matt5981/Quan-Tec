package org.example.utils;

import java.io.IOException;
import java.io.InputStream;

public class HTTPInputStreamReader {
    private final InputStream inputStream;

    public HTTPInputStreamReader(InputStream inputStream){
        this.inputStream = inputStream;
    }

    /** <h2>readLine</h2>
     * Reads from the input stream until a CRLF (\r\n) character is encountered. This will block until either a full string is
     * read, EOF is read from the input stream, or the input stream throws an IOException.
     * @return The text read from the stream prior to the CRLF. No line separators will be returned at the end of this string.
     */
    public String readLine() throws IOException {
        StringBuilder out = new StringBuilder();
        char lastChar = 0, curChar = (char)inputStream.read();
        for(; !(lastChar == '\r' && curChar == '\n'); lastChar = curChar, curChar = (char)inputStream.read()){
            out.append(curChar);
        }

        out.deleteCharAt(out.length()-1); // Remove trailing \r
        return out.toString();
    }

    /** <h2>Read</h2>
     * reads a singular byte from the input stream.
     * @return The read byte. This may be -1 to signify EOF.
     */
    public int read() throws IOException {
        return inputStream.read();
    }

    public int read(byte[] buf) throws IOException {
        for(int i = 0; i < buf.length; i++){
            int read = inputStream.read();
            if(read == -1){
                return i;
            } else {
                buf[i] = (byte)inputStream.read();
            }
        }
        return buf.length;
    }
}
