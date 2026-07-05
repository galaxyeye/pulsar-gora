package ai.platon.gora.util;

import java.nio.ByteBuffer;

import org.apache.avro.util.Utf8;
import ai.platon.gora.examples.generated.WebPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestAvroUtils {

    @Test
    public void testDeepClonePersistent() throws Exception {
        CharSequence url = new Utf8("http://gora.apache.org/");
        WebPage.Builder builder = WebPage.newBuilder()
            .setUrl(url)
            .setContent(ByteBuffer.wrap("Gora".getBytes("UTF-8")));
        WebPage webPage = builder.build();
        WebPage clonedWebPage = AvroUtils.deepClonePersistent(webPage);
        assertNotNull(clonedWebPage);
        assertEquals(url, clonedWebPage.getUrl());
        assertNotNull(clonedWebPage.getContent());
        String clonedWebPageContent = new String(clonedWebPage.getContent().array(), "UTF-8");
        assertEquals("Gora", clonedWebPageContent);
    }

}
