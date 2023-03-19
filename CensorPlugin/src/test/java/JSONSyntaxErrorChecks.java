import org.example.plugins.MessageBusEvent;
import org.example.webserver.utils.Request;
import org.example.webserver.utils.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matt598.CensorPlugin.CensorPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.BiConsumer;

public class JSONSyntaxErrorChecks {


    @Test
    void WordPUTTestMalformedGuild() {
        try {
            final String malformedJSON = "{\"guild:\"1234\"}";

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final String req = "PUT /words HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "+malformedJSON.length()+"\r\n\r\n"+malformedJSON;
            final Request malformed = Request.readRequest(new BufferedReader(new StringReader(req)));
            Assertions.assertNotNull(malformed);

            BiConsumer<Request, OutputStream> subj = new CensorPlugin().getRESTEndpoints().get("words");
            Assertions.assertNotNull(subj);

            subj.accept(malformed, byteArrayOutputStream);
            Assertions.assertEquals(byteArrayOutputStream.toString(StandardCharsets.UTF_8), Response.badRequest("Malformed JSON."));
        } catch (Exception e){
            Assertions.fail("Request.readRequest() threw an IOException.");
        }
    }

    @Test
    void WordPUTTestMalformedArray() {
        try {

            CensorPlugin subject = new CensorPlugin();
            subject.initializePlugin(new ArrayList<>(), MessageBusEvent -> {});
            subject.postInitializePlugin();

            final String malformedJSON = "{\"guild\":\"1234\",\"words\":[\"test1\",\"test2\",}";

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final String req = "PUT /words HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "+malformedJSON.length()+"\r\n\r\n"+malformedJSON;
            final Request malformed = Request.readRequest(new BufferedReader(new StringReader(req)));
            Assertions.assertNotNull(malformed);

            BiConsumer<Request, OutputStream> subj = subject.getRESTEndpoints().get("words");
            Assertions.assertNotNull(subj);

            subj.accept(malformed, byteArrayOutputStream);
            Assertions.assertEquals(byteArrayOutputStream.toString(StandardCharsets.UTF_8), Response.badRequest("Malformed JSON."));
        } catch (Exception e){
            Assertions.fail("Request.readRequest() threw an IOException: "+e.getMessage());
        }
    }

    @Test
    void WordPUTTestValidRequest() {
        try {
            CensorPlugin subject = new CensorPlugin();
            subject.initializePlugin(new ArrayList<>(), MessageBusEvent -> {});
            subject.postInitializePlugin();

            final String malformedJSON = "{\"guild\":\"1234\",\"words\":[\"test1\",\"test2\",\"test3\"]}";

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final String req = "PUT /words HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "+malformedJSON.length()+"\r\n\r\n"+malformedJSON;
            final Request malformed = Request.readRequest(new BufferedReader(new StringReader(req)));
            Assertions.assertNotNull(malformed);

            BiConsumer<Request, OutputStream> subj = subject.getRESTEndpoints().get("words");
            Assertions.assertNotNull(subj);

            subj.accept(malformed, byteArrayOutputStream);
            Assertions.assertEquals(byteArrayOutputStream.toString(StandardCharsets.UTF_8), Response.okNoContent());
        } catch (Exception e){
            Assertions.fail("Request.readRequest() threw an IOException: "+e.getMessage());
        }
    }
}
