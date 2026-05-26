package ai.platon.gora.examples;

import java.io.IOException;

import ai.platon.gora.examples.generated.WebPage;
import ai.platon.gora.memory.store.MemStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestWebPageDataCreator {

  @Test 
  public void testCreatesData() throws IOException{
    MemStore<String, WebPage> dataStore = new MemStore<>();
    WebPageDataCreator.createWebPageData(dataStore);
  }
  
}
